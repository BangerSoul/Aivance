package com.bangersoul.aivance.core.datastore

import kotlinx.serialization.Serializable

@Serializable
data class UserPreferences(
    val onboardingCompleted: Boolean = false,

    /**
     * Deliberate provider-optional choice recorded by onboarding's "Continue
     * without AI" (the former silent Skip All).
     *
     * This is a product contract, not an error state: job discovery works
     * through free keyless providers (Arbeitnow/Jobicy/Adzuna/USAJobs) and the
     * assistant has a deterministic local Copilot fallback
     * ([com.bangersoul.aivance.core.domain.usecase.assistant.GetAssistantResponseUseCase]),
     * so a user who explicitly declines AI must still be able to use the app.
     * The central gate reads this only through that explicit choice — it is never
     * inferred from a missing provider, and never written by auth completion.
     */
    val providerOptional: Boolean = false,
    /**
     * Design language (BYOX P2): selects the token set — geometry, gradients,
     * glass luminance — composed by [AivanceTheme]. Serialized by name so
     * removing a kit in future builds falls back to the Aurora default.
     */
    val designKit: String = "AURORA_GLASS",
    val themeConfig: ThemeConfig = ThemeConfig.FOLLOW_SYSTEM,
    val accentSeed: String = "INDIGO",
    val dynamicColor: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val geminiApiKey: String? = null,

    /** Notification preferences (Settings Hub toggles). */
    val jobAlertsEnabled: Boolean = true,
    val interviewRemindersEnabled: Boolean = true,
    val followUpRemindersEnabled: Boolean = true,

    /** Persisted identity-provider subject for the v2 auth flow. SplashScreen
     * uses this to auto-login returning users without re-hitting the provider
     * on every cold start.
     */
    val userId: String? = null,
    val userEmail: String? = null,
    val userFirstName: String? = null,

    /** ISO-639 language code selected in Settings (default: English). */
    val language: String = "en"
)

enum class ThemeConfig {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
    AMOLED
}
