package com.bangersoul.aivance.feature.assistant

import com.bangersoul.aivance.core.common.model.AssistantJobContext
import com.bangersoul.aivance.core.common.model.CareerState
import com.bangersoul.aivance.core.common.model.ProfileState
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.engine.CareerIntent
import com.bangersoul.aivance.core.domain.engine.CareerStateEngine
import com.bangersoul.aivance.core.domain.engine.ContextEngine
import com.bangersoul.aivance.core.domain.engine.IntentEngine
import com.bangersoul.aivance.core.domain.engine.PromptOrchestrator
import com.bangersoul.aivance.core.domain.repository.AssistantRepository
import com.bangersoul.aivance.core.domain.usecase.assistant.AssistantRequest
import com.bangersoul.aivance.core.domain.usecase.assistant.GetAssistantResponseUseCase
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.core.ProviderType
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import com.bangersoul.aivance.sdk.infrastructure.ProviderRegistry
import com.bangersoul.aivance.sdk.model.AiMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockRepository: AssistantRepository = mockk()
    private val mockResponseUseCase: GetAssistantResponseUseCase = mockk()
    private val mockProviderManager: ProviderManager = mockk()
    private val mockProviderRegistry: ProviderRegistry = mockk()
    private val mockStateEngine: CareerStateEngine = mockk()
    private val mockContextEngine: ContextEngine = mockk()
    private val mockIntentEngine: IntentEngine = mockk()
    private val mockPromptOrchestrator: PromptOrchestrator = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockRepository.saveMessage(any(), any(), any()) } returns Result.Success(1L)
        every { mockRepository.getMessages(any()) } returns flowOf(Result.Success(emptyList()))
        every { mockProviderManager.providerStatuses } returns MutableStateFlow(
            mapOf("groq" to ProviderStatus.Active, "naukri" to ProviderStatus.Ready)
        )
        every { mockProviderRegistry.getProvidersByCapability(ProviderCapability.AI.Chat) } returns
            listOf(fakeAiProvider("groq"))
        // The Copilot workspace drives the assistant off the CareerState engine
        // rather than a one-shot LoadProfile use case.
        every { mockStateEngine.state } returns MutableStateFlow(
            CareerState(profile = ProfileState(name = "Azmath Shaik", targetRole = "Software Engineer"))
        )
        every { mockIntentEngine.detectIntent(any(), any()) } returns CareerIntent.RESUME_HELP
        every { mockPromptOrchestrator.buildCopilotPrompt(any(), any(), any(), any()) } returns "copilot-prompt"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AssistantViewModel(
        mockRepository,
        mockResponseUseCase,
        mockProviderManager,
        mockProviderRegistry,
        mockStateEngine,
        mockContextEngine,
        mockIntentEngine,
        mockPromptOrchestrator
    )

    /** Minimal AI provider double for registry stubbing. */
    private fun fakeAiProvider(id: String) = object : AIProvider(
        metadata = ProviderMetadata(
            id = id,
            name = "Fake $id",
            type = ProviderType.AI,
            version = "1.0.0",
            description = "fake",
            author = "test"
        ),
        capabilities = setOf(ProviderCapability.AI.Chat, ProviderCapability.AI.Streaming)
    ) {
        override suspend fun generateText(prompt: String): Result<String> = Result.Success("answer")

        override suspend fun chat(messages: List<AiMessage>): Result<String> = Result.Success("answer")

        override fun streamText(prompt: String): Flow<String> = flowOf("answer")

        override fun streamChat(messages: List<AiMessage>): Flow<Result<String>> =
            flowOf(Result.Success("answer"))

        override suspend fun listModels(): Result<List<String>> = Result.Success(emptyList())

        override suspend fun onInitialize() {}
        override suspend fun onStart() {}
        override suspend fun onStop() {}
        override suspend fun onDispose() {}
    }

    @Test
    fun `provider status surfaces ready AI provider`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        // WhileSubscribed(5s) StateFlows only materialize once subscribed.
        backgroundScope.launch { viewModel.providerStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.providerStatus.value.isReady)
        assertEquals("Groq", viewModel.providerStatus.value.providerName)
    }

    @Test
    fun `provider status never labels a job provider as the AI provider`() = runTest(testDispatcher) {
        // Only a job feed (naukri) is Ready; no AI provider is ready.
        every { mockProviderRegistry.getProvidersByCapability(ProviderCapability.AI.Chat) } returns
            listOf(fakeAiProvider("groq"), fakeAiProvider("gemma"))
        every { mockProviderManager.providerStatuses } returns MutableStateFlow(
            mapOf("naukri" to ProviderStatus.Ready)
        )

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.providerStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.providerStatus.value.isReady)
        assertEquals(null, viewModel.providerStatus.value.providerName)
    }

    @Test
    fun `user name is first name only`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        // WhileSubscribed(5s) StateFlows only materialize once subscribed.
        backgroundScope.launch { viewModel.userName.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Azmath", viewModel.userName.value)
    }

    @Test
    fun `sendMessage streams chunks into the bubble and commits on completion`() = runTest(testDispatcher) {
        every { mockResponseUseCase.stream(any()) } returns flowOf("Hello ", "world!")

        val viewModel = createViewModel()
        viewModel.sendMessage("Hi")

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as AssistantUiState.Chatting
        assertEquals(2, state.messages.size) // user + assistant
        assertEquals("Hello world!", state.messages.last().content)
        assertEquals(null, state.streamingContent)
        assertEquals(false, state.streamFailed)
    }

    @Test
    fun `single-flight ignores a second send while streaming`() = runTest(testDispatcher) {
        every { mockResponseUseCase.stream(any()) } returns flowOf("a", "b", "c")

        val viewModel = createViewModel()
        viewModel.sendMessage("first")
        viewModel.sendMessage("second") // ignored while in flight

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as AssistantUiState.Chatting
        // Only ONE user message should be present (first); the second was dropped.
        assertEquals(2, state.messages.size)
        assertEquals("first", state.messages.first().content)
    }

    @Test
    fun `stream failure with partial text shows partial and sets streamFailed but does NOT persist it`() = runTest(testDispatcher) {
        every { mockResponseUseCase.stream(any()) } returns flow {
            emit("partial ")
            throw RuntimeException("connection lost")
        }

        val viewModel = createViewModel()
        viewModel.sendMessage("hi")

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AssistantUiState.Chatting)
        val chatting = state as AssistantUiState.Chatting
        assertEquals(true, chatting.streamFailed)
        assertEquals("partial ", chatting.streamingContent)
        // Truthful history: a failed/truncated turn must NEVER be persisted as an
        // assistant message. Only the USER turn is saved; no ASSISTANT save.
        coVerify(exactly = 1) { mockRepository.saveMessage("main_session", "USER", "hi") }
        coVerify(exactly = 0) { mockRepository.saveMessage(any(), "ASSISTANT", any()) }
    }

    @Test
    fun `successful stream persists exactly one completed assistant message`() = runTest(testDispatcher) {
        every { mockResponseUseCase.stream(any()) } returns flowOf("Hello ", "world!")

        val viewModel = createViewModel()
        viewModel.sendMessage("Hi")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.saveMessage("main_session", "USER", "Hi") }
        coVerify(exactly = 1) { mockRepository.saveMessage("main_session", "ASSISTANT", "Hello world!") }
    }

    @Test
    fun `stream failure with no text shows error`() = runTest(testDispatcher) {
        every { mockResponseUseCase.stream(any()) } returns flow<String> { throw RuntimeException("boom") }

        val viewModel = createViewModel()
        viewModel.sendMessage("hi")

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AssistantUiState.Error)
    }

    @Test
    fun `retry resends the last user message`() = runTest(testDispatcher) {
        var calls = 0
        every { mockResponseUseCase.stream(any()) } answers {
            calls++
            if (calls == 1) flow<String> { throw RuntimeException("boom") } else flowOf("ok")
        }

        val viewModel = createViewModel()
        viewModel.sendMessage("please")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AssistantUiState.Error)

        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as AssistantUiState.Chatting
        assertEquals("ok", state.messages.last().content)
    }

    @Test
    fun `retry after partial failure does not duplicate the user message or history`() = runTest(testDispatcher) {
        var calls = 0
        every { mockResponseUseCase.stream(any()) } answers {
            calls++
            if (calls == 1) {
                flow { emit("half "); throw RuntimeException("dropped") }
            } else {
                flowOf("complete answer")
            }
        }

        val viewModel = createViewModel()
        viewModel.sendMessage("draft my summary")
        testDispatcher.scheduler.advanceUntilIdle()
        // After partial failure: transcript holds exactly the one user turn.
        val failed = viewModel.uiState.value as AssistantUiState.Chatting
        assertEquals(1, failed.messages.size)
        assertEquals(true, failed.streamFailed)

        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        val ok = viewModel.uiState.value as AssistantUiState.Chatting
        // Exactly one USER + one ASSISTANT — retry did not append a duplicate user turn.
        assertEquals(2, ok.messages.size)
        assertEquals("draft my summary", ok.messages.first().content)
        assertEquals("complete answer", ok.messages.last().content)
        // The user turn was saved once (initial send); retry must not re-save it.
        coVerify(exactly = 1) { mockRepository.saveMessage("main_session", "USER", "draft my summary") }
        // Only the successful assistant answer is persisted — the failed partial never is.
        coVerify(exactly = 1) { mockRepository.saveMessage("main_session", "ASSISTANT", "complete answer") }
        coVerify(exactly = 0) { mockRepository.saveMessage(any(), "ASSISTANT", "half ") }
    }

    @Test
    fun `history is restored from the repository on init`() = runTest(testDispatcher) {
        every { mockRepository.getMessages("main_session") } returns flowOf(
            Result.Success(
                listOf(
                    com.bangersoul.aivance.core.domain.repository.AssistantMessage(
                        1L, "main_session", "USER", "old question", 1_000L
                    ),
                    com.bangersoul.aivance.core.domain.repository.AssistantMessage(
                        2L, "main_session", "ASSISTANT", "old answer", 2_000L
                    )
                )
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as AssistantUiState.Chatting
        assertEquals(2, state.messages.size)
        assertEquals("old question", state.messages.first().content)
        assertEquals("old answer", state.messages.last().content)
    }

    @Test
    fun `setJobContext passes the job into the orchestrated prompt`() = runTest(testDispatcher) {
        every { mockResponseUseCase.stream(any()) } returns flowOf("tailored reply")
        val contextSlot = io.mockk.slot<AssistantJobContext?>()
        every {
            mockPromptOrchestrator.buildCopilotPrompt(any(), any(), any(), captureNullable(contextSlot))
        } returns "copilot-prompt"

        val viewModel = createViewModel()
        viewModel.setJobContext(
            AssistantJobContext(
                jobId = "job-1",
                title = "Android Engineer",
                company = "Acme",
                description = "Kotlin + Compose"
            )
        )
        viewModel.sendMessage("Tailor my resume")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Android Engineer", contextSlot.captured?.title)
        assertEquals("Acme", contextSlot.captured?.company)
        assertEquals("Kotlin + Compose", contextSlot.captured?.description)
    }
}
