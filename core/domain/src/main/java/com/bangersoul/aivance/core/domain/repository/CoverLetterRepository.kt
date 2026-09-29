package com.bangersoul.aivance.core.domain.repository

import com.bangersoul.aivance.core.common.model.CoverLetter
import com.bangersoul.aivance.core.common.model.CoverLetterVersion
import com.bangersoul.aivance.core.common.result.CoreResult
import kotlinx.coroutines.flow.Flow

interface CoverLetterRepository {
    fun getCoverLetters(): Flow<CoreResult<List<CoverLetter>>>
    fun getCoverLetterById(id: Long): Flow<CoreResult<CoverLetter>>
    suspend fun saveCoverLetter(coverLetter: CoverLetter): CoreResult<Long>
    suspend fun deleteCoverLetter(id: Long): CoreResult<Unit>

    // Versions
    fun getVersions(coverLetterId: Long): Flow<CoreResult<List<CoverLetterVersion>>>
    suspend fun saveVersion(version: CoverLetterVersion): CoreResult<Long>
    suspend fun deleteVersion(coverLetterId: Long, versionId: Long): CoreResult<Unit>

    // Generation
    suspend fun generateCoverLetter(
        resumeId: Long,
        resumeVersionId: Long,
        jobId: Long?,
        recruiterId: String?,
        writingStyle: String
    ): CoreResult<Long>

    /**
     * Streaming variant of [generateCoverLetter]. Emits token chunks in real
     * time from a streaming-capable provider (falling back to a single
     * emission for one-shot providers) and persists the finished letter when
     * the stream completes.
     */
    fun streamGenerateCoverLetter(
        resumeId: Long,
        resumeVersionId: Long,
        jobId: Long?,
        recruiterId: String?,
        writingStyle: String
    ): kotlinx.coroutines.flow.Flow<String>

    suspend fun regenerateSection(
        versionId: Long,
        sectionType: String
    ): CoreResult<Unit>
}
