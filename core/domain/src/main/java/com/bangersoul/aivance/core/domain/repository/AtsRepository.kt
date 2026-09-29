package com.bangersoul.aivance.core.domain.repository

import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.common.model.JobDescription
import com.bangersoul.aivance.core.common.result.CoreResult
import kotlinx.coroutines.flow.Flow

/**
 * Live ATS analysis events — tokens stream as they are generated so the UI can
 * show a typewriter-style preview instead of a blank spinner.
 */
sealed interface AtsStreamEvent {
    data class Chunk(val text: String) : AtsStreamEvent
    data class Completed(val report: AtsReport) : AtsStreamEvent
    data class Failed(val message: String) : AtsStreamEvent
}

interface AtsRepository {
    fun getReportsForVersion(versionId: Long): Flow<CoreResult<List<AtsReport>>>

    /** All ATS reports across every resume version, newest first. */
    fun getAllReports(): Flow<CoreResult<List<AtsReport>>>
    suspend fun getReportById(id: Long): AtsReport?
    suspend fun saveReport(report: AtsReport): CoreResult<Long>
    suspend fun deleteReport(id: Long): CoreResult<Unit>

    suspend fun saveJobDescription(jd: JobDescription): CoreResult<Long>
    suspend fun getJobDescription(id: Long): JobDescription?

    suspend fun performAtsAnalysis(resumeId: Long, versionId: Long, jobDescriptionId: Long): CoreResult<AtsReport>

    /**
     * Streams the ATS analysis token-by-token. Emits [AtsStreamEvent.Chunk]
     * for each chunk, then a single [AtsStreamEvent.Completed] with the parsed,
     * persisted report (or [AtsStreamEvent.Failed] on error). Falls back to the
     * one-shot [performAtsAnalysis] when no streaming-capable provider exists.
     */
    fun streamAtsAnalysis(resumeId: Long, versionId: Long, jobDescriptionId: Long): Flow<AtsStreamEvent>
}

