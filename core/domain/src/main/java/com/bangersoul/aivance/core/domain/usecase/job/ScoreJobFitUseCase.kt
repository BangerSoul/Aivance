package com.bangersoul.aivance.core.domain.usecase.job

import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.ProfileState
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class ScoreJobFitRequest(
    val jobs: List<JobListing>,
    val profile: ProfileState?,
    val query: String = ""
)

data class FitDetail(val score: Int, val gapAnalysis: String = "")

@Singleton
class ScoreJobFitUseCase @Inject constructor(
    private val providerManager: ProviderManager
) {
    private val cache = ConcurrentHashMap<String, Int>()
    private val maxJobsPerBatch = 10
    private val maxDescriptionChars = 300

    suspend operator fun invoke(request: ScoreJobFitRequest): Map<String, Int> {
        val profile = request.profile ?: return emptyMap()
        val jobs = request.jobs.take(maxJobsPerBatch)
        if (jobs.isEmpty()) return emptyMap()

        val profileKey = profile.signature()
        val keyFor = { jobId: String -> "$jobId::$profileKey" }
        val uncached = jobs.filter { !cache.containsKey(keyFor(it.id)) }

        if (uncached.isNotEmpty()) {
            val provider = providerManager.getBestProviderFor(ProviderCapability.AI.Chat) as? AIProvider
            if (provider != null) {
                scoreWithProvider(provider, uncached, request, profileKey, keyFor)
            }
        }
        return jobs.mapNotNull { job ->
            cache[keyFor(job.id)]?.let { score -> job.id to score }
        }.toMap()
    }

    private suspend fun scoreWithProvider(
        provider: AIProvider,
        jobs: List<JobListing>,
        request: ScoreJobFitRequest,
        profileKey: String,
        keyFor: (String) -> String
    ) {
        try {
            val prompt = buildPrompt(jobs, request.profile ?: return, request.query)
            val response = provider.generateText(prompt).getOrNull() ?: return
            val allowedIds = jobs.map { it.id }.toSet()
            parseScores(response).forEach { (jobId, score) ->
                if (jobId in allowedIds) {
                    cache[keyFor(jobId)] = score.coerceIn(0, 100)
                }
            }
        } catch (_: Exception) {}
    }

    private fun buildPrompt(jobs: List<JobListing>, profile: ProfileState, query: String): String = buildString {
        appendLine("You are a job-fit scoring engine. Score how well each job matches the candidate profile.")
        appendLine("Return ONLY a JSON object mapping each job id to an integer fit score from 0 to 100 (higher = better fit).")
        appendLine("Weigh: role and experience match, skill overlap, and location/remote-work preference.")
        appendLine()
        appendLine("Candidate profile:")
        if (profile.targetRole.isNotBlank()) appendLine("- Target role: ${profile.targetRole}")
        if (profile.skills.isNotEmpty()) appendLine("- Skills: ${profile.skills.joinToString(", ")}")
        if (profile.workPreference.isNotBlank()) appendLine("- Work preference: ${profile.workPreference}")
        if (query.isNotBlank()) appendLine("- Active search query: $query")
        appendLine()
        appendLine("Jobs (id | title | company | location | description):")
        jobs.forEach { job ->
            val desc = job.description.replace('\n', ' ').take(maxDescriptionChars)
            appendLine("${job.id} | ${job.title} | ${job.company} | ${job.location} | $desc")
        }
    }

    private fun parseScores(response: String): Map<String, Int> {
        val body = if (response.contains("```")) {
            response.substringAfter("```").substringBeforeLast("```").trim()
        } else {
            response
        }
        val result = mutableMapOf<String, Int>()

        // Match nested object format: "id": { ... "score": 88 ... }
        val nestedRegex = Regex("\"([^\"]+)\"\\s*:\\s*\\{[^}]*\"score\"\\s*:\\s*(-?\\d+)")
        nestedRegex.findAll(body).forEach { match ->
            val id = match.groupValues[1].trim()
            val score = match.groupValues[2].toIntOrNull()
            if (id.isNotBlank() && score != null) {
                result[id] = score
            }
        }

        // Match direct format: "id": 88
        val directRegex = Regex("\"([^\"]+)\"\\s*:\\s*(-?\\d{1,4})")
        directRegex.findAll(body).forEach { match ->
            val id = match.groupValues[1].trim()
            val score = match.groupValues[2].toIntOrNull()
            if (id.isNotBlank() && score != null && id != "score") {
                result.putIfAbsent(id, score)
            }
        }

        return result
    }

    private fun ProfileState.signature(): String =
        listOf(targetRole, skills.joinToString(","), workPreference, salaryExpectation)
            .joinToString("::")
            .lowercase()

    companion object {
        const val MAX_JOBS_PER_BATCH = 10
    }
}
