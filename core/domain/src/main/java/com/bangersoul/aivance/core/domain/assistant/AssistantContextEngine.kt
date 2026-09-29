package com.bangersoul.aivance.core.domain.assistant

import com.bangersoul.aivance.core.common.model.*
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.domain.repository.*
import com.bangersoul.aivance.core.domain.repository.crm.RecruiterIntelligenceRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantContextEngine @Inject constructor(
    private val resumeRepository: ResumeRepository,
    private val atsRepository: AtsRepository,
    private val workflowRepository: ApplicationWorkflowRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val userRepository: UserRepository
) {
    suspend fun buildActiveContext(): String {
        val profile = userRepository.getProfile().firstOrNull()?.getOrNull()
        val resumes = resumeRepository.getResumes().firstOrNull()?.getOrNull() ?: emptyList()
        val latestAnalyses = analyticsRepository.getSnapshots().firstOrNull()?.getOrNull() ?: emptyList()
        val apps = workflowRepository.getApplications().firstOrNull()?.getOrNull() ?: emptyList()

        return buildString {
            appendLine("--- PERSONAL CAREER PROFILE ---")
            if (profile != null) {
                appendLine("User: ${profile.fullName} | Role: ${profile.currentRole.ifBlank { "Entry Level" }}")
                appendLine("Target: ${profile.targetRole.ifBlank { "General Growth" }}")
                appendLine("Location: ${profile.location.ifBlank { "Not specified" }} | Pref: ${profile.workPreference}")
                appendLine("Key Skills: ${profile.skills.joinToString(", ").ifBlank { "Not defined" }}")
                appendLine("Experience: ${profile.experienceYears} years")
            } else {
                appendLine("Profile: Not configured.")
            }
            
            appendLine("\n--- WORKSPACE METRICS ---")
            appendLine("- Total Resumes: ${resumes.size}")
            appendLine("- Active Applications: ${apps.count { it.status == "ACTIVE" }}")
            appendLine("- Latest Career Score: ${latestAnalyses.firstOrNull()?.careerScore ?: "N/A"}")

            if (apps.isNotEmpty()) {
                val topApp = apps.maxByOrNull { it.lastModified }
                if (topApp != null) {
                    appendLine("- Top Focus: ${topApp.job?.title} at ${topApp.job?.company} (${topApp.currentStageId})")
                }
            }
        }
    }
}
