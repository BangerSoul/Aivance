package com.bangersoul.aivance.core.domain.engine

import com.bangersoul.aivance.core.common.events.CareerEventBus
import com.bangersoul.aivance.core.common.events.InterviewEvent
import com.bangersoul.aivance.core.common.graph.CareerGraph
import com.bangersoul.aivance.core.common.graph.CareerGraphNode
import com.bangersoul.aivance.core.common.model.AnalyticsSnapshot
import com.bangersoul.aivance.core.common.model.CareerIntelligence
import com.bangersoul.aivance.core.common.model.CareerRecommendation
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.PredictiveMetrics
import com.bangersoul.aivance.core.common.model.UserProfile
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.careergraph.CareerGraphEngine
import com.bangersoul.aivance.core.domain.careergraph.contentSignature
import com.bangersoul.aivance.core.domain.repository.AnalyticsRepository
import com.bangersoul.aivance.core.domain.repository.ApplicationWorkflowRepository
import com.bangersoul.aivance.core.domain.repository.CareerGraphRepository
import com.bangersoul.aivance.core.domain.repository.InterviewRepository
import com.bangersoul.aivance.core.domain.repository.JobRepository
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import com.bangersoul.aivance.core.domain.repository.UserRepository
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * R2 — proves durable graph persistence is off the state-emission path.
 *
 * A **real** [CareerGraphEngine] is used (so the projection and its content signature are the
 * production ones) over a controllable [CareerGraphRepository], and the real
 * [CareerStateEngine] drives it. The three properties under test are:
 *
 *  1. a state emission completes even while the graph write is still blocked in the store;
 *  2. re-emissions that do not change the graph's content never rewrite the table;
 *  3. a genuine content change is written exactly once.
 */
class CareerStateEngineGraphPersistenceTest {

    private val userRepository: UserRepository = mockk()
    private val resumeRepository: ResumeRepository = mockk()
    private val workflowRepository: ApplicationWorkflowRepository = mockk()
    private val analyticsRepository: AnalyticsRepository = mockk()
    private val jobRepository: JobRepository = mockk()
    private val interviewRepository: InterviewRepository = mockk()
    private val providerManager: ProviderManager = mockk()

    private val savedJobs = MutableStateFlow<CoreResult<List<JobListing>>>(Result.Success(emptyList()))
    private val sessions = MutableStateFlow<CoreResult<List<com.bangersoul.aivance.core.common.model.InterviewSession>>>(Result.Success(emptyList()))

    /**
     * A graph store whose write can be held open by the test, so it can be proven that the
     * state emission does not wait on it.
     */
    private class ControllableGraphRepository : CareerGraphRepository {
        /** Completes when the store is allowed to finish a write. */
        val writeGate = CompletableDeferred<Unit>()
        val persistCalls = AtomicInteger(0)

        @Volatile
        var writesCompleted: Int = 0

        @Volatile
        var lastPersistedSignature: String? = null

        override suspend fun persist(graph: CareerGraph) {
            persistCalls.incrementAndGet()
            writeGate.await()
            lastPersistedSignature = graph.contentSignature()
            writesCompleted += 1
        }

        override suspend fun loadGraph(userId: String): CareerGraph = CareerGraph(userId = userId)

        override suspend fun replaceEventProjection(eventNodes: List<CareerGraphNode>) = Unit
    }

    private val graphStore = ControllableGraphRepository()

    private fun buildEngine(eventBus: CareerEventBus = CareerEventBus()): CareerStateEngine {
        every { userRepository.getProfile() } returns flowOf(
            Result.Success(UserProfile(id = "u1", fullName = "Ada Lovelace", email = "ada@x.io", targetRole = "Engineer"))
        )
        every { resumeRepository.getResumes() } returns flowOf(Result.Success(emptyList()))
        every { workflowRepository.getApplications() } returns flowOf(Result.Success(emptyList()))
        every { jobRepository.getSavedJobs() } returns savedJobs
        every { interviewRepository.getSessions() } returns sessions
        every { analyticsRepository.getSnapshots() } returns flowOf(Result.Success(emptyList<AnalyticsSnapshot>()))
        every { analyticsRepository.getActiveRecommendations() } returns
            flowOf(Result.Success(emptyList<CareerRecommendation>()))
        every { analyticsRepository.getCareerIntelligence() } returns MutableStateFlow<CoreResult<CareerIntelligence>>(
            Result.Success(
                CareerIntelligence(
                    careerScore = null,
                    dimensionScores = emptyMap(),
                    predictions = PredictiveMetrics(0, 0, ""),
                    health = emptyList()
                )
            )
        )
        every { providerManager.providerStatuses } returns MutableStateFlow(mapOf("gemma" to ProviderStatus.Healthy))

        val graphEngine = CareerGraphEngine(
            userRepository = userRepository,
            resumeRepository = resumeRepository,
            jobRepository = jobRepository,
            workflowRepository = workflowRepository,
            interviewRepository = interviewRepository,
            careerGraphRepository = graphStore
        )

        return CareerStateEngine(
            userRepository, resumeRepository, workflowRepository, analyticsRepository,
            jobRepository, interviewRepository, providerManager, eventBus, graphEngine
        )
    }

    private fun awaitUntil(timeoutMs: Long = 3_000, predicate: () -> Boolean) = runBlocking {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return@runBlocking true
            delay(15)
        }
        false
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
    fun `a state is emitted while the graph write is still blocked in the store`() {
        val engine = buildEngine()

        // The projection reaches the state without the write ever completing.
        assertTrue(
            "state must be emitted before the graph is durably written",
            awaitUntil { engine.state.value.graphNodeCount > 0 }
        )
        assertEquals("exactly one projection was handed to the writer", 1, graphStore.persistCalls.get())
        assertEquals("the write is still held open", 0, graphStore.writesCompleted)
        assertEquals("no write has completed, so the revision is still 0", 0L, engine.state.value.graphRevision)

        // Releasing the store lets the write land; only then does the revision advance.
        graphStore.writeGate.complete(Unit)
        assertTrue(
            "the post-write revision must be published once the write completes",
            awaitUntil { engine.state.value.graphRevision == 1L }
        )
        assertEquals(1, graphStore.writesCompleted)
    }

    @Test
    fun `emissions that do not change the graph content never rewrite the table`() {
        graphStore.writeGate.complete(Unit) // let writes finish immediately
        val bus = CareerEventBus()
        val engine = buildEngine(bus)

        assertTrue(awaitUntil { engine.state.value.graphRevision == 1L })
        val writesAfterFirstProjection = graphStore.persistCalls.get()
        assertEquals(1, writesAfterFirstProjection)

        // Twelve flows, many of which emit without touching the graph's content: a bus event and
        // a re-emission of unchanged saved jobs. Under the old code each of these rewrote every
        // graph row inside the state transform.
        bus.tryEmit(InterviewEvent.Completed(sessionId = "s1", overallScore = 90))
        bus.tryEmit(InterviewEvent.Completed(sessionId = "s1", overallScore = 90))
        savedJobs.value = Result.Success(emptyList())
        savedJobs.value = Result.Success(emptyList())

        // Give the writer every chance to perform a redundant write.
        assertTrue(awaitUntil(timeoutMs = 1_000) { engine.state.value.lastEventTimestamp > 0L })
        runBlocking { delay(300) }

        assertEquals(
            "content-identical re-emissions must not rewrite the graph",
            writesAfterFirstProjection,
            graphStore.persistCalls.get()
        )
        assertEquals(1L, engine.state.value.graphRevision)
    }

    @Test
    fun `a genuine content change is persisted exactly once more`() {
        graphStore.writeGate.complete(Unit)
        val engine = buildEngine()

        assertTrue(awaitUntil { engine.state.value.graphRevision == 1L })
        assertEquals(1, graphStore.persistCalls.get())

        // A new saved job genuinely changes the projected graph.
        savedJobs.value = Result.Success(listOf(savedJob("job_1")))

        assertTrue(
            "the changed projection must be written",
            awaitUntil { engine.state.value.graphRevision == 2L }
        )
        assertEquals(2, graphStore.persistCalls.get())

        // ...and the follow-up emission for that write must not itself trigger another write.
        runBlocking { delay(300) }
        assertEquals(2, graphStore.persistCalls.get())
        assertEquals(
            "the persisted graph must be the one that carries the new job",
            true,
            graphStore.lastPersistedSignature?.contains("job_job_1") == true
        )
    }
}
