package com.bangersoul.aivance.core.domain.usecase.coverletter

import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.common.result.ValidationError
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.domain.repository.CoverLetterRepository
import com.bangersoul.aivance.core.domain.usecase.UseCase
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

data class ImproveCoverLetterRequest(
    val coverLetterId: Long,
    val feedback: String = ""
)

/**
 * Improves an existing cover letter draft.
 */
class ImproveCoverLetterUseCase @Inject constructor(
    private val coverLetterRepository: CoverLetterRepository
) : UseCase<ImproveCoverLetterRequest, CoreResult<Long>>() {

    override suspend operator fun invoke(input: ImproveCoverLetterRequest): CoreResult<Long> {
        if (input.coverLetterId <= 0) {
            return Result.Failure(ValidationError("coverLetterId", "Invalid cover letter ID."))
        }

        return runCatchingCore {
            val letterResult = coverLetterRepository.getCoverLetterById(input.coverLetterId).firstOrNull()
            val letter = when (letterResult) {
                is Result.Success -> letterResult.data
                is Result.Failure -> throw Exception(letterResult.error.message)
                null -> throw Exception("Cover letter not found.")
            }

            val improvedId = coverLetterRepository.generateCoverLetter(
                resumeId = 0L,
                resumeVersionId = letter.resumeVersionId ?: 0L,
                // Null job id flows through the generic (no-job) generation path
                // instead of being rewritten to 0 (which fails validation).
                jobId = letter.jobId,
                recruiterId = letter.recruiterId,
                writingStyle = "IMPROVED: ${input.feedback}"
            )

            when (improvedId) {
                is Result.Success -> improvedId.data
                is Result.Failure -> throw Exception(improvedId.error.message)
            }
        }
    }
}
