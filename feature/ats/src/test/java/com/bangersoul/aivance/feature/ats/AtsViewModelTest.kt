package com.bangersoul.aivance.feature.ats

import app.cash.turbine.test
import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.common.model.Resume
import com.bangersoul.aivance.core.common.model.ResumeVersion
import com.bangersoul.aivance.core.common.result.DomainError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.repository.AtsRepository
import com.bangersoul.aivance.core.domain.repository.AtsStreamEvent
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.ats.AnalyzeJobDescriptionUseCase
import com.bangersoul.aivance.core.domain.usecase.ats.StreamAtsAnalysisUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AtsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockResumeRepository: ResumeRepository = mockk()
    private val mockAnalyzeJd: AnalyzeJobDescriptionUseCase = mockk()
    private val mockStreamAts: StreamAtsAnalysisUseCase = mockk()
    private val mockAtsRepository: AtsRepository = mockk()
    private val mockTrackEvent: TrackEventUseCase = mockk()

    private lateinit var viewModel: AtsViewModel

    private val sampleResume = Resume(id = 1L, name = "resume.pdf", fileName = "resume.pdf", rawText = "text")
    private val sampleVersion = ResumeVersion(id = 1L, resumeId = 1L, versionName = "Original Import")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockTrackEvent.invoke(any()) } returns Result.Success(Unit)
        every { mockResumeRepository.getResumes() } returns flowOf(Result.Success(listOf(sampleResume)))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        AtsViewModel(mockResumeRepository, mockAnalyzeJd, mockStreamAts, mockAtsRepository, mockTrackEvent)

    @Test
    fun `init loads resumes and stays on the resume picker`() {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Not tautological: init drives resumeRepository.getResumes() into the
        // resumes flow, while the UI state remains on the picker.
        assertTrue(viewModel.uiState.value is AtsUiState.SelectingResume)
        assertEquals(listOf(sampleResume), viewModel.resumes.value)
    }

    @Test
    fun `resume load failure surfaces error`() {
        every { mockResumeRepository.getResumes() } returns flowOf(
            Result.Failure(DomainError("Database unreachable"))
        )

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AtsUiState.Error)
        assertTrue((state as AtsUiState.Error).message.contains("Database unreachable"))
    }

    @Test
    fun `loadReport opens the saved analysis directly`() = runTest {
        val savedReport = AtsReport(
            id = 42L,
            resumeVersionId = 1L,
            jobDescriptionId = 9L,
            overallScore = 87,
            matchPercentage = 87
        )
        coEvery { mockAtsRepository.getReportById(42L) } returns savedReport

        viewModel = createViewModel()
        viewModel.loadReport(42L)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AtsUiState.DisplayReport)
        assertEquals(42L, (state as AtsUiState.DisplayReport).report.id)
        assertEquals(87, state.report.overallScore)
    }

    @Test
    fun `loadReport surfaces an error when the report is missing`() = runTest {
        coEvery { mockAtsRepository.getReportById(999L) } returns null

        viewModel = createViewModel()
        viewModel.loadReport(999L)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AtsUiState.Error)
        assertTrue((state as AtsUiState.Error).message.contains("not found"))
    }

    @Test
    fun `selecting resume version transitions to InputJobDescription`() {
        viewModel = createViewModel()
        viewModel.onEvent(AtsUiEvent.SelectResumeVersion(sampleResume, sampleVersion))
        assertTrue(viewModel.uiState.value is AtsUiState.InputJobDescription)
    }

    // STEP 9 live-reactive scoring only fires once the JD exceeds 50 characters
    // (the combine → debounce(800) pipeline is gated on jd.length > 50).
    private val longJd = "Senior Android Engineer with 5+ years of experience building scalable " +
        "mobile applications using Kotlin, Jetpack Compose, and Clean Architecture, with strong " +
        "knowledge of coroutines, Hilt, Room, and CI/CD pipelines."

    @Test
    fun `analyze success streams chunks then transitions to DisplayReport`() = runTest(testDispatcher) {
        coEvery { mockAnalyzeJd.invoke(any()) } returns Result.Success(99L)
        coEvery { mockStreamAts.invoke(any()) } returns flowOf(
            AtsStreamEvent.Chunk("{\"overallScore\":85,"),
            AtsStreamEvent.Chunk("\"matchPercentage\":80}"),
            AtsStreamEvent.Completed(
                AtsReport(resumeVersionId = 1L, jobDescriptionId = 99L, overallScore = 85, matchPercentage = 80)
            )
        )

        viewModel = createViewModel()
        viewModel.onEvent(AtsUiEvent.SelectResumeVersion(sampleResume, sampleVersion))
        viewModel.onEvent(AtsUiEvent.Analyze(longJd))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AtsUiState.DisplayReport)
        assertEquals(85, (state as AtsUiState.DisplayReport).report.overallScore)
        coVerify { mockTrackEvent.invoke(match { it.eventName == "ats_analyze_success" }) }
    }

    @Test
    fun `analyze streams chunks into Analyzing state before completion`() = runTest(testDispatcher) {
        coEvery { mockAnalyzeJd.invoke(any()) } returns Result.Success(99L)
        // Only chunks, no terminal event — the state should stay Analyzing.
        coEvery { mockStreamAts.invoke(any()) } returns flowOf(
            AtsStreamEvent.Chunk("token1 "),
            AtsStreamEvent.Chunk("token2")
        )

        viewModel = createViewModel()
        viewModel.onEvent(AtsUiEvent.SelectResumeVersion(sampleResume, sampleVersion))
        viewModel.onEvent(AtsUiEvent.Analyze(longJd))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AtsUiState.Analyzing)
        assertEquals("token1 token2", (state as AtsUiState.Analyzing).streamingText)
    }

    @Test
    fun `analyze failure transitions to Error`() = runTest(testDispatcher) {
        coEvery { mockAnalyzeJd.invoke(any()) } returns Result.Failure(DomainError("AI parsing failed"))

        viewModel = createViewModel()
        viewModel.onEvent(AtsUiEvent.SelectResumeVersion(sampleResume, sampleVersion))
        viewModel.onEvent(AtsUiEvent.Analyze(longJd))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AtsUiState.Error)
    }

    @Test
    fun `stream failure transitions to Error`() = runTest(testDispatcher) {
        coEvery { mockAnalyzeJd.invoke(any()) } returns Result.Success(99L)
        coEvery { mockStreamAts.invoke(any()) } returns flowOf(
            AtsStreamEvent.Failed("Provider unreachable")
        )

        viewModel = createViewModel()
        viewModel.onEvent(AtsUiEvent.SelectResumeVersion(sampleResume, sampleVersion))
        viewModel.onEvent(AtsUiEvent.Analyze(longJd))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AtsUiState.Error)
    }

    @Test
    fun `reset returns to SelectingResume`() {
        viewModel = createViewModel()
        viewModel.onEvent(AtsUiEvent.SelectResumeVersion(sampleResume, sampleVersion))
        viewModel.onEvent(AtsUiEvent.Reset)
        assertTrue(viewModel.uiState.value is AtsUiState.SelectingResume)
    }

    @Test
    fun `generateCoverLetter from report emits navigation effect and tracks event`() = runTest(testDispatcher) {
        coEvery { mockAnalyzeJd.invoke(any()) } returns Result.Success(99L)
        coEvery { mockStreamAts.invoke(any()) } returns flowOf(
            AtsStreamEvent.Completed(
                AtsReport(
                    id = 99L,
                    resumeVersionId = 1L,
                    jobDescriptionId = 1L,
                    overallScore = 85,
                    matchPercentage = 80
                )
            )
        )

        viewModel = createViewModel()
        viewModel.onEvent(AtsUiEvent.SelectResumeVersion(sampleResume, sampleVersion))
        viewModel.onEvent(AtsUiEvent.Analyze(longJd))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AtsUiState.DisplayReport)

        viewModel.onEvent(AtsUiEvent.GenerateCoverLetter)
        advanceUntilIdle()

        viewModel.effects.test {
            val effect = awaitItem()
            assertTrue(effect is AtsUiEffect.NavigateToCoverLetter)
            assertEquals(99L, (effect as AtsUiEffect.NavigateToCoverLetter).reportId)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { mockTrackEvent.invoke(match { it.eventName == "ats_cover_letter_request" }) }
    }

    @Test
    fun `exportReport emits export effect containing the report content`() = runTest(testDispatcher) {
        coEvery { mockAnalyzeJd.invoke(any()) } returns Result.Success(99L)
        coEvery { mockStreamAts.invoke(any()) } returns flowOf(
            AtsStreamEvent.Completed(
                AtsReport(
                    resumeVersionId = 1L,
                    jobDescriptionId = 99L,
                    overallScore = 85,
                    matchPercentage = 80,
                    matchedKeywords = listOf("Kotlin"),
                    missingKeywords = listOf("Rust")
                )
            )
        )

        viewModel = createViewModel()
        viewModel.onEvent(AtsUiEvent.SelectResumeVersion(sampleResume, sampleVersion))
        viewModel.onEvent(AtsUiEvent.Analyze(longJd))
        advanceUntilIdle()

        viewModel.onEvent(AtsUiEvent.ExportReport)
        advanceUntilIdle()

        viewModel.effects.test {
            val effect = awaitItem()
            assertTrue(effect is AtsUiEffect.ExportReport)
            val text = (effect as AtsUiEffect.ExportReport).text
            assertTrue(text.contains("Overall Score: 85"))
            assertTrue(text.contains("Match Probability: 80%"))
            assertTrue(text.contains("+ Kotlin"))
            assertTrue(text.contains("- Rust"))
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { mockTrackEvent.invoke(match { it.eventName == "ats_report_export" }) }
    }

    @Test
    fun `updateJobDescription feeds the live jd text flow`() {
        viewModel = createViewModel()

        viewModel.onEvent(AtsUiEvent.UpdateJobDescription("Senior Android Kotlin Compose"))

        assertEquals("Senior Android Kotlin Compose", viewModel.jdText.value)
    }
}
