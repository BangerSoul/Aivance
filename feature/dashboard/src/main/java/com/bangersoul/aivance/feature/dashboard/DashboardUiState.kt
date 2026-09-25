
package com.bangersoul.aivance.feature.dashboard

/**
 * Aggregated Career HQ state — transformed from a stat-sheet to an Agentic Command Center.
 */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val greeting: String = "",
    val userDesignation: String = "",
    /**
     * Composite career score, or `null` when nothing has been measured yet. A `0` here used to
     * be indistinguishable from "brand-new install", which let a fabricated composite render
     * as a real reading (R3-1).
     */
    val careerScore: Int? = null,
    /** Latest measured ATS score, or `null` when no ATS analysis has run yet. */
    val atsScore: Int? = null,
    /** Count of active applications — a count, so `0` is a real and useful zero. */
    val activeApplications: Int = 0,
    /** Real interview datetime of the soonest scheduled session, or `null` when none exists. */
    val nextInterview: String? = null,
    /** Count of bookmarked jobs — a count, so `0` is a real and useful zero. */
    val savedJobs: Int = 0,
    val aiRecommendation: String? = null,
    val nextBestAction: com.bangersoul.aivance.core.domain.engine.NavigationIntent = com.bangersoul.aivance.core.domain.engine.NavigationIntent.None,
    /**
     * Agent missions. Always empty until a real agent producer is wired (R3-6 removed the
     * hardcoded literals that used to sit here and were never rendered).
     */
    val agentMissions: List<AgentMission> = emptyList(),
    val activeTask: AgentTask? = null,
    val graphInsights: CareerGraphInsightsUi = CareerGraphInsightsUi(),
    val error: String? = null
)

/**
 * Dashboard-facing view of the Career Knowledge Graph reader
 * ([com.bangersoul.aivance.core.domain.usecase.career.GetCareerGraphInsightsUseCase]).
 */
data class CareerGraphInsightsUi(
    val available: Boolean = false,
    /**
     * 0..100 share of target-job skills already demonstrated, or `null` when no target job
     * demanded a recognised skill. `null` is "not measurable yet", never 100% (R3-2).
     */
    val skillMatchPercent: Int? = null,
    val demonstratedSkillCount: Int = 0,
    val targetSkillCount: Int = 0,
    val missingSkills: List<MissingSkillUi> = emptyList(),
    val applicationContexts: List<ApplicationContextUi> = emptyList(),
    /** How many of the surfaced missing skills the user has acted on. */
    val gapsActedOn: Int = 0
) {
    /** 0..1 momentum toward closing the surfaced gaps (acted-on / total). */
    val gapProgress: Float
        get() = if (missingSkills.isEmpty()) 0f else gapsActedOn.toFloat() / missingSkills.size
}

/** Whether/how far the user has engaged with a missing skill. Mirrors the domain enum. */
enum class SkillEngagementUi { NONE, EXPLORED_JOBS, STARTED_LEARNING }

data class MissingSkillUi(
    val skill: String,
    val demandedByJobs: Int,
    val engagement: SkillEngagementUi = SkillEngagementUi.NONE
)

data class ApplicationContextUi(
    val applicationId: Long,
    val jobTitle: String,
    val company: String,
    val interviewCount: Int
)

data class AgentMission(
    val id: String,
    val title: String,
    val status: MissionStatus,
    val progress: Float = 0f,
    val lastUpdate: String,
    val actionRoute: String? = null
)

enum class MissionStatus {
    PENDING, RUNNING, COMPLETED, FAILED, REVIEW_REQUIRED
}

data class AgentTask(
    val id: String,
    val description: String,
    val status: MissionStatus,
    val progress: Float = 0f
)
