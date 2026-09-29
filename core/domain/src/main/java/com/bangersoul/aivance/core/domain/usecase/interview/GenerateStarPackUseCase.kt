package com.bangersoul.aivance.core.domain.usecase.interview

import com.bangersoul.aivance.core.common.model.InterviewQuestion
import com.bangersoul.aivance.core.domain.repository.AiRepository
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Request for a role-specific STAR question pack.
 *
 * @param role Target role the pack is tailored to; blank falls back to a
 *   generic software-engineering pack.
 * @param count Number of questions (clamped 1..20).
 */
data class GenerateStarPackRequest(
    val role: String,
    val count: Int = 5
)

/**
 * Generates a STAR-format (Situation, Task, Action, Result) interview question
 * pack for a chosen role (R-05).
 *
 * The AI path uses the existing streaming pipeline ([AiRepository.streamAnalyzeText])
 * and asks for a JSON array of questions that each carry the STAR key-points
 * framework and a worked example answer.
 *
 * **Graceful degradation is structural:** when no AI provider is configured,
 * the stream fails, or the response cannot be parsed, the use case falls back
 * to the deterministic role-interpolated [STARPrepGenerator] template pack. It
 * never throws and never dead-ends the Prep Studio on an empty pack — callers
 * always receive a usable, persisted pack.
 */
@Singleton
class GenerateStarPackUseCase @Inject constructor(
    private val aiRepository: AiRepository
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Max questions per pack — bounds token cost + latency. */
    private val maxCount = 20

    suspend operator fun invoke(request: GenerateStarPackRequest): List<InterviewQuestion> {
        val role = request.role.ifBlank { "Software Engineer" }
        val count = request.count.coerceIn(1, maxCount)
        return generateWithAi(role, count) ?: STARPrepGenerator.generateStarPack(role, count)
    }

    private suspend fun generateWithAi(role: String, count: Int): List<InterviewQuestion>? {
        return try {
            val response = aiRepository.streamAnalyzeText(role, buildPrompt(role, count))
                .toList()
                .joinToString("")
            parseQuestions(response).takeIf { it.isNotEmpty() }?.take(count)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildPrompt(role: String, count: Int): String =
        STARCoachingPrompts.buildPackPrompt(role, count)

    /**
     * Extracts the question array from the provider response, tolerating
     * markdown JSON fences and extra prose before/after the array.
     */
    private fun parseQuestions(response: String): List<InterviewQuestion> {
        val body = if (response.contains("```")) {
            response.substringAfter("```").substringBeforeLast("```").trim()
        } else {
            response
        }
        val start = body.indexOf('[')
        val end = body.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        return try {
            json.decodeFromString<List<InterviewQuestion>>(body.substring(start, end + 1))
        } catch (_: Exception) {
            emptyList()
        }
    }
}
