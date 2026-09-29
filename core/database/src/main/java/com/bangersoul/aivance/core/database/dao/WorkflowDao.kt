package com.bangersoul.aivance.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.bangersoul.aivance.core.database.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkflowDao {

    // Applications
    @Query("SELECT * FROM applications ORDER BY lastModified DESC")
    fun getAllApplications(): Flow<List<ApplicationEntity>>

    @Query("SELECT * FROM applications WHERE id = :id")
    suspend fun getApplicationById(id: Long): ApplicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApplication(application: ApplicationEntity): Long

    @Update
    suspend fun updateApplication(application: ApplicationEntity)

    @Delete
    suspend fun deleteApplication(application: ApplicationEntity)

    // Stages
    @Query("SELECT * FROM application_stages ORDER BY `order` ASC")
    fun getStages(): Flow<List<ApplicationStageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStage(stage: ApplicationStageEntity)

    // Timeline
    @Query("SELECT * FROM application_timeline WHERE applicationId = :applicationId ORDER BY timestamp DESC")
    fun getTimelineForApplication(applicationId: Long): Flow<List<ApplicationTimelineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimelineEvent(event: ApplicationTimelineEntity): Long

    @Query("DELETE FROM application_timeline WHERE id = :id")
    suspend fun deleteTimelineEvent(id: Long)

    // Tasks
    @Query("SELECT * FROM application_tasks WHERE applicationId = :applicationId ORDER BY dueDate ASC")
    fun getTasksForApplication(applicationId: Long): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    // Automation Rules
    @Query("SELECT * FROM automation_rules WHERE isEnabled = 1")
    fun getActiveRules(): Flow<List<AutomationRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AutomationRuleEntity): Long
}
