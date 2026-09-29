package com.bangersoul.aivance.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.common.model.*
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.datastore.UserPreferencesRepository
import com.bangersoul.aivance.core.domain.repository.ProviderRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.provider.GetAvailableModelsUseCase
import com.bangersoul.aivance.core.domain.usecase.provider.GetProviderHealthUseCase
import com.bangersoul.aivance.core.domain.usecase.settings.*
import com.bangersoul.aivance.core.domain.usecase.user.*
import com.bangersoul.aivance.sdk.config.ProviderConfiguration
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.core.ProviderType
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import com.bangersoul.aivance.sdk.infrastructure.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IdentityHubUiState(
    val profile: UserProfile? = null,
    val draftProfile: UserProfile? = null,
    val settings: AppSettings = AppSettings(),
    val providers: List<ProviderInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaving: Boolean = false,
    val isEditing: Boolean = false
)

sealed interface IdentityHubUiEvent {
    data object Refresh : IdentityHubUiEvent
    data object ToggleEdit : IdentityHubUiEvent
    data class UpdateDraftProfile(val profile: UserProfile) : IdentityHubUiEvent
    data object SaveDraftProfile : IdentityHubUiEvent
    data class UpdateSettings(val settings: AppSettings) : IdentityHubUiEvent
    data class ToggleProvider(val providerId: String, val enabled: Boolean) : IdentityHubUiEvent
    data class TestProvider(val providerId: String) : IdentityHubUiEvent
    data class SaveProvider(val providerId: String, val apiKey: String, val model: String) : IdentityHubUiEvent
    data object SignOut : IdentityHubUiEvent
    data object ResetAll : IdentityHubUiEvent
}

sealed interface IdentityHubUiEffect {
    /** Emitted after the session is cleared so the UI can leave the hub. */
    data object SignOutCompleted : IdentityHubUiEffect
}

@HiltViewModel
class IdentityHubViewModel @Inject constructor(
    private val loadProfileUseCase: LoadProfileUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val loadSettingsUseCase: LoadSettingsUseCase,
    private val saveSettingsUseCase: SaveSettingsUseCase,
    private val resetSettingsUseCase: ResetSettingsUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val providerRegistry: ProviderRegistry,
    private val providerManager: ProviderManager,
    private val providerRepository: ProviderRepository,
    private val trackEventUseCase: TrackEventUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(IdentityHubUiState())
    val uiState: StateFlow<IdentityHubUiState> = _uiState.asStateFlow()

    private val _effects = Channel<IdentityHubUiEffect>(Channel.BUFFERED)
    val effects: Flow<IdentityHubUiEffect> = _effects.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            trackEventUseCase(TrackEventRequest("identity_hub_refresh"))

            combine(
                loadProfileUseCase(),
                loadSettingsFlow(),
                loadProvidersFlow()
            ) { profileRes, settings, providers ->
                val profile = profileRes.getOrNull()
                _uiState.update { it.copy(
                    profile = profile,
                    draftProfile = profile,
                    settings = settings,
                    providers = providers,
                    isLoading = false,
                    error = if (profileRes is Result.Failure) profileRes.error.message else null
                ) }
            }.collect()
        }
    }

    private fun loadSettingsFlow(): Flow<AppSettings> {
        return userPreferencesRepository.userPreferences.map { prefs ->
            AppSettings(
                jobAlertsEnabled = prefs.jobAlertsEnabled,
                interviewRemindersEnabled = prefs.interviewRemindersEnabled,
                followUpRemindersEnabled = prefs.followUpRemindersEnabled,
                language = prefs.language,
                themeMode = prefs.themeConfig.name.lowercase(),
                dynamicColorEnabled = prefs.dynamicColor,
                biometricLockEnabled = prefs.biometricLockEnabled
            )
        }
    }

    private fun loadProvidersFlow(): Flow<List<ProviderInfo>> {
        return providerManager.providerStatuses.map { statuses ->
            providerRegistry.getAllProviders().map { base ->
                val meta = base.metadata
                val persisted = providerRepository.getProviderConfig(meta.id)
                val secretValue = persisted?.secrets?.values?.firstOrNull { it.isNotBlank() }
                ProviderInfo(
                    id = meta.id,
                    name = meta.name,
                    category = when (meta.type) {
                        ProviderType.AI -> ProviderCategory.AI
                        ProviderType.JOB -> ProviderCategory.JOB
                        ProviderType.ENRICHMENT -> ProviderCategory.ENRICHMENT
                    },
                    isEnabled = persisted?.settings?.get("isEnabled")?.toBoolean() ?: (statuses[meta.id] == ProviderStatus.Active),
                    isConnected = secretValue != null,
                    maskedApiKey = secretValue?.let { if (it.length > 8) "${it.take(4)}...${it.takeLast(4)}" else "****" } ?: "",
                    healthStatus = mapStatus(statuses[meta.id] ?: base.status)
                )
            }
        }
    }

    private fun mapStatus(status: ProviderStatus): ProviderHealthStatus = when (status) {
        ProviderStatus.Active, ProviderStatus.Ready, ProviderStatus.Healthy -> ProviderHealthStatus.HEALTHY
        ProviderStatus.Degraded -> ProviderHealthStatus.DEGRADED
        ProviderStatus.Uninitialized, ProviderStatus.Initializing -> ProviderHealthStatus.UNKNOWN
        else -> ProviderHealthStatus.UNHEALTHY
    }

    fun onEvent(event: IdentityHubUiEvent) {
        when (event) {
            IdentityHubUiEvent.Refresh -> refresh()
            IdentityHubUiEvent.ToggleEdit -> toggleEdit()
            is IdentityHubUiEvent.UpdateDraftProfile -> updateDraftProfile(event.profile)
            IdentityHubUiEvent.SaveDraftProfile -> saveDraftProfile()
            is IdentityHubUiEvent.UpdateSettings -> updateSettings(event.settings)
            is IdentityHubUiEvent.ToggleProvider -> toggleProvider(event.providerId, event.enabled)
            is IdentityHubUiEvent.TestProvider -> testProvider(event.providerId)
            is IdentityHubUiEvent.SaveProvider -> saveProvider(event.providerId, event.apiKey, event.model)
            IdentityHubUiEvent.SignOut -> signOut()
            IdentityHubUiEvent.ResetAll -> resetAll()
        }
    }

    private fun toggleEdit() {
        _uiState.update { it.copy(isEditing = !it.isEditing, draftProfile = it.profile) }
    }

    private fun updateDraftProfile(profile: UserProfile) {
        _uiState.update { it.copy(draftProfile = profile) }
    }

    private fun saveDraftProfile() {
        val draft = _uiState.value.draftProfile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val request = UpdateProfileRequest(
                fullName = draft.fullName,
                phone = draft.phone,
                targetRole = draft.targetRole,
                currentRole = draft.currentRole,
                company = draft.company,
                linkedinUrl = draft.linkedinUrl,
                githubUrl = draft.githubUrl,
                dateOfBirth = draft.dateOfBirth,
                skills = draft.skills,
                experienceYears = draft.experienceYears,
                preferredIndustries = draft.preferredIndustries,
                salaryExpectation = draft.salaryExpectation,
                workPreference = draft.workPreference,
                visaRequired = draft.visaRequired,
                noticePeriod = draft.noticePeriod
            )
            val result = updateProfileUseCase(request)
            if (result is Result.Success) {
                _uiState.update { it.copy(isSaving = false, isEditing = false, profile = result.data) }
            } else {
                _uiState.update { it.copy(isSaving = false, error = (result as? Result.Failure)?.error?.message) }
            }
        }
    }

    private fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            userPreferencesRepository.updateJobAlertsEnabled(settings.jobAlertsEnabled)
            userPreferencesRepository.updateInterviewRemindersEnabled(settings.interviewRemindersEnabled)
            userPreferencesRepository.updateFollowUpRemindersEnabled(settings.followUpRemindersEnabled)
            userPreferencesRepository.updateLanguage(settings.language)
            userPreferencesRepository.updateDynamicColor(settings.dynamicColorEnabled)
            userPreferencesRepository.updateBiometricLockEnabled(settings.biometricLockEnabled)
        }
    }

    private fun toggleProvider(providerId: String, enabled: Boolean) {
        viewModelScope.launch {
            val persisted = providerRepository.getProviderConfig(providerId)
            val updatedConfig = (persisted ?: ProviderConfiguration(providerId)).copy(
                settings = (persisted?.settings ?: emptyMap()) + ("isEnabled" to enabled.toString())
            )
            providerRepository.saveProviderConfig(updatedConfig)
        }
    }

    private fun testProvider(providerId: String) {
        viewModelScope.launch {
            val config = providerRepository.getProviderConfig(providerId) ?: return@launch
            val result = providerManager.validateProvider(providerId, config)

            val updatedProviders = _uiState.value.providers.map {
                if (it.id == providerId) {
                    it.copy(healthStatus = if (result is Result.Success) ProviderHealthStatus.HEALTHY else ProviderHealthStatus.UNHEALTHY)
                } else it
            }
            _uiState.update { it.copy(providers = updatedProviders) }
        }
    }

    private fun saveProvider(providerId: String, apiKey: String, model: String) {
        viewModelScope.launch {
            val persisted = providerRepository.getProviderConfig(providerId)
            val updatedConfig = ProviderConfiguration(
                providerId = providerId,
                settings = (persisted?.settings ?: emptyMap()) + mapOf("selectedModel" to model, "isEnabled" to "true"),
                secrets = mapOf("apiKey" to apiKey)
            )
            providerRepository.saveProviderConfig(updatedConfig)
        }
    }

    private fun signOut() {
        viewModelScope.launch {
            userPreferencesRepository.clearSession()
            _effects.send(IdentityHubUiEffect.SignOutCompleted)
        }
    }

    private fun resetAll() {
        viewModelScope.launch {
            resetSettingsUseCase()
            refresh()
        }
    }
}
