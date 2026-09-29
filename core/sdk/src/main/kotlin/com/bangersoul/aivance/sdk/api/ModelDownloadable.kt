package com.bangersoul.aivance.sdk.api

import com.bangersoul.aivance.core.common.result.Result

/**
 * Smaller alternative to a provider's primary on-device model, offered when the
 * device is constrained (limited free storage and/or RAM).
 *
 * @param name Human-readable model name, e.g. "FunctionGemma 270M".
 * @param sizeBytes Exact size of the model file in bytes (verified at build time).
 * @param url Direct HTTPS URL of the `.task` file.
 */
data class CompactModel(
    val name: String,
    val sizeBytes: Long,
    val url: String
)

/**
 * Implemented by AI providers that require a large model file to be present on
 * the device before they can run (e.g. on-device LLMs like Gemma).
 *
 * The provider self-reports readiness through [isModelReady]; the host UI
 * (Provider Management) uses [downloadModel] to fetch the model on demand and
 * [deleteModel] to reclaim storage. Providers implementing this interface are
 * keyless by design — the model file replaces an API key.
 */
interface ModelDownloadable {

    /**
     * True when the model file exists and the provider can generate responses.
     */
    val isModelReady: Boolean

    /**
     * Exact size in bytes of the primary model file. The host UI uses this to
     * show a truthful pre-download confirmation (free storage check + size),
     * so it must be the real file size, not an estimate.
     */
    val modelSizeBytes: Long

    /**
     * Optional smaller alternative model for constrained devices. When present,
     * the host UI offers it if free storage is insufficient for [modelSizeBytes]
     * or total RAM is low.
     */
    val compactModel: CompactModel?

    /**
     * Downloads (or verifies) the model file.
     *
     * @param url Optional explicit model URL to download. When null, the
     *   provider's configured/default primary URL is used. Used by the host UI
     *   to download [compactModel] on constrained devices.
     * @param onProgress Invoked on the caller's dispatcher with progress 0f..1f.
     * @return Success when the model is usable after download.
     */
    suspend fun downloadModel(
        url: String? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit>

    /**
     * Removes the downloaded model from device storage.
     */
    suspend fun deleteModel(): Result<Unit>
}
