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
import com.bangersoul.aivance.core.domain.usecase.career.CareerGraphInsights
import com.bangersoul.aivance.core.domain.usecase.career.GetCareerGraphInsightsUseCase
import com.bangersoul.aivance.core.domain.usecase.career.RecordSkillGapEngagementUseCase
import com.bangersoul.aivance.core.domain.usecase.career.SkillGapItem
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockStateEngine: CareerStateEngine = mockk()
    private val mockNavWorkflowEngine: NavigationWorkflowEngine = mockk()
    private val mockGraphInsights: GetCareerGraphInsightsUseCase = mockk()
    private val mockRecordEngagement: RecordSkillGapEngagementUseCase = mockk()
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
        coEvery { mockGraphInsights.invoke() } returns CareerGraphInsights.EMPTY
        coEvery { mockRecordEngagement.invoke(any()) } returns Unit
        coEvery { mockTrackEvent.invoke(any()) } returns Result.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DashboardViewModel(
        mockStateEngine,
        mockNavWorkflowEngine,
        mockGraphInsights,
        mockRecordEngagement,
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
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `aiRecommendation is the first persisted recommendation and null when there are none`() =
        runTest(testDispatcher) {
            // The recommendations list is the sole source: it is loaded from the
            // `recommendations` Room table via AnalyticsRepository.getActiveRecommendations,
            // populated only by RecommendationEngine (an AI-provider-backed generator run by
            // the weekly AnalyticsSnapshotWorker). A zero-data / no-provider user therefore has
            // an empty table, and aiRecommendation must be null rather than a hardcoded string.
            every { mockStateEngine.state } returns MutableStateFlow(
                sampleCareerState().copy(recommendations = emptyList())
            )
            viewModel = createViewModel()

            viewModel.uiState.test {
                skipItems(1)
                val state = awaitItem()
                assertNull(
                    "empty recommendations must not become a fabricated AI tip",
                    state.aiRecommendation
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `surfaces career graph skill-gap insights when the reader returns them`() = runTest(testDispatcher) {
        coEvery { mockGraphInsights.invoke() } returns CareerGraphInsights(
            hasGraph = true,
            demonstratedSkillCount = 3,
            targetSkillCount = 5,
            skillMatchPercent = 60,
            topMissingSkills = listOf(
                SkillGapItem(skill = "Kubernetes", demandedByJobs = 2),
                SkillGapItem(skill = "GraphQL", demandedByJobs = 1)
            )
        )
        viewModel = createViewModel()

        viewModel.uiState.test {
            skipItems(1)
            val state = awaitItem()
            assertTrue(state.graphInsights.available)
            assertEquals(60, state.graphInsights.skillMatchPercent)
            assertEquals(2, state.graphInsights.missingSkills.size)
            assertEquals("Kubernetes", state.graphInsights.missingSkills.first().skill)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `graph insights stay unavailable when no graph is persisted`() = runTest(testDispatcher) {
        coEvery { mockGraphInsights.invoke() } returns CareerGraphInsights.EMPTY
        viewModel = createViewModel()

        viewModel.uiState.test {
            skipItems(1)
            val state = awaitItem()
            assertFalse(state.graphInsights.available)
            assertTrue(state.graphInsights.missingSkills.isEmpty())
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

    @Test
    fun `exploring a skill gap logs analytics and records EXPLORED_JOBS engagement`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.onEvent(DashboardUiEvent.ExploreSkillJobs("Kubernetes"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            mockTrackEvent.invoke(match {
                it.eventName == "dashboard_skill_gap_explore_jobs" &&
                    it.properties["skill"] == "Kubernetes"
            })
        }
        coVerify {
            mockRecordEngagement.invoke(match {
                it.skill == "Kubernetes" &&
                    it.engagement == com.bangersoul.aivance.core.domain.repository.SkillGapEngagement.EXPLORED_JOBS
            })
        }
    }

    @Test
    fun `learning a skill gap logs analytics and records STARTED_LEARNING engagement`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.onEvent(DashboardUiEvent.LearnSkill("GraphQL"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            mockTrackEvent.invoke(match {
                it.eventName == "dashboard_skill_gap_learn" && it.properties["skill"] == "GraphQL"
            })
        }
        coVerify {
            mockRecordEngagement.invoke(match {
                it.skill == "GraphQL" &&
                    it.engagement == com.bangersoul.aivance.core.domain.repository.SkillGapEngagement.STARTED_LEARNING
            })
        }
    }

    @Test
    fun `acting on a skill gap re-reads insights so the dashboard reflects momentum`() = runTest(testDispatcher) {
        // First read: untouched gap. Second read (after engagement): acted-on.
        coEvery { mockGraphInsights.invoke() } returnsMany listOf(
            CareerGraphInsights(
                hasGraph = true,
                skillMatchPercent = 50,
                topMissingSkills = listOf(SkillGapItem(skill = "Kubernetes", demandedByJobs = 2))
            ),
            CareerGraphInsights(
                hasGraph = true,
                skillMatchPercent = 50,
                topMissingSkills = listOf(
                    SkillGapItem(
                        skill = "Kubernetes",
                        demandedByJobs = 2,
                        engagement = com.bangersoul.aivance.core.domain.repository.SkillGapEngagement.EXPLORED_JOBS
                    )
                ),
                gapsActedOn = 1
            )
        )
        viewModel = createViewModel()

        viewModel.uiState.test {
            skipItems(1)
            val before = awaitItem()
            assertEquals(0, before.graphInsights.gapsActedOn)

            viewModel.onEvent(DashboardUiEvent.ExploreSkillJobs("Kubernetes"))
            testDispatcher.scheduler.advanceUntilIdle()

            val after = awaitItem()
            assertEquals(1, after.graphInsights.gapsActedOn)
            assertEquals(SkillEngagementUi.EXPLORED_JOBS, after.graphInsights.missingSkills.first().engagement)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
