package com.bangersoul.aivance.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.bangersoul.aivance.core.database.model.ResumeEntity
import com.bangersoul.aivance.core.database.model.ResumeSectionEntity
import com.bangersoul.aivance.core.database.model.ResumeVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResumeDao {
    @Query("SELECT * FROM resumes ORDER BY lastModified DESC")
    fun getResumes(): Flow<List<ResumeEntity>>

    @Query("SELECT * FROM resumes WHERE id = :id")
    suspend fun getResumeById(id: Long): ResumeEntity?

    // @Upsert (not REPLACE): REPLACE deletes the conflicting parent row and
    // re-inserts it, firing the ON DELETE CASCADE that wipes every child
    // resume_versions row. That silently destroyed the freshly created
    // "Original Import" version whenever the primary-version id was written
    // back onto an existing resume (the in-wizard ATS scan then failed with
    // "Version not found"). @Upsert emits ON CONFLICT DO UPDATE — no delete,
    // no cascade.
    @Upsert
    suspend fun insertResume(resume: ResumeEntity): Long

    @Delete
    suspend fun deleteResume(resume: ResumeEntity)

    // Versions
    @Query("SELECT * FROM resume_versions WHERE resumeId = :resumeId ORDER BY lastModified DESC")
    fun getVersionsForResume(resumeId: Long): Flow<List<ResumeVersionEntity>>

    @Query("SELECT * FROM resume_versions WHERE id = :versionId")
    suspend fun getVersionById(versionId: Long): ResumeVersionEntity?

    // Same rationale as upsertResume: REPLACE on a version row would delete
    // it (cascading to its sections) before re-inserting.
    @Upsert
    suspend fun insertVersion(version: ResumeVersionEntity): Long

    @Delete
    suspend fun deleteVersion(version: ResumeVersionEntity)

    // Sections
    @Query("SELECT * FROM resume_sections WHERE versionId = :versionId ORDER BY sectionOrder ASC")
    fun getSectionsForVersion(versionId: Long): Flow<List<ResumeSectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<ResumeSectionEntity>)

    @Query("DELETE FROM resume_sections WHERE versionId = :versionId")
    suspend fun deleteSectionsForVersion(versionId: Long)

    @Transaction
    suspend fun updateVersionWithSections(version: ResumeVersionEntity, sections: List<ResumeSectionEntity>) {
        insertVersion(version)
        deleteSectionsForVersion(version.id)
        insertSections(sections)
    }
}
