package com.bangersoul.aivance.core.domain.workflow

import com.bangersoul.aivance.core.common.model.*
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.domain.events.CareerEventDispatcher
import com.bangersoul.aivance.core.domain.repository.AnalyticsRepository
import com.bangersoul.aivance.core.domain.repository.ApplicationWorkflowRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkflowEngine @Inject constructor(
    private val repository: ApplicationWorkflowRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val taskGenerator: com.bangersoul.aivance.core.domain.usecase.workflow.TaskGeneratorUseCase,
    private val careerEventDispatcher: CareerEventDispatcher
) {
    fun determineLifecycleStage(state: CareerState): CareerLifecycleStage {
        return when {
            state.profile.targetRole.isEmpty() -> CareerLifecycleStage.ONBOARDING
            state.intelligence.totalResumes == 0 -> CareerLifecycleStage.PREPARING
            state.intelligence.atsScore < 70 && state.intelligence.totalResumes > 0 -> CareerLifecycleStage.OPTIMIZING
            state.discovery.savedJobsCount < 5 -> CareerLifecycleStage.EXPLORING
            state.pipeline.activeApplications < 3 -> CareerLifecycleStage.APPLYING
            state.pipeline.upcomingInterviews.isNotEmpty() -> CareerLifecycleStage.INTERVIEWING
            else -> CareerLifecycleStage.STRATEGIZING
        }
    }

    suspend fun transitionApplicationTo(application: Application, nextStageId: String): CoreResult<Unit> = runCatchingCore {
        if (application.currentStageId == nextStageId) return@runCatchingCore

        val updated = application.copy(
            currentStageId = nextStageId,
            lastModified = System.currentTimeMillis()
        )
        repository.saveApplication(updated)

        repository.addTimelineEvent(
            TimelineEvent(
                applicationId = application.id,
                eventType = "STAGE_CHANGE",
                title = "Stage Changed",
                description = "Moved from ${application.currentStageId} to $nextStageId",
                metadata = mapOf("from" to application.currentStageId, "to" to nextStageId)
            )
        )

        taskGenerator(updated)

        analyticsRepository.createSnapshot()

        // The stage transition has been persisted successfully. Publish the
        // real domain event so the reactive V2 pipeline (CareerEventBus ->
        // CareerStateEngine) observes the mutation. Dispatch is fire-and-forget
        // and non-suspending, so it neither blocks nor swallows failures into
        // this operation, and it is only reached when the stage actually
        // changed (the no-op early return above emits nothing).
        careerEventDispatcher.onApplicationStageChanged(
            applicationId = application.id,
            oldStage = application.currentStageId,
            newStage = nextStageId
        )
    }

    suspend fun transitionTo(application: Application, nextStageId: String): CoreResult<Unit> =
        transitionApplicationTo(application, nextStageId)
}
