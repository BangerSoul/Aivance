package com.bangersoul.aivance.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bangersoul.aivance.core.database.model.AtsReportEntity
import com.bangersoul.aivance.core.database.model.JobDescriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AtsDao {

    // Modern ATS methods
    @Query("SELECT * FROM ats_reports ORDER BY dateGenerated DESC")
    fun getAllReports(): Flow<List<AtsReportEntity>>

    @Query("SELECT * FROM ats_reports WHERE resumeVersionId = :versionId ORDER BY dateGenerated DESC")
    fun getReportsForVersion(versionId: Long): Flow<List<AtsReportEntity>>

    @Query("SELECT * FROM ats_reports WHERE id = :id")
    suspend fun getReportById(id: Long): AtsReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: AtsReportEntity): Long

    @Delete
    suspend fun deleteReport(report: AtsReportEntity)

    // Job Descriptions
    @Query("SELECT * FROM job_descriptions WHERE id = :id")
    suspend fun getJobDescriptionById(id: Long): JobDescriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobDescription(jd: JobDescriptionEntity): Long
}
