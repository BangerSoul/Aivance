package com.bangersoul.aivance.core.domain.careergraph

import com.bangersoul.aivance.core.common.graph.CareerEdgeType
import com.bangersoul.aivance.core.common.graph.CareerNodeType
import com.bangersoul.aivance.core.common.model.*
import com.bangersoul.aivance.core.domain.repository.*
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CareerGraphEngineTest {

    private val userRepository: UserRepository = mockk(relaxed = true)
    private val resumeRepository: ResumeRepository = mockk(relaxed = true)
    private val jobRepository: JobRepository = mockk(relaxed = true)
    private val workflowRepository: ApplicationWorkflowRepository = mockk(relaxed = true)
    private val interviewRepository: InterviewRepository = mockk(relaxed = true)
    private val careerGraphRepository: CareerGraphRepository = mockk(relaxed = true)

    private lateinit var engine: CareerGraphEngine

    @Before
    fun setUp() {
        engine = CareerGraphEngine(
            userRepository = userRepository,
            resumeRepository = resumeRepository,
            jobRepository = jobRepository,
            workflowRepository = workflowRepository,
            interviewRepository = interviewRepository,
            careerGraphRepository = careerGraphRepository
        )
    }

    @Test
    fun `buildGraph connects profile, skills, resumes, jobs, and applications`() {
        val profile = UserProfile(
            id = "user_123",
            fullName = "Alice Developer",
            email = "alice@example.com",
            targetRole = "Staff Android Engineer",
            skills = listOf("Kotlin", "Jetpack Compose", "Coroutines")
        )

        val resume = Resume(
            id = 1L,
            name = "Android Lead Resume",
            versions = listOf(
                ResumeVersion(id = 10L, resumeId = 1L, versionName = "v1-Staff")
            )
        )

        val job = JobListing(
            id = "job_456",
            title = "Senior Mobile Architect",
            company = "Acme Corp",
            description = "Build cutting edge mobile systems",
            url = "https://example.com/jobs/456",
            sourceProvider = "Greenhouse"
        )

        val application = Application(
            id = 999L,
            jobId = 456L,
            resumeVersionId = 10L,
            currentStageId = "SCREENING"
        )

        val interview = InterviewSession(
            id = "session_777",
            targetRole = "Staff Android Engineer",
            feedback = InterviewFeedback(
                overallScore = 85,
                improvements = listOf("Deep dive into structured concurrency")
            )
        )

        val graph = engine.buildGraph(
            profile = profile,
            resumes = listOf(resume),
            jobs = listOf(job),
            applications = listOf(application),
            interviews = listOf(interview)
        )

        // Assert Profile Node & Skills
        val profileNode = graph.getNode("user_user_123")
        assertNotNull(profileNode)
        assertEquals("Alice Developer", profileNode?.label)

        val profileSkills = graph.getOutgoingNeighbors("user_user_123", CareerEdgeType.HAS_SKILL)
        assertEquals(3, profileSkills.size)
        assertTrue(profileSkills.any { it.label.equals("Kotlin", ignoreCase = true) })

        // Assert Resume & Version connection
        val resumesInGraph = graph.getOutgoingNeighbors("user_user_123", CareerEdgeType.HAS_RESUME)
        assertEquals(1, resumesInGraph.size)
        val versionsInGraph = graph.getOutgoingNeighbors(resumesInGraph.first().id, CareerEdgeType.HAS_VERSION)
        assertEquals(1, versionsInGraph.size)
        assertEquals("v1-Staff", versionsInGraph.first().label)

        // Assert Application edges
        val appNode = graph.getNode("app_999")
        assertNotNull(appNode)
        val usedVersions = graph.getOutgoingNeighbors("app_999", CareerEdgeType.USES_RESUME)
        assertEquals(1, usedVersions.size)
        assertEquals("resume_version_10", usedVersions.first().id)

        // Assert Interview weakness nodes
        val interviewNode = graph.getNode("interview_session_session_777")
        assertNotNull(interviewNode)
        val weaknesses = graph.getOutgoingNeighbors("interview_session_session_777", CareerEdgeType.IDENTIFIED_WEAKNESS)
        assertEquals(1, weaknesses.size)
        assertEquals(CareerNodeType.CAREER_MEMORY, weaknesses.first().type)
    }

    @Test
    fun `analyzeSkillGaps returns 100 percent match when no missing skills required`() {
        val profile = UserProfile(
            id = "user_123",
            fullName = "Alice Developer",
            email = "alice@example.com",
            skills = listOf("Kotlin", "Coroutines")
        )

        val graph = engine.buildGraph(
            profile = profile,
            resumes = emptyList(),
            jobs = emptyList(),
            applications = emptyList(),
            interviews = emptyList()
        )

        val analysis = engine.analyzeSkillGaps(graph)
        assertEquals(1.0f, analysis.matchRatio, 0.01f)
        assertTrue(analysis.missingSkills.isEmpty())
    }
}
