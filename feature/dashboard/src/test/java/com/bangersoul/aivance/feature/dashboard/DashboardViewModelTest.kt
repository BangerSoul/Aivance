package com.bangersoul.aivance.feature.dashboard

import app.cash.turbine.test
import com.bangersoul.aivance.core.common.model.CareerRecommendation
import com.bangersoul.aivance.core.common.model.CareerState
import com.bangersoul.aivance.core.common.model.DiscoveryState
import com.bangersoul.aivance.core.common.model.GrowthState
import com.bangersoul.aivance.core.common.model.IntelligenceState
import com.bangersoul.aivance.core.common.model.PipelineState
import com.bangersoul.aivance.core.common.model.ProfileState
import com.bangersoul.aivance.core.common.model.UpcomingInterviewShort
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.engine.CareerStateEngine
import com.bangersoul.aivance.core.domain.engine.NavigationIntent
import com.bangersoul.aivance.core.domain.engine.NavigationWorkflowEngine
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventRequest
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockStateEngine: CareerStateEngine = mockk()
    private val mockNavWorkflowEngine: NavigationWorkflowEngine = mockk()
    private val mockTrackEvent: TrackEventUseCase = mockk()

    private lateinit var viewModel: DashboardViewModel

    /**
     * A real [StateFlow] whose collection throws — lets the error test drive
     * the ViewModel's catch branch without a cold-flow type mismatch.
     */
    private fun throwingStateFlow(): StateFlow<CareerState> = object : StateFlow<CareerState> {
        override val replayCache: List<CareerState> get() = emptyList()
        override val value: CareerState get() = CareerState()
        override suspend fun collect(collector: FlowCollector<CareerState>): Nothing =
            throw RuntimeException("boom")
    }

    private fun sampleCareerState() = CareerState(
        profile = ProfileState(name = "Azmath Shaik", targetRole = "Software Engineer"),
        intelligence = IntelligenceState(atsScore = 85),
        discovery = DiscoveryState(savedJobsCount = 3),
        pipeline = PipelineState(
            activeApplications = 5,
            upcomingInterviews = listOf(
                UpcomingInterviewShort(id = "1", company = "Google", role = "Android Engineer", dateTime = "Fri 10:00")
            )
        ),
        growth = GrowthState(careerScore = 78),
        recommendations = listOf(
            CareerRecommendation(
                id = 1,
                title = "Polish Resume",
                description = "Your resume needs keyword polish.",
                priority = "HIGH",
                category = "RESUME"
            )
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockStateEngine.state } returns MutableStateFlow(sampleCareerState())
        every { mockNavWorkflowEngine.getRecommendedDestination(any()) } returns
            NavigationIntent.Action(label = "Search Jobs", route = "job_search")
        coEvery { mockTrackEvent.invoke(any()) } returns Result.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DashboardViewModel(
        mockStateEngine,
        mockNavWorkflowEngine,
        mockTrackEvent
    )

    @Test
    fun `initial state is loading then aggregates career state`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        // Tautological-assertion fix (L-02 / P2-02): the initial Loading state
        // must be observed transitioning into the fully aggregated state.
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.uiState.test {
            // Skip the initial Loading emission(s).
            skipItems(1)
            val state = awaitItem()

            assertFalse(state.isLoading)
            assertEquals("Hello, Azmath", state.greeting)
            assertEquals("Software Engineer", state.userDesignation)
            assertEquals(78, state.careerScore)
            assertEquals(85, state.atsScore)
            assertEquals(5, state.activeApplications)
            assertEquals("Fri 10:00", state.nextInterview)
            assertEquals(3, state.savedJobs)
            assertEquals("AI Tip: Polish Resume", state.aiRecommendation)
            assertEquals(NavigationIntent.Action("Search Jobs", "job_search"), state.nextBestAction)
            assertTrue(state.agentMissions.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits error state when state engine fails`() = runTest(testDispatcher) {
        every { mockStateEngine.state } returns throwingStateFlow()
        viewModel = createViewModel()

        viewModel.uiState.test {
            // Initial Loading emission first...
            assertTrue(awaitItem().isLoading)
            // ...then the catch branch surfaces the error instead of hanging.
            val errorState = awaitItem()
            assertFalse(errorState.isLoading)
            assertEquals("boom", errorState.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigation events send correct effects`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.onEvent(DashboardUiEvent.NavigateToResume)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            val effect = awaitItem()
            assertTrue(effect is DashboardUiEffect.NavigateTo)
            assertEquals("resume", (effect as DashboardUiEffect.NavigateTo).route)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh triggers track event`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.onEvent(DashboardUiEvent.Refresh)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockTrackEvent.invoke(match { it.eventName == "dashboard_refresh" }) }
    }

    @Test
    fun `settings event opens settings`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.onEvent(DashboardUiEvent.NavigateToSettings)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            assertTrue(awaitItem() is DashboardUiEffect.OpenSettings)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `trackEvent is forwarded with the given name`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.trackEvent("dashboard_retry")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockTrackEvent.invoke(match { it.eventName == "dashboard_retry" }) }
    }
}
