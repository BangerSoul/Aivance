package com.bangersoul.aivance.core.domain.usecase.career

import com.bangersoul.aivance.core.common.graph.CareerGraph
import com.bangersoul.aivance.core.common.model.Application
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.domain.careergraph.ApplicationGraphContext
import com.bangersoul.aivance.core.domain.careergraph.CareerGraphEngine
import com.bangersoul.aivance.core.domain.careergraph.SkillGapAnalysis
import com.bangersoul.aivance.core.domain.repository.ApplicationWorkflowRepository
import com.bangersoul.aivance.core.domain.repository.SkillGapEngagement
import com.bangersoul.aivance.core.domain.repository.SkillGapProgressRepository
import com.bangersoul.aivance.core.domain.repository.UserRepository
import com.bangersoul.aivance.core.domain.usecase.NoInputUseCase
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * A single missing skill surfaced to the UI, kept decoupled from graph internals.
 */
data class SkillGapItem(
    val skill: String,
    /** How many of the candidate's target jobs demand this skill. */
    val demandedByJobs: Int,
    /**
     * How far the user has moved toward closing this gap. Derived from recorded
     * engagement (exploring jobs / starting learning), NOT from skill possession
     * — possession stays graph-derived. [SkillGapEngagement.NONE] means untouched.
     */
    val engagement: SkillGapEngagement = SkillGapEngagement.NONE
)

/**
 * A read-model summary of one active application's graph context.
 */
data class ApplicationContextSummary(
    val applicationId: Long,
    val jobTitle: String?,
    val company: String?,
    val resumeVersion: String?,
    val interviewCount: Int
)

/**
 * The dashboard-facing projection of the Career Knowledge Graph.
 *
 * [hasGraph] distinguishes "no graph persisted yet" from "graph present but nothing to show",
 * so the UI can stay silent until the live [CareerStateEngine] has produced a projection at
 * least once.
 */
data class CareerGraphInsights(
    val hasGraph: Boolean = false,
    val demonstratedSkillCount: Int = 0,
    val targetSkillCount: Int = 0,
    /**
     * 0..100 — share of target-job skills the candidate already demonstrates, or `null` when no
     * target job demanded a recognised skill (nothing to match against). Never fabricated into a
     * perfect score (R3-2).
     */
    val skillMatchPercent: Int? = null,
    val topMissingSkills: List<SkillGapItem> = emptyList(),
    val activeApplicationContexts: List<ApplicationContextSummary> = emptyList(),
    /**
     * How many of the surfaced missing skills the user has already acted on
     * (explored jobs or started learning). Lets the dashboard show momentum
     * toward closing gaps without claiming the skill is demonstrated.
     */
    val gapsActedOn: Int = 0
) {
    companion object {
        val EMPTY = CareerGraphInsights()
    }
}

/**
 * First production **reader** of the durable Career Knowledge Graph.
 *
 * Loads the persisted graph (owned/written by [CareerStateEngine] via the M05 entity projection)
 * and runs the existing [CareerGraphEngine] traversal queries — skill-gap analysis and
 * per-application context — turning them into a UI-ready [CareerGraphInsights] read model.
 *
 * This is strictly read-only: it never writes to the graph, so it cannot disturb either the
 * entity projection or the replay-owned `CAREER_EVENT` provenance slice. Missing skills are only
 * ever those the graph's grounded `REQUIRES_SKILL` edges expose, so nothing is fabricated.
 */
class GetCareerGraphInsightsUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val workflowRepository: ApplicationWorkflowRepository,
    private val careerGraphEngine: CareerGraphEngine,
    private val skillGapProgressRepository: SkillGapProgressRepository
) : NoInputUseCase<CareerGraphInsights>() {

    override suspend fun invoke(): CareerGraphInsights {
        // Key the graph on the same profile id the live CareerStateEngine projects from.
        val userId = userRepository.getProfile().firstOrNull()?.getOrNull()?.id ?: "user_default"

        val graph = careerGraphEngine.loadGraph(userId)
        if (graph.nodes.isEmpty()) return CareerGraphInsights.EMPTY

        val gap: SkillGapAnalysis = careerGraphEngine.analyzeSkillGaps(graph)

        // Recorded engagement per skill (intent/momentum), keyed by normalized skill.
        val engagementByKey = skillGapProgressRepository.observeProgress().firstOrNull()
            .orEmpty()
            .associate { it.skillKey to it.engagement }

        val missing = gap.missingSkills
            .map { node -> node.label to demandCount(graph, node.label) }
            .sortedByDescending { it.second }
            .take(MAX_MISSING_SKILLS)
            .map { (skill, demand) ->
                SkillGapItem(
                    skill = skill,
                    demandedByJobs = demand,
                    engagement = engagementByKey[skill.lowercase().trim()]
                        ?: SkillGapEngagement.NONE
                )
            }

        val contexts = activeApplicationIds()
            .mapNotNull { appId -> careerGraphEngine.getApplicationContext(graph, appId)?.toSummary(appId) }

        return CareerGraphInsights(
            hasGraph = true,
            demonstratedSkillCount = gap.demonstratedSkills.size,
            targetSkillCount = gap.targetSkills.size,
            skillMatchPercent = gap.matchRatio?.let { (it * 100).toInt().coerceIn(0, 100) },
            topMissingSkills = missing,
            activeApplicationContexts = contexts,
            gapsActedOn = missing.count { it.engagement != SkillGapEngagement.NONE }
        )
    }

    /** How many target JOB nodes require the skill labelled [skillLabel]. */
    private fun demandCount(graph: CareerGraph, skillLabel: String): Int {
        val skillId = "skill_${skillLabel.lowercase().trim()}"
        return graph.getEdgesTo(skillId).count { it.relationType.name == "REQUIRES_SKILL" }
    }

    private suspend fun activeApplicationIds(): List<Long> =
        workflowRepository.getApplications().firstOrNull()?.getOrNull()
            ?.filter { it.status == "ACTIVE" }
            ?.map { it.id }
            ?: emptyList()

    private fun ApplicationGraphContext.toSummary(applicationId: Long) = ApplicationContextSummary(
        applicationId = applicationId,
        jobTitle = jobNode?.properties?.get("title") ?: jobNode?.label,
        company = companyNode?.label ?: jobNode?.properties?.get("company"),
        resumeVersion = resumeVersionNode?.label,
        interviewCount = interviewSessions.size
    )

    companion object {
        const val MAX_MISSING_SKILLS = 5
    }
}
