package com.bangersoul.aivance.core.domain.usecase.career

import com.bangersoul.aivance.core.common.model.Application
import com.bangersoul.aivance.core.common.model.InterviewSession
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.Resume
import com.bangersoul.aivance.core.common.model.ResumeVersion
import com.bangersoul.aivance.core.common.model.UserProfile
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.careergraph.CareerGraphEngine
import com.bangersoul.aivance.core.domain.repository.ApplicationWorkflowRepository
import com.bangersoul.aivance.core.domain.repository.CareerGraphRepository
import com.bangersoul.aivance.core.domain.repository.InterviewRepository
import com.bangersoul.aivance.core.domain.repository.JobRepository
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import com.bangersoul.aivance.core.domain.repository.SkillGapEngagement
import com.bangersoul.aivance.core.domain.repository.SkillGapProgress
import com.bangersoul.aivance.core.domain.repository.SkillGapProgressRepository
import com.bangersoul.aivance.core.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the first production READER of the durable Career Knowledge Graph.
 *
 * A real [CareerGraphEngine] is used (only the repository boundaries are mocked) so the test
 * exercises the actual grounded skill-gap traversal and application-context resolution the
 * dashboard consumes. The graph is persisted via a fake in-memory graph repository, proving the
 * reader loads what the live projection wrote.
 */
class GetCareerGraphInsightsUseCaseTest {

    private val userRepository: UserRepository = mockk()
    private val workflowRepository: ApplicationWorkflowRepository = mockk()
    private val resumeRepository: ResumeRepository = mockk(relaxed = true)
    private val jobRepository: JobRepository = mockk(relaxed = true)
    private val interviewRepository: InterviewRepository = mockk(relaxed = true)

    /** Minimal in-memory graph store so persist()/loadGraph() round-trip like production. */
    private val graphRepository = object : CareerGraphRepository {
        private var stored = com.bangersoul.aivance.core.common.graph.CareerGraph(userId = "none")
        override suspend fun persist(graph: com.bangersoul.aivance.core.common.graph.CareerGraph) {
            // Preserve any CAREER_EVENT provenance nodes exactly like the M05 entity-slice writer.
            val eventNodes = stored.nodes.filterValues {
                it.type == com.bangersoul.aivance.core.common.graph.CareerNodeType.CAREER_EVENT
            }
            stored = graph.copy(nodes = graph.nodes + eventNodes)
        }
        override suspend fun loadGraph(userId: String) = stored.copy(userId = userId)
        override suspend fun replaceEventProjection(eventNodes: List<com.bangersoul.aivance.core.common.graph.CareerGraphNode>) {
            val nonEvent = stored.nodes.filterValues {
                it.type != com.bangersoul.aivance.core.common.graph.CareerNodeType.CAREER_EVENT
            }
            stored = stored.copy(nodes = nonEvent + eventNodes.associateBy { it.id })
        }
    }

    private lateinit var engine: CareerGraphEngine
    private lateinit var useCase: GetCareerGraphInsightsUseCase

    /** In-memory skill-gap engagement store, keyed by normalized skill. */
    private val progressFlow = MutableStateFlow<List<SkillGapProgress>>(emptyList())
    private val skillGapProgressRepository = object : SkillGapProgressRepository {
        override fun observeProgress() = progressFlow
        override suspend fun recordEngagement(skillKey: String, engagement: SkillGapEngagement) {
            val normalized = skillKey.lowercase().trim()
            val merged = progressFlow.value.filterNot { it.skillKey == normalized } +
                SkillGapProgress(normalized, engagement, 0L)
            progressFlow.value = merged
        }
    }

    @Before
    fun setUp() {
        engine = CareerGraphEngine(
            userRepository = userRepository,
            resumeRepository = resumeRepository,
            jobRepository = jobRepository,
            workflowRepository = workflowRepository,
            interviewRepository = interviewRepository,
            careerGraphRepository = graphRepository
        )
        useCase = GetCareerGraphInsightsUseCase(
            userRepository,
            workflowRepository,
            engine,
            skillGapProgressRepository
        )
    }

    @Test
    fun `returns empty insights when no graph is persisted`() = runBlocking {
        every { userRepository.getProfile() } returns
            flowOf(Result.Success(UserProfile(id = "u1", fullName = "A", email = "a@x.io")))
        every { workflowRepository.getApplications() } returns flowOf(Result.Success(emptyList()))

        val insights = useCase()

        assertFalse(insights.hasGraph)
        assertTrue(insights.topMissingSkills.isEmpty())
    }

    @Test
    fun `surfaces grounded skill gaps derived from persisted job text`() = runBlocking {
        val profile = UserProfile(
            id = "u1",
            fullName = "Alice Dev",
            email = "alice@x.io",
            skills = listOf("Kotlin", "Coroutines")
        )
        val job = JobListing(
            id = "job_1",
            title = "Senior Android Engineer",
            company = "Acme",
            // Kotlin (owned) + Kubernetes & GraphQL (gaps) appear verbatim; nothing else invented.
            description = "We need Kotlin, Kubernetes and GraphQL experience for our platform team.",
            url = "https://x.io/1",
            sourceProvider = "Greenhouse"
        )

        // The live engine builds + persists the entity projection (as CareerStateEngine would).
        val graph = engine.buildGraph(
            profile = profile,
            resumes = emptyList(),
            jobs = listOf(job),
            applications = emptyList(),
            interviews = emptyList()
        )
        engine.persist(graph)

        every { userRepository.getProfile() } returns flowOf(Result.Success(profile))
        every { workflowRepository.getApplications() } returns flowOf(Result.Success(emptyList()))

        val insights = useCase()

        assertTrue(insights.hasGraph)
        val missing = insights.topMissingSkills.map { it.skill }.toSet()
        assertTrue("Kubernetes should be a gap", missing.contains("Kubernetes"))
        assertTrue("GraphQL should be a gap", missing.contains("GraphQL"))
        assertFalse("Kotlin is demonstrated, not a gap", missing.contains("Kotlin"))
        // Required from title+description: Android, Kotlin, Kubernetes, GraphQL (4 distinct).
        // Demonstrated among them: Kotlin only → 1/4 = 25%.
        assertEquals(25, insights.skillMatchPercent)
    }

    @Test
    fun `skill match is unmeasured when no target job demands a recognised skill`() = runBlocking {
        val profile = UserProfile(
            id = "u1", fullName = "Alice Dev", email = "alice@x.io", skills = listOf("Kotlin")
        )
        // No target jobs at all, so nothing demands anything.
        val graph = engine.buildGraph(
            profile = profile, resumes = emptyList(), jobs = emptyList(),
            applications = emptyList(), interviews = emptyList()
        )
        engine.persist(graph)

        every { userRepository.getProfile() } returns flowOf(Result.Success(profile))
        every { workflowRepository.getApplications() } returns flowOf(Result.Success(emptyList()))

        val insights = useCase()

        assertTrue(insights.hasGraph)
        assertEquals(0, insights.targetSkillCount)
        // R3-2: this used to be 100 — the unmeasured ratio was coerced from a `1.0f` fallback and
        // rendered beside "0 of 0 target-job skills demonstrated".
        assertNull(insights.skillMatchPercent)
        assertTrue(insights.topMissingSkills.isEmpty())
    }

    @Test
    fun `resolves application context for active applications`() = runBlocking {
        val profile = UserProfile(id = "u1", fullName = "Alice Dev", email = "alice@x.io", skills = listOf("Kotlin"))
        val job = JobListing(
            id = "1", title = "Android Engineer", company = "Acme",
            description = "Kotlin role", url = "https://x.io/1", sourceProvider = "Greenhouse"
        )
        val resume = Resume(
            id = 1L, name = "Resume",
            versions = listOf(ResumeVersion(id = 10L, resumeId = 1L, versionName = "v1"))
        )
        val application = Application(id = 55L, jobId = 1L, resumeVersionId = 10L, currentStageId = "SCREENING", status = "ACTIVE")

        val graph = engine.buildGraph(
            profile = profile,
            resumes = listOf(resume),
            jobs = listOf(job),
            applications = listOf(application),
            interviews = emptyList<InterviewSession>()
        )
        engine.persist(graph)

        every { userRepository.getProfile() } returns flowOf(Result.Success(profile))
        every { workflowRepository.getApplications() } returns flowOf(Result.Success(listOf(application)))

        val insights = useCase()

        assertEquals(1, insights.activeApplicationContexts.size)
        val ctx = insights.activeApplicationContexts.first()
        assertEquals(55L, ctx.applicationId)
        assertEquals("Android Engineer", ctx.jobTitle)
    }

    @Test
    fun `folds recorded engagement into surfaced gaps and gapsActedOn count`() = runBlocking {
        val profile = UserProfile(
            id = "u1", fullName = "Alice Dev", email = "alice@x.io", skills = listOf("Kotlin")
        )
        val job = JobListing(
            id = "job_1", title = "Platform Engineer", company = "Acme",
            description = "We need Kubernetes and GraphQL experience.",
            url = "https://x.io/1", sourceProvider = "Greenhouse"
        )
        val graph = engine.buildGraph(
            profile = profile, resumes = emptyList(), jobs = listOf(job),
            applications = emptyList(), interviews = emptyList()
        )
        engine.persist(graph)

        every { userRepository.getProfile() } returns flowOf(Result.Success(profile))
        every { workflowRepository.getApplications() } returns flowOf(Result.Success(emptyList()))

        // User has explored jobs for Kubernetes only.
        skillGapProgressRepository.recordEngagement("Kubernetes", SkillGapEngagement.EXPLORED_JOBS)

        val insights = useCase()

        val kubernetes = insights.topMissingSkills.first { it.skill == "Kubernetes" }
        assertEquals(SkillGapEngagement.EXPLORED_JOBS, kubernetes.engagement)
        val graphql = insights.topMissingSkills.first { it.skill == "GraphQL" }
        assertEquals(SkillGapEngagement.NONE, graphql.engagement)
        assertEquals(1, insights.gapsActedOn)
    }
}
