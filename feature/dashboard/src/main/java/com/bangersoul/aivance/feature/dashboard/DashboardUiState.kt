
package com.bangersoul.aivance.feature.dashboard

/**
 * Aggregated Career HQ state — transformed from a stat-sheet to an Agentic Command Center.
 */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val greeting: String = "",
    val userDesignation: String = "",
    val careerScore: Int = 0,
    val atsScore: Int = 0,
    val activeApplications: Int = 0,
    val nextInterview: String? = null,
    val savedJobs: Int = 0,
    val aiRecommendation: String? = null,
    val nextBestAction: com.bangersoul.aivance.core.domain.engine.NavigationIntent = com.bangersoul.aivance.core.domain.engine.NavigationIntent.None,
    val agentMissions: List<AgentMission> = emptyList(),
    val activeTask: AgentTask? = null,
    val error: String? = null
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
