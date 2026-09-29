package com.bangersoul.aivance.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.bangersoul.aivance.core.database.model.CoverLetterEntity
import com.bangersoul.aivance.core.database.model.CoverLetterSectionEntity
import com.bangersoul.aivance.core.database.model.CoverLetterVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoverLetterDao {
    @Query("SELECT * FROM cover_letters ORDER BY dateCreated DESC")
    fun getCoverLetters(): Flow<List<CoverLetterEntity>>

    @Query("SELECT * FROM cover_letters WHERE id = :id")
    suspend fun getCoverLetterById(id: Long): CoverLetterEntity?

    // @Upsert (not REPLACE): REPLACE deletes the conflicting parent row and
    // re-inserts it, firing the ON DELETE CASCADE that wipes every child
    // cover_letter_versions row whenever an existing cover letter is resaved.
    @Upsert
    suspend fun insertCoverLetter(coverLetter: CoverLetterEntity): Long

    @Delete
    suspend fun deleteCoverLetter(coverLetter: CoverLetterEntity)

    // Versions
    @Query("SELECT * FROM cover_letter_versions WHERE coverLetterId = :coverLetterId ORDER BY lastModified DESC")
    fun getVersionsForCoverLetter(coverLetterId: Long): Flow<List<CoverLetterVersionEntity>>

    @Query("SELECT * FROM cover_letter_versions WHERE id = :versionId")
    suspend fun getVersionById(versionId: Long): CoverLetterVersionEntity?

    // Same rationale as insertCoverLetter — never delete-and-reinsert a row
    // that has CASCADE children.
    @Upsert
    suspend fun insertVersion(version: CoverLetterVersionEntity): Long

    @Delete
    suspend fun deleteVersion(version: CoverLetterVersionEntity)

    // Sections
    @Query("SELECT * FROM cover_letter_sections WHERE versionId = :versionId ORDER BY sectionOrder ASC")
    fun getSectionsForVersion(versionId: Long): Flow<List<CoverLetterSectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<CoverLetterSectionEntity>)

    @Query("DELETE FROM cover_letter_sections WHERE versionId = :versionId")
    suspend fun deleteSectionsForVersion(versionId: Long)

    @Transaction
    suspend fun updateVersionWithSections(version: CoverLetterVersionEntity, sections: List<CoverLetterSectionEntity>) {
        insertVersion(version)
        deleteSectionsForVersion(version.id)
        insertSections(sections)
    }
}
