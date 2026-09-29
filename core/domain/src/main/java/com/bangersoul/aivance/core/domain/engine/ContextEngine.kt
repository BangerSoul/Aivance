package com.bangersoul.aivance.core.domain.engine

import com.bangersoul.aivance.core.common.model.CareerState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextEngine @Inject constructor(
    private val resumeRepository: com.bangersoul.aivance.core.domain.repository.ResumeRepository,
    private val jobRepository: com.bangersoul.aivance.core.domain.repository.JobRepository
) {

    fun generateAssistantContext(state: CareerState): String {
        return """
            The user is currently in the ${state.lifecycleStage} stage of their career journey.
            - Name: ${state.profile.name}
            - Target Role: ${state.profile.targetRole}
            - Profile Completion: ${state.profile.completionPercentage}%
            - Latest ATS Score: ${state.intelligence.atsScore?.let { "$it%" } ?: "not measured yet"}
            - Active Applications: ${state.pipeline.activeApplications}
            - Upcoming Interviews: ${state.pipeline.upcomingInterviews.size}
            - Overall Career Score: ${state.growth.careerScore ?: "not measured yet"}
            - Next Action: ${state.nextBestAction?.title ?: "N/A"}
        """.trimIndent()
    }

    suspend fun resolveGranularContext(state: CareerState): Map<String, String> {
        val context = mutableMapOf<String, String>()
        context["lifecycle_stage"] = state.lifecycleStage.name
        context["target_role"] = state.profile.targetRole

        state.intelligence.latestResumeId?.let { id ->
            // In a real implementation, we would fetch the actual resume text here
            context["resume_available"] = "true"
        }

        context["active_apps"] = state.pipeline.activeApplications.toString()
        context["career_score"] = state.growth.careerScore?.toString() ?: "not_measured"

        return context
    }

    fun generateSystemPrompt(state: CareerState): String {
        val basePrompt = "You are AiVance, an expert AI Career Coach. "
        val context = generateAssistantContext(state)
        return "$basePrompt\n\nCurrent User Context:\n$context\n\nProvide helpful, actionable advice based on this state."
    }
}
