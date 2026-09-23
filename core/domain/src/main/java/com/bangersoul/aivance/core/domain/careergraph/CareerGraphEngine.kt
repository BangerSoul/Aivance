package com.bangersoul.aivance.core.domain.careergraph

import com.bangersoul.aivance.core.common.graph.*
import com.bangersoul.aivance.core.common.model.*
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Traversal and query result representing detected skill gaps between the candidate's
 * demonstrated profile and target job opportunities.
 */
data class SkillGapAnalysis(
    val demonstratedSkills: List<CareerGraphNode>,
    val targetSkills: List<CareerGraphNode>,
    val missingSkills: List<CareerGraphNode>,
    val matchRatio: Float
)

/**
 * Traversal result representing a candidate's complete application workflow context.
 */
data class ApplicationGraphContext(
    val applicationNode: CareerGraphNode,
    val jobNode: CareerGraphNode?,
    val companyNode: CareerGraphNode?,
    val recruiterNode: CareerGraphNode?,
    val resumeVersionNode: CareerGraphNode?,
    val interviewSessions: List<CareerGraphNode>
)

/**
 * Canonical Career Knowledge Graph Engine.
 *
 * Bridges existing relational entities (UserProfile, Resumes, Jobs, Applications,
 * Interviews, CRM) into a queryable, in-memory Career Knowledge Graph without
 * requiring destructive database migrations.
 */
@Singleton
class CareerGraphEngine @Inject constructor(
    private val userRepository: UserRepository,
    private val resumeRepository: ResumeRepository,
    private val jobRepository: JobRepository,
    private val workflowRepository: ApplicationWorkflowRepository,
    private val interviewRepository: InterviewRepository,
    private val careerGraphRepository: CareerGraphRepository
) {

    /**
     * Persists a freshly projected [graph] durably via [CareerGraphRepository].
     * Idempotent: deterministic node/edge IDs upsert the same rows, and the write is
     * transactional so a crash never leaves a half-applied projection.
     */
    suspend fun persist(graph: CareerGraph) {
        careerGraphRepository.persist(graph)
    }

    /**
     * Hydrates the persisted graph for [userId] from disk (empty if none persisted yet).
     */
    suspend fun loadGraph(userId: String): CareerGraph =
        careerGraphRepository.loadGraph(userId)

    /**
     * Builds an immutable, connected [CareerGraph] snapshot from current repository states.
     */
    fun buildGraph(
        profile: UserProfile?,
        resumes: List<Resume>,
        jobs: List<JobListing>,
        applications: List<Application>,
        interviews: List<InterviewSession>
    ): CareerGraph {
        val nodes = mutableMapOf<String, CareerGraphNode>()
        val edges = mutableListOf<CareerGraphEdge>()

        val userId = profile?.id ?: "user_default"

        // 1. Profile Node
        val profileNodeId = "user_$userId"
        nodes[profileNodeId] = CareerGraphNode(
            id = profileNodeId,
            type = CareerNodeType.PROFILE,
            label = profile?.fullName?.ifBlank { "Candidate" } ?: "Candidate",
            properties = mapOf(
                "targetRole" to (profile?.targetRole ?: ""),
                "currentRole" to (profile?.currentRole ?: ""),
                "workPreference" to (profile?.workPreference ?: "REMOTE"),
                "experienceYears" to (profile?.experienceYears?.toString() ?: "0")
            )
        )

        // 2. Profile Skills
        profile?.skills?.forEach { skillName ->
            val skillNodeId = "skill_${skillName.lowercase().trim()}"
            val skillNode = nodes.getOrPut(skillNodeId) {
                CareerGraphNode(
                    id = skillNodeId,
                    type = CareerNodeType.SKILL,
                    label = skillName.trim(),
                    properties = mapOf("source" to "PROFILE")
                )
            }
            edges.add(
                CareerGraphEdge(
                    sourceId = profileNodeId,
                    targetId = skillNode.id,
                    relationType = CareerEdgeType.HAS_SKILL,
                    weight = 1.0f
                )
            )
        }

        // 3. Resumes & Resume Versions
        resumes.forEach { resume ->
            val resumeNodeId = "resume_${resume.id}"
            nodes[resumeNodeId] = CareerGraphNode(
                id = resumeNodeId,
                type = CareerNodeType.RESUME,
                label = resume.name,
                properties = mapOf(
                    "lastModified" to resume.lastModified.toString(),
                    "versionCount" to resume.versions.size.toString()
                )
            )
            edges.add(
                CareerGraphEdge(
                    sourceId = profileNodeId,
                    targetId = resumeNodeId,
                    relationType = CareerEdgeType.HAS_RESUME
                )
            )

            resume.versions.forEach { version ->
                val versionNodeId = "resume_version_${version.id}"
                nodes[versionNodeId] = CareerGraphNode(
                    id = versionNodeId,
                    type = CareerNodeType.RESUME_VERSION,
                    label = version.versionName,
                    properties = mapOf(
                        "templateId" to version.templateId,
                        "resumeId" to resume.id.toString()
                    )
                )
                edges.add(
                    CareerGraphEdge(
                        sourceId = resumeNodeId,
                        targetId = versionNodeId,
                        relationType = CareerEdgeType.HAS_VERSION
                    )
                )
            }
        }

        // 4. Jobs & Required Skills
        jobs.forEach { job ->
            val jobNodeId = "job_${job.id}"
            nodes[jobNodeId] = CareerGraphNode(
                id = jobNodeId,
                type = CareerNodeType.JOB,
                label = "${job.title} at ${job.company}",
                properties = mapOf(
                    "title" to job.title,
                    "company" to job.company,
                    "location" to job.location,
                    "isRemote" to job.isRemote.toString(),
                    "sourceProvider" to job.sourceProvider
                )
            )

            // Company Node
            val companyNodeId = "company_${job.company.lowercase().replace(" ", "_")}"
            val companyNode = nodes.getOrPut(companyNodeId) {
                CareerGraphNode(
                    id = companyNodeId,
                    type = CareerNodeType.COMPANY,
                    label = job.company,
                    properties = mapOf("name" to job.company)
                )
            }
            edges.add(
                CareerGraphEdge(
                    sourceId = jobNodeId,
                    targetId = companyNode.id,
                    relationType = CareerEdgeType.BELONGS_TO
                )
            )
        }

        // 5. Applications
        applications.forEach { app ->
            val appNodeId = "app_${app.id}"
            nodes[appNodeId] = CareerGraphNode(
                id = appNodeId,
                type = CareerNodeType.APPLICATION,
                label = "Application #${app.id} (${app.currentStageId})",
                properties = mapOf(
                    "stage" to app.currentStageId,
                    "status" to app.status,
                    "dateApplied" to (app.dateApplied?.toString() ?: "")
                )
            )
            edges.add(
                CareerGraphEdge(
                    sourceId = profileNodeId,
                    targetId = appNodeId,
                    relationType = CareerEdgeType.APPLIED_TO
                )
            )

            val jobNodeId = "job_${app.jobId}"
            if (nodes.containsKey(jobNodeId)) {
                edges.add(
                    CareerGraphEdge(
                        sourceId = appNodeId,
                        targetId = jobNodeId,
                        relationType = CareerEdgeType.FOR_JOB
                    )
                )
            }

            app.resumeVersionId?.let { versionId ->
                val versionNodeId = "resume_version_$versionId"
                if (nodes.containsKey(versionNodeId)) {
                    edges.add(
                        CareerGraphEdge(
                            sourceId = appNodeId,
                            targetId = versionNodeId,
                            relationType = CareerEdgeType.USES_RESUME
                        )
                    )
                }
            }
        }

        // 6. Interview Sessions
        interviews.forEach { session ->
            val sessionNodeId = "interview_session_${session.id}"
            nodes[sessionNodeId] = CareerGraphNode(
                id = sessionNodeId,
                type = CareerNodeType.INTERVIEW_SESSION,
                label = "Interview: ${session.targetRole} (${session.type})",
                properties = mapOf(
                    "role" to session.targetRole,
                    "type" to session.type,
                    "company" to session.companyName,
                    "isCompleted" to session.isCompleted.toString(),
                    "overallScore" to (session.feedback?.overallScore?.toString() ?: "0")
                )
            )

            session.feedback?.improvements?.forEach { improvement ->
                val weaknessNodeId = "weakness_${improvement.lowercase().take(20).replace(" ", "_")}"
                val weaknessNode = nodes.getOrPut(weaknessNodeId) {
                    CareerGraphNode(
                        id = weaknessNodeId,
                        type = CareerNodeType.CAREER_MEMORY,
                        label = improvement,
                        properties = mapOf("type" to "INTERVIEW_WEAKNESS")
                    )
                }
                edges.add(
                    CareerGraphEdge(
                        sourceId = sessionNodeId,
                        targetId = weaknessNode.id,
                        relationType = CareerEdgeType.IDENTIFIED_WEAKNESS
                    )
                )
            }
        }

        return CareerGraph(
            userId = userId,
            nodes = nodes,
            edges = edges
        )
    }

    /**
     * Traversal Query: Identifies skill gaps between candidate profile skills and skills
     * demanded by saved/applied target jobs.
     */
    fun analyzeSkillGaps(graph: CareerGraph): SkillGapAnalysis {
        val demonstratedSkills = graph.getNodesByType(CareerNodeType.SKILL)
        val demonstratedNames = demonstratedSkills.map { it.label.lowercase().trim() }.toSet()

        // Discover target skills required by saved target jobs
        val targetJobs = graph.getNodesByType(CareerNodeType.JOB)
        val requiredSkills = mutableListOf<CareerGraphNode>()

        targetJobs.forEach { jobNode ->
            val required = graph.getOutgoingNeighbors(jobNode.id, CareerEdgeType.REQUIRES_SKILL)
            requiredSkills.addAll(required)
        }

        val missingSkills = requiredSkills.filter { req ->
            req.label.lowercase().trim() !in demonstratedNames
        }.distinctBy { it.label.lowercase().trim() }

        val totalTarget = requiredSkills.map { it.label.lowercase().trim() }.distinct().size
        val matchRatio = if (totalTarget > 0) {
            (totalTarget - missingSkills.size).toFloat() / totalTarget.toFloat()
        } else {
            1.0f
        }

        return SkillGapAnalysis(
            demonstratedSkills = demonstratedSkills,
            targetSkills = requiredSkills.distinctBy { it.id },
            missingSkills = missingSkills,
            matchRatio = matchRatio.coerceIn(0.0f, 1.0f)
        )
    }

    /**
     * Traversal Query: Resolves full graph context for an active application.
     */
    fun getApplicationContext(graph: CareerGraph, applicationId: Long): ApplicationGraphContext? {
        val appNode = graph.getNode("app_$applicationId") ?: return null

        val jobNode = graph.getOutgoingNeighbors(appNode.id, CareerEdgeType.FOR_JOB).firstOrNull()
        val companyNode = jobNode?.let {
            graph.getOutgoingNeighbors(it.id, CareerEdgeType.BELONGS_TO).firstOrNull()
        }
        val resumeVersion = graph.getOutgoingNeighbors(appNode.id, CareerEdgeType.USES_RESUME).firstOrNull()
        val recruiter = jobNode?.let {
            graph.getIncomingNeighbors(it.id, CareerEdgeType.CONTACTED_BY).firstOrNull()
        }
        val interviews = graph.getOutgoingNeighbors(appNode.id, CareerEdgeType.HAS_INTERVIEW)

        return ApplicationGraphContext(
            applicationNode = appNode,
            jobNode = jobNode,
            companyNode = companyNode,
            recruiterNode = recruiter,
            resumeVersionNode = resumeVersion,
            interviewSessions = interviews
        )
    }
}
