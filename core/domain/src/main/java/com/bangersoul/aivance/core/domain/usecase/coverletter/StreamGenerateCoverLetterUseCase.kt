package com.bangersoul.aivance.core.domain.usecase.coverletter

import com.bangersoul.aivance.core.common.result.ValidationError
import com.bangersoul.aivance.core.domain.repository.CoverLetterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Streaming cover letter generation — mirrors [GenerateCoverLetterUseCase] but
 * emits token chunks in real time so the UI can render a live, typewriter
 * letter instead of a loading spinner. The letter is persisted when the stream
 * completes. Falls back to a single emission for one-shot providers.
 */
class StreamGenerateCoverLetterUseCase @Inject constructor(
    private val coverLetterRepository: CoverLetterRepository
) {
    fun stream(input: GenerateCoverLetterRequest): Flow<String> = flow {
        if (input.resumeId <= 0) throw IllegalArgumentException("Invalid resume ID")
        if (input.jobId != null && input.jobId <= 0) throw IllegalArgumentException("Invalid job ID")
        coverLetterRepository.streamGenerateCoverLetter(
            resumeId = input.resumeId,
            resumeVersionId = input.resumeVersionId,
            jobId = input.jobId,
            recruiterId = input.recruiterId,
            writingStyle = input.writingStyle
        ).collect { chunk -> emit(chunk) }
    }
}
