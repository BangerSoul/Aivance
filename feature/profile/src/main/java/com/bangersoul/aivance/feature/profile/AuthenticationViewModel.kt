package com.bangersoul.aivance.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.database.dao.UserDao
import com.bangersoul.aivance.core.datastore.UserPreferences
import com.bangersoul.aivance.core.datastore.UserPreferencesRepository
import com.bangersoul.aivance.core.domain.repository.ProviderRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.provider.GetProviderHealthUseCase
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.core.ProviderType
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import com.bangersoul.aivance.sdk.infrastructure.ProviderRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthenticationUiState {
    data object Idle : AuthenticationUiState
    data object Loading : AuthenticationUiState
    data object Authenticated : AuthenticationUiState
    data object Unauthenticated : AuthenticationUiState
    data class Error(val message: String) : AuthenticationUiState
}

/**
 * The product-entry contract (R2.2 provider gate).
 *
 * [UNCONFIGURED] — no validated provider and no deliberate provider-optional
 * choice: the user belongs in onboarding's provider setup.
 * [OPTIONAL] — the user explicitly chose to continue without AI in onboarding.
 * This is a product contract, not an error state: job discovery runs on the
 * free keyless providers (Arbeitnow/Jobicy/Adzuna/USAJobs) and the assistant
 * has a deterministic local Copilot fallback
 * (`GetAssistantResponseUseCase`), so this user may use the app.
 * [CONFIGURED] — at least one AI provider is persisted and not reporting an
 * invalid configuration.
 * [INVALID] — a provider was configured at some point but now reports an
 * unusable configuration: the user is sent back to provider setup for
 * remediation instead of into a silently degraded product.
 */
enum class ProviderGateState { UNCONFIGURED, OPTIONAL, CONFIGURED, INVALID }

sealed interface AuthenticationUiEvent {
    data class Login(val apiKey: String) : AuthenticationUiEvent
    data object Logout : AuthenticationUiEvent
    data object CheckAuth : AuthenticationUiEvent
}

sealed interface AuthenticationUiEffect {
    data class ShowSnackbar(val message: String) : AuthenticationUiEffect
    data object NavigateToHome : AuthenticationUiEffect
    data object NavigateToOnboarding : AuthenticationUiEffect
}

@HiltViewModel
class AuthenticationViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val getProviderHealthUseCase: GetProviderHealthUseCase,
    private val trackEventUseCase: TrackEventUseCase,
    private val userDao: UserDao,
    private val providerManager: ProviderManager,
    private val providerRegistry: ProviderRegistry,
    private val providerRepository: ProviderRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthenticationUiState>(AuthenticationUiState.Loading)
    val uiState: StateFlow<AuthenticationUiState> = _uiState.asStateFlow()

    /**
     * Ids of AI providers the app can actually call, resolved from the live
     * registry (typed via [ProviderType], not an id-prefix heuristic). Used to
     * intersect the persisted configurations and the live status map.
     *
     * Declared BEFORE [providerGate]: that flow is `SharingStarted.Eagerly` on
     * `Dispatchers.Main.immediate`, so its combine can execute synchronously
     * during construction — property initializers run in declaration order, and
     * a combine reading this set before it is initialized would NPE on cold
     * start.
     */
    private val registeredAiProviderIds: Set<String> =
        providerRegistry.getAllProviders()
            .filter { it.metadata.type == ProviderType.AI }
            .map { it.metadata.id }
            .toSet()

    /**
     * Central, persisted-state evaluation of the provider configuration
     * contract (R2.2). This is the single authority the navigation layer
     * consults before granting product entry — provider validity is never
     * asserted by a UI step, and never inferred from `onboardingCompleted`
     * alone.
     *
     * `null` means the first evaluation has not landed yet: navigation defers
     * (neither grants nor blocks) until a value arrives, so the asynchronous
     * cold-start hydration (`AivanceApp.hydrateSavedProviderConfigs`) can never
     * flash a false lock-out. Cold-start *entry* additionally resolves the gate
     * synchronously through [resolveProviderGate], so the splash decision is
     * never made on unset state.
     *
     * The stored provider configurations (repository — the authoritative record
     * of what the user actually set up, written by onboarding AND by provider
     * management) and the live [ProviderManager.providerStatuses] (applied
     * runtime state) must agree. Live state alone is not trusted: hydration is
     * asynchronous and `initializeAll()` marks every registered provider `Ready`
     * even with zero configuration, so a bare live status is not evidence of a
     * configured product. The persisted record alone is not trusted because a
     * provider can be invalidated at runtime — detected through the
     * deterministic self-reported statuses ([ProviderStatus.InvalidConfiguration]
     * / [ProviderStatus.AuthenticationFailed]). Transient statuses (Offline,
     * Error after a failed network probe) deliberately do not lock a configured
     * user out of their own data.
     */
    val providerGate: StateFlow<ProviderGateState?> =
        combine(
            userPreferencesRepository.userPreferences,
            providerRepository.getProviderConfigs(),
            providerManager.providerStatuses
        ) { prefs, savedConfigs, statuses ->
            evaluateProviderGate(
                optional = prefs.providerOptional,
                onboarded = prefs.onboardingCompleted,
                hasSavedAiProvider = savedConfigs.any { it.providerId in registeredAiProviderIds },
                liveAiStatuses = statuses.filterKeys { it in registeredAiProviderIds }
            )
        }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    private val _effects = Channel<AuthenticationUiEffect>(Channel.BUFFERED)
    val effects: Flow<AuthenticationUiEffect> = _effects.receiveAsFlow()

    /**
     * One-shot, suspend gate resolution for the splash contract: reads the same
     * persisted + live sources as [providerGate] directly, so the cold-start
     * decision is made on settled data rather than racing the async hydration
     * (AivanceApp re-applies saved provider configs on a background dispatcher).
     */
    suspend fun resolveProviderGate(): ProviderGateState {
        val prefs = userPreferencesRepository.userPreferences.firstOrNull()
        val savedConfigs = providerRepository.getProviderConfigs().firstOrNull() ?: emptyList()
        return evaluateProviderGate(
            optional = prefs?.providerOptional == true,
            onboarded = prefs?.onboardingCompleted == true,
            hasSavedAiProvider = savedConfigs.any { it.providerId in registeredAiProviderIds },
            liveAiStatuses = providerManager.providerStatuses.value
                .filterKeys { it in registeredAiProviderIds }
        )
    }

    /** True once [providerGate] has produced its first evaluated value. */
    val providerGateSettled: Boolean
        get() = providerGate.value != null

    init {
        checkAuthentication()
    }

    fun onEvent(event: AuthenticationUiEvent) {
        when (event) {
            is AuthenticationUiEvent.Login -> login(event.apiKey)
            AuthenticationUiEvent.Logout -> logout()
            AuthenticationUiEvent.CheckAuth -> checkAuthentication()
        }
    }

    private fun checkAuthentication() {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferences.firstOrNull()
            // A completed onboarding (with any AI provider) is equivalent to being
            // authenticated; the legacy Gemini key check alone locked users out of the
            // app after onboarding with Ollama or other non-Gemini providers.
            val isOnboarded = prefs?.onboardingCompleted == true
            val hasKey = !prefs?.geminiApiKey.isNullOrBlank()

            // Google-authenticated sessions must still have a live Firebase user.
            // If the DataStore session points at a Google account but Firebase has
            // been signed out (server-side, another device, token expiry), force
            // re-authentication instead of silently trusting the local flag.
            val needsReauth = sessionNeedsReauth(prefs?.userId)

            val authenticated = (isOnboarded || hasKey) && needsReauth != true
            _uiState.value = if (authenticated) AuthenticationUiState.Authenticated
            else AuthenticationUiState.Unauthenticated
        }
    }

    /**
     * The gate itself — pure and testable (R2.2). Reads persisted DataStore
     * state (the record of the user's deliberate choice / configured provider)
     * plus the live provider statuses (whether that configuration still
     * validates), so neither a UI flag nor un-persisted runtime state can grant
     * entry.
     *
     * Precedence:
     * 1. An un-onboarded user is [ProviderGateState.UNCONFIGURED]; onboarding
     *    completion alone never grants entry.
     * 2. A stored AI-provider configuration is the product's own record that the
     *    user completed provider setup — the strongest signal, so it outranks
     *    the optional opt-out (a provider configured later from Settings

     *    upgrades an optional user to full mode automatically). The live map is
     *    consulted only to detect invalidation, never to grant entry during the
     *    async hydration window.
     * 3. The explicit provider-optional choice — a deliberate product contract,
     *    not an error state — grants entry in degraded mode.
     * 4. Onboarded with neither is the legacy "completed the steps without a
     *    provider" state and stays [ProviderGateState.UNCONFIGURED].
     *
     * Transient runtime statuses (Offline / Unavailable / Error) do not
     * hard-invalidate a configured user — a genuinely broken configuration
     * reports [ProviderStatus.InvalidConfiguration] or
     * [ProviderStatus.AuthenticationFailed].
     */
    fun evaluateProviderGate(
        optional: Boolean,
        onboarded: Boolean,
        hasSavedAiProvider: Boolean,
        liveAiStatuses: Map<String, ProviderStatus>
    ): ProviderGateState = when {
        !onboarded -> ProviderGateState.UNCONFIGURED
        hasSavedAiProvider ->
            if (liveAiStatuses.values.any {
                    it == ProviderStatus.InvalidConfiguration || it == ProviderStatus.AuthenticationFailed
                }
            ) {
                ProviderGateState.INVALID
            } else {
                ProviderGateState.CONFIGURED
            }
        optional -> ProviderGateState.OPTIONAL
        else -> ProviderGateState.UNCONFIGURED
    }

    /**
     * @return true when the stored session belongs to a Google account whose
     * Firebase session is no longer valid, false when the session is valid, and
     * null when Firebase isn't configured or the session can't be classified.
     */
    private suspend fun sessionNeedsReauth(sessionUserId: String?): Boolean? {
        if (sessionUserId.isNullOrBlank()) return null
        return try {
            if (FirebaseApp.getApps(appContext).isEmpty()) return null
            if (FirebaseAuth.getInstance().currentUser != null) return false
            // No live Firebase user: only force re-auth when the stored session
            // was created through Google Sign-In (email sessions stay local).
            userDao.getUserById(sessionUserId)?.googleId?.isNotBlank() == true
        } catch (e: Exception) {
            null
        }
    }

    private fun login(apiKey: String) {
        if (apiKey.isBlank()) {
            _uiState.value = AuthenticationUiState.Error("API key is required")
            return
        }
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest(eventName = "auth_login"))
            _uiState.value = AuthenticationUiState.Loading

            try {
                userPreferencesRepository.updateGeminiApiKey(apiKey)
                _uiState.value = AuthenticationUiState.Authenticated
                _effects.send(AuthenticationUiEffect.NavigateToHome)
                _effects.send(AuthenticationUiEffect.ShowSnackbar("Authenticated successfully"))
            } catch (e: Exception) {
                _uiState.value = AuthenticationUiState.Error(e.message ?: "Authentication failed")
            }
        }
    }

    private fun logout() {
        viewModelScope.launch {
            trackEventUseCase(TrackEventRequest(eventName = "auth_logout"))
            userPreferencesRepository.updateGeminiApiKey("")
            // Reset the onboarding gate too — checkAuthentication() treats
            // onboardingCompleted == true as Authenticated, so a sign-out that
            // only cleared the API key would silently log the user back in on
            // the next cold start. The provider-optional choice is cleared with
            // it: it belongs to the onboarding session that made it (R2.2).
            userPreferencesRepository.updateOnboardingCompleted(false)
            userPreferencesRepository.updateProviderOptional(false)
            userPreferencesRepository.clearSession()
            // Best-effort Firebase sign-out so the Google account isn't silently
            // re-authenticated on the next cold start (local-only; safe to skip
            // when Firebase isn't configured).
            try {
                if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                    FirebaseAuth.getInstance().signOut()
                }
            } catch (e: Exception) {
                // Local session reset above is sufficient when Firebase is absent.
            }
            _uiState.value = AuthenticationUiState.Unauthenticated
            _effects.send(AuthenticationUiEffect.NavigateToOnboarding)
            _effects.send(AuthenticationUiEffect.ShowSnackbar("Logged out"))
        }
    }
}
