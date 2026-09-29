package com.bangersoul.aivance.feature.coverletter

import app.cash.turbine.test
import com.bangersoul.aivance.core.common.model.CoverLetter
import com.bangersoul.aivance.core.common.model.CoverLetterSection
import com.bangersoul.aivance.core.common.model.CoverLetterVersion
import com.bangersoul.aivance.core.common.model.Resume
import com.bangersoul.aivance.core.common.model.ResumeVersion
import com.bangersoul.aivance.core.common.result.DomainError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.repository.CoverLetterRepository
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.coverletter.GenerateCoverLetterUseCase
import com.bangersoul.aivance.core.domain.usecase.coverletter.RegenerateCoverLetterSectionUseCase
import com.bangersoul.aivance.core.domain.usecase.coverletter.StreamGenerateCoverLetterUseCase
import com.bangersoul.aivance.core.util.PdfExporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
class CoverLetterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockRepository: CoverLetterRepository = mockk()
    private val mockResumeRepository: ResumeRepository = mockk()
    private val mockGenerateUseCase: GenerateCoverLetterUseCase = mockk()
    private val mockStreamGenerateUseCase: StreamGenerateCoverLetterUseCase = mockk()
    private val mockRegenerateSectionUseCase: RegenerateCoverLetterSectionUseCase = mockk()
    private val mockTrackEvent: TrackEventUseCase = mockk()
    private val mockPdfExporter: PdfExporter = mockk()

    private lateinit var viewModel: CoverLetterViewModel

    private val sampleVersion = CoverLetterVersion(
        id = 1L,
        coverLetterId = 1L,
        versionName = "v1",
        sections = listOf(
            CoverLetterSection(id = 1L, versionId = 1L, sectionType = "INTRODUCTION", title = "Introduction", content = "Hi there", sectionOrder = 0),
            CoverLetterSection(id = 2L, versionId = 1L, sectionType = "BODY", title = "Body", content = "I am a great fit", sectionOrder = 1)
        )
    )

    private val sampleLetter = CoverLetter(
        id = 1L,
        resumeVersionId = 1L,
        jobId = 2L,
        recruiterId = null,
        company = "Tech Corp",
        role = "Engineer",
        versions = listOf(sampleVersion)
    )

    private val sampleResume = Resume(
        id = 1L,
        name = "resume.pdf",
        primaryVersionId = 1L,
        versions = listOf(ResumeVersion(id = 1L, resumeId = 1L, versionName = "Original Import"))
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockTrackEvent.invoke(any()) } returns Result.Success(Unit)
        every { mockRepository.getCoverLetters() } returns flowOf(Result.Success(listOf(sampleLetter)))
        every { mockResumeRepository.getResumes() } returns flowOf(Result.Success(listOf(sampleResume)))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = CoverLetterViewModel(
        mockRepository,
        mockResumeRepository,
        mockGenerateUseCase,
        mockStreamGenerateUseCase,
        mockRegenerateSectionUseCase,
        mockTrackEvent,
        mockPdfExporter
    )

    @Test
    fun `load with existing letters enters Success`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.onEvent(CoverLetterUiEvent.Load)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CoverLetterUiState.Success)
        assertEquals("Tech Corp", (state as CoverLetterUiState.Success).coverLetter?.company)
    }

    @Test
    fun `successful generation streams tokens then loads letter`() = runTest(testDispatcher) {
        every { mockStreamGenerateUseCase.stream(any()) } returns flowOf("Hello ", "Tech Corp")

        viewModel = createViewModel()

        viewModel.onEvent(CoverLetterUiEvent.Generate(resumeId = 1L, versionId = 1L, jobId = 2L, recruiterId = null))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CoverLetterUiState.Success)
        assertEquals("Tech Corp", (state as CoverLetterUiState.Success).coverLetter?.company)
    }

    @Test
    fun `generation failure shows error`() = runTest(testDispatcher) {
        every { mockStreamGenerateUseCase.stream(any()) } returns flow { throw RuntimeException("Generation failed") }

        viewModel = createViewModel()

        viewModel.onEvent(CoverLetterUiEvent.Generate(resumeId = 1L, versionId = 1L, jobId = 2L, recruiterId = null))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CoverLetterUiState.Error)
    }

    @Test
    fun `load with no letters stays Idle`() = runTest(testDispatcher) {
        every { mockRepository.getCoverLetters() } returns flowOf(Result.Success(emptyList()))

        viewModel = createViewModel()

        viewModel.onEvent(CoverLetterUiEvent.Load)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CoverLetterUiState.Idle)
    }

    // ── Inline edit mode (ToggleEdit / UpdateSection / SaveEdits) ──

    @Test
    fun `toggleEdit populates section drafts from the version`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        viewModel.onEvent(CoverLetterUiEvent.Load)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CoverLetterUiEvent.ToggleEdit)

        val state = viewModel.uiState.value as CoverLetterUiState.Success
        assertTrue(state.isEditing)
        assertEquals("Hi there", state.sectionDrafts[0])
        assertEquals("I am a great fit", state.sectionDrafts[1])
    }

    @Test
    fun `toggleEdit again discards drafts and leaves edit mode`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        viewModel.onEvent(CoverLetterUiEvent.Load)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CoverLetterUiEvent.ToggleEdit)
        viewModel.onEvent(CoverLetterUiEvent.UpdateSection(0, "edited"))
        viewModel.onEvent(CoverLetterUiEvent.ToggleEdit)

        val state = viewModel.uiState.value as CoverLetterUiState.Success
        assertTrue(!state.isEditing)
        assertTrue(state.sectionDrafts.isEmpty())
    }

    @Test
    fun `updateSection edits a draft while in edit mode`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        viewModel.onEvent(CoverLetterUiEvent.Load)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CoverLetterUiEvent.ToggleEdit)
        viewModel.onEvent(CoverLetterUiEvent.UpdateSection(1, "Refined body"))

        val state = viewModel.uiState.value as CoverLetterUiState.Success
        assertEquals("Refined body", state.sectionDrafts[1])
        // Original version is untouched until SaveEdits.
        assertEquals("I am a great fit", state.coverLetter?.versions?.first()?.sections?.get(1)?.content)
    }

    @Test
    fun `updateSection is ignored outside edit mode`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        viewModel.onEvent(CoverLetterUiEvent.Load)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CoverLetterUiEvent.UpdateSection(0, "sneaky"))

        val state = viewModel.uiState.value as CoverLetterUiState.Success
        assertTrue(state.sectionDrafts.isEmpty())
    }

    @Test
    fun `saveEdits persists updated sections and exits edit mode`() = runTest(testDispatcher) {
        coEvery { mockRepository.saveVersion(any()) } returns Result.Success(1L)
        viewModel = createViewModel()
        viewModel.onEvent(CoverLetterUiEvent.Load)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CoverLetterUiEvent.ToggleEdit)
        viewModel.onEvent(CoverLetterUiEvent.UpdateSection(0, "Hello!"))
        viewModel.onEvent(CoverLetterUiEvent.SaveEdits)
        testDispatcher.scheduler.advanceUntilIdle()

        val slot = io.mockk.slot<CoverLetterVersion>()
        coVerify { mockRepository.saveVersion(capture(slot)) }
        assertEquals("Hello!", slot.captured.sections.first().content)
        assertEquals("I am a great fit", slot.captured.sections[1].content)
        coVerify { mockTrackEvent.invoke(match { it.eventName == "cover_letter_edit_save" }) }

        viewModel.effects.test {
            val effect = awaitItem()
            assertTrue(effect is CoverLetterUiEffect.ShowSnackbar)
            cancelAndIgnoreRemainingEvents()
        }
        val state = viewModel.uiState.value as CoverLetterUiState.Success
        assertTrue(!state.isEditing)
    }

    @Test
    fun `saveEdits failure surfaces the real cause in a snackbar`() = runTest(testDispatcher) {
        coEvery { mockRepository.saveVersion(any()) } returns Result.Failure(DomainError("Disk full"))
        viewModel = createViewModel()
        viewModel.onEvent(CoverLetterUiEvent.Load)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CoverLetterUiEvent.ToggleEdit)
        viewModel.onEvent(CoverLetterUiEvent.UpdateSection(0, "Hello!"))
        viewModel.onEvent(CoverLetterUiEvent.SaveEdits)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            val effect = awaitItem()
            assertTrue(effect is CoverLetterUiEffect.ShowSnackbar)
            assertEquals("Disk full", (effect as CoverLetterUiEffect.ShowSnackbar).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Copy / Export / Regenerate ──

    @Test
    fun `copyAll emits CopyText effect with the full letter`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        viewModel.onEvent(CoverLetterUiEvent.Load)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CoverLetterUiEvent.CopyAll)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            val effect = awaitItem()
            assertTrue(effect is CoverLetterUiEffect.CopyText)
            assertEquals("Hi there\n\nI am a great fit", (effect as CoverLetterUiEffect.CopyText).text)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { mockTrackEvent.invoke(match { it.eventName == "cover_letter_copy" }) }
    }

    @Test
    fun `export emits ExportPdf effect and tracks event`() = runTest(testDispatcher) {
        coEvery { mockPdfExporter.exportToPdf(any(), any(), any()) } returns Result.Success(mockk())
        viewModel = createViewModel()
        viewModel.onEvent(CoverLetterUiEvent.Load)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CoverLetterUiEvent.Export)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            val effect = awaitItem()
            assertTrue(effect is CoverLetterUiEffect.ExportPdf)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { mockTrackEvent.invoke(match { it.eventName == "cover_letter_export" }) }
        coVerify { mockPdfExporter.exportToPdf(any(), any(), any()) }
    }

    @Test
    fun `regenerateSection failure shows snackbar and clears generating`() = runTest(testDispatcher) {
        coEvery { mockRegenerateSectionUseCase.invoke(any()) } returns Result.Failure(DomainError("AI down"))
        viewModel = createViewModel()
        viewModel.onEvent(CoverLetterUiEvent.Load)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(CoverLetterUiEvent.RegenerateSection(1L, "BODY"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as CoverLetterUiState.Success
        assertTrue(!state.isGenerating)
        viewModel.effects.test {
            val effect = awaitItem()
            assertTrue(effect is CoverLetterUiEffect.ShowSnackbar)
            assertEquals("AI down", (effect as CoverLetterUiEffect.ShowSnackbar).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── GenerateForJob (Job Details entry point) ──

    @Test
    fun `generateForJob without a resume shows snackbar and stays Idle`() = runTest(testDispatcher) {
        every { mockResumeRepository.getResumes() } returns flowOf(Result.Success(emptyList()))
        viewModel = createViewModel()

        viewModel.onEvent(CoverLetterUiEvent.GenerateForJob(2L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CoverLetterUiState.Idle)
        viewModel.effects.test {
            val effect = awaitItem()
            assertTrue(effect is CoverLetterUiEffect.ShowSnackbar)
            assertTrue((effect as CoverLetterUiEffect.ShowSnackbar).message.contains("Import a resume"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `generateForJob with a resume streams into a tailored letter`() = runTest(testDispatcher) {
        every { mockStreamGenerateUseCase.stream(any()) } returns flowOf("Hello ", "Tech Corp")
        viewModel = createViewModel()

        viewModel.onEvent(CoverLetterUiEvent.GenerateForJob(2L))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CoverLetterUiState.Success)
        assertEquals("Tech Corp", (state as CoverLetterUiState.Success).coverLetter?.company)
        coVerify { mockTrackEvent.invoke(match { it.eventName == "cover_letter_generate_for_job" }) }
    }

    @Test
    fun `generateFromPrimary streams a letter with a null job id`() = runTest(testDispatcher) {
        every { mockStreamGenerateUseCase.stream(any()) } returns flowOf("Hello ", "Your Next Employer")
        viewModel = createViewModel()

        viewModel.onEvent(CoverLetterUiEvent.GenerateFromPrimary)
        testDispatcher.scheduler.advanceUntilIdle()

        // The request must carry jobId = null (generic letter) so the
        // GenerateCoverLetterUseCase no longer rejects it.
        coVerify {
            mockStreamGenerateUseCase.stream(
                match { it.jobId == null && it.resumeId == 1L && it.resumeVersionId == 1L }
            )
        }
        coVerify { mockTrackEvent.invoke(match { it.eventName == "cover_letter_generate_primary" }) }
    }
}
