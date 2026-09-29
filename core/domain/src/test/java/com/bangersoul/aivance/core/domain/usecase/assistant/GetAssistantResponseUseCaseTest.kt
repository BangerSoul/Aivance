package com.bangersoul.aivance.core.domain.usecase.assistant

import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.assistant.AssistantContextEngine
import com.bangersoul.aivance.core.domain.assistant.CapabilityRouter
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.api.ModelDownloadable
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderType
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import com.bangersoul.aivance.sdk.model.AiMessage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetAssistantResponseUseCaseTest {

    private lateinit var contextEngine: AssistantContextEngine
    private lateinit var capabilityRouter: CapabilityRouter
    private lateinit var providerManager: ProviderManager
    private lateinit var useCase: GetAssistantResponseUseCase

    @Before
    fun setUp() {
        contextEngine = mockk()
        capabilityRouter = mockk()
        providerManager = mockk()
        useCase = GetAssistantResponseUseCase(contextEngine, capabilityRouter, providerManager)
        coEvery { contextEngine.buildActiveContext() } returns "career context"
        // Default: no cloud provider, no on-device model.
        every { providerManager.getBestProviderFor(any()) } returns null
        every { providerManager.getOnDeviceProviderFor(any()) } returns null
    }

    // ── Streaming fallback routing ─────────────────────────────────

    @Test
    fun `streams from on-device model when cloud provider is unreachable`() = runTest {
        val cloud = FakeAiProvider(
            id = "cloud",
            streamChunks = listOf(Result.Failure(providerError("network down")))
        )
        val onDevice = FakeAiProvider(
            id = "gemma",
            isOnDevice = true,
            streamChunks = listOf(Result.Success("offline-answer"))
        )
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Streaming) } returns cloud
        every { providerManager.getOnDeviceProviderFor(ProviderCapability.AI.Streaming) } returns onDevice

        val chunks = useCase.stream(AssistantRequest("c1", "help me with my career")).toList()

        assertEquals(listOf("offline-answer"), chunks)
    }

    @Test
    fun `streams from on-device model when no cloud provider is configured`() = runTest {
        val onDevice = FakeAiProvider(
            id = "gemma",
            isOnDevice = true,
            streamChunks = listOf(Result.Success("offline"), Result.Success(" answer"))
        )
        every { providerManager.getOnDeviceProviderFor(ProviderCapability.AI.Streaming) } returns onDevice

        val chunks = useCase.stream(AssistantRequest("c1", "what should I do?")).toList()

        assertEquals(listOf("offline", " answer"), chunks)
    }

    @Test
    fun `does not re-try the on-device provider when it is already the best streaming provider`() = runTest {
        // When the on-device model is the only configured provider it IS the
        // best streaming provider; when it fails, the fallback must NOT invoke
        // the same instance a second time (identity guard) — it goes straight
        // to the Copilot fallback.
        val onDevice = FakeAiProvider(
            id = "gemma",
            isOnDevice = true,
            streamChunks = listOf(Result.Failure(providerError("model failed")))
        )
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Streaming) } returns onDevice
        every { providerManager.getOnDeviceProviderFor(ProviderCapability.AI.Streaming) } returns onDevice

        val chunks = useCase.stream(AssistantRequest("c1", "hello")).toList()

        assertTrue(chunks.single().contains("AiVance Copilot"))
    }

    @Test
    fun `prefers cloud provider when it responds`() = runTest {
        val cloud = FakeAiProvider(
            id = "cloud",
            streamChunks = listOf(Result.Success("cloud-answer"))
        )
        val onDevice = FakeAiProvider(id = "gemma", isOnDevice = true)
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Streaming) } returns cloud
        every { providerManager.getOnDeviceProviderFor(ProviderCapability.AI.Streaming) } returns onDevice

        val chunks = useCase.stream(AssistantRequest("c1", "hello")).toList()

        assertEquals(listOf("cloud-answer"), chunks)
    }

    @Test
    fun `falls back to copilot when both cloud and on-device fail`() = runTest {
        val cloud = FakeAiProvider(
            id = "cloud",
            streamChunks = listOf(Result.Failure(providerError("down")))
        )
        val onDevice = FakeAiProvider(
            id = "gemma",
            isOnDevice = true,
            streamChunks = listOf(Result.Failure(providerError("no model")))
        )
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Streaming) } returns cloud
        every { providerManager.getOnDeviceProviderFor(ProviderCapability.AI.Streaming) } returns onDevice

        val chunks = useCase.stream(AssistantRequest("c1", "hello")).toList()

        assertTrue(chunks.single().contains("Hello! I am your AiVance Copilot"))
    }

    @Test
    fun `falls back to copilot when nothing is configured`() = runTest {
        val chunks = useCase.stream(AssistantRequest("c1", "hello")).toList()
        assertTrue(chunks.single().contains("AiVance Copilot"))
    }

    // ── Mid-stream failure semantics (truthful, no fake completion) ─

    @Test
    fun `provider failing AFTER partial tokens ends in a terminal failure, not a completed answer`() = runTest {
        // Emits two real tokens, then a failure chunk. This must NOT be presented
        // as a completed response, and must NOT silently fall through to another
        // provider that concatenates its own output.
        val cloud = FakeAiProvider(
            id = "cloud",
            streamChunks = listOf(
                Result.Success("partial "),
                Result.Success("answer"),
                Result.Failure(providerError("connection reset mid-stream"))
            )
        )
        val onDevice = FakeAiProvider(
            id = "gemma",
            isOnDevice = true,
            streamChunks = listOf(Result.Success("SHOULD-NOT-APPEAR"))
        )
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Streaming) } returns cloud
        every { providerManager.getOnDeviceProviderFor(ProviderCapability.AI.Streaming) } returns onDevice
        every { providerManager.getOnDeviceProviderFor(ProviderCapability.AI.Chat) } returns onDevice

        val collected = mutableListOf<String>()
        var terminalError: Throwable? = null
        try {
            useCase.stream(AssistantRequest("c1", "help me with my career")).collect { collected.add(it) }
        } catch (e: Exception) {
            terminalError = e
        }

        // The partial tokens the user already saw are preserved...
        assertEquals(listOf("partial ", "answer"), collected)
        // ...and the flow ends in a truthful terminal failure (no fabricated completion).
        assertTrue("expected a terminal failure after partial output", terminalError != null)
        // The on-device provider's output must never be appended after partial cloud tokens.
        assertTrue(collected.none { it.contains("SHOULD-NOT-APPEAR") })
    }

    @Test
    fun `provider failing BEFORE the first token falls back safely to on-device`() = runTest {
        // No token emitted before failure -> safe to fall back (Case A).
        val cloud = FakeAiProvider(
            id = "cloud",
            streamChunks = listOf(Result.Failure(providerError("down before first token")))
        )
        val onDevice = FakeAiProvider(
            id = "gemma",
            isOnDevice = true,
            streamChunks = listOf(Result.Success("offline-answer"))
        )
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Streaming) } returns cloud
        every { providerManager.getOnDeviceProviderFor(ProviderCapability.AI.Streaming) } returns onDevice

        val chunks = useCase.stream(AssistantRequest("c1", "help me with my career")).toList()

        assertEquals(listOf("offline-answer"), chunks)
    }

    @Test
    fun `user cancellation is propagated, not swallowed into fallback or error`() = runTest {
        // A provider that throws CancellationException mid-stream (mirrors the
        // user cancelling the coroutine). It must surface as cancellation, and
        // must NOT trigger the on-device/Copilot fallback.
        val cloud = FakeAiProvider(id = "cloud", cancelAfter = 1,
            streamChunks = listOf(Result.Success("tok1"), Result.Success("tok2")))
        val onDevice = FakeAiProvider(
            id = "gemma", isOnDevice = true,
            streamChunks = listOf(Result.Success("SHOULD-NOT-APPEAR"))
        )
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Streaming) } returns cloud
        every { providerManager.getOnDeviceProviderFor(ProviderCapability.AI.Streaming) } returns onDevice
        every { providerManager.getOnDeviceProviderFor(ProviderCapability.AI.Chat) } returns onDevice

        val collected = mutableListOf<String>()
        var cancelled = false
        try {
            useCase.stream(AssistantRequest("c1", "help me with my career")).collect { collected.add(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }

        assertTrue("cancellation must propagate", cancelled)
        assertTrue(collected.none { it.contains("SHOULD-NOT-APPEAR") })
    }

    // ── Intent routing keys off the raw user message ────────────────

    @Test
    fun `routes intent on the raw message, not the context-laden orchestrated prompt`() = runTest {
        // The orchestrated prompt embeds platform context like "Latest ATS
        // Score: 0%" — matching keywords against it would misroute every
        // message to ANALYZE_RESUME and the LLM would never be reached.
        coEvery { capabilityRouter.routeIntent(any(), any()) } returns
            com.bangersoul.aivance.core.common.result.Result.Success("routed-response")
        val provider = FakeAiProvider(
            id = "cloud",
            streamChunks = listOf(Result.Success("llm-answer"))
        )
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Streaming) } returns provider

        val orchestrated = "You are an ATS Expert.\nLatest ATS Score: 0%\nUSER MESSAGE:\nhow are you today"
        val chunks = useCase.stream(
            AssistantRequest("c1", userMessage = orchestrated, rawUserMessage = "how are you today")
        ).toList()

        assertEquals(listOf("llm-answer"), chunks)
    }

    @Test
    fun `still routes capability intents detected from the raw message`() = runTest {
        coEvery { capabilityRouter.routeIntent("ANALYZE_RESUME", any()) } returns
            com.bangersoul.aivance.core.common.result.Result.Success("resume-routed")

        val chunks = useCase.stream(
            AssistantRequest(
                "c1",
                userMessage = "context with ATS Score 0% wrapped around the message",
                rawUserMessage = "analyze my resume"
            )
        ).toList()

        assertEquals(listOf("resume-routed"), chunks)
    }

    // ── One-shot invocation fallback ───────────────────────────────

    @Test
    fun `invoke uses on-device model when cloud provider is unreachable`() = runTest {
        val cloud = FakeAiProvider(id = "cloud", chatResult = Result.Failure(providerError("down")))
        val onDevice = FakeAiProvider(id = "gemma", isOnDevice = true, chatResult = Result.Success("offline-answer"))
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Chat) } returns cloud
        every { providerManager.getOnDeviceProviderFor(ProviderCapability.AI.Chat) } returns onDevice

        val result = useCase(AssistantRequest("c1", "help"))

        assertTrue(result.isSuccess)
        assertEquals("offline-answer", (result as Result.Success).data)
    }

    @Test
    fun `invoke falls back to copilot when on-device model is not downloaded`() = runTest {
        val cloud = FakeAiProvider(id = "cloud", chatResult = Result.Failure(providerError("down")))
        every { providerManager.getBestProviderFor(ProviderCapability.AI.Chat) } returns cloud
        // No on-device model present.

        val result = useCase(AssistantRequest("c1", "hello"))

        assertTrue(result.isSuccess)
        assertTrue((result as Result.Success).data.contains("AiVance Copilot"))
    }

    @Test
    fun `invoke surfaces an error when no provider exists at all`() = runTest {
        val result = useCase(AssistantRequest("c1", "hello"))

        assertTrue(result.isFailure)
        val failure = result as Result.Failure
        assertTrue(failure.error.message!!.contains("No AI provider configured"))
    }

    private fun providerError(message: String) =
        com.bangersoul.aivance.core.common.result.ProviderError("x", message = message)

    /**
     * Minimal AI provider double. When [isOnDevice] is true it also implements
     * [ModelDownloadable] with a ready model, mirroring the Gemma provider.
     */
    private class FakeAiProvider(
        id: String,
        private val chatResult: Result<String> = Result.Success("chat-answer"),
        private val streamChunks: List<Result<String>> = listOf(Result.Success("stream-answer")),
        val isOnDevice: Boolean = false,
        /**
         * When set, the stream emits [cancelAfter] chunks and then throws a
         * [kotlinx.coroutines.CancellationException], simulating the user
         * cancelling the collecting coroutine mid-stream.
         */
        private val cancelAfter: Int? = null
    ) : AIProvider(
        metadata = ProviderMetadata(
            id = id,
            name = "Fake $id",
            type = ProviderType.AI,
            version = "1.0.0",
            description = "fake",
            author = "test"
        ),
        capabilities = setOf(ProviderCapability.AI.Chat, ProviderCapability.AI.Streaming)
    ), ModelDownloadable {

        override val isModelReady: Boolean = isOnDevice
        override val modelSizeBytes: Long = 1_000L
        override val compactModel: com.bangersoul.aivance.sdk.api.CompactModel? = null

        override suspend fun generateText(prompt: String): Result<String> = chatResult
        override suspend fun chat(messages: List<AiMessage>): Result<String> = chatResult
        override fun streamText(prompt: String): Flow<String> = flowOf("stream-answer")
        override fun streamChat(messages: List<AiMessage>): Flow<Result<String>> =
            if (cancelAfter != null) {
                kotlinx.coroutines.flow.flow {
                    streamChunks.take(cancelAfter).forEach { emit(it) }
                    throw kotlinx.coroutines.CancellationException("user cancelled")
                }
            } else {
                streamChunks.asFlow()
            }

        override suspend fun listModels(): Result<List<String>> = Result.Success(emptyList())

        override suspend fun downloadModel(
            url: String?,
            onProgress: (Float) -> Unit
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun deleteModel(): Result<Unit> = Result.Success(Unit)

        override suspend fun onInitialize() {}
        override suspend fun onStart() {}
        override suspend fun onStop() {}
        override suspend fun onDispose() {}
    }
}
