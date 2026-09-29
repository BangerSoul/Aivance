package com.bangersoul.aivance.core.domain.repository

import com.bangersoul.aivance.core.common.model.Application
import com.bangersoul.aivance.core.common.model.ApplicationStage
import com.bangersoul.aivance.core.common.model.ApplicationTask
import com.bangersoul.aivance.core.common.model.TimelineEvent
import com.bangersoul.aivance.core.common.result.CoreResult
import kotlinx.coroutines.flow.Flow

interface ApplicationWorkflowRepository {
    fun getApplications(): Flow<CoreResult<List<Application>>>
    fun getApplicationById(id: Long): Flow<CoreResult<Application>>
    suspend fun saveApplication(application: Application): CoreResult<Long>
    suspend fun deleteApplication(id: Long): CoreResult<Unit>
    suspend fun updateNotes(applicationId: Long, notes: String): CoreResult<Unit>

    fun getStages(): Flow<CoreResult<List<ApplicationStage>>>

    suspend fun addTimelineEvent(event: TimelineEvent): CoreResult<Long>
    suspend fun deleteTimelineEvent(id: Long): CoreResult<Unit>
    suspend fun addTask(task: ApplicationTask): CoreResult<Long>
    suspend fun updateTask(task: ApplicationTask): CoreResult<Unit>
}
