package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.enums.MessageRole
import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.common.model.JobDescription
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.data.mapper.toDomain
import com.bangersoul.aivance.core.data.mapper.toEntity
import com.bangersoul.aivance.core.database.dao.AtsDao
import com.bangersoul.aivance.core.database.dao.ResumeDao
import com.bangersoul.aivance.core.domain.repository.AtsRepository
import com.bangersoul.aivance.core.domain.repository.AtsStreamEvent
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import com.bangersoul.aivance.sdk.model.AiMessage as SdkAiMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AtsRepositoryImpl @Inject constructor(
    private val atsDao: AtsDao,
    private val resumeDao: ResumeDao,
    private val providerManager: ProviderManager
) : AtsRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getReportsForVersion(versionId: Long): Flow<CoreResult<List<AtsReport>>> {
        return atsDao.getReportsForVersion(versionId).map { entities ->
            runCatchingCore { entities.map { it.toDomain() } }
        }
    }

    override fun getAllReports(): Flow<CoreResult<List<AtsReport>>> {
        return atsDao.getAllReports().map { entities ->
            runCatchingCore {
                entities.map { it.toDomain() }.sortedByDescending { it.dateGenerated }
            }
        }
    }

    override suspend fun getReportById(id: Long): AtsReport? {
        return atsDao.getReportById(id)?.toDomain()
    }

    override suspend fun saveReport(report: AtsReport): CoreResult<Long> = runCatchingCore {
        atsDao.insertReport(report.toEntity())
    }

    override suspend fun deleteReport(id: Long): CoreResult<Unit> = runCatchingCore {
        val report = atsDao.getReportById(id) ?: throw Exception("Report not found")
        atsDao.deleteReport(report)
    }

    override suspend fun saveJobDescription(jd: JobDescription): CoreResult<Long> = runCatchingCore {
        atsDao.insertJobDescription(jd.toEntity())
    }

    override suspend fun getJobDescription(id: Long): JobDescription? {
        return atsDao.getJobDescriptionById(id)?.toDomain()
    }

    override suspend fun performAtsAnalysis(
        resumeId: Long,
        versionId: Long,
        jobDescriptionId: Long
    ): CoreResult<AtsReport> = runCatchingCore {
        val prompt = buildPrompt(versionId, jobDescriptionId)
        val provider = providerManager.getBestProviderFor(ProviderCapability.AI.Chat) as? AIProvider
            ?: throw Exception("No AI provider available")

        val result = provider.generateText(prompt).getOrNull() ?: throw Exception("AI analysis failed")
        persistParsedReport(result, versionId, jobDescriptionId)
    }

    override fun streamAtsAnalysis(
        resumeId: Long,
        versionId: Long,
        jobDescriptionId: Long
    ): Flow<AtsStreamEvent> = flow {
        try {
            val prompt = buildPrompt(versionId, jobDescriptionId)

            // Prefer a streaming-capable provider so tokens render live; fall
            // back to one-shot analysis (single Completed event) otherwise.
            val streamingProvider =
                providerManager.getBestProviderFor(ProviderCapability.AI.Streaming) as? AIProvider
            if (streamingProvider != null) {
                val sdkMessages = listOf(SdkAiMessage(MessageRole.USER, prompt))
                val full = StringBuilder()
                streamingProvider.streamChat(sdkMessages).collect { chunkResult ->
                    when (chunkResult) {
                        is Result.Success -> {
                            full.append(chunkResult.data)
                            emit(AtsStreamEvent.Chunk(chunkResult.data))
                        }
                        is Result.Failure -> throw Exception(chunkResult.error.message)
                    }
                }
                emit(AtsStreamEvent.Completed(persistParsedReport(full.toString(), versionId, jobDescriptionId)))
            } else {
                // No streaming provider — reuse the one-shot path as a fallback.
                when (val result = performAtsAnalysis(resumeId, versionId, jobDescriptionId)) {
                    is Result.Success -> emit(AtsStreamEvent.Completed(result.data))
                    is Result.Failure -> emit(AtsStreamEvent.Failed(result.error.message))
                }
            }
        } catch (e: Exception) {
            emit(AtsStreamEvent.Failed(e.message ?: "ATS analysis failed"))
        }
    }

    /** Builds the shared ATS analysis prompt for a resume version + job description. */
    private suspend fun buildPrompt(versionId: Long, jobDescriptionId: Long): String {
        val sections = resumeDao.getSectionsForVersion(versionId).firstOrNull() ?: emptyList()
        val jd = atsDao.getJobDescriptionById(jobDescriptionId) ?: throw Exception("Job description not found")
        val resumeContent = sections.joinToString("\n\n") { "${it.title}:\n${it.content}" }
        return """
            Perform a detailed ATS (Applicant Tracking System) analysis by comparing the Resume against the Job Description.

            Resume:
            $resumeContent

            Job Description:
            ${jd.rawText}

            Return ONLY a structured JSON object with the following fields:
            "overallScore": Int (0-100),
            "matchPercentage": Int (0-100),
            "matchedKeywords": [String],
            "missingKeywords": [String],
            "sectionScores": { "Summary": Int, "Experience": Int, "Skills": Int, "Education": Int },
            "optimizationTips": [
                { "category": String, "description": String, "priority": "HIGH"|"MEDIUM"|"LOW" }
            ]
        """.trimIndent()
    }

    /** Parses the raw AI output into an [AtsReport] and persists it. */
    private suspend fun persistParsedReport(
        result: String,
        versionId: Long,
        jobDescriptionId: Long
    ): AtsReport {
        val jsonText = if (result.contains("```json")) {
            result.substringAfter("```json").substringBefore("```").trim()
        } else if (result.contains("{") && result.contains("}")) {
            result.substring(result.indexOf("{"), result.lastIndexOf("}") + 1)
        } else result

        val report = json.decodeFromString<AtsReport>(jsonText).copy(
            resumeVersionId = versionId,
            jobDescriptionId = jobDescriptionId
        )
        val id = atsDao.insertReport(report.toEntity())
        return report.copy(id = id)
    }
}
