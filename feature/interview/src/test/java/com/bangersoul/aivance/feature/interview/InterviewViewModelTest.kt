package com.bangersoul.aivance.feature.interview

import com.bangersoul.aivance.core.common.enums.InterviewDifficulty
import com.bangersoul.aivance.core.common.model.CareerState
import com.bangersoul.aivance.core.common.model.InterviewFeedback
import com.bangersoul.aivance.core.common.model.InterviewMessage
import com.bangersoul.aivance.core.common.model.InterviewSession
import com.bangersoul.aivance.core.common.result.DomainError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.analytics.InterviewReadinessCalculator
import com.bangersoul.aivance.core.domain.engine.CareerStateEngine
import com.bangersoul.aivance.core.domain.repository.InterviewRepository
import com.bangersoul.aivance.core.domain.repository.crm.CompanyIntelligenceRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.interview.GenerateStarPackRequest
import com.bangersoul.aivance.core.domain.usecase.interview.GenerateStarPackUseCase
import com.bangersoul.aivance.core.domain.usecase.interview.STARPrepGenerator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InterviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockRepository: InterviewRepository = mockk()
    private val mockCareerStateEngine: CareerStateEngine = mockk()
    private val mockCompanyRepository: CompanyIntelligenceRepository = mockk()
    private val mockGenerateStarPack: GenerateStarPackUseCase = mockk()
    private val mockTrackEvent: TrackEventUseCase = mockk()

    private lateinit var viewModel: InterviewViewModel

    private val sampleSession = InterviewSession(
        id = "session_1",
        targetRole = "Android Dev",
        type = "BEHAVIORAL",
        companyName = "Tech Corp",
        difficulty = InterviewDifficulty.MEDIUM
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockTrackEvent.invoke(any()) } returns Result.Success(Unit)
        every { mockCareerStateEngine.state } returns MutableStateFlow(CareerState())
        coEvery {
            mockRepository.startSession(any(), any(), any(), any(), any(), any())
        } returns Result.Success(sampleSession)
        coEvery { mockRepository.generateQuestions(any(), any()) } returns Result.Success(Unit)
        coEvery { mockRepository.persistPackQuestions(any(), any()) } returns Result.Success(Unit)
        coEvery { mockRepository.submitAnswer(any(), any()) } returns Result.Success(Unit)
        coEvery { mockGenerateStarPack.invoke(any()) } returns STARPrepGenerator.generateStarPack("Android Dev", 5)
        // History is loaded in init — provide an empty session list by default.
        every { mockRepository.getSessions() } returns flowOf(Result.Success(emptyList()))
        every { mockRepository.getQuestions(any()) } returns flowOf(Result.Success(emptyList()))
        every { mockRepository.getSessionById(any()) } returns flowOf(Result.Success(sampleSession))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = InterviewViewModel(
        mockRepository,
        mockCareerStateEngine,
        mockCompanyRepository,
        mockGenerateStarPack,
        mockTrackEvent,
        InterviewReadinessCalculator()
    )

    @Test
    fun `initial state is Idle`() {
        viewModel = createViewModel()
        assertTrue(viewModel.uiState.value is InterviewUiState.Idle)
    }

    @Test
    fun `start session transitions to Active`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.onEvent(InterviewUiEvent.StartSession("Android Dev", "Tech Corp", "BEHAVIORAL"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is InterviewUiState.Active)
        assertEquals("session_1", (state as InterviewUiState.Active).session.id)
    }

    @Test
    fun `start session failure shows error`() = runTest(testDispatcher) {
        coEvery {
            mockRepository.startSession(any(), any(), any(), any(), any(), any())
        } returns Result.Failure(DomainError("Failed to start session"))

        viewModel = createViewModel()

        viewModel.onEvent(InterviewUiEvent.StartSession("Android Dev", "Tech Corp", "BEHAVIORAL"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is InterviewUiState.Error)
    }

    @Test
    fun `complete session transitions to Review`() = runTest(testDispatcher) {
        coEvery { mockRepository.completeSession(any()) } returns Result.Success(Unit)

        viewModel = createViewModel()

        viewModel.onEvent(InterviewUiEvent.StartSession("Android Dev", "Tech Corp", "BEHAVIORAL"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(InterviewUiEvent.Complete)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is InterviewUiState.Review)
    }

    @Test
    fun `next question increments index`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.onEvent(InterviewUiEvent.StartSession("Android Dev", "Tech Corp", "BEHAVIORAL"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(InterviewUiEvent.NextQuestion)

        val state = viewModel.uiState.value
        assertTrue(state is InterviewUiState.Active)
        assertEquals(1, (state as InterviewUiState.Active).currentQuestionIndex)
    }

    @Test
    fun `reset returns to Idle`() {
        viewModel = createViewModel()
        viewModel.onEvent(InterviewUiEvent.Reset)
        assertTrue(viewModel.uiState.value is InterviewUiState.Idle)
    }

    @Test
    fun `generate star pack populates idle state with role pack`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.onEvent(InterviewUiEvent.GenerateStarPack("Android Dev"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is InterviewUiState.Idle)
        assertEquals(5, (state as InterviewUiState.Idle).starPack?.size)
        assertTrue((state as InterviewUiState.Idle).starPack.orEmpty().all { it.expectedKeyPoints.isNotEmpty() })
    }

    @Test
    fun `start session with pack questions persists them and seeds the session`() = runTest(testDispatcher) {
        val pack = STARPrepGenerator.generateStarPack("Android Dev", 3)
        every { mockRepository.getQuestions("session_1") } returns flowOf(Result.Success(pack))

        viewModel = createViewModel()

        viewModel.onEvent(InterviewUiEvent.StartSession("Android Dev", "Tech Corp", "BEHAVIORAL", packQuestions = pack))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockRepository.persistPackQuestions("session_1", pack) }
        val state = viewModel.uiState.value
        assertTrue(state is InterviewUiState.Active)
        assertEquals(pack, (state as InterviewUiState.Active).session.questions)
    }

    @Test
    fun `fallback pack is persisted when AI question generation fails`() = runTest(testDispatcher) {
        coEvery { mockRepository.generateQuestions(any(), any()) } returns Result.Failure(DomainError("No AI provider"))

        viewModel = createViewModel()

        viewModel.onEvent(InterviewUiEvent.StartSession("Android Dev", "Tech Corp", "BEHAVIORAL"))
        testDispatcher.scheduler.advanceUntilIdle()

        val fallback = STARPrepGenerator.generateStarPack("Android Dev", 5)
        coVerify { mockGenerateStarPack.invoke(GenerateStarPackRequest(role = "Android Dev", count = 5)) }
        coVerify { mockRepository.persistPackQuestions("session_1", fallback) }
        assertTrue(viewModel.uiState.value is InterviewUiState.Active)
    }

    @Test
    fun `submitAnswer persists the answer and stays Active`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.onEvent(InterviewUiEvent.StartSession("Android Dev", "Tech Corp", "BEHAVIORAL"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(InterviewUiEvent.SubmitAnswer("I led a team of five"))
        testDispatcher.scheduler.advanceUntilIdle()

        val slot = io.mockk.slot<InterviewMessage>()
        coVerify { mockRepository.submitAnswer("session_1", capture(slot)) }
        assertEquals("I led a team of five", slot.captured.text)
        assertEquals(com.bangersoul.aivance.core.common.enums.MessageSender.USER, slot.captured.sender)

        val state = viewModel.uiState.value
        assertTrue(state is InterviewUiState.Active)
        assertEquals(false, (state as InterviewUiState.Active).isSubmitting)
    }

    @Test
    fun `submitAnswer failure surfaces the real cause`() = runTest(testDispatcher) {
        coEvery { mockRepository.submitAnswer(any(), any()) } returns Result.Failure(DomainError("Database busy"))

        viewModel = createViewModel()

        viewModel.onEvent(InterviewUiEvent.StartSession("Android Dev", "Tech Corp", "BEHAVIORAL"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(InterviewUiEvent.SubmitAnswer("My answer"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is InterviewUiState.Error)
        assertTrue((state as InterviewUiState.Error).message.contains("Database busy"))
    }

    @Test
    fun `submitAnswer is ignored outside an active session`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.onEvent(InterviewUiEvent.SubmitAnswer("orphaned answer"))
        testDispatcher.scheduler.advanceUntilIdle()

        // No persistence attempt, state unchanged.
        coVerify(exactly = 0) { mockRepository.submitAnswer(any(), any()) }
        assertTrue(viewModel.uiState.value is InterviewUiState.Idle)
    }

    @Test
    fun `readiness is not measured when no session has produced feedback`() = runTest(testDispatcher) {
        // R3-3: this is exactly the zero-data case that used to render "1%" in Prep Studio
        // (`careerScore / 10` off a fabricated 18) while the analytics path rendered 18 for the
        // same data. Readiness now reports the absence instead of inventing a number.
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is InterviewUiState.Idle)
        assertNull((state as InterviewUiState.Idle).readinessScore)
    }

    @Test
    fun `readiness is the shared calculator's mean over earned session feedback`() = runTest(testDispatcher) {
        every { mockRepository.getSessions() } returns flowOf(
            Result.Success(
                listOf(
                    sampleSession.copy(isCompleted = true, feedback = InterviewFeedback(overallScore = 90)),
                    sampleSession.copy(id = "session_2", isCompleted = true, feedback = InterviewFeedback(overallScore = 80))
                )
            )
        )

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Same value the analytics path reports: one owner, one formula (R3-3).
        assertEquals(85, (viewModel.uiState.value as InterviewUiState.Idle).readinessScore)
    }

    @Test
    fun `load history populates Idle state with past sessions`() = runTest(testDispatcher) {
        every { mockRepository.getSessions() } returns flowOf(
            Result.Success(listOf(sampleSession.copy(isCompleted = true)))
        )

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is InterviewUiState.Idle)
        assertEquals(1, (state as InterviewUiState.Idle).history.size)
    }
}
