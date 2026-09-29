package com.bangersoul.aivance.ai.offline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bangersoul.aivance.core.common.enums.MessageRole
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.sdk.config.ProviderConfiguration
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.model.AiMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class GemmaOnDeviceProviderTest {

    private lateinit var context: Context

    private var generatedPrompts = mutableListOf<String>()
    private var streamedPrompts = mutableListOf<String>()
    private var engineClosed = false

    private var lastDownloadUrl: String? = null
    private var downloadFailure: Exception? = null
    private var downloadCalls = 0

    private lateinit var provider: GemmaOnDeviceProvider

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Ensure a clean model dir per test.
        File(context.filesDir, "models").deleteRecursively()
        resetFakes()
        provider = createProvider()
    }

    private fun resetFakes() {
        generatedPrompts = mutableListOf()
        streamedPrompts = mutableListOf()
        engineClosed = false
        lastDownloadUrl = null
        downloadFailure = null
        downloadCalls = 0
    }

    private fun createProvider(config: ProviderConfiguration = ProviderConfiguration("gemma")): GemmaOnDeviceProvider {
        return GemmaOnDeviceProvider(
            context = context,
            config = config,
            downloader = object : ModelFileDownloader {
                override suspend fun download(
                    url: String,
                    destination: File,
                    onProgress: (Float) -> Unit
                ): Result<Unit> {
                    downloadCalls++
                    lastDownloadUrl = url
                    val failure = downloadFailure
                    if (failure != null) {
                        return Result.Failure(
                            com.bangersoul.aivance.core.common.result.ProviderError(
                                providerId = "gemma",
                                message = failure.message ?: "download failed",
                                cause = failure
                            )
                        )
                    }
                    destination.parentFile?.mkdirs()
                    destination.writeBytes(ByteArray(1024) { 1 })
                    onProgress(1f)
                    return Result.Success(Unit)
                }
            },
            engineFactory = { _, _ ->
                object : OnDeviceLlmEngine {
                    override fun generateResponse(prompt: String): String {
                        generatedPrompts.add(prompt)
                        return "offline-response-to:${prompt.take(20)}"
                    }

                    override fun streamResponse(prompt: String): Flow<String> {
                        streamedPrompts.add(prompt)
                        return flow {
                            emit("Hel")
                            emit("lo")
                        }
                    }

                    override fun close() {
                        engineClosed = true
                    }
                }
            }
        )
    }

    // ── Readiness / lifecycle ──────────────────────────────────────

    @Test
    fun `reports InvalidConfiguration before model download`() = runTest {
        provider.onInitialize()
        assertEquals(ProviderStatus.InvalidConfiguration, provider.status)
        assertFalse(provider.isConfigured)
        assertFalse(provider.isModelReady)
    }

    @Test
    fun `reports Ready after successful download`() = runTest {
        val result = provider.downloadModel()
        assertTrue(result is Result.Success)
        assertTrue(provider.isModelReady)
        assertTrue(provider.isConfigured)
        assertEquals(ProviderStatus.Ready, provider.status)
    }

    @Test
    fun `is keyless - no credentials required`() {
        assertFalse(provider.hasCredentials)
    }

    @Test
    fun `deleteModel clears file and readiness`() = runTest {
        provider.downloadModel()
        assertTrue(provider.isModelReady)

        val result = provider.deleteModel()
        assertTrue(result is Result.Success)
        assertFalse(provider.isModelReady)
        assertFalse(provider.isConfigured)
        assertEquals(ProviderStatus.InvalidConfiguration, provider.status)
    }

    @Test
    fun `download failure keeps provider unready`() = runTest {
        downloadFailure = RuntimeException("network down")
        val result = provider.downloadModel()
        assertTrue(result is Result.Failure)
        assertFalse(provider.isModelReady)
        assertEquals(ProviderStatus.InvalidConfiguration, provider.status)
    }

    @Test
    fun `second download is a no-op once ready`() = runTest {
        provider.downloadModel()
        assertEquals(1, downloadCalls)
        val result = provider.downloadModel()
        assertTrue(result is Result.Success)
        // Idempotent — no second network fetch when the model is already present.
        assertEquals(1, downloadCalls)
    }

    // ── Generation ─────────────────────────────────────────────────

    @Test
    fun `generateText fails honestly before model is downloaded`() = runTest {
        val result = provider.generateText("hello")
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message!!.contains("not downloaded"))
    }

    @Test
    fun `generateText delegates to the engine after download`() = runTest {
        provider.downloadModel()
        val result = provider.generateText("hello world")
        assertTrue(result is Result.Success)
        assertEquals("offline-response-to:hello world", (result as Result.Success).data)
        assertEquals(listOf("hello world"), generatedPrompts)
    }

    @Test
    fun `chat formats turns with gemma tags`() = runTest {
        provider.downloadModel()
        val result = provider.chat(
            listOf(
                AiMessage(MessageRole.USER, "Tell me a tip"),
                AiMessage(MessageRole.ASSISTANT, "Sure"),
                AiMessage(MessageRole.USER, "Another")
            )
        )
        assertTrue(result is Result.Success)
        assertTrue(generatedPrompts.single().contains("<start_of_turn>user"))
        assertTrue(generatedPrompts.single().contains("<end_of_turn>"))
    }

    @Test
    fun `streamChat emits partial chunks`() = runTest {
        provider.downloadModel()
        val chunks = provider.streamChat(listOf(AiMessage(MessageRole.USER, "hi")))
            .toList()
        assertEquals(
            listOf("Hel", "lo"),
            chunks.filterIsInstance<Result.Success<String>>().map { it.data }
        )
        assertTrue(streamedPrompts.single().contains("hi"))
    }

    @Test
    fun `streamText maps results to raw text`() = runTest {
        provider.downloadModel()
        val text = provider.streamText("hi").toList()
        assertEquals(listOf("Hel", "lo"), text)
    }

    @Test
    fun `dispose closes the engine`() = runTest {
        provider.downloadModel()
        provider.onDispose()
        assertTrue(engineClosed)
        assertEquals(ProviderStatus.Disposed, provider.status)
    }

    @Test
    fun `listModels returns the supported model id`() = runTest {
        val result = provider.listModels()
        assertEquals(listOf("gemma-3n-e2b-it-int4"), (result as Result.Success).data)
    }

    // ── Size / compact metadata (used by the pre-download capability dialog) ──

    @Test
    fun `exposes the verified primary model size`() {
        assertEquals(3_136_226_711L, provider.modelSizeBytes)
        assertEquals(3_136_226_711L, GemmaOnDeviceProvider.DEFAULT_MODEL_SIZE_BYTES)
    }

    @Test
    fun `exposes a smaller compact alternative`() {
        val compact = provider.compactModel
        assertEquals("FunctionGemma 270M (compact)", compact.name)
        assertEquals(284_342_855L, compact.sizeBytes)
        assertEquals(GemmaOnDeviceProvider.COMPACT_MODEL_SIZE_BYTES, compact.sizeBytes)
        assertEquals(GemmaOnDeviceProvider.COMPACT_MODEL_URL, compact.url)
        // The compact model is genuinely smaller than the primary.
        assertTrue(compact.sizeBytes < provider.modelSizeBytes / 10)
    }

    @Test
    fun `downloadModel with a compact URL downloads that URL`() = runTest {
        val compactUrl = provider.compactModel.url
        val result = provider.downloadModel(url = compactUrl)
        assertTrue(result is Result.Success)
        assertEquals(compactUrl, lastDownloadUrl)
        assertTrue(provider.isModelReady)
    }

    @Test
    fun `downloadModel without a URL uses the configured default`() = runTest {
        provider.downloadModel()
        assertEquals(GemmaOnDeviceProvider.DEFAULT_MODEL_URL, lastDownloadUrl)
    }

    @Test
    fun `custom modelUrl config overrides the default for URL-less downloads`() = runTest {
        val custom = "https://example.com/my-gemma.task"
        val configured = createProvider(
            ProviderConfiguration(
                providerId = "gemma",
                settings = mapOf("modelUrl" to custom)
            )
        )
        configured.downloadModel()
        assertEquals(custom, lastDownloadUrl)
    }
}
