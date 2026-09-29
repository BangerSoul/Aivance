package com.bangersoul.aivance.core.domain.assistant

import com.bangersoul.aivance.core.common.model.JobSearchFilter
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import com.bangersoul.aivance.core.domain.usecase.career.GenerateCareerRoadmapRequest
import com.bangersoul.aivance.core.domain.usecase.career.GenerateCareerRoadmapUseCase
import com.bangersoul.aivance.core.domain.usecase.interview.StartInterviewSessionRequest
import com.bangersoul.aivance.core.domain.usecase.interview.StartInterviewSessionUseCase
import com.bangersoul.aivance.core.domain.usecase.job.SearchJobsRequest
import com.bangersoul.aivance.core.domain.usecase.job.SearchJobsUseCase
import com.bangersoul.aivance.core.domain.usecase.resume.AnalyseResumeRequest
import com.bangersoul.aivance.core.domain.usecase.resume.AnalyseResumeUseCase
import com.bangersoul.aivance.sdk.api.JobProvider
import com.bangersoul.aivance.sdk.core.ProviderStatus
import com.bangersoul.aivance.sdk.infrastructure.ProviderRegistry
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes user intents to concrete platform capabilities so the Assistant acts as
 * an orchestrator rather than a standalone chatbot.
 */
@Singleton
class CapabilityRouter @Inject constructor(
    private val resumeRepository: ResumeRepository,
    private val analyseResumeUseCase: AnalyseResumeUseCase,
    private val searchJobsUseCase: SearchJobsUseCase,
    private val generateCareerRoadmapUseCase: GenerateCareerRoadmapUseCase,
    private val startInterviewSessionUseCase: StartInterviewSessionUseCase,
    private val providerRegistry: ProviderRegistry
) {
    suspend fun routeIntent(intent: String, params: Map<String, String>): CoreResult<String> = runCatchingCore {
        when (intent) {
            "ANALYZE_RESUME" -> {
                val jobDescription = params["jobDescription"].orEmpty()
                if (jobDescription.isBlank()) {
                    "Please share the job description you'd like me to analyze your resume against."
                } else {
                    val resume = resumeRepository.getResumes().firstOrNull()?.getOrNull()?.firstOrNull()
                        ?: return@runCatchingCore "No resume found — upload a resume in Resume Intelligence first."
                    val versionId = resume.primaryVersionId
                        ?: resumeRepository.getVersions(resume.id).firstOrNull()?.getOrNull()?.firstOrNull()?.id
                    if (versionId == null || versionId <= 0L) {
                        return@runCatchingCore "Your resume has no version to analyze yet — open Resume Intelligence to create one first."
                    }
                    val analysis = analyseResumeUseCase(
                        AnalyseResumeRequest(resume.id, versionId, jobDescription)
                    ).getOrNull() ?: return@runCatchingCore "Resume analysis failed — check your AI provider."
                    buildString {
                        appendLine("Resume match: ${analysis.overallScore}/100")
                        if (analysis.matchedKeywords.isNotEmpty()) {
                            appendLine("Matched keywords: ${analysis.matchedKeywords.take(8).joinToString(", ")}")
                        }
                        if (analysis.missingKeywords.isNotEmpty()) {
                            appendLine("Missing keywords: ${analysis.missingKeywords.take(8).joinToString(", ")}")
                        }
                        if (analysis.optimizationTips.isNotEmpty()) {
                            appendLine("Tips: ${analysis.optimizationTips.take(3).joinToString(" ") { it.description }}")
                        }
                    }
                }
            }
            "SEARCH_JOBS" -> {
                val query = params["query"].orEmpty()
                if (query.isBlank()) {
                    "What role are you looking for? Tell me a job title or keywords."
                } else if (noConnectedJobProviders()) {
                    "No job providers are connected yet — open Settings → Providers to connect one (e.g. Apify, Greenhouse) before searching."
                } else {
                    val jobs = searchJobsUseCase(SearchJobsRequest(JobSearchFilter(query = query))).getOrNull().orEmpty()
                    if (jobs.isEmpty()) {
                        "No jobs found for \"$query\" — try different keywords."
                    } else {
                        buildString {
                            appendLine("Top matches for \"$query\":")
                            jobs.take(5).forEachIndexed { index, job ->
                                appendLine("${index + 1}. ${job.title} — ${job.company}")
                            }
                        }
                    }
                }
            }
            "GENERATE_ROADMAP" -> {
                val targetRole = params["targetRole"].orEmpty()
                if (targetRole.isBlank()) {
                    "What role do you want a roadmap for?"
                } else {
                    val roadmap = generateCareerRoadmapUseCase(
                        GenerateCareerRoadmapRequest(targetRole = targetRole)
                    ).getOrNull() ?: return@runCatchingCore "Roadmap generation failed — check your AI provider."
                    buildString {
                        appendLine("Roadmap to $targetRole:")
                        roadmap.steps.take(6).forEachIndexed { index, step ->
                            appendLine("${index + 1}. ${step.title}")
                        }
                    }
                }
            }
            "START_INTERVIEW" -> {
                val role = params["targetRole"].orEmpty()
                val result = startInterviewSessionUseCase(StartInterviewSessionRequest(targetRole = role))
                when (result) {
                    is Result.Success -> "Mock interview started for ${role.ifBlank { "your target role" }}. Open the Interview module to begin."
                    is Result.Failure -> "Could not start interview: ${result.error.message}"
                }
            }
            else -> "I understand your intent is $intent, but I cannot execute it yet."
        }
    }

    private fun noConnectedJobProviders(): Boolean {
        // Mirrors JobRepositoryImpl.searchJobs, which only queries Active/Ready
        // providers, so the message is never contradicted by an empty result.
        return providerRegistry.getAllProviders()
            .filterIsInstance<JobProvider>()
            .none {
                it.status == ProviderStatus.Active ||
                    it.status == ProviderStatus.Ready
            }
    }
}
