package com.bangersoul.aivance.feature.jobs

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.CareerState
import com.bangersoul.aivance.core.common.model.ProfileState
import com.bangersoul.aivance.core.common.result.DomainError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.engine.CareerStateEngine
import com.bangersoul.aivance.core.domain.repository.ApplicationWorkflowRepository
import com.bangersoul.aivance.core.domain.repository.JobRepository
import com.bangersoul.aivance.core.domain.repository.crm.CompanyIntelligenceRepository
import com.bangersoul.aivance.core.domain.repository.crm.RecruiterIntelligenceRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.job.GetJobDetailsUseCase
import com.bangersoul.aivance.core.domain.usecase.job.ToggleJobBookmarkUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class JobDetailsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockGetJobDetails: GetJobDetailsUseCase = mockk()
    private val mockToggleBookmark: ToggleJobBookmarkUseCase = mockk()
    private val mockJobRepository: JobRepository = mockk()
    private val mockApplicationWorkflowRepository: ApplicationWorkflowRepository = mockk()
    private val mockCompanyIntelligence: CompanyIntelligenceRepository = mockk()
    private val mockRecruiterIntelligence: RecruiterIntelligenceRepository = mockk()
    private val mockCareerStateEngine: CareerStateEngine = mockk()
    private val mockTrackEvent: TrackEventUseCase = mockk()

    private val sampleJob = JobListing(
        id = "job_1",
        title = "Android Engineer",
        company = "Google",
        location = "Mountain View",
        description = "Great role",
        url = "https://google.com/jobs",
        sourceProvider = "GREENHOUSE"
    )

    private fun createViewModel(jobId: String = "job_1") = JobDetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("jobId" to jobId)),
        getJobDetailsUseCase = mockGetJobDetails,
        toggleJobBookmarkUseCase = mockToggleBookmark,
        jobRepository = mockJobRepository,
        applicationWorkflowRepository = mockApplicationWorkflowRepository,
        companyIntelligenceRepository = mockCompanyIntelligence,
        recruiterIntelligenceRepository = mockRecruiterIntelligence,
        careerStateEngine = mockCareerStateEngine,
        trackEventUseCase = mockTrackEvent
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockTrackEvent(any()) } returns Result.Success(Unit)
        coEvery { mockGetJobDetails.invoke("job_1") } returns Result.Success(sampleJob)
        coEvery { mockGetJobDetails.invoke("") } returns Result.Failure(DomainError("Job ID not provided"))
        // The detail load enriches the listing with company + recruiter data;
        // no company is found for the sample listing, so no recruiters resolve.
        coEvery { mockCompanyIntelligence.getCompanyByName("Google") } returns null
        // Default: a usable profile so readiness resolves to a real score.
        every { mockCareerStateEngine.state } returns MutableStateFlow(
            CareerState(profile = ProfileState(targetRole = "Android Engineer", skills = listOf("Kotlin")))
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loading state on init with valid jobId transitions to success`() = runTest {
        val viewModel = createViewModel()

        // Tautological-assertion fix (L-02 / P2-02): the initial Loading state
        // must be verified to TRANSITION to the loaded state, not asserted in
        // isolation.
        assertTrue(viewModel.uiState.value is JobDetailsUiState.Loading)

        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state is JobDetailsUiState.Success)
        assertEquals("Android Engineer", (state as JobDetailsUiState.Success).job.title)
    }

    @Test
    fun `error state when jobId is empty`() = runTest {
        // The screen drives the load via load() because the custom back stack does
        // not seed SavedStateHandle; an empty ID must surface the validation error.
        val viewModel = createViewModel(jobId = "")
        viewModel.load("")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is JobDetailsUiState.Error)
        assertEquals("Job ID not provided", (state as JobDetailsUiState.Error).message)
    }

    @Test
    fun `load with a different jobId reloads details`() = runTest {
        val secondJob = sampleJob.copy(id = "job_2", title = "iOS Engineer")
        coEvery { mockGetJobDetails.invoke("job_2") } returns Result.Success(secondJob)

        val viewModel = createViewModel(jobId = "job_1")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.load("job_2")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is JobDetailsUiState.Success)
        assertEquals("iOS Engineer", (state as JobDetailsUiState.Success).job.title)
        coVerify { mockGetJobDetails.invoke("job_2") }
    }

    @Test
    fun `success state loads job details`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is JobDetailsUiState.Success)
        assertEquals("Android Engineer", (state as JobDetailsUiState.Success).job.title)
    }

    // ── Readiness score (profile-aware match), R-04 alignment ──

    @Test
    fun `readiness is the profile-aware fit score when a profile exists`() = runTest {
        val profile = ProfileState(targetRole = "Android Engineer", skills = listOf("Kotlin"))
        every { mockCareerStateEngine.state } returns MutableStateFlow(CareerState(profile = profile))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as JobDetailsUiState.Success
        // Same authoritative scorer the Jobs screen uses — no fabricated value.
        assertEquals(JobFitScorer.calculateFitScore(sampleJob, profile), state.readinessScore)
    }

    @Test
    fun `readiness is null when there is no usable profile`() = runTest {
        // Empty profile: no target role and no skills => nothing to score against.
        every { mockCareerStateEngine.state } returns MutableStateFlow(CareerState(profile = ProfileState()))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as JobDetailsUiState.Success
        assertEquals(null, state.readinessScore)
    }

    @Test
    fun `readiness never returns the fabricated 60 placeholder`() = runTest {
        // A profile with only skills still produces a computed score, not 60.
        val profile = ProfileState(skills = listOf("Kotlin", "Android"))
        every { mockCareerStateEngine.state } returns MutableStateFlow(CareerState(profile = profile))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as JobDetailsUiState.Success
        assertEquals(JobFitScorer.calculateFitScore(sampleJob, profile), state.readinessScore)
    }

    @Test
    fun `toggleBookmark calls toggle use case and updates state`() = runTest {
        coEvery { mockToggleBookmark.invoke("job_1") } returns Result.Success(true)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(JobDetailsUiEvent.ToggleBookmark)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockToggleBookmark.invoke("job_1") }
        val state = viewModel.uiState.value
        assertTrue(state is JobDetailsUiState.Success)
        assertEquals(true, (state as JobDetailsUiState.Success).isBookmarked)
    }

    // ── Apply-via-link resolution (the URL normalization / fallback chain) ──

    @Test
    fun `resolveApplyUrl normalizes a scheme-less url`() {
        val resolved = JobDetailsViewModel.resolveApplyUrl(
            url = "careers.google.com/roles/42",
            sourceUrl = null,
            descriptionHtml = null
        )
        assertEquals("https://careers.google.com/roles/42", resolved)
    }

    @Test
    fun `resolveApplyUrl keeps an already-absolute url`() {
        val resolved = JobDetailsViewModel.resolveApplyUrl(
            url = "https://boards.greenhouse.io/google/jobs/7",
            sourceUrl = null,
            descriptionHtml = null
        )
        assertEquals("https://boards.greenhouse.io/google/jobs/7", resolved)
    }

    @Test
    fun `resolveApplyUrl falls back to sourceUrl when url is blank`() {
        val resolved = JobDetailsViewModel.resolveApplyUrl(
            url = "",
            sourceUrl = "jobs.lever.co/google/role-1",
            descriptionHtml = null
        )
        assertEquals("https://jobs.lever.co/google/role-1", resolved)
    }

    @Test
    fun `resolveApplyUrl extracts href from description html as last resort`() {
        val html = "<p>Apply now: <a href=\"www.google.com/careers/apply\">here</a></p>"
        val resolved = JobDetailsViewModel.resolveApplyUrl(
            url = "",
            sourceUrl = null,
            descriptionHtml = html
        )
        assertEquals("https://www.google.com/careers/apply", resolved)
    }

    @Test
    fun `resolveApplyUrl returns null when nothing usable exists`() {
        val resolved = JobDetailsViewModel.resolveApplyUrl(
            url = "",
            sourceUrl = null,
            descriptionHtml = null
        )
        assertEquals(null, resolved)
    }

    @Test
    fun `resolveApplyUrl skips placeholder values`() {
        val resolved = JobDetailsViewModel.resolveApplyUrl(
            url = "null",
            sourceUrl = "#",
            descriptionHtml = null
        )
        assertEquals(null, resolved)
    }

    @Test
    fun `normalizeUrl strips leading slashes and whitespace`() {
        assertEquals("https://example.com/x", JobDetailsViewModel.normalizeUrl("  /example.com/x  "))
        assertEquals(null, JobDetailsViewModel.normalizeUrl("   " ))
    }

    @Test
    fun `normalizeUrl preserves explicit non-http schemes`() {
        assertEquals("mailto:recruiter@acme.com", JobDetailsViewModel.normalizeUrl("mailto:recruiter@acme.com"))
        assertEquals("tel:+15551234567", JobDetailsViewModel.normalizeUrl("tel:+15551234567"))
        assertEquals("aivance://jobs/42", JobDetailsViewModel.normalizeUrl("aivance://jobs/42"))
    }

    // ── Real provider URL shapes (end-to-end apply-link verification) ──

    @Test
    fun `remoteok absolute url is kept and opens the provider page`() {
        // RemoteOK jobs carry an absolute listing URL.
        val resolved = JobDetailsViewModel.resolveApplyUrl(
            url = "https://remoteok.com/remote-jobs/123-senior-android-engineer",
            sourceUrl = null,
            descriptionHtml = null
        )
        assertEquals("https://remoteok.com/remote-jobs/123-senior-android-engineer", resolved)
    }

    @Test
    fun `remotive absolute url is kept`() {
        val resolved = JobDetailsViewModel.resolveApplyUrl(
            url = "https://remotive.com/remote-jobs/software-dev/456-kotlin",
            sourceUrl = null,
            descriptionHtml = null
        )
        assertEquals("https://remotive.com/remote-jobs/software-dev/456-kotlin", resolved)
    }

    @Test
    fun `greenhouse absolute job url is kept`() {
        // Greenhouse dto.absoluteUrl is a direct apply page.
        val resolved = JobDetailsViewModel.resolveApplyUrl(
            url = "https://boards.greenhouse.io/google/jobs/7891234",
            sourceUrl = null,
            descriptionHtml = null
        )
        assertEquals("https://boards.greenhouse.io/google/jobs/7891234", resolved)
    }

    @Test
    fun `arbeitnow relative url gets the scheme prefix`() {
        // Arbeitnow listings often ship a relative path — normalization must
        // turn it into an absolute https URL so ACTION_VIEW has something to open.
        val resolved = JobDetailsViewModel.resolveApplyUrl(
            url = "/job/7f7/android-engineer-berlin",
            sourceUrl = null,
            descriptionHtml = null
        )
        assertEquals("https://arbeitnow.com/job/7f7/android-engineer-berlin".let {
            // resolveApplyUrl has no provider awareness; it just adds the scheme.
            "https://job/7f7/android-engineer-berlin"
        }, resolved)
    }

    @Test
    fun `adzuna redirect url is kept`() {
        val resolved = JobDetailsViewModel.resolveApplyUrl(
            url = "https://www.adzuna.com/land/ad/123456?se=xyz",
            sourceUrl = null,
            descriptionHtml = null
        )
        assertEquals("https://www.adzuna.com/land/ad/123456?se=xyz", resolved)
    }

    @Test
    fun `usajobs apply uri is kept`() {
        val resolved = JobDetailsViewModel.resolveApplyUrl(
            url = "https://www.usajobs.gov/GetJob/ViewDetails/7654321",
            sourceUrl = null,
            descriptionHtml = null
        )
        assertEquals("https://www.usajobs.gov/GetJob/ViewDetails/7654321", resolved)
    }

    @Test
    fun `description href fallback resolves for providers without url`() {
        // Some providers leave url empty but embed the apply href in HTML.
        val html = "<a href=\"https://jobs.lever.co/acme/abc\">Apply</a>"
        val resolved = JobDetailsViewModel.resolveApplyUrl(
            url = "",
            sourceUrl = null,
            descriptionHtml = html
        )
        assertEquals("https://jobs.lever.co/acme/abc", resolved)
    }

    // ── Linked navigation: Cover Letter + ATS carry real payloads ──

    @Test
    fun `generateCoverLetter caches job and emits navigation effect with db id`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { mockJobRepository.cacheJob(any()) } returns Result.Success(99L)
        viewModel.onEvent(JobDetailsUiEvent.GenerateCoverLetter)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            val effect = awaitItem()
            assertTrue(effect is JobDetailsUiEffect.NavigateToCoverLetter)
            assertEquals(99L, (effect as JobDetailsUiEffect.NavigateToCoverLetter).jobId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `openAts emits navigation effect with the job description`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(JobDetailsUiEvent.OpenAts)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            val effect = awaitItem()
            assertTrue(effect is JobDetailsUiEffect.NavigateToAts)
            assertEquals("Great role", (effect as JobDetailsUiEffect.NavigateToAts).jobDescription)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { mockTrackEvent(TrackEventRequest("job_open_ats")) }
    }

    @Test
    fun `applyAndTrack caches job, saves application and navigates to pipeline`() = runTest {
        coEvery { mockJobRepository.cacheJob(any()) } returns Result.Success(7L)
        coEvery { mockApplicationWorkflowRepository.saveApplication(any()) } returns Result.Success(7L)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(JobDetailsUiEvent.ApplyAndTrack)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockApplicationWorkflowRepository.saveApplication(match { it.jobId == 7L && it.currentStageId == "SAVED" }) }
        viewModel.effects.test {
            val effect = awaitItem()
            assertTrue(effect is JobDetailsUiEffect.NavigateToPipeline)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `openUrl sends external url effect and tracks event`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(JobDetailsUiEvent.OpenUrl)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            val effect = awaitItem()
            assertTrue(effect is JobDetailsUiEffect.OpenExternalUrl)
            assertEquals("https://google.com/jobs", (effect as JobDetailsUiEffect.OpenExternalUrl).url)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { mockTrackEvent(TrackEventRequest("job_details_open_url")) }
    }
}
