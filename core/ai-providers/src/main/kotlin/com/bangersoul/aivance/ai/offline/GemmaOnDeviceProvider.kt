package com.bangersoul.aivance.ai.offline

import android.content.Context
import com.bangersoul.aivance.core.common.enums.MessageRole
import com.bangersoul.aivance.core.common.result.DomainError
import com.bangersoul.aivance.core.common.result.ProviderError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.api.CompactModel
import com.bangersoul.aivance.sdk.api.ModelDownloadable
import com.bangersoul.aivance.sdk.config.ProviderConfiguration
import com.bangersoul.aivance.sdk.core.ConfigField
import com.bangersoul.aivance.sdk.core.FieldType
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.core.ProviderType
import com.bangersoul.aivance.sdk.model.AiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Keyless on-device AI provider running a quantized Gemma model locally via
 * MediaPipe LLM Inference. No API key, no network once the model is downloaded.
 *
 * The primary model file (int4 Gemma 3N E2B, 3,136,226,711 bytes ≈ 2.9 GiB —
 * exact size verified against the live Hugging Face artifact, see
 * [DEFAULT_MODEL_URL]) is downloaded at runtime into app-private storage on
 * first use; until then the provider self-reports
 * [ProviderStatus.InvalidConfiguration] so it is never auto-selected by
 * [com.bangersoul.aivance.sdk.infrastructure.ProviderManager].
 *
 * A compact alternative ([COMPACT_MODEL_URL], FunctionGemma 270M int8,
 * 284,342,855 bytes ≈ 271 MiB) is offered on constrained devices: it needs
 * roughly 10× less storage and its smaller KV cache lowers peak RAM usage.
 *
 * ## Model distribution & licensing
 *
 * The default [DEFAULT_MODEL_URL] and [COMPACT_MODEL_URL] point to Hugging Face
 * artifacts (the Gemma primary is a community-hosted mirror, not an official
 * Google binary). Deployments should self-host the model file and override
 * `modelUrl` to their own URL. Gemma is governed by the Gemma Terms of Use (a
 * custom license, not Apache-2.0): apps that expose the model to end users must
 * bind those users to the Gemma Prohibited Use Policy via the app's Terms of
 * Service.
 */
class GemmaOnDeviceProvider(
    private val context: Context,
    private var config: ProviderConfiguration,
    private val downloader: ModelFileDownloader = OkHttpModelFileDownloader(),
    private val engineFactory: (Context, String) -> OnDeviceLlmEngine = { ctx, path ->
        MediaPipeOnDeviceLlmEngine.create(ctx, path)
    }
) : AIProvider(
    metadata = ProviderMetadata(
        id = "gemma",
        name = "Gemma (On-device)",
        type = ProviderType.AI,
        version = "1.0.0",
        description = "Runs Gemma 3N E2B fully offline on your device — no API key, no cloud. Model (int4, ~2.9 GB) downloads once on first use; a smaller ~271 MB variant is offered on low-storage devices.",
        author = "Google",
        configFields = listOf(
            ConfigField(
                key = "modelUrl",
                label = "Model file URL (.task, int4 Gemma 3N E2B)",
                isRequired = false,
                isSensitive = false,
                fieldType = FieldType.TEXT,
                hint = "Optional direct HTTPS URL to a Gemma 3N E2B int4 .task file (~2.9 GB). If blank, uses the bundled default source."
            )
        ),
        supportedModels = listOf("gemma-3n-e2b-it-int4")
    ),
    capabilities = setOf(
        ProviderCapability.AI.Chat,
        ProviderCapability.AI.TextGeneration,
        ProviderCapability.AI.Streaming
    )
), ModelDownloadable {

    /** Model file name — matches the supported model id. */
    private val modelFileName = "gemma-3n-e2b-it-int4.task"

    /** App-private model directory. */
    private val modelDir: File
        get() = File(context.filesDir, "models")

    /** Absolute path of the model file. */
    private val modelFile: File
        get() = File(modelDir, modelFileName)

    private val loadMutex = Mutex()

    @Volatile
    private var engine: OnDeviceLlmEngine? = null

    // ── Configuration ──────────────────────────────────────────────

    override val isConfigured: Boolean
        get() = isModelReady

    override val hasCredentials: Boolean
        // Keyless: no user secrets. hasCredentials=false keeps a keyed provider
        // (Groq/OpenAI/Claude) preferred over this offline one when both are set up.
        get() = false

    override val isModelReady: Boolean
        get() = modelFile.exists() && modelFile.length() > 0

    /** Exact size of the primary model file — verified against the live artifact. */
    override val modelSizeBytes: Long = DEFAULT_MODEL_SIZE_BYTES

    /** Smaller int8 alternative for constrained devices (≈271 MB, 10× smaller). */
    override val compactModel: CompactModel = CompactModel(
        name = "FunctionGemma 270M (compact)",
        sizeBytes = COMPACT_MODEL_SIZE_BYTES,
        url = COMPACT_MODEL_URL
    )

    private val modelUrl: String
        get() = config.settings["modelUrl"] ?: DEFAULT_MODEL_URL

    override suspend fun applyConfiguration(config: ProviderConfiguration) {
        this.config = config
    }

    // ── Lifecycle ──────────────────────────────────────────────────

    override suspend fun onInitialize() {
        updateStatus(ProviderStatus.Initializing)
        if (isModelReady) {
            try {
                loadEngine()
                updateStatus(ProviderStatus.Ready)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load on-device model")
                updateStatus(ProviderStatus.Error)
            }
        } else {
            // Model not downloaded yet — stay out of Ready so provider selection
            // never picks an unusable on-device model.
            updateStatus(ProviderStatus.InvalidConfiguration)
        }
    }

    override suspend fun onStart() {
        if (status == ProviderStatus.Ready) {
            updateStatus(ProviderStatus.Active)
        }
    }

    override suspend fun onStop() {
        if (status == ProviderStatus.Active) {
            updateStatus(ProviderStatus.Ready)
        }
    }

    override suspend fun onDispose() {
        engine?.close()
        engine = null
        updateStatus(ProviderStatus.Disposed)
    }

    override suspend fun checkHealth(): ProviderStatus {
        return if (isModelReady && engine != null) ProviderStatus.Ready else ProviderStatus.InvalidConfiguration
    }

    // ── Model management (ModelDownloadable) ───────────────────────

    override suspend fun downloadModel(
        url: String?,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        val effectiveUrl = url ?: modelUrl
        return loadMutex.withLock {
            if (isModelReady) {
                onProgress(1f)
                return@withLock Result.Success(Unit)
            }
            updateStatus(ProviderStatus.Initializing)
            when (val result = downloader.download(effectiveUrl, modelFile, onProgress)) {
                is Result.Success -> {
                    try {
                        loadEngine()
                        updateStatus(ProviderStatus.Ready)
                        Result.Success(Unit)
                    } catch (e: Exception) {
                        // Model file present but unloadable — surface the failure
                        // rather than claiming readiness.
                        modelFile.delete()
                        updateStatus(ProviderStatus.Error)
                        Result.Failure(ProviderError(metadata.id, message = e.message ?: "Model failed to load", cause = e))
                    }
                }
                is Result.Failure -> {
                    updateStatus(ProviderStatus.InvalidConfiguration)
                    result
                }
            }
        }
    }

    override suspend fun deleteModel(): Result<Unit> {
        return loadMutex.withLock {
            engine?.close()
            engine = null
            val deleted = modelFile.delete()
            // Also clean any leftover partial download.
            File(modelDir, "${modelFileName}.part").delete()
            updateStatus(ProviderStatus.InvalidConfiguration)
            if (deleted || !modelFile.exists()) Result.Success(Unit)
            else Result.Failure(ProviderError(metadata.id, message = "Failed to delete model file"))
        }
    }

    // ── AIProvider ─────────────────────────────────────────────────

    override suspend fun generateText(prompt: String): Result<String> {
        val engine = ensureEngine() ?: return modelNotReadyError()
        return try {
            Result.Success(withContext(Dispatchers.Default) { engine.generateResponse(prompt) })
        } catch (e: Exception) {
            Timber.e(e, "Gemma generateText failed")
            Result.Failure(ProviderError(metadata.id, message = e.message ?: "Generation failed", cause = e))
        }
    }

    override suspend fun chat(messages: List<AiMessage>): Result<String> {
        return generateText(messages.toPrompt())
    }

    override fun streamText(prompt: String): Flow<String> {
        return streamChat(listOf(AiMessage(MessageRole.USER, prompt)))
            .mapResultToText()
    }

    override fun streamChat(messages: List<AiMessage>): Flow<Result<String>> {
        val prompt = messages.toPrompt()
        return flow {
            val engine = ensureEngine()
            if (engine == null) {
                emit(Result.Failure(ProviderError(metadata.id, message = "On-device model not downloaded")))
                return@flow
            }
            engine.streamResponse(prompt).collect { partial ->
                emit(Result.Success(partial))
            }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun listModels(): Result<List<String>> {
        return Result.Success(metadata.supportedModels)
    }

    // ── Internals ──────────────────────────────────────────────────

    private suspend fun loadEngine() {
        val newEngine = engineFactory(context, modelFile.absolutePath)
        engine?.close()
        engine = newEngine
    }

    private suspend fun ensureEngine(): OnDeviceLlmEngine? {
        engine?.let { return it }
        return loadMutex.withLock {
            engine?.let { return it }
            if (!isModelReady) return null
            try {
                loadEngine()
                engine
            } catch (e: Exception) {
                Timber.e(e, "Failed to load on-device engine")
                null
            }
        }
    }

    private fun modelNotReadyError(): Result<String> {
        return Result.Failure(
            DomainError(
                message = "Gemma model is not downloaded yet. Open Provider Management → Gemma (On-device) → Download model."
            )
        )
    }

    private fun List<AiMessage>.toPrompt(): String {
        val system = filter { it.role == MessageRole.SYSTEM }
            .joinToString("\n") { it.content }
            .takeIf { it.isNotBlank() }
        val turns = filter { it.role != MessageRole.SYSTEM }
            .joinToString("\n") { msg ->
                val role = if (msg.role == MessageRole.ASSISTANT) "model" else "user"
                "<start_of_turn>$role\n${msg.content}\n<end_of_turn>"
            }
        return listOfNotNull(system, turns).joinToString("\n")
    }

    private fun Flow<Result<String>>.mapResultToText(): Flow<String> {
        return flow {
            collect { result ->
                when (result) {
                    is Result.Success -> emit(result.data)
                    is Result.Failure -> throw Exception(result.error.message, result.error.cause)
                }
            }
        }
    }

    companion object {
        /**
         * Default source for the quantized Gemma int4 model (Gemma 3N E2B,
         * a 2B-class model). File size verified via HTTP Range request on
         * 2026-08-08: content-range bytes 0-0/3136226711.
         */
        const val DEFAULT_MODEL_URL: String =
            "https://huggingface.co/realbyte/gemma-3n-E2B-it-int4-mediapipe/resolve/main/gemma-3n-E2B-it-int4.task"

        /** Exact byte size of [DEFAULT_MODEL_URL] (3136226711 bytes ≈ 2.9 GiB). */
        const val DEFAULT_MODEL_SIZE_BYTES: Long = 3_136_226_711L

        /**
         * Compact alternative for constrained devices: FunctionGemma 270M int8
         * converted to the MediaPipe `.task` format (Gemma license). File size
         * verified via HTTP Range request on 2026-08-08:
         * content-range bytes 0-0/284342855 (≈271 MiB).
         */
        const val COMPACT_MODEL_URL: String =
            "https://huggingface.co/2796gauravc/artha-functiongemma-270m-mediapipe/resolve/main/artha_functiongemma_v9_0_0.task"

        /** Exact byte size of [COMPACT_MODEL_URL] (284342855 bytes ≈ 271 MiB). */
        const val COMPACT_MODEL_SIZE_BYTES: Long = 284_342_855L
    }
}
