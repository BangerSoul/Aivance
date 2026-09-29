package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.model.CoverLetter
import com.bangersoul.aivance.core.common.model.CoverLetterVersion
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.data.mapper.toDomain
import com.bangersoul.aivance.core.data.mapper.toEntity
import com.bangersoul.aivance.core.database.dao.CoverLetterDao
import com.bangersoul.aivance.core.database.dao.JobDao
import com.bangersoul.aivance.core.domain.repository.CoverLetterRepository
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverLetterRepositoryImpl @Inject constructor(
    private val coverLetterDao: CoverLetterDao,
    private val jobDao: JobDao,
    private val providerManager: ProviderManager
) : CoverLetterRepository {

    override fun getCoverLetters(): Flow<CoreResult<List<CoverLetter>>> {
        return coverLetterDao.getCoverLetters().map { entities ->
            runCatchingCore { entities.map { it.toDomain() } }
        }
    }

    override fun getCoverLetterById(id: Long): Flow<CoreResult<CoverLetter>> {
        return coverLetterDao.getCoverLetters().map { list ->
            runCatchingCore {
                val entity = list.find { it.id.toLong() == id } ?: throw Exception("Cover letter not found")
                val vEntities = coverLetterDao.getVersionsForCoverLetter(id).firstOrNull() ?: emptyList()
                val versions = vEntities.map { v ->
                    val sections = coverLetterDao.getSectionsForVersion(v.id).firstOrNull() ?: emptyList()
                    v.toDomain(sections)
                }
                entity.toDomain(versions)
            }
        }
    }

    override suspend fun saveCoverLetter(coverLetter: CoverLetter): CoreResult<Long> = runCatchingCore {
        coverLetterDao.insertCoverLetter(coverLetter.toEntity())
    }

    override suspend fun deleteCoverLetter(id: Long): CoreResult<Unit> = runCatchingCore {
        val entity = coverLetterDao.getCoverLetterById(id) ?: throw Exception("Not found")
        coverLetterDao.deleteCoverLetter(entity)
    }

    override fun getVersions(coverLetterId: Long): Flow<CoreResult<List<CoverLetterVersion>>> {
        return coverLetterDao.getVersionsForCoverLetter(coverLetterId).map { entities ->
            runCatchingCore {
                entities.map { v ->
                    val sections = coverLetterDao.getSectionsForVersion(v.id).firstOrNull() ?: emptyList()
                    v.toDomain(sections)
                }
            }
        }
    }

    override suspend fun saveVersion(version: CoverLetterVersion): CoreResult<Long> = runCatchingCore {
        val entity = version.toEntity()
        val sections = version.sections.map { it.toEntity(version.id) }
        coverLetterDao.updateVersionWithSections(entity, sections)
        entity.id
    }

    override suspend fun deleteVersion(coverLetterId: Long, versionId: Long): CoreResult<Unit> = runCatchingCore {
        val version = coverLetterDao.getVersionById(versionId) ?: throw Exception("Version not found")
        coverLetterDao.deleteVersion(version)
    }

    override suspend fun generateCoverLetter(
        resumeId: Long,
        resumeVersionId: Long,
        jobId: Long?,
        recruiterId: String?,
        writingStyle: String
    ): CoreResult<Long> = runCatchingCore {
        val job = jobId?.let { jobDao.getJobWithDetailsById(it) }
        val (companyName, roleTitle) = letterTarget(job)

        val provider = providerManager.getBestProviderFor(ProviderCapability.AI.Chat) as? AIProvider
            ?: throw Exception("No AI provider available")

        val prompt = buildPrompt(companyName, roleTitle, writingStyle)
        val content = provider.generateText(prompt).getOrNull() ?: throw Exception("AI generation failed")

        persistLetter(jobId, resumeVersionId, recruiterId, writingStyle, companyName, roleTitle, content)
    }

    override fun streamGenerateCoverLetter(
        resumeId: Long,
        resumeVersionId: Long,
        jobId: Long?,
        recruiterId: String?,
        writingStyle: String
    ): Flow<String> = flow {
        // jobId null → generic letter ("Generate cover letter" without a job).
        val job = jobId?.let { jobDao.getJobWithDetailsById(it) }
        val (companyName, roleTitle) = letterTarget(job)
        val prompt = buildPrompt(companyName, roleTitle, writingStyle)

        val streamingProvider =
            providerManager.getBestProviderFor(ProviderCapability.AI.Streaming) as? AIProvider
        val content = StringBuilder()
        if (streamingProvider != null) {
            streamingProvider.streamText(prompt).collect { chunk ->
                content.append(chunk)
                emit(chunk)
            }
        } else {
            // Graceful fallback: non-streaming provider emits the full letter once.
            val provider = providerManager.getBestProviderFor(ProviderCapability.AI.Chat) as? AIProvider
                ?: throw Exception("No AI provider available")
            val full = provider.generateText(prompt).getOrNull()
                ?: throw Exception("AI generation failed")
            content.append(full)
            emit(full)
        }

        if (content.isEmpty()) throw Exception("AI returned an empty cover letter")

        persistLetter(
            jobId, resumeVersionId, recruiterId, writingStyle,
            companyName, roleTitle, content.toString()
        )
    }

    /**
     * Resolves the letter's audience: a real job listing when one is attached,
     * otherwise a neutral generic target so a letter can be generated without
     * picking a job first.
     */
    private fun letterTarget(job: com.bangersoul.aivance.core.database.model.JobWithDetails?): Pair<String, String> {
        return if (job != null) {
            job.company.name to job.job.title
        } else {
            "Your Next Employer" to "your target role"
        }
    }

    private suspend fun persistLetter(
        jobId: Long?,
        resumeVersionId: Long,
        recruiterId: String?,
        writingStyle: String,
        companyName: String,
        roleTitle: String,
        content: String
    ): Long {
        val coverLetterId = coverLetterDao.insertCoverLetter(
            CoverLetter(
                resumeVersionId = resumeVersionId,
                jobId = jobId,
                recruiterId = recruiterId,
                company = companyName,
                role = roleTitle
            ).toEntity()
        )

        val version = CoverLetterVersion(
            coverLetterId = coverLetterId,
            versionName = "Initial Draft",
            writingStyle = writingStyle,
            sections = listOf(com.bangersoul.aivance.core.common.model.CoverLetterSection(sectionType = "BODY", title = "Body", content = content))
        )
        saveVersion(version)
        return coverLetterId
    }

    private fun buildPrompt(companyName: String, roleTitle: String, writingStyle: String): String =
        "Generate a $writingStyle cover letter for $companyName as $roleTitle."

    override suspend fun regenerateSection(versionId: Long, sectionType: String): CoreResult<Unit> = runCatchingCore {
        val versionEntity = coverLetterDao.getVersionById(versionId) ?: throw Exception("Version not found")
        val coverLetter = coverLetterDao.getCoverLetterById(versionEntity.coverLetterId) ?: throw Exception("Cover letter not found")

        val provider = providerManager.getBestProviderFor(ProviderCapability.AI.Chat) as? AIProvider
            ?: throw Exception("No AI provider available")

        val prompt = "Regenerate the $sectionType section for a cover letter at ${coverLetter.company} for the role ${coverLetter.role}. Keep it ${versionEntity.writingStyle}."
        val newContent = provider.generateText(prompt).getOrNull() ?: throw Exception("AI regeneration failed")

        val sections = coverLetterDao.getSectionsForVersion(versionId).firstOrNull() ?: emptyList()
        val updatedSections = if (sections.any { it.sectionType == sectionType }) {
            sections.map {
                if (it.sectionType == sectionType) it.copy(content = newContent) else it
            }
        } else {
             sections + com.bangersoul.aivance.core.database.model.CoverLetterSectionEntity(
                 id = 0,
                 versionId = versionId,
                 sectionType = sectionType,
                 title = sectionType.lowercase().replaceFirstChar { it.uppercase() },
                 content = newContent,
                 sectionOrder = sections.size
             )
        }

        saveVersion(versionEntity.toDomain(updatedSections))
        Unit
    }
}
