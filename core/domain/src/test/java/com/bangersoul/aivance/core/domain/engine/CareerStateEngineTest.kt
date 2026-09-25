package com.bangersoul.aivance.core.domain.engine

import com.bangersoul.aivance.core.common.events.CareerEventBus
import com.bangersoul.aivance.core.common.events.InterviewEvent
import com.bangersoul.aivance.core.common.graph.CareerGraph
import com.bangersoul.aivance.core.common.model.AnalyticsSnapshot
import com.bangersoul.aivance.core.common.model.CareerIntelligence
import com.bangersoul.aivance.core.common.model.CareerRecommendation
import com.bangersoul.aivance.core.common.model.CareerState
import com.bangersoul.aivance.core.common.model.HealthDimension
import com.bangersoul.aivance.core.common.model.InterviewSession
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.PredictiveMetrics
import com.bangersoul.aivance.core.common.model.UserProfile
import com.bangersoul.aivance.core.common.util.DateUtils
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.careergraph.CareerGraphEngine
import com.bangersoul.aivance.core.domain.engine.CareerStateEngine
import com.bangersoul.aivance.core.domain.repository.AnalyticsRepository
import com.bangersoul.aivance.core.domain.repository.ApplicationWorkflowRepository
import com.bangersoul.aivance.core.domain.repository.InterviewRepository
import com.bangersoul.aivance.core.domain.repository.JobRepository
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import com.bangersoul.aivance.core.domain.repository.UserRepository
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the M03 activation: the reactive career-intelligence hub
 * ([AnalyticsRepository.getCareerIntelligence]) — which recomputes from ATS
 * reports and interview sessions as soon as a `ResumeAnalysisCompleted` /
 * `InterviewCompleted` event lands — is surfaced by [CareerStateEngine] into the
 * single [com.bangersoul.aivance.core.common.model.CareerState.intelligence] /
 * `growth` score fields every consumer reads.
 *
 * The engine owns its own `Dispatchers.Default` scope with
 * `SharingStarted.Eagerly`, so the test polls `state.value` rather than driving a
 * virtual scheduler; only the true repository/provider boundaries are mocked and
 * a real [CareerEventBus] is used.
 */
class CareerStateEngineTest {

    private val userRepository: UserRepository = mockk()
    private val resumeRepository: ResumeRepository = mockk()
    private val workflowRepository: ApplicationWorkflowRepository = mockk()
    private val analyticsRepository: AnalyticsRepository = mockk()
    private val jobRepository: JobRepository = mockk(relaxed = true)
    private val interviewRepository: InterviewRepository = mockk(relaxed = true)
    private val providerManager: ProviderManager = mockk()
    private val careerGraphEngine: CareerGraphEngine = mockk()

    private val healthyProviders = MutableStateFlow(mapOf("gemma" to ProviderStatus.Healthy))

    private fun intelligence(ats: Int, career: Int) = CareerIntelligence(
        careerScore = career,
        dimensionScores = mapOf("ATS_READINESS" to ats, "OVERALL" to career),
        predictions = PredictiveMetrics(0, 0, ""),
        health = emptyList<HealthDimension>()
    )

    private fun snapshot(ats: Int, career: Int) = AnalyticsSnapshot(
        careerScore = career,
        dimensionScores = mapOf("ATS_READINESS" to ats)
    )

    private fun buildEngine(
        snapshots: CoreResult<List<AnalyticsSnapshot>> = Result.Success(listOf(snapshot(40, 30))),
        intelligence: MutableStateFlow<CoreResult<CareerIntelligence>>,
        savedJobs: CoreResult<List<JobListing>> = Result.Success(emptyList()),
        interviewSessions: CoreResult<List<InterviewSession>> = Result.Success(emptyList()),
        eventBus: CareerEventBus = CareerEventBus()
    ): Pair<CareerStateEngine, CareerEventBus> {
        every { userRepository.getProfile() } returns
            flowOf(Result.Success(UserProfile(fullName = "Ada Lovelace", email = "ada@x.io", targetRole = "Engineer")))
        every { resumeRepository.getResumes() } returns flowOf(Result.Success(emptyList()))
        every { workflowRepository.getApplications() } returns flowOf(Result.Success(emptyList()))
        every { jobRepository.getSavedJobs() } returns flowOf(savedJobs)
        every { interviewRepository.getSessions() } returns flowOf(interviewSessions)
        every { analyticsRepository.getSnapshots() } returns flowOf(snapshots)
        every { analyticsRepository.getActiveRecommendations() } returns
            flowOf(Result.Success(emptyList<CareerRecommendation>()))
        every { analyticsRepository.getCareerIntelligence() } returns intelligence
        every { providerManager.providerStatuses } returns healthyProviders
        every { careerGraphEngine.buildGraph(any(), any(), any(), any(), any()) } returns CareerGraph(userId = "u")
        coEvery { careerGraphEngine.persist(any()) } returns Unit

        val engine = CareerStateEngine(
            userRepository, resumeRepository, workflowRepository, analyticsRepository,
            jobRepository, interviewRepository, providerManager, eventBus, careerGraphEngine
        )
        return engine to eventBus
    }

    /** Polls the eagerly-shared state until [predicate] holds for the whole state. */
    private fun awaitStateMatching(
        engine: CareerStateEngine,
        timeoutMs: Long = 2_000,
        predicate: (CareerState) -> Boolean
    ) = runBlocking {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val s = engine.state.value
            if (predicate(s)) return@runBlocking s
            kotlinx.coroutines.delay(20)
        }
        engine.state.value
    }

    /** Polls the eagerly-shared state until [predicate] holds or the deadline passes. */
    private fun awaitState(engine: CareerStateEngine, timeoutMs: Long = 2_000, predicate: (Int?, Int?) -> Boolean) =
        runBlocking {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val s = engine.state.value
                if (predicate(s.intelligence.atsScore, s.growth.careerScore)) return@runBlocking s
                kotlinx.coroutines.delay(20)
            }
            engine.state.value
        }

    @Test
    fun `live intelligence hub scores are surfaced over the lagging snapshot`() {
        val intel = MutableStateFlow<CoreResult<CareerIntelligence>>(Result.Success(intelligence(ats = 87, career = 78)))
        val (engine, _) = buildEngine(intelligence = intel)

        val state = awaitState(engine) { ats, career -> ats == 87 && career == 78 }

        // The hub (live from ATS reports / interview sessions) wins over the
        // snapshot's stale 40/30 — so resume-analysis & interview-completion
        // events become user-visible in the shared score fields.
        assertEquals(87, state.intelligence.atsScore)
        assertEquals(78, state.growth.careerScore)
        assertEquals(87, state.intelligenceHub?.dimensionScores?.get("ATS_READINESS"))
    }

    @Test
    fun `falls back to the snapshot when the intelligence hub is unavailable`() {
        val intel = MutableStateFlow<CoreResult<CareerIntelligence>>(
            Result.Failure(com.bangersoul.aivance.core.common.result.DomainError("no intel"))
        )
        val (engine, _) = buildEngine(
            snapshots = Result.Success(listOf(snapshot(40, 30))),
            intelligence = intel
        )

        val state = awaitState(engine) { ats, career -> ats == 40 && career == 30 }

        assertEquals(40, state.intelligence.atsScore)
        assertEquals(30, state.growth.careerScore)
    }

    @Test
    fun `state recomputes when the live source data changes`() {
        val intel = MutableStateFlow<CoreResult<CareerIntelligence>>(Result.Success(intelligence(ats = 50, career = 45)))
        val (engine, _) = buildEngine(intelligence = intel)

        awaitState(engine) { ats, _ -> ats == 50 }

        // Simulate what InterviewCompleted / ResumeAnalysisCompleted cause: the
        // reactive intelligence recomputes to a higher score.
        intel.value = Result.Success(intelligence(ats = 92, career = 88))

        val updated = awaitState(engine) { ats, career -> ats == 92 && career == 88 }
        assertEquals(92, updated.intelligence.atsScore)
        assertEquals(88, updated.growth.careerScore)
    }

    @Test
    fun `an event on the bus advances the recomputation timestamp without altering scores`() {
        val intel = MutableStateFlow<CoreResult<CareerIntelligence>>(Result.Success(intelligence(ats = 70, career = 60)))
        val (engine, bus) = buildEngine(intelligence = intel)

        val before = awaitState(engine) { ats, _ -> ats == 70 }
        val beforeScore = before.intelligence.atsScore

        bus.tryEmit(InterviewEvent.Completed(sessionId = "1", overallScore = 90))

        val after = runBlocking {
            val deadline = System.currentTimeMillis() + 2_000
            while (System.currentTimeMillis() < deadline) {
                val s = engine.state.value
                if (s.lastEventTimestamp > 0L) return@runBlocking s
                kotlinx.coroutines.delay(20)
            }
            engine.state.value
        }

        // The event drives recomputation (timestamp set) but does not itself
        // mutate the score — scores remain sourced only from the hub.
        assert(after.lastEventTimestamp > 0L)
        assertEquals(beforeScore, after.intelligence.atsScore)
    }

    private fun savedJob(id: String) = JobListing(
        id = id,
        title = "Android Engineer",
        company = "Acme",
        description = "Kotlin role",
        url = "https://x.io/$id",
        sourceProvider = "Greenhouse"
    )

    @Test
    fun `saved jobs are counted from the saved-jobs table, not from an application stage`() {
        val intel = MutableStateFlow<CoreResult<CareerIntelligence>>(Result.Success(intelligence(ats = 50, career = 45)))
        val (engine, _) = buildEngine(
            intelligence = intel,
            savedJobs = Result.Success(listOf(savedJob("job_1"), savedJob("job_2")))
        )

        val state = awaitStateMatching(engine) { it.discovery.savedJobsCount == 2 }

        // R3-4: a bookmarked job with no application row is still a saved job. The previous
        // metric counted `applications.currentStageId == "SAVED"`, so bookmarks made from Job
        // Discovery never appeared in the dashboard's "Saved Jobs" stat.
        assertEquals(2, state.discovery.savedJobsCount)
        assertEquals(0, state.pipeline.activeApplications)
    }

    @Test
    fun `upcoming interviews use the session start time, never the application date`() {
        val appliedAt = 1_700_000_000_000L
        val interviewStartsAt = 1_800_000_000_000L
        val scheduled = InterviewSession(
            id = "session_1",
            targetRole = "Senior Android Engineer",
            companyName = "Acme",
            startTime = interviewStartsAt,
            isCompleted = false
        )
        val intel = MutableStateFlow<CoreResult<CareerIntelligence>>(Result.Success(intelligence(ats = 50, career = 45)))
        val (engine, _) = buildEngine(
            intelligence = intel,
            interviewSessions = Result.Success(listOf(scheduled))
        )

        val state = awaitStateMatching(engine) { it.pipeline.upcomingInterviews.isNotEmpty() }

        val upcoming = state.pipeline.upcomingInterviews.single()
        // R3-5: the date is formatted from the interview session's own start time. The previous
        // implementation rendered `application.dateApplied` — the date the user *applied* — as if
        // it were the interview datetime.
        assertEquals(
            "${DateUtils.formatDateDisplay(interviewStartsAt)} · ${DateUtils.formatTimeDisplay(interviewStartsAt)}",
            upcoming.dateTime
        )
        assertFalse(upcoming.dateTime.contains(appliedAt.toString()))
        assertEquals("Acme", upcoming.company)
        assertEquals("Senior Android Engineer", upcoming.role)
    }

    @Test
    fun `completed interview sessions are not surfaced as upcoming interviews`() {
        val finished = InterviewSession(
            id = "session_1",
            targetRole = "Engineer",
            companyName = "Acme",
            isCompleted = true
        )
        val intel = MutableStateFlow<CoreResult<CareerIntelligence>>(Result.Success(intelligence(ats = 50, career = 45)))
        val (engine, _) = buildEngine(
            intelligence = intel,
            interviewSessions = Result.Success(listOf(finished))
        )

        val state = awaitStateMatching(engine) { it.growth.careerScore == 45 }

        assertTrue(state.pipeline.upcomingInterviews.isEmpty())
    }
}
