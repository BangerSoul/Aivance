package com.bangersoul.aivance.feature.profile

import com.bangersoul.aivance.sdk.core.ConfigField

/** Functional category shown in the provider manager. */
enum class ProviderCategory {
    AI,
    JOB,
    ENRICHMENT
}

data class ProviderInfo(
    val id: String,
    val name: String,
    val category: ProviderCategory = ProviderCategory.AI,
    val description: String = "",
    val isEnabled: Boolean = false,
    val isConnected: Boolean = false,
    val selectedModel: String = "",
    val availableModels: List<String> = emptyList(),
    val apiKeyConfigured: Boolean = false,
    /** Masked preview of the configured credential, e.g. `sk-…abcd` — never the full key. */
    val maskedApiKey: String = "",
    val healthStatus: ProviderHealthStatus = ProviderHealthStatus.UNKNOWN,
    /** True for keyless on-device providers (e.g. Gemma) that need a model download instead of an API key. */
    val isOnDevice: Boolean = false,
    /** For on-device providers: whether the model file is downloaded and usable. */
    val modelDownloaded: Boolean = false,
    /** For on-device providers: live download progress 0f..1f while downloading. */
    val modelDownloadProgress: Float? = null,
    /**
     * Metadata-declared credential/preference fields rendered as the provider's
     * config form. Drives Provider Management's multi-field onboarding (e.g.
     * Adzuna = `appId` + `appKey`, USAJobs = `apiKey`) instead of a hardcoded
     * single API-key input. Sensitive/PASSWORD fields are routed to encrypted
     * secrets; everything else is plaintext settings.
     */
    val configFields: List<ConfigField> = emptyList()
)

enum class ProviderHealthStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    UNKNOWN
}

/**
 * Pre-download confirmation shown before fetching an on-device model.
 *
 * @param providerId Provider whose model is being downloaded.
 * @param modelName Display name of the primary model.
 * @param modelSizeBytes Exact byte size of the primary model file.
 * @param compactName Display name of the smaller alternative, when offered.
 * @param compactSizeBytes Exact byte size of the compact model file.
 * @param freeStorageBytes Free app-data storage at the time of the check.
 * @param storageBlocked True when free storage is below the ≥2 GiB requirement
 *   (or the primary file cannot fit) — the primary download is not offered.
 * @param ramWarning True when total RAM is below the ≥4 GiB recommendation —
 *   the download is allowed but the user is warned and offered the compact model.
 * @param offersCompact True when a smaller alternative is available and the
 *   device is constrained (storage blocked or low RAM).
 */
data class ModelDownloadDialog(
    val providerId: String,
    val modelName: String,
    val modelSizeBytes: Long,
    val compactName: String? = null,
    val compactSizeBytes: Long = 0L,
    val freeStorageBytes: Long = 0L,
    val storageBlocked: Boolean = false,
    val ramWarning: Boolean = false,
    val offersCompact: Boolean = false
)

data class AppSettings(
    val themeMode: String = "system",
    val dynamicColorEnabled: Boolean = true,
    val language: String = "en",
    val analyticsEnabled: Boolean = true,
    val localProcessingOnly: Boolean = false,
    val autoSave: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val jobAlertsEnabled: Boolean = true,
    val interviewRemindersEnabled: Boolean = true,
    val followUpRemindersEnabled: Boolean = true
)
