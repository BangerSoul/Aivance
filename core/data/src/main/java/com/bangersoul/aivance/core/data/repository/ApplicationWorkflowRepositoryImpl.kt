package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.model.Application
import com.bangersoul.aivance.core.common.model.ApplicationStage
import com.bangersoul.aivance.core.common.model.ApplicationTask
import com.bangersoul.aivance.core.common.model.TimelineEvent
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.data.mapper.toDomain
import com.bangersoul.aivance.core.data.mapper.toEntity
import com.bangersoul.aivance.core.database.dao.JobDao
import com.bangersoul.aivance.core.database.dao.WorkflowDao
import com.bangersoul.aivance.core.database.model.ApplicationStageEntity
import com.bangersoul.aivance.core.domain.repository.ApplicationWorkflowRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplicationWorkflowRepositoryImpl @Inject constructor(
    private val workflowDao: WorkflowDao,
    private val jobDao: JobDao
) : ApplicationWorkflowRepository {

    override fun getApplications(): Flow<CoreResult<List<Application>>> {
        return workflowDao.getAllApplications().map { entities ->
            runCatchingCore {
                entities.map { entity ->
                    val job = jobDao.getJobWithDetailsById(entity.jobId)?.toDomain()
                    entity.toDomain(job = job)
                }
            }
        }
    }

    override fun getApplicationById(id: Long): Flow<CoreResult<Application>> {
        // Implementation for single application with full context (timeline, tasks)
        return workflowDao.getAllApplications().map { list ->
            runCatchingCore {
                val entity = list.find { it.id == id } ?: throw Exception("Application not found")
                val job = jobDao.getJobWithDetailsById(entity.jobId)?.toDomain()
                val timeline = workflowDao.getTimelineForApplication(id).firstOrNull() ?: emptyList()
                val tasks = workflowDao.getTasksForApplication(id).firstOrNull() ?: emptyList()

                entity.toDomain(
                    job = job,
                    timeline = timeline.map { it.toDomain() },
                    tasks = tasks.map { it.toDomain() }
                )
            }
        }
    }

    override suspend fun saveApplication(application: Application): CoreResult<Long> = runCatchingCore {
        workflowDao.insertApplication(application.toEntity())
    }

    override suspend fun deleteApplication(id: Long): CoreResult<Unit> = runCatchingCore {
        val entity = workflowDao.getApplicationById(id) ?: throw Exception("Not found")
        workflowDao.deleteApplication(entity)
    }

    override suspend fun updateNotes(applicationId: Long, notes: String): CoreResult<Unit> = runCatchingCore {
        val entity = workflowDao.getApplicationById(applicationId) ?: throw Exception("Application not found")
        workflowDao.updateApplication(entity.copy(notes = notes, lastModified = System.currentTimeMillis()))
    }

    override fun getStages(): Flow<CoreResult<List<ApplicationStage>>> = flow {
        // The pipeline kanban depends on the six system stages existing in
        // application_stages. They are only created by MIGRATION_16_17 for
        // upgrades; a fresh install seeds nothing, so the board would render
        // the empty/error state forever. Seed once on first access.
        seedDefaultStagesIfEmpty()
        emitAll(
            workflowDao.getStages().map { entities ->
                runCatchingCore { entities.map { it.toDomain() } }
            }
        )
    }

    override suspend fun addTimelineEvent(event: TimelineEvent): CoreResult<Long> = runCatchingCore {
        workflowDao.insertTimelineEvent(event.toEntity())
    }

    override suspend fun deleteTimelineEvent(id: Long): CoreResult<Unit> = runCatchingCore {
        workflowDao.deleteTimelineEvent(id)
    }

    override suspend fun addTask(task: ApplicationTask): CoreResult<Long> = runCatchingCore {
        workflowDao.insertTask(task.toEntity())
    }

    override suspend fun updateTask(task: ApplicationTask): CoreResult<Unit> = runCatchingCore {
        workflowDao.updateTask(task.toEntity())
    }

    /**
     * Inserts the six default kanban stages (Saved → Rejected) exactly once,
     * so a fresh install has a usable Pipeline board without a manual seed.
     */
    private suspend fun seedDefaultStagesIfEmpty() {
        val existing = workflowDao.getStages().firstOrNull().orEmpty()
        if (existing.isEmpty()) {
            DEFAULT_STAGES.forEach { stage ->
                workflowDao.insertStage(stage)
            }
        }
    }

    private companion object {
        val DEFAULT_STAGES = listOf(
            ApplicationStageEntity(id = "SAVED", label = "Saved", order = 1),
            ApplicationStageEntity(id = "PREPARING", label = "Preparing", order = 2),
            ApplicationStageEntity(id = "APPLIED", label = "Applied", order = 3),
            ApplicationStageEntity(id = "ASSESSMENT", label = "Assessment", order = 4),
            ApplicationStageEntity(id = "INTERVIEW", label = "Interview", order = 5),
            ApplicationStageEntity(id = "OFFER", label = "Offer", order = 6),
            ApplicationStageEntity(id = "REJECTED", label = "Rejected", order = 7),
            ApplicationStageEntity(id = "ARCHIVED", label = "Archived", order = 8)
        )
    }
}
