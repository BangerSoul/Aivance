package com.bangersoul.aivance.feature.dashboard

import com.bangersoul.aivance.core.common.graph.CareerGraph
import com.bangersoul.aivance.core.common.graph.CareerGraphNode
import com.bangersoul.aivance.core.common.model.CareerIntelligence
import com.bangersoul.aivance.core.common.model.UserProfile
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.analytics.CareerIntelligenceEngine
import com.bangersoul.aivance.core.domain.analytics.CareerScoreEngine
import com.bangersoul.aivance.core.domain.analytics.InterviewReadinessCalculator
import com.bangersoul.aivance.core.domain.analytics.KPIEngine
import com.bangersoul.aivance.core.domain.careergraph.CareerGraphEngine
import com.bangersoul.aivance.core.domain.engine.CareerStateEngine
import com.bangersoul.aivance.core.domain.engine.NavigationIntent
import com.bangersoul.aivance.core.domain.engine.NavigationWorkflowEngine
import com.bangersoul.aivance.core.domain.repository.AnalyticsRepository
import com.bangersoul.aivance.core.domain.repository.ApplicationWorkflowRepository
import com.bangersoul.aivance.core.domain.repository.CareerGraphRepository
import com.bangersoul.aivance.core.domain.repository.InterviewRepository
import com.bangersoul.aivance.core.domain.repository.JobRepository
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import com.bangersoul.aivance.core.domain.repository.SkillGapEngagement
import com.bangersoul.aivance.core.domain.repository.SkillGapProgress
import com.bangersoul.aivance.core.domain.repository.SkillGapProgressRepository
import com.bangersoul.aivance.core.domain.repository.UserRepository
import com.bangersoul.aivance.core.domain.usecase.analytics.TrackEventUseCase
import com.bangersoul.aivance.core.domain.usecase.career.GetCareerGraphInsightsUseCase
import com.bangersoul.aivance.core.domain.usecase.career.RecordSkillGapEngagementUseCase
import com.bangersoul.aivance.core.common.events.CareerEventBus
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **Metric integrity guard (R3).**
 *
 * Renders the dashboard from a genuine zero-data install — a real [CareerStateEngine] driven by
 * empty repository flows, a real [CareerGraphEngine] over an in-memory graph store, the real
 * [GetCareerGraphInsightsUseCase], the real score engines, and the real [DashboardViewModel] — and
 * asserts that **no metric claims a measurement that was never taken**.
 *
 * The contract is declared in [METRIC_CONTRACT] and checked twice:
 *
 *  1. **value** — every rated metric is `null`, every count is `0`, every collection is empty;
 *  2. **completeness** — the contract names exactly the fields that exist, so adding a metric to
 *     [DashboardUiState] without deciding its zero-data meaning **fails this test**.
 *
 * That second half is what makes fabrication unable to regress quietly: the specific defects that
 * motivated this guard (Career Score `18`, Skill Match `100%`, `ATS Score 0`, hardcoded agent
 * missions) each would have to be re-declared here as a deliberate, reviewable choice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardZeroDataMetricGuardTest {

    /** The zero-data meaning of a dashboard value. */
    private enum class MetricKind {
        /** A measurement. Must be `null` when nothing has been measured — never a default. */
        RATING,

        /** A count of real rows. `0` is honest and useful. */
        COUNT,

        /** A collection with no producer output yet. Must be empty. */
        COLLECTION,

        /** Optional free text with no producer yet. Must be `null`. */
        TEXT,

        /** Presentation label derived from identity rather than measurement. Exempt. */
        LABEL,

        /** A control flag or navigation default. Exempt. */
        CONTROL
    }

    private companion object {
        /**
         * The zero-data contract for every dashboard metric.
         *
         * Adding a field to [DashboardUiState] or [CareerGraphInsightsUi] without adding it here
         * fails `every dashboard metric field is classified by this guard`.
         */
        val METRIC_CONTRACT: Map<String, MetricKind> = mapOf(
            // Controls / labels — no metric meaning.
            "isLoading" to MetricKind.CONTROL,
            "error" to MetricKind.TEXT,
            "greeting" to MetricKind.LABEL,
            "userDesignation" to MetricKind.LABEL,
            "nextBestAction" to MetricKind.CONTROL,
            "graphInsights" to MetricKind.CONTROL,

            // Rated metrics — must be absent at zero data.
            "careerScore" to MetricKind.RATING,
            "atsScore" to MetricKind.RATING,

            // Counts — a real zero.
            "activeApplications" to MetricKind.COUNT,
            "savedJobs" to MetricKind.COUNT,

            // No producer until the user actually does something.
            "nextInterview" to MetricKind.TEXT,
            "aiRecommendation" to MetricKind.TEXT
        )

        val INSIGHTS_CONTRACT: Map<String, MetricKind> = mapOf(
            "available" to MetricKind.CONTROL,
            "skillMatchPercent" to MetricKind.RATING,
            "demonstratedSkillCount" to MetricKind.COUNT,
            "targetSkillCount" to MetricKind.COUNT,
            "missingSkills" to MetricKind.COLLECTION,
            "applicationContexts" to MetricKind.COLLECTION,
            "gapsActedOn" to MetricKind.COUNT
        )
    }

    private val userRepository: UserRepository = mockk()
    private val resumeRepository: ResumeRepository = mockk()
    private val workflowRepository: ApplicationWorkflowRepository = mockk()
    private val analyticsRepository: AnalyticsRepository = mockk()
    private val jobRepository: JobRepository = mockk()
    private val interviewRepository: InterviewRepository = mockk()
    private val providerManager: ProviderManager = mockk()

    /** In-memory graph store that preserves the replay-owned slice, like the Room writer. */
    private val graphStore = object : CareerGraphRepository {
        @Volatile
        private var stored = CareerGraph(userId = "none")

        override suspend fun persist(graph: CareerGraph) {
            val eventNodes = stored.nodes.filterValues {
                it.type == com.bangersoul.aivance.core.common.graph.CareerNodeType.CAREER_EVENT
            }
            stored = graph.copy(nodes = graph.nodes + eventNodes)
        }

        override suspend fun loadGraph(userId: String) = stored.copy(userId = userId)

        override suspend fun replaceEventProjection(eventNodes: List<CareerGraphNode>) {
            val nonEvent = stored.nodes.filterValues {
                it.type != com.bangersoul.aivance.core.common.graph.CareerNodeType.CAREER_EVENT
            }
            stored = stored.copy(nodes = nonEvent + eventNodes.associateBy { it.id })
        }
    }

    private val progressFlow = MutableStateFlow<List<SkillGapProgress>>(emptyList())
    private val skillGapProgressRepository = object : SkillGapProgressRepository {
        override fun observeProgress() = progressFlow
        override suspend fun recordEngagement(skillKey: String, engagement: SkillGapEngagement) {
            progressFlow.value = progressFlow.value.filterNot { it.skillKey == skillKey.lowercase().trim() } +
                SkillGapProgress(skillKey.lowercase().trim(), engagement, 0L)
        }
    }

    private lateinit var graphEngine: CareerGraphEngine
    private lateinit var stateEngine: CareerStateEngine

    /** Owns the ViewModel so its scope can be cancelled deterministically in [tearDown]. */
    private val viewModelStore = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // ── A genuinely clean install: no profile content, no data anywhere, no providers. ──
        every { userRepository.getProfile() } returns flowOf(
            Result.Success(UserProfile(id = "u1", fullName = "Test User", email = "test@x.io"))
        )
        every { resumeRepository.getResumes() } returns flowOf(Result.Success(emptyList()))
        every { workflowRepository.getApplications() } returns flowOf(Result.Success(emptyList()))
        every { jobRepository.getSavedJobs() } returns flowOf(Result.Success(emptyList()))
        every { interviewRepository.getSessions() } returns flowOf(Result.Success(emptyList()))
        every { analyticsRepository.getSnapshots() } returns flowOf(Result.Success(emptyList()))
        every { analyticsRepository.getActiveRecommendations() } returns flowOf(Result.Success(emptyList()))
        every { providerManager.providerStatuses } returns MutableStateFlow(emptyMap())

        // The real production computation over zero data — not a hand-written stub.
        val calculators = InterviewReadinessCalculator()
        val zeroDataIntelligence = CareerIntelligenceEngine(KPIEngine(), CareerScoreEngine())
            .calculateIntelligence(
                latestAtsReports = emptyList(),
                recruiters = emptyList(),
                applications = emptyList(),
                interviewReadiness = calculators.calculate(emptyList())
            )
        every { analyticsRepository.getCareerIntelligence() } returns
            flowOf<CoreResult<CareerIntelligence>>(Result.Success(zeroDataIntelligence))

        graphEngine = CareerGraphEngine(
            userRepository = userRepository,
            resumeRepository = resumeRepository,
            jobRepository = jobRepository,
            workflowRepository = workflowRepository,
            interviewRepository = interviewRepository,
            careerGraphRepository = graphStore
        )

        stateEngine = CareerStateEngine(
            userRepository, resumeRepository, workflowRepository, analyticsRepository,
            jobRepository, interviewRepository, providerManager, CareerEventBus(), graphEngine
        )
    }

    @After
    fun tearDown() {
        // Cancel the ViewModel's scope — and with it the live collectors this test set up — BEFORE
        // the main dispatcher is removed, so nothing is left to resume onto a dispatcher that no
        // longer exists and poison the next test class in this JVM.
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): DashboardViewModel {
        val insightsUseCase = GetCareerGraphInsightsUseCase(
            userRepository,
            workflowRepository,
            graphEngine,
            skillGapProgressRepository
        )
        val recordEngagement: RecordSkillGapEngagementUseCase = mockk(relaxed = true)
        val trackEvent: TrackEventUseCase = mockk()
        coEvery { trackEvent.invoke(any()) } returns Result.Success(Unit)

        val viewModel = DashboardViewModel(
            stateEngine = stateEngine,
            navWorkflowEngine = NavigationWorkflowEngine(),
            getCareerGraphInsights = insightsUseCase,
            recordSkillGapEngagement = recordEngagement,
            trackEventUseCase = trackEvent
        )

        // Registering through a store is what makes `viewModelStore.clear()` able to cancel the
        // ViewModel's `viewModelScope` in tearDown.
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
        }
        return ViewModelProvider(viewModelStore, factory)[DashboardViewModel::class.java]
    }

    /** Subscribes so the ViewModel's `WhileSubscribed` pipeline actually runs, then polls it. */
    private fun renderZeroDataDashboard(): DashboardUiState = runBlocking {
        val viewModel = createViewModel()
        val subscription = kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
            viewModel.uiState.collect { }
        }
        try {
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline) {
                val state = viewModel.uiState.value
                if (!state.isLoading && state.greeting.isNotBlank()) return@runBlocking state
                delay(25)
            }
            viewModel.uiState.value
        } finally {
            subscription.cancel()
        }
    }

    @Test
    fun `every rated dashboard metric is absent at zero data`() {
        val state = renderZeroDataDashboard()

        assertFalse("the dashboard finished loading", state.isLoading)
        assertNull("Career Score must not be fabricated (was 18)", state.careerScore)
        assertNull("ATS Score must not present the 0 floor as a measurement", state.atsScore)
        assertNull("Skill Match must not claim a measurement", state.graphInsights.skillMatchPercent)
        assertNull("no interview is scheduled", state.nextInterview)
        assertNull("no recommendation has been generated", state.aiRecommendation)
        assertNull("no error", state.error)
    }

    @Test
    fun `counts are real zeros and collections are empty at zero data`() {
        val state = renderZeroDataDashboard()

        assertEquals("no applications", 0, state.activeApplications)
        assertEquals("no saved jobs", 0, state.savedJobs)
        assertEquals("no skills demonstrated", 0, state.graphInsights.demonstratedSkillCount)
        assertEquals("no target-job skills", 0, state.graphInsights.targetSkillCount)
        assertEquals("no gaps acted on", 0, state.graphInsights.gapsActedOn)
        assertTrue("no missing skills", state.graphInsights.missingSkills.isEmpty())
        assertTrue("no application contexts", state.graphInsights.applicationContexts.isEmpty())
    }

    @Test
    fun `a clean install is guided to provider setup instead of shown a score`() {
        val state = renderZeroDataDashboard()

        // Navigation guidance is legitimately present at zero data because it is not a metric.
        // With no provider configured the honest next step is setup — which is what the hero
        // card should offer in place of a number.
        assertEquals(
            NavigationIntent.Action(label = "Setup Providers", route = "provider_setup"),
            state.nextBestAction
        )
    }

    @Test
    fun `every dashboard metric field is classified by this guard`() {
        // If a new metric is added to either state class without a zero-data decision being made
        // here, this fails — which is the point: a fabricated value cannot ship unexamined.
        assertEquals(
            "DashboardUiState fields must each declare their zero-data meaning in METRIC_CONTRACT",
            METRIC_CONTRACT.keys,
            instanceFieldNames(DashboardUiState::class.java)
        )
        assertEquals(
            "CareerGraphInsightsUi fields must each declare their zero-data meaning in INSIGHTS_CONTRACT",
            INSIGHTS_CONTRACT.keys,
            instanceFieldNames(CareerGraphInsightsUi::class.java)
        )
    }

    @Test
    fun `the guard's own fixture really is zero data`() {
        // Stops the guard from silently becoming vacuous if the fixture ever starts returning data.
        assertTrue(stateEngine.state.value.intelligence.totalResumes == 0)
        assertTrue(stateEngine.state.value.discovery.savedJobsCount == 0)
        assertTrue(stateEngine.state.value.pipeline.activeApplications == 0)
        assertTrue(stateEngine.state.value.pipeline.upcomingInterviews.isEmpty())
        assertNull("the zero-data intelligence composite must itself be unmeasured", stateEngine.state.value.growth.careerScore)
        assertNull(stateEngine.state.value.intelligence.atsScore)
        assertTrue(stateEngine.state.value.recommendations.isEmpty())
    }

    private fun instanceFieldNames(type: Class<*>): Set<String> =
        type.declaredFields
            .filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
}
