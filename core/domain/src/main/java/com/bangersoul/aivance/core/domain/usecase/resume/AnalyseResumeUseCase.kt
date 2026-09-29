package com.bangersoul.aivance.core.domain.usecase.resume

import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.common.result.ValidationError
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.domain.repository.ResumeRepository
import com.bangersoul.aivance.core.domain.usecase.UseCase
import javax.inject.Inject

data class AnalyseResumeRequest(
    val resumeId: Long,
    val versionId: Long,
    val jobDescription: String
)

/**
 * Analyses a resume version against a job description using AI.
 * Returns a persisted [AtsReport] written to `ats_reports` via [ResumeRepository.analyzeResume].
 */
class AnalyseResumeUseCase @Inject constructor(
    private val resumeRepository: ResumeRepository
) : UseCase<AnalyseResumeRequest, CoreResult<AtsReport>>() {

    override suspend operator fun invoke(input: AnalyseResumeRequest): CoreResult<AtsReport> {
        if (input.resumeId <= 0) {
            return Result.Failure(ValidationError("resumeId", "Invalid resume ID."))
        }
        if (input.versionId <= 0) {
            return Result.Failure(ValidationError("versionId", "Invalid version ID."))
        }
        if (input.jobDescription.isBlank()) {
            return Result.Failure(ValidationError("jobDescription", "Job description cannot be blank."))
        }

        return runCatchingCore {
            val analysis = resumeRepository.analyzeResume(input.resumeId, input.versionId, input.jobDescription)
            when (analysis) {
                is Result.Success -> analysis.data
                is Result.Failure -> throw Exception(analysis.error.message)
            }
        }
    }
}
