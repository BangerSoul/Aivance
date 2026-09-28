package com.bangersoul.aivance.feature.jobs

import app.cash.turbine.test
import androidx.lifecycle.SavedStateHandle
import com.bangersoul.aivance.core.common.model.CareerState
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.core.common.model.ProfileState
import com.bangersoul.aivance.core.common.result.ProviderError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.engine.CareerStateEngine
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.job.GetCachedJobsUseCase
import com.bangersoul.aivance.core.domain.usecase.job.ScoreJobFitRequest
import com.bangersoul.aivance.core.domain.usecase.job.ScoreJobFitUseCase
import com.bangersoul.aivance.core.domain.usecase.job.SearchJobsRequest
import com.bangersoul.aivance.core.domain.usecase.job.SearchJobsUseCase
import com.bangersoul.aivance.core.domain.usecase.job.ToggleJobBookmarkUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [JobsViewModel].
 *
 * uiState is a `WhileSubscribed` stateIn flow, so a collector must be active
 * for emissions to be produced; assertions therefore read the latest value
 * after collecting (see [collectStates]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JobsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockSearchJobs: SearchJobsUseCase = mockk()
    private val mockGetCachedJobs: GetCachedJobsUseCase = mockk()
    private val mockToggleBookmark: ToggleJobBookmarkUseCase = mockk()
    private val mockCareerStateEngine: CareerStateEngine = mockk()
    private val mockScoreJobFit: ScoreJobFitUseCase = mockk()
    private val mockTrackEvent: TrackEventUseCase = mockk()

    private val jobs = listOf(
        JobListing(
            id = "1",
            title = "Android Dev",
            company = "Google",
            description = "Android role",
            url = "https://careers.google.com",
            sourceProvider = "REMOTE_OK"
        ),
        JobListing(
            id = "2",
            title = "iOS Dev",
            company = "Apple",
            description = "iOS role",
            url = "https://apple.com",
            sourceProvider = "REMOTE_OK"
        )
    )

    private fun createViewModel() = JobsViewModel(
        mockSearchJobs, mockGetCachedJobs, mockToggleBookmark, mockCareerStateEngine, mockScoreJobFit,
        mockTrackEvent,
        savedStateHandle = SavedStateHandle()
    )

    /**
     * Starts a background collector so the stateIn upstream activates, and
     * returns the list of every emitted state for ordering assertions. The
     * collector must outlive the test's main coroutine, so the caller passes
     * runTest's [backgroundScope].
     */
    private fun collectStates(viewModel: JobsViewModel, scope: CoroutineScope): MutableList<JobsUiState> {
        val states = mutableListOf<JobsUiState>()
        scope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            viewModel.uiState.collect { states += it }
        }
        return states
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockTrackEvent(any()) } returns Result.Success(Unit)
        coEvery { mockScoreJobFit.invoke(any()) } returns emptyMap()
        // Cold-start cache hydration (B3/B4) must not interfere with the search
        // assertions below: an empty corpus leaves the initial state untouched.
        coEvery { mockGetCachedJobs(Unit) } returns Result.Success(emptyList())
        every { mockCareerStateEngine.state } returns MutableStateFlow(CareerState())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loading then search returns jobs`() = runTest {
        coEvery { mockSearchJobs.invoke(any()) } returns Result.Success(jobs)

        val viewModel = createViewModel()
        val states = collectStates(viewModel, backgroundScope)

        // Tautological-assertion fix (L-02 / P2-02): the initial Loading state
        // is observed through collection and must TRANSITION to a loaded state.
        assertTrue(states.first() is JobsUiState.Loading)

        viewModel.onEvent(JobsUiEvent.Search("Android"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is JobsUiState.Success)
        assertEquals(2, (state as JobsUiState.Success).jobs.size)
        assertEquals("Android", state.filter.query)
    }

    @Test
    fun `opens on the locally cached corpus when no search has run`() = runTest {
        // B3/B4: the background harvesters keep the jobs table populated, so a
        // cold start must render those listings instead of "Found 0 active
        // opportunities" while the inbox advertises new matches.
        coEvery { mockGetCachedJobs(Unit) } returns Result.Success(jobs)

        val viewModel = createViewModel()
        val states = collectStates(viewModel, backgroundScope)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is JobsUiState.Success)
        assertEquals(2, (state as JobsUiState.Success).jobs.size)
        assertEquals("1", state.jobs.first().id)
    }

    @Test
    fun `restores typed search query from saved state`() = runTest {
        coEvery { mockSearchJobs.invoke(any()) } returns Result.Success(emptyList())

        // Simulates process death: the query the user typed was written to
        // SavedStateHandle and is picked up by the recreated ViewModel.
        val handle = SavedStateHandle().apply { set("jobs_search_query", "android") }
        val viewModel = JobsViewModel(
            mockSearchJobs, mockGetCachedJobs, mockToggleBookmark, mockCareerStateEngine, mockScoreJobFit,
            mockTrackEvent,
            savedStateHandle = handle
        )
        val states = collectStates(viewModel, backgroundScope)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is JobsUiState.Success)
        assertEquals("android", (state as JobsUiState.Success).filter.query)
    }

    @Test
    fun `search tracks discovery event`() = runTest {
        coEvery { mockSearchJobs.invoke(any()) } returns Result.Success(emptyList())

        val viewModel = createViewModel()
        viewModel.onEvent(JobsUiEvent.Search("Android"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockTrackEvent(TrackEventRequest("job_discovery_search")) }
    }

    @Test
    fun `empty search results show Success with empty jobs`() = runTest {
        coEvery { mockSearchJobs.invoke(any()) } returns Result.Success(emptyList())

        val viewModel = createViewModel()
        collectStates(viewModel, backgroundScope)
        viewModel.onEvent(JobsUiEvent.Search("nothing"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is JobsUiState.Success)
        assertEquals(0, (state as JobsUiState.Success).jobs.size)
    }

    @Test
    fun `search failure surfaces snackbar effect`() = runTest {
        // The current ViewModel never emits JobsUiState.Error — a failed search
        // surfaces a snackbar while the aggregated Success state stays stable.
        coEvery { mockSearchJobs.invoke(any()) } returns Result.Failure(
            ProviderError("test", message = "Search failed")
        )

        val viewModel = createViewModel()
        viewModel.onEvent(JobsUiEvent.Search("Android"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            val effect = awaitItem()
            assertTrue(effect is JobsUiEffect.ShowSnackbar)
            assertEquals("Search failed", (effect as JobsUiEffect.ShowSnackbar).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `view details triggers navigation effect`() = runTest {
        coEvery { mockSearchJobs.invoke(any()) } returns Result.Success(emptyList())

        val viewModel = createViewModel()
        viewModel.onEvent(JobsUiEvent.ViewDetails("job_1"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            val effect = awaitItem()
            assertTrue(effect is JobsUiEffect.NavigateToDetails)
            assertEquals("job_1", (effect as JobsUiEffect.NavigateToDetails).jobId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggle bookmark shows snackbar effect`() = runTest {
        coEvery { mockToggleBookmark.invoke("job_1") } returns Result.Success(true)

        val viewModel = createViewModel()
        viewModel.onEvent(JobsUiEvent.ToggleBookmark("job_1"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            val effect = awaitItem()
            assertTrue(effect is JobsUiEffect.ShowSnackbar)
            assertEquals("Job bookmarked", (effect as JobsUiEffect.ShowSnackbar).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fit scores merge AI results with rule-based fallback`() = runTest {
        val profile = ProfileState(
            targetRole = "Android Engineer", skills = listOf("Kotlin"), workPreference = "REMOTE"
        )
        every { mockCareerStateEngine.state } returns MutableStateFlow(CareerState(profile = profile))
        coEvery { mockSearchJobs.invoke(any()) } returns Result.Success(jobs)
        coEvery { mockScoreJobFit.invoke(any()) } returns mapOf("1" to 95)

        val viewModel = createViewModel()
        collectStates(viewModel, backgroundScope)
        viewModel.onEvent(JobsUiEvent.Search("Android"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as JobsUiState.Success
        assertEquals(95, state.fitScores["1"])
        // Job "2" was not AI-scored -> the rule-based scorer fills it.
        assertEquals(
            JobFitScorer.calculateFitScore(jobs[1], profile),
            state.fitScores["2"]
        )
        assertEquals(2, state.fitScores.size)
        coVerify { mockScoreJobFit.invoke(ScoreJobFitRequest(jobs = jobs, profile = profile)) }
    }

    @Test
    fun `fit scores fall back to rule-based when AI returns nothing`() = runTest {
        val profile = ProfileState(targetRole = "Android Engineer", skills = listOf("Kotlin"))
        every { mockCareerStateEngine.state } returns MutableStateFlow(CareerState(profile = profile))
        coEvery { mockSearchJobs.invoke(any()) } returns Result.Success(jobs)
        coEvery { mockScoreJobFit.invoke(any()) } returns emptyMap()

        val viewModel = createViewModel()
        collectStates(viewModel, backgroundScope)
        viewModel.onEvent(JobsUiEvent.Search("Android"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as JobsUiState.Success
        assertEquals(JobFitScorer.calculateFitScore(jobs[0], profile), state.fitScores["1"])
        assertEquals(JobFitScorer.calculateFitScore(jobs[1], profile), state.fitScores["2"])
    }

    @Test
    fun `fit scores are cleared when a new search starts`() = runTest {
        val profile = ProfileState(targetRole = "Android Engineer")
        every { mockCareerStateEngine.state } returns MutableStateFlow(CareerState(profile = profile))
        coEvery { mockSearchJobs.invoke(any()) } returns Result.Success(jobs)
        coEvery { mockScoreJobFit.invoke(any()) } returns mapOf("1" to 95)

        val viewModel = createViewModel()
        collectStates(viewModel, backgroundScope)
        viewModel.onEvent(JobsUiEvent.Search("Android"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(95, (viewModel.uiState.value as JobsUiState.Success).fitScores["1"])

        // A newer search drops the stale scores.
        coEvery { mockSearchJobs.invoke(any()) } returns Result.Success(emptyList())
        viewModel.onEvent(JobsUiEvent.Search("iOS"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue((viewModel.uiState.value as JobsUiState.Success).fitScores.isEmpty())
    }

    @Test
    fun `update filter triggers search with new filter`() = runTest {
        coEvery { mockSearchJobs.invoke(any()) } returns Result.Success(jobs)

        val viewModel = createViewModel()
        collectStates(viewModel, backgroundScope)
        val newFilter = JobSearchFilter(query = "Kotlin")
        viewModel.onEvent(JobsUiEvent.UpdateFilter(newFilter))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockSearchJobs.invoke(SearchJobsRequest(filter = newFilter)) }
        val state = viewModel.uiState.value
        assertTrue(state is JobsUiState.Success)
        assertEquals("Kotlin", (state as JobsUiState.Success).filter.query)
    }

    @Test
    fun `profile work preference is a ranking signal not a hard remote filter`() = runTest {
        // Regression: the profile default (REMOTE) used to be promoted into the
        // displayed filter, making the Workplace dropdown show "Remote" as
        // pre-selected and silently zeroing out remote-friendly boards. It must
        // stay null unless the user explicitly picks a Workplace filter.
        val profile = ProfileState(targetRole = "Android Engineer", workPreference = "REMOTE")
        every { mockCareerStateEngine.state } returns MutableStateFlow(CareerState(profile = profile))
        coEvery { mockSearchJobs.invoke(any()) } returns Result.Success(jobs)

        val viewModel = createViewModel()
        collectStates(viewModel, backgroundScope)
        viewModel.onEvent(JobsUiEvent.Search("Android"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as JobsUiState.Success
        assertEquals(null, state.filter.remoteType)

        // An explicit Workplace choice is still honored and passes through.
        viewModel.onEvent(JobsUiEvent.UpdateFilter(
            JobSearchFilter(remoteType = com.bangersoul.aivance.core.common.enums.RemoteType.REMOTE)
        ))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            com.bangersoul.aivance.core.common.enums.RemoteType.REMOTE,
            (viewModel.uiState.value as JobsUiState.Success).filter.remoteType
        )
    }

    @Test
    fun `clear filters resets non-query filters and re-searches with the query kept`() = runTest {
        coEvery { mockSearchJobs.invoke(any()) } returns Result.Success(jobs)

        val viewModel = createViewModel()
        collectStates(viewModel, backgroundScope)
        val richFilter = JobSearchFilter(
            query = "Kotlin",
            remotePolicy = com.bangersoul.aivance.core.common.enums.RemotePolicy.FULLY_REMOTE,
            includedKeywords = listOf("senior")
        )
        viewModel.onEvent(JobsUiEvent.UpdateFilter(richFilter))
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { mockSearchJobs.invoke(SearchJobsRequest(filter = richFilter)) }

        viewModel.onEvent(JobsUiEvent.ClearFilters)
        testDispatcher.scheduler.advanceUntilIdle()

        // The query survives; every catalog/filter dimension is reset to default.
        coVerify { mockSearchJobs.invoke(SearchJobsRequest(filter = JobSearchFilter(query = "Kotlin"))) }
        val state = viewModel.uiState.value as JobsUiState.Success
        assertEquals("Kotlin", state.filter.query)
        assertEquals(null, state.filter.remotePolicy)
        assertTrue(state.filter.includedKeywords.isEmpty())
    }

    @Test
    fun `refresh re-runs the current search`() = runTest {
        coEvery { mockSearchJobs.invoke(any()) } returns Result.Success(jobs)

        val viewModel = createViewModel()
        collectStates(viewModel, backgroundScope)
        viewModel.onEvent(JobsUiEvent.Search("Android"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, (viewModel.uiState.value as JobsUiState.Success).jobs.size)

        // The repository now returns fresh data; Refresh must re-run the search.
        coEvery { mockSearchJobs.invoke(any()) } returns Result.Success(listOf(jobs.first()))
        viewModel.onEvent(JobsUiEvent.Refresh)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as JobsUiState.Success
        assertEquals(1, state.jobs.size)
        coVerify(exactly = 2) {
            mockSearchJobs.invoke(SearchJobsRequest(filter = JobSearchFilter(query = "Android")))
        }
    }
}
