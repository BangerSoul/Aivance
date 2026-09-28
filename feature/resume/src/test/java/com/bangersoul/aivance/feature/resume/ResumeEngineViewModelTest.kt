package com.bangersoul.aivance.feature.resume

import android.net.Uri
import com.bangersoul.aivance.core.common.model.Resume
import com.bangersoul.aivance.core.common.model.ResumeSection
import com.bangersoul.aivance.core.common.model.ResumeVersion
import com.bangersoul.aivance.core.common.result.DomainError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.provider.AiProviderAvailability
import com.bangersoul.aivance.core.domain.usecase.provider.GetAiProviderAvailabilityUseCase
import com.bangersoul.aivance.core.domain.usecase.resume.CalculateATSScoreUseCase
import com.bangersoul.aivance.core.domain.usecase.resume.ExportResumeUseCase
import com.bangersoul.aivance.core.domain.usecase.resume.ImportResumeUseCase
import com.bangersoul.aivance.core.domain.usecase.resume.ImproveResumeUseCase
import com.bangersoul.aivance.core.domain.usecase.resume.ParseResumeUseCase
import com.bangersoul.aivance.core.domain.usecase.resume.StreamImproveSectionUseCase
import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.util.PdfExporter
import io.mockk.coEvery
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
class ResumeEngineViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockRepository: ResumeRepository = mockk()
    private val mockImport: ImportResumeUseCase = mockk()
    private val mockParse: ParseResumeUseCase = mockk()
    private val mockCalculateAts: CalculateATSScoreUseCase = mockk()
    private val mockImprove: ImproveResumeUseCase = mockk()
    private val mockStreamImprove: StreamImproveSectionUseCase = mockk()
    private val mockExport: ExportResumeUseCase = mockk()
    private val mockAiAvailability: GetAiProviderAvailabilityUseCase = mockk()
    private val mockTrackEvent: TrackEventUseCase = mockk()
    private val mockPdfExporter: PdfExporter = mockk()
    private val mockDocxExporter: com.bangersoul.aivance.core.util.DocxExporter = mockk()

    private val section = ResumeSection(
        id = 1L, versionId = 1L, sectionType = "EXPERIENCE",
        title = "Experience", content = "Android Engineer at Acme"
    )
    private val version = ResumeVersion(
        id = 1L, resumeId = 1L, versionName = "Original Import",
        sections = listOf(section)
    )
    private val resume = Resume(id = 1L, name = "resume.pdf", primaryVersionId = 1L)

    private val longJd = "Senior Android Engineer with 5+ years building scalable mobile " +
        "applications using Kotlin, Jetpack Compose, and Clean Architecture."

    private fun createViewModel() = ResumeEngineViewModel(
        mockRepository, mockImport, mockParse, mockCalculateAts,
        mockImprove, mockStreamImprove, mockExport, mockAiAvailability, mockTrackEvent, mockPdfExporter,
        mockDocxExporter
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockTrackEvent(any()) } returns Result.Success(Unit)
        // Default to a configured provider so the AI pre-flight (B2) never
        // short-circuits the ATS/optimization paths under test.
        coEvery { mockAiAvailability(Unit) } returns
            Result.Success(AiProviderAvailability(isConfigured = true, providerName = "gemini"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Import`() = runTest {
        val viewModel = createViewModel()
        assertEquals(ResumeEngineState.Import, viewModel.state.value)
    }

    @Test
    fun `import success transitions to Preview with parsed sections`() = runTest {
        coEvery { mockImport.invoke(any()) } returns Result.Success(1L)
        coEvery { mockRepository.getVersions(1L) } returns flowOf(Result.Success(listOf(version)))
        coEvery { mockRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))

        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportFile(mockk<Uri>()))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is ResumeEngineState.Preview)
        assertEquals(1, (state as ResumeEngineState.Preview).version.sections.size)
    }

    @Test
    fun `importOcrText enters Preview with the scanned text section`() = runTest {
        // B1: an import stores a draft (parent resume + first version) before
        // Preview, so the ATS step and Save have real rows to reference.
        coEvery { mockRepository.saveResume(any()) } returns Result.Success(7L)
        coEvery { mockRepository.saveVersion(any()) } returns Result.Success(9L)
        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportOcrText("Jane Doe\nSenior Android Engineer"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is ResumeEngineState.Preview)
        val preview = state as ResumeEngineState.Preview
        assertEquals("Raw Text", preview.version.sections.first().title)
        assertEquals("Jane Doe\nSenior Android Engineer", preview.version.sections.first().content)
        assertEquals("Camera Scan Resume", preview.resume.name)
        assertEquals("the preview carries the persisted resume id", 7L, preview.resume.id)
        assertEquals("the preview carries the persisted version id", 9L, preview.version.id)
    }

    @Test
    fun `importOcrText with blank text enters Import error`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportOcrText("   \n  "))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is ResumeEngineState.Error)
        assertEquals("Import", (state as ResumeEngineState.Error).step)
    }

    @Test
    fun `importJsonText enters Preview from a JSON Resume document`() = runTest {
        coEvery { mockRepository.saveResume(any()) } returns Result.Success(7L)
        coEvery { mockRepository.saveVersion(any()) } returns Result.Success(9L)
        val json = """{"basics":{"name":"Jane Doe","summary":"Senior Android engineer"}}"""
        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportJsonText(json))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is ResumeEngineState.Preview)
        val preview = state as ResumeEngineState.Preview
        assertEquals("Senior Android engineer", preview.version.sections.first().content)
        // The version is stored against the real parent row — the fabricated
        // clock id is what produced the FK failure at Save (B1).
        assertEquals(7L, preview.version.resumeId)
        assertEquals(9L, preview.version.id)
    }

    @Test
    fun `importJsonText with malformed json enters Import error`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportJsonText("{not json"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is ResumeEngineState.Error)
        assertEquals("Import", (state as ResumeEngineState.Error).step)
    }

    @Test
    fun `updateSectionContent edits the preview version section`() = runTest {
        coEvery { mockImport.invoke(any()) } returns Result.Success(1L)
        coEvery { mockRepository.getVersions(1L) } returns flowOf(Result.Success(listOf(version)))
        coEvery { mockRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))

        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportFile(mockk<Uri>()))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value is ResumeEngineState.Preview)

        viewModel.onEvent(ResumeEngineEvent.UpdateSectionContent("Experience", "Edited content"))
        val state = viewModel.state.value as ResumeEngineState.Preview
        assertEquals("Edited content", state.version.sections.first().content)
    }

    @Test
    fun `discardSuggestion removes the suggestion without applying it`() = runTest {
        coEvery { mockImport.invoke(any()) } returns Result.Success(1L)
        coEvery { mockRepository.getVersions(1L) } returns flowOf(Result.Success(listOf(version)))
        coEvery { mockRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))
        every { mockStreamImprove.stream(any()) } returns flowOf("Improved content")

        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportFile(mockk<Uri>()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(ResumeEngineEvent.ContinueFromPreview)
        viewModel.onEvent(ResumeEngineEvent.SkipAts)
        viewModel.onEvent(ResumeEngineEvent.ImproveSection("Experience"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            "Improved content",
            (viewModel.state.value as ResumeEngineState.Optimizing).suggestions["Experience"]
        )

        viewModel.onEvent(ResumeEngineEvent.DiscardSuggestion("Experience"))

        val state = viewModel.state.value as ResumeEngineState.Optimizing
        assertTrue(state.suggestions.isEmpty())
        // The version content is untouched — the improvement was NOT applied.
        assertEquals("Android Engineer at Acme", state.version.sections.first().content)
    }

    @Test
    fun `import failure enters Error and retry re-imports`() = runTest {
        coEvery { mockImport.invoke(any()) } returns Result.Failure(DomainError("Bad file"))

        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportFile(mockk<Uri>()))
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.state.value
        assertTrue(error is ResumeEngineState.Error)
        assertEquals("Import", (error as ResumeEngineState.Error).step)

        // Retry after an Import error re-runs the import.
        coEvery { mockImport.invoke(any()) } returns Result.Success(1L)
        coEvery { mockRepository.getVersions(1L) } returns flowOf(Result.Success(listOf(version)))
        coEvery { mockRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))

        viewModel.onEvent(ResumeEngineEvent.Retry)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value is ResumeEngineState.Preview)
    }

    @Test
    fun `preloaded job description is carried into the ATS scan step`() = runTest {
        coEvery { mockImport.invoke(any()) } returns Result.Success(1L)
        coEvery { mockRepository.getVersions(1L) } returns flowOf(Result.Success(listOf(version)))
        coEvery { mockRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))

        val viewModel = createViewModel()
        // Simulates the "Create tailored resume" cross-feature jump.
        viewModel.onEvent(ResumeEngineEvent.SetInitialJobDescription(longJd))
        viewModel.onEvent(ResumeEngineEvent.ImportFile(mockk<Uri>()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(ResumeEngineEvent.ContinueFromPreview)

        val state = viewModel.state.value
        assertTrue(state is ResumeEngineState.AtsScanning)
        assertEquals(longJd, (state as ResumeEngineState.AtsScanning).jdText)
    }

    @Test
    fun `continue from preview transitions to AtsScanning`() = runTest {
        coEvery { mockImport.invoke(any()) } returns Result.Success(1L)
        coEvery { mockRepository.getVersions(1L) } returns flowOf(Result.Success(listOf(version)))
        coEvery { mockRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))

        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportFile(mockk<Uri>()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(ResumeEngineEvent.ContinueFromPreview)

        assertTrue(viewModel.state.value is ResumeEngineState.AtsScanning)
    }

    @Test
    fun `ats scan with short JD stays on AtsScanning`() = runTest {
        coEvery { mockImport.invoke(any()) } returns Result.Success(1L)
        coEvery { mockRepository.getVersions(1L) } returns flowOf(Result.Success(listOf(version)))
        coEvery { mockRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))

        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportFile(mockk<Uri>()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(ResumeEngineEvent.ContinueFromPreview)
        viewModel.onEvent(ResumeEngineEvent.UpdateJdText("too short"))
        viewModel.onEvent(ResumeEngineEvent.RunAtsScan)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value is ResumeEngineState.AtsScanning)
    }

    @Test
    fun `ats scan success transitions to AtsResult`() = runTest {
        coEvery { mockImport.invoke(any()) } returns Result.Success(1L)
        coEvery { mockRepository.getVersions(1L) } returns flowOf(Result.Success(listOf(version)))
        coEvery { mockRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))
        coEvery { mockCalculateAts.invoke(any()) } returns Result.Success(
            AtsReport(
                resumeVersionId = 1L,
                jobDescriptionId = 1L,
                overallScore = 80,
                matchPercentage = 80,
                matchedKeywords = listOf("Kotlin"),
                missingKeywords = listOf("Rust")
            )
        )

        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportFile(mockk<Uri>()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(ResumeEngineEvent.ContinueFromPreview)
        viewModel.onEvent(ResumeEngineEvent.UpdateJdText(longJd))
        viewModel.onEvent(ResumeEngineEvent.RunAtsScan)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is ResumeEngineState.AtsResult)
        assertEquals(80, (state as ResumeEngineState.AtsResult).score)
    }

    @Test
    fun `ats failure enters Error and back restores AtsScanning`() = runTest {
        coEvery { mockImport.invoke(any()) } returns Result.Success(1L)
        coEvery { mockRepository.getVersions(1L) } returns flowOf(Result.Success(listOf(version)))
        coEvery { mockRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))
        coEvery { mockCalculateAts.invoke(any()) } returns Result.Failure(DomainError("AI down"))

        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportFile(mockk<Uri>()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(ResumeEngineEvent.ContinueFromPreview)
        viewModel.onEvent(ResumeEngineEvent.UpdateJdText(longJd))
        viewModel.onEvent(ResumeEngineEvent.RunAtsScan)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value is ResumeEngineState.Error)

        // Back from an ATS error restores AtsScanning (not Import) via lastStableState.
        viewModel.onEvent(ResumeEngineEvent.Back)
        assertTrue(viewModel.state.value is ResumeEngineState.AtsScanning)
    }

    @Test
    fun `skip ats advances to Optimizing`() = runTest {
        coEvery { mockImport.invoke(any()) } returns Result.Success(1L)
        coEvery { mockRepository.getVersions(1L) } returns flowOf(Result.Success(listOf(version)))
        coEvery { mockRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))

        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportFile(mockk<Uri>()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(ResumeEngineEvent.ContinueFromPreview)
        viewModel.onEvent(ResumeEngineEvent.SkipAts)

        assertTrue(viewModel.state.value is ResumeEngineState.Optimizing)
    }

    @Test
    fun `improve section streams suggestion and accept applies it`() = runTest {
        coEvery { mockImport.invoke(any()) } returns Result.Success(1L)
        coEvery { mockRepository.getVersions(1L) } returns flowOf(Result.Success(listOf(version)))
        coEvery { mockRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))
        every { mockStreamImprove.stream(any()) } returns flowOf("Improved", " content")

        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportFile(mockk<Uri>()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(ResumeEngineEvent.ContinueFromPreview)
        viewModel.onEvent(ResumeEngineEvent.SkipAts)

        viewModel.onEvent(ResumeEngineEvent.ImproveSection("Experience"))
        testDispatcher.scheduler.advanceUntilIdle()

        val optimizing = viewModel.state.value as ResumeEngineState.Optimizing
        assertEquals("Improved content", optimizing.suggestions["Experience"])
        // Live streaming content is cleared once the full suggestion lands.
        assertEquals(null, optimizing.streamingContent)

        viewModel.onEvent(ResumeEngineEvent.AcceptSuggestion("Experience"))
        val updated = viewModel.state.value as ResumeEngineState.Optimizing
        assertEquals("Improved content", updated.version.sections.first().content)
        assertTrue(updated.suggestions.isEmpty())
    }

    @Test
    fun `improve section failure enters error`() = runTest {
        coEvery { mockImport.invoke(any()) } returns Result.Success(1L)
        coEvery { mockRepository.getVersions(1L) } returns flowOf(Result.Success(listOf(version)))
        coEvery { mockRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))
        every { mockStreamImprove.stream(any()) } returns flow { throw RuntimeException("AI down") }

        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportFile(mockk<Uri>()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(ResumeEngineEvent.ContinueFromPreview)
        viewModel.onEvent(ResumeEngineEvent.SkipAts)

        viewModel.onEvent(ResumeEngineEvent.ImproveSection("Experience"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value is ResumeEngineState.Error)
    }

    @Test
    fun `save version persists and transitions to Exporting`() = runTest {
        coEvery { mockImport.invoke(any()) } returns Result.Success(1L)
        coEvery { mockRepository.getVersions(1L) } returns flowOf(Result.Success(listOf(version)))
        coEvery { mockRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))
        coEvery { mockRepository.saveVersion(any()) } returns Result.Success(42L)

        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportFile(mockk<Uri>()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(ResumeEngineEvent.ContinueFromPreview)
        viewModel.onEvent(ResumeEngineEvent.SkipAts)
        viewModel.onEvent(ResumeEngineEvent.SaveVersion("v2 — Optimized"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is ResumeEngineState.Exporting)
        assertEquals(42L, (state as ResumeEngineState.Exporting).version.id)
    }

    @Test
    fun `save failure enters Error and back restores Optimizing`() = runTest {
        coEvery { mockImport.invoke(any()) } returns Result.Success(1L)
        coEvery { mockRepository.getVersions(1L) } returns flowOf(Result.Success(listOf(version)))
        coEvery { mockRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))
        coEvery { mockRepository.saveVersion(any()) } returns Result.Failure(DomainError("Disk full"))

        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportFile(mockk<Uri>()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(ResumeEngineEvent.ContinueFromPreview)
        viewModel.onEvent(ResumeEngineEvent.SkipAts)
        viewModel.onEvent(ResumeEngineEvent.SaveVersion("v2 — Optimized"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value is ResumeEngineState.Error)

        // Back from a Save error restores Optimizing (not a stuck Saving spinner).
        viewModel.onEvent(ResumeEngineEvent.Back)
        assertTrue(viewModel.state.value is ResumeEngineState.Optimizing)
    }

    @Test
    fun `finish resets state to Import`() = runTest {
        coEvery { mockImport.invoke(any()) } returns Result.Success(1L)
        coEvery { mockRepository.getVersions(1L) } returns flowOf(Result.Success(listOf(version)))
        coEvery { mockRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))
        coEvery { mockRepository.saveVersion(any()) } returns Result.Success(42L)

        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportFile(mockk<Uri>()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(ResumeEngineEvent.ContinueFromPreview)
        viewModel.onEvent(ResumeEngineEvent.SkipAts)
        viewModel.onEvent(ResumeEngineEvent.SaveVersion("v2"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ResumeEngineEvent.Finish)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ResumeEngineState.Import, viewModel.state.value)
    }

    @Test
    fun `back from Preview returns to Import`() = runTest {
        coEvery { mockImport.invoke(any()) } returns Result.Success(1L)
        coEvery { mockRepository.getVersions(1L) } returns flowOf(Result.Success(listOf(version)))
        coEvery { mockRepository.getResumeById(1L) } returns flowOf(Result.Success(resume))

        val viewModel = createViewModel()
        viewModel.onEvent(ResumeEngineEvent.ImportFile(mockk<Uri>()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(ResumeEngineEvent.Back)

        assertEquals(ResumeEngineState.Import, viewModel.state.value)
    }
}
