package com.bangersoul.aivance.feature.profile

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangersoul.aivance.core.database.dao.UserDao
import com.bangersoul.aivance.core.database.model.UserEntity
import com.bangersoul.aivance.core.datastore.UserPreferencesRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Error(val message: String) : AuthUiState
    data object Success : AuthUiState

    /** Brand-new account — route to ProviderSetup. */
    data object NewUser : AuthUiState

    /** Existing session — route straight to Dashboard. */
    data object ReturningUser : AuthUiState
}

sealed interface AuthUiEvent {
    /** Continue with a typed email (and, for new accounts, profile fields). */
    data class ContinueWithEmail(
        val email: String,
        val firstName: String = "",
        val lastName: String = "",
        val phone: String? = null
    ) : AuthUiEvent

    /** Google is the intended identity provider; wired once Firebase is configured. */
    data class ContinueWithGoogle(val context: Context? = null) : AuthUiEvent
}

/**
 * v2 authentication.
 *
 * Email continues to work as a provider-agnostic local session (account row in
 * Room + session id in DataStore so Splash auto-logs-in returning users).
 * Google Sign-In is wired to Firebase Auth via the Credential Manager API:
 * a Google ID token is minted, exchanged for a Firebase credential, and the
 * Firebase UID becomes the account primary key.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userDao: UserDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val trackEventUseCase: TrackEventUseCase,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEvent(event: AuthUiEvent) {
        when (event) {
            is AuthUiEvent.ContinueWithEmail -> continueWithEmail(event)
            is AuthUiEvent.ContinueWithGoogle -> continueWithGoogle(event.context)
        }
    }

    private fun continueWithEmail(event: AuthUiEvent.ContinueWithEmail) {
        val email = event.email.trim()
        if (!isValidEmail(email)) {
            _uiState.value = AuthUiState.Error("Enter a valid email address")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            trackEventUseCase(TrackEventRequest(eventName = "auth_email_continue"))

            // Existing account? Reuse its id so the session is stable across devices/restarts.
            // (Lookup is by email — the id primary key is a generated subject id.)
            val existing = userDao.getUserByEmail(email.lowercase())
            val isNew = existing == null

            val user = existing ?: UserEntity(
                id = "user_${UUID.randomUUID().toString().take(8)}",
                googleId = "",
                email = email.lowercase(),
                firstName = event.firstName.trim(),
                lastName = event.lastName.trim(),
                phone = event.phone?.trim()?.takeIf { it.isNotEmpty() }
            )
            userDao.upsertUser(user)
            userPreferencesRepository.updateSession(
                userId = user.id,
                email = user.email,
                firstName = user.firstName.ifBlank { user.email.substringBefore("@") }
            )            // R2.2: completing the account step is NOT onboarding completion and
            // must never grant product entry. The gate flags are set exclusively
            // by onboarding's own Finish / provider-optional choice; the nav graph
            // routes `AuthUiState.NewUser` to ProviderSetup and the persisted gate
            // keeps this user out of the product until a provider is configured (or
            // the user explicitly chooses provider-optional mode).
            _uiState.value = if (isNew) AuthUiState.NewUser else AuthUiState.ReturningUser
        }
    }

    private fun continueWithGoogle(context: Context? = null) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading

            try {
                val targetContext = context ?: appContext
                val webClientId = resolveWebClientId(targetContext)
                if (webClientId.isNullOrBlank()) {
                    _uiState.value = AuthUiState.Error(
                        "Google Sign-In needs a web client ID in google-services.json. " +
                            "In Firebase Console → Project settings → Your apps, add an OAuth client " +
                            "of type Web, then re-download google-services.json."
                    )
                    return@launch
                }

                val credentialManager = CredentialManager.create(targetContext)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(targetContext, request)
                val credential = result.credential

                val idToken = if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    try {
                        GoogleIdTokenCredential.createFrom(credential.data).idToken
                    } catch (e: GoogleIdTokenParsingException) {
                        throw Exception("Couldn't parse the Google ID token (${e.message})", e)
                    }
                } else {
                    throw Exception("Unexpected credential type returned by Google Sign-In")
                }
                if (idToken.isNullOrBlank()) {
                    throw Exception("Google Sign-In returned no ID token")
                }

                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = FirebaseAuth.getInstance()
                    .signInWithCredential(firebaseCredential)
                    .await()
                val firebaseUser = authResult.user
                    ?: throw Exception("Firebase sign-in returned no user")

                val uid = firebaseUser.uid
                val email = firebaseUser.email ?: ""
                val displayName = firebaseUser.displayName ?: ""
                val photoUrl = firebaseUser.photoUrl?.toString()

                trackEventUseCase(TrackEventRequest(eventName = "auth_google_success"))

                // Firebase UID is the account primary key; returning users keep
                // their existing row, brand-new users get one upserted.
                val existing = userDao.getUserById(uid)
                val isNew = existing == null
                val entity = existing ?: UserEntity(
                    id = uid,
                    googleId = uid,
                    email = email,
                    firstName = displayName.substringBefore(" ").trim(),
                    lastName = displayName.substringAfter(" ", "").trim(),
                    photoUrl = photoUrl
                )
                userDao.upsertUser(entity)
                userPreferencesRepository.updateSession(
                    userId = uid,
                    email = email,
                    firstName = entity.firstName.ifBlank { email.substringBefore("@") }
                )
                // R2.2: only a returning user may carry onboarding completion — a
                // fresh Google account goes through provider setup like a new email
                // account. This used to set `updateOnboardingCompleted(!isNew)`,
                // which let a brand-new Google account skip the provider gate.
                _uiState.value = if (isNew) AuthUiState.NewUser else AuthUiState.ReturningUser
            } catch (e: GetCredentialCancellationException) {
                // User dismissed the account picker — not an error.
                _uiState.value = AuthUiState.Idle
            } catch (e: NoCredentialException) {
                _uiState.value = AuthUiState.Error(
                    "No Google account found on this device. Add one in Settings → Accounts."
                )
            } catch (e: GetCredentialException) {
                _uiState.value = AuthUiState.Error(
                    "Google Sign-In failed: ${e.message ?: e.errorMessage ?: "unknown error"}"
                )
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(
                    "Google Sign-In failed: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    /**
     * Resolves the web client ID generated by the google-services plugin. Returns
     * null when google-services.json has no web OAuth client — the caller then
     * surfaces an actionable setup message instead of a dead-end sign-in.
     */
    private fun resolveWebClientId(context: Context): String? {
        val res = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        return if (res != 0) context.getString(res) else null
    }

    private fun isValidEmail(email: String): Boolean =
        android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

    /** Resets transient state after navigation consumed the result. */
    fun consumeResult() {
        if (_uiState.value is AuthUiState.NewUser || _uiState.value is AuthUiState.ReturningUser) {
            _uiState.value = AuthUiState.Idle
        }
    }
}
