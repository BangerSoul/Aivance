package com.bangersoul.aivance.core.data.source

import com.bangersoul.aivance.core.common.model.Resume
import com.bangersoul.aivance.core.common.model.ResumeVersion
import com.bangersoul.aivance.core.data.mapper.toDomain
import com.bangersoul.aivance.core.data.mapper.toEntity
import com.bangersoul.aivance.core.database.dao.AtsDao
import com.bangersoul.aivance.core.database.dao.ResumeDao
import com.bangersoul.aivance.core.database.model.ResumeSectionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface ResumeLocalDataSource {
    fun getResumes(): Flow<List<Resume>>
    suspend fun getResumeById(id: Long): Resume?
    suspend fun saveResume(resume: Resume): Long
    suspend fun deleteResume(resume: Resume)

    fun getVersionsForResume(resumeId: Long): Flow<List<ResumeVersion>>
    suspend fun saveVersion(version: ResumeVersion): Long
    suspend fun deleteVersion(version: ResumeVersion)
}

class ResumeLocalDataSourceImpl @Inject constructor(
    private val resumeDao: ResumeDao,
    private val atsDao: AtsDao
) : ResumeLocalDataSource {

    override fun getResumes(): Flow<List<Resume>> {
        return resumeDao.getResumes().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getResumeById(id: Long): Resume? {
        val entity = resumeDao.getResumeById(id) ?: return null
        val versions = resumeDao.getVersionsForResume(id).first().map { vEntity ->
            val sections = resumeDao.getSectionsForVersion(vEntity.id).first()
            vEntity.toDomain(sections)
        }
        return entity.toDomain(versions)
    }

    override suspend fun saveResume(resume: Resume): Long {
        return resumeDao.insertResume(resume.toEntity())
    }

    override suspend fun deleteResume(resume: Resume) {
        resumeDao.deleteResume(resume.toEntity())
    }

    override fun getVersionsForResume(resumeId: Long): Flow<List<ResumeVersion>> {
        return resumeDao.getVersionsForResume(resumeId).map { versions ->
            versions.map { v ->
                // Load the sections for each version so callers (the Resume
                // Engine's import step in particular) receive a fully hydrated
                // version instead of an empty-shell Preview.
                v.toDomain(resumeDao.getSectionsForVersion(v.id).first())
            }
        }
    }

    override suspend fun saveVersion(version: ResumeVersion): Long {
        // Insert the version row first and capture the real (auto-generated)
        // id when this is a brand-new version, then write the sections under
        // THAT id. The previous path passed the pre-insert id (0) to
        // updateVersionWithSections, orphaning every section on a new version.
        val id = if (version.id == 0L) {
            resumeDao.insertVersion(version.toEntity())
        } else {
            resumeDao.insertVersion(version.toEntity())
            version.id
        }
        resumeDao.deleteSectionsForVersion(id)
        resumeDao.insertSections(version.sections.map { it.toEntity(id) })
        return id
    }

    override suspend fun deleteVersion(version: ResumeVersion) {
        resumeDao.deleteVersion(version.toEntity())
    }
}

