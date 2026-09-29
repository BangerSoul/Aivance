package com.bangersoul.aivance.core.domain.usecase.resume

import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.common.model.OptimizationTip
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.common.result.ValidationError
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import com.bangersoul.aivance.core.domain.usecase.UseCase
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

data class AtsScoreRequest(
    val resumeId: Long,
    val versionId: Long,
    val jobDescription: String,
    val companyName: String? = null
)

/**
 * Calculates the ATS score for a specific resume version.
 * Returns a persisted [AtsReport] from the AI analysis pipeline.
 */
class CalculateATSScoreUseCase @Inject constructor(
    private val resumeRepository: ResumeRepository
) : UseCase<AtsScoreRequest, CoreResult<AtsReport>>() {

    override suspend operator fun invoke(input: AtsScoreRequest): CoreResult<AtsReport> {
        if (input.resumeId <= 0) {
            return Result.Failure(ValidationError("resumeId", "Invalid resume ID."))
        }
        if (input.jobDescription.isBlank()) {
            return Result.Failure(ValidationError("jobDescription", "Job description cannot be blank."))
        }

        return runCatchingCore {
            val resumeResult = resumeRepository.getResumeById(input.resumeId).firstOrNull()
            val resume = when (resumeResult) {
                is Result.Success -> resumeResult.data
                is Result.Failure -> throw Exception(resumeResult.error.message)
                null -> throw Exception("Resume not found.")
            }

            val analysisResult = resumeRepository.analyzeResume(input.resumeId, input.versionId, input.jobDescription)
            val report = when (analysisResult) {
                is Result.Success -> analysisResult.data
                is Result.Failure -> throw Exception(analysisResult.error.message)
            }

            // Clamp scores at the domain boundary (matches the legacy AtsResult
            // contract), then augment with a formatting score as an optimization
            // tip when the resume layout is poor.
            val formattingScore = calculateFormattingScore(resume.rawText ?: "")
            val clamped = report.copy(
                overallScore = report.overallScore.coerceIn(0, 100),
                matchPercentage = report.matchPercentage.coerceIn(0, 100)
            )
            if (formattingScore < 100) {
                clamped.copy(
                    optimizationTips = clamped.optimizationTips + OptimizationTip(
                        category = "Formatting",
                        description = "Resume formatting score: $formattingScore/100. Consider using bullet points and shorter lines.",
                        priority = if (formattingScore < 80) "HIGH" else "MEDIUM"
                    )
                )
            } else {
                clamped
            }
        }
    }

    private fun calculateFormattingScore(text: String): Int {
        var score = 100
        if (text.isBlank()) return 0
        if (text.lines().any { it.length > 150 }) score -= 10
        val bulletPoints = text.count { it == '•' || it == '-' || it == '*' }
        if (bulletPoints == 0 && text.length > 500) score -= 15
        return score.coerceIn(0, 100)
    }
}
