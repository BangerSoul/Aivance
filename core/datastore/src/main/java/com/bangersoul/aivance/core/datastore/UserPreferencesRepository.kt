package com.bangersoul.aivance.core.datastore

import androidx.datastore.core.DataStore
import com.bangersoul.aivance.core.database.security.EncryptionService
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface UserPreferencesRepository {
    val userPreferences: Flow<UserPreferences>
    suspend fun updateGeminiApiKey(apiKey: String)
    suspend fun updateOnboardingCompleted(completed: Boolean)

    /** Persists the explicit provider-optional choice made in onboarding (R2.2). */
    suspend fun updateProviderOptional(optional: Boolean)
    suspend fun updateThemeConfig(themeConfig: ThemeConfig)
    suspend fun updateAccentSeed(accentSeed: String)
    suspend fun updateDynamicColor(enabled: Boolean)
    suspend fun updateBiometricLockEnabled(enabled: Boolean)
    suspend fun updateJobAlertsEnabled(enabled: Boolean)
    suspend fun updateInterviewRemindersEnabled(enabled: Boolean)
    suspend fun updateFollowUpRemindersEnabled(enabled: Boolean)

    /** Persists the ISO-639 language code chosen in Settings. */
    suspend fun updateLanguage(language: String)

    /** Persists the signed-in user's session identity so Splash can auto-login. */
    suspend fun updateSession(userId: String?, email: String? = null, firstName: String? = null)
    suspend fun clearSession()
}

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<UserPreferences>,
    private val encryptionService: EncryptionService
) : UserPreferencesRepository {

    override val userPreferences: Flow<UserPreferences> = dataStore.data

    override suspend fun updateGeminiApiKey(apiKey: String) {
        // Security audit S-07: never persist a credential in plaintext. The
        // legacy field doubles as the Apify token, so encrypt it at rest.
        // Empty clears the value; failures propagate (fail-closed).
        val stored = if (apiKey.isBlank()) "" else encryptionService.encrypt(apiKey)
        dataStore.updateData {
            it.copy(geminiApiKey = stored)
        }
    }

    override suspend fun updateOnboardingCompleted(completed: Boolean) {
        dataStore.updateData {
            it.copy(onboardingCompleted = completed)
        }
    }

    override suspend fun updateProviderOptional(optional: Boolean) {
        dataStore.updateData {
            it.copy(providerOptional = optional)
        }
    }

    override suspend fun updateThemeConfig(themeConfig: ThemeConfig) {
        dataStore.updateData {
            it.copy(themeConfig = themeConfig)
        }
    }

    override suspend fun updateAccentSeed(accentSeed: String) {
        dataStore.updateData {
            it.copy(accentSeed = accentSeed)
        }
    }

    override suspend fun updateDynamicColor(enabled: Boolean) {
        dataStore.updateData { it.copy(dynamicColor = enabled) }
    }

    override suspend fun updateBiometricLockEnabled(enabled: Boolean) {
        dataStore.updateData { it.copy(biometricLockEnabled = enabled) }
    }

    override suspend fun updateJobAlertsEnabled(enabled: Boolean) {
        dataStore.updateData {
            it.copy(jobAlertsEnabled = enabled)
        }
    }

    override suspend fun updateInterviewRemindersEnabled(enabled: Boolean) {
        dataStore.updateData {
            it.copy(interviewRemindersEnabled = enabled)
        }
    }

    override suspend fun updateFollowUpRemindersEnabled(enabled: Boolean) {
        dataStore.updateData {
            it.copy(followUpRemindersEnabled = enabled)
        }
    }

    override suspend fun updateLanguage(language: String) {
        dataStore.updateData {
            it.copy(language = language)
        }
    }

    override suspend fun updateSession(userId: String?, email: String?, firstName: String?) {
        dataStore.updateData {
            it.copy(
                userId = userId,
                userEmail = email,
                userFirstName = firstName
            )
        }
    }

    override suspend fun clearSession() {
        dataStore.updateData {
            it.copy(userId = null, userEmail = null, userFirstName = null)
        }
    }
}
