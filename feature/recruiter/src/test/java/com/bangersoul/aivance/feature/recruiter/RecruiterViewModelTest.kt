package com.bangersoul.aivance.feature.recruiter

import androidx.lifecycle.SavedStateHandle
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.OutreachDraft
import com.bangersoul.aivance.core.common.model.Recruiter
import com.bangersoul.aivance.core.common.model.Resume
import com.bangersoul.aivance.core.common.model.ResumeVersion
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.crm.FindRecruitersUseCase
import com.bangersoul.aivance.core.domain.usecase.crm.GenerateOutreachDraftUseCase
import com.bangersoul.aivance.core.domain.usecase.crm.OutreachRequest
import com.bangersoul.aivance.core.domain.usecase.job.GetJobDetailsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecruiterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockFindRecruiters: FindRecruitersUseCase = mockk()
    private val mockGetJobDetails: GetJobDetailsUseCase = mockk()
    private val mockGenerateOutreach: GenerateOutreachDraftUseCase = mockk()
    private val mockResumeRepository: ResumeRepository = mockk()
    private val mockTrackEvent: TrackEventUseCase = mockk()

    private val job = JobListing(
        id = "job-1",
        title = "Android Engineer",
        company = "Acme Corp",
        description = "Android role at Acme",
        url = "https://acme.com/jobs/android",
        sourceProvider = "REMOTE_OK"
    )

    private val recruiter = Recruiter(
        id = "rec-1",
        name = "Jane Smith",
        companyId = "acme"
    )

    private val resume = Resume(
        id = 7L,
        name = "My Resume",
        primaryVersionId = 42L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockTrackEvent.invoke(any()) } returns Result.Success(Unit)
        // Default: user has a real resume with a primary version.
        every { mockResumeRepository.getResumes() } returns flowOf(Result.Success(listOf(resume)))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = RecruiterViewModel(
        SavedStateHandle(),
        mockFindRecruiters,
        mockGetJobDetails,
        mockGenerateOutreach,
        mockResumeRepository,
        mockTrackEvent
    )

    @Test
    fun `blank job id keeps view model loading`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value is RecruiterUiState.Loading)
    }

    @Test
    fun `load resolves domain from company and surfaces recruiters`() = runTest(testDispatcher) {
        coEvery { mockGetJobDetails.invoke("job-1") } returns Result.Success(job)
        coEvery { mockFindRecruiters.invoke("acmecorp.com") } returns Result.Success(listOf(recruiter))

        val viewModel = createViewModel()
        viewModel.load("job-1")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is RecruiterUiState.Success)
        assertEquals(1, (state as RecruiterUiState.Success).recruiters.size)
        assertEquals("Jane Smith", state.recruiters.first().name)
        coVerify { mockFindRecruiters.invoke("acmecorp.com") }
    }

    @Test
    fun `job not found surfaces error`() = runTest(testDispatcher) {
        coEvery { mockGetJobDetails.invoke("job-missing") } returns Result.Failure(
            com.bangersoul.aivance.core.common.result.DomainError("Job not found")
        )

        val viewModel = createViewModel()
        viewModel.load("job-missing")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is RecruiterUiState.Error)
        assertEquals("Job not found", (state as RecruiterUiState.Error).message)
    }

    @Test
    fun `recruiter lookup failure surfaces error`() = runTest(testDispatcher) {
        coEvery { mockGetJobDetails.invoke("job-1") } returns Result.Success(job)
        coEvery { mockFindRecruiters.invoke(any()) } returns Result.Failure(
            com.bangersoul.aivance.core.common.result.DomainError("Provider down")
        )

        val viewModel = createViewModel()
        viewModel.load("job-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RecruiterUiState.Error)
    }

    @Test
    fun `selecting a recruiter updates selection and clears draft`() = runTest(testDispatcher) {
        coEvery { mockGetJobDetails.invoke("job-1") } returns Result.Success(job)
        coEvery { mockFindRecruiters.invoke(any()) } returns Result.Success(listOf(recruiter))

        val viewModel = createViewModel()
        viewModel.load("job-1")
        testDispatcher.scheduler.advanceUntilIdle()

        val other = recruiter.copy(id = "rec-2", name = "John Doe")
        viewModel.onEvent(RecruiterUiEvent.SelectRecruiter(other))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as RecruiterUiState.Success
        assertEquals("John Doe", state.selectedRecruiter?.name)
        assertNull(state.draft)
    }

    @Test
    fun `generating outreach sets draft on success`() = runTest(testDispatcher) {
        coEvery { mockGetJobDetails.invoke("job-1") } returns Result.Success(job)
        coEvery { mockFindRecruiters.invoke(any()) } returns Result.Success(listOf(recruiter))
        coEvery { mockGenerateOutreach.invoke(any()) } returns Result.Success(
            OutreachDraft(recruiterId = "rec-1", jobId = "job-1", type = "COLD_EMAIL", content = "Hi Jane")
        )

        val viewModel = createViewModel()
        viewModel.load("job-1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(RecruiterUiEvent.SelectRecruiter(recruiter))
        viewModel.onEvent(RecruiterUiEvent.GenerateOutreach("COLD_EMAIL"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as RecruiterUiState.Success
        assertEquals(false, state.isGenerating)
        assertEquals("Hi Jane", state.draft?.content)
        assertNull(state.outreachError)
        // Uses the user's REAL resume id + primaryVersionId, not a hardcoded (1L, 1L).
        coVerify { mockGenerateOutreach.invoke(OutreachRequest(7L, 42L, "rec-1", "job-1", "COLD_EMAIL")) }
    }

    @Test
    fun `generating outreach falls back to first version when no primary version is set`() = runTest(testDispatcher) {
        val resumeNoPrimary = Resume(id = 9L, name = "No Primary", primaryVersionId = null)
        every { mockResumeRepository.getResumes() } returns flowOf(Result.Success(listOf(resumeNoPrimary)))
        every { mockResumeRepository.getVersions(9L) } returns flowOf(
            Result.Success(listOf(ResumeVersion(id = 5L, resumeId = 9L, versionName = "v1")))
        )
        coEvery { mockGetJobDetails.invoke("job-1") } returns Result.Success(job)
        coEvery { mockFindRecruiters.invoke(any()) } returns Result.Success(listOf(recruiter))
        coEvery { mockGenerateOutreach.invoke(any()) } returns Result.Success(
            OutreachDraft(recruiterId = "rec-1", jobId = "job-1", type = "COLD_EMAIL", content = "Hi Jane")
        )

        val viewModel = createViewModel()
        viewModel.load("job-1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(RecruiterUiEvent.SelectRecruiter(recruiter))
        viewModel.onEvent(RecruiterUiEvent.GenerateOutreach("COLD_EMAIL"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockGenerateOutreach.invoke(OutreachRequest(9L, 5L, "rec-1", "job-1", "COLD_EMAIL")) }
    }

    @Test
    fun `generating outreach without a resume surfaces an actionable error and never calls the use case`() = runTest(testDispatcher) {
        every { mockResumeRepository.getResumes() } returns flowOf(Result.Success(emptyList()))
        coEvery { mockGetJobDetails.invoke("job-1") } returns Result.Success(job)
        coEvery { mockFindRecruiters.invoke(any()) } returns Result.Success(listOf(recruiter))

        val viewModel = createViewModel()
        viewModel.load("job-1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(RecruiterUiEvent.SelectRecruiter(recruiter))
        viewModel.onEvent(RecruiterUiEvent.GenerateOutreach("COLD_EMAIL"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as RecruiterUiState.Success
        assertEquals(false, state.isGenerating)
        assertNull(state.draft)
        assertNotNull(state.outreachError)
        coVerify(exactly = 0) { mockGenerateOutreach.invoke(any()) }
    }

    @Test
    fun `outreach generation failure keeps state without draft`() = runTest(testDispatcher) {
        coEvery { mockGetJobDetails.invoke("job-1") } returns Result.Success(job)
        coEvery { mockFindRecruiters.invoke(any()) } returns Result.Success(listOf(recruiter))
        coEvery { mockGenerateOutreach.invoke(any()) } returns Result.Failure(
            com.bangersoul.aivance.core.common.result.DomainError("AI provider down")
        )

        val viewModel = createViewModel()
        viewModel.load("job-1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(RecruiterUiEvent.SelectRecruiter(recruiter))
        viewModel.onEvent(RecruiterUiEvent.GenerateOutreach("LINKEDIN_REQUEST"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as RecruiterUiState.Success
        assertEquals(false, state.isGenerating)
        assertNull(state.draft)
        assertEquals("AI provider down", state.outreachError)
    }
}
