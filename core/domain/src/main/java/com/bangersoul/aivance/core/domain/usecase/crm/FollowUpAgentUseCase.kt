package com.bangersoul.aivance.core.domain.usecase.crm

import com.bangersoul.aivance.core.common.model.Application
import com.bangersoul.aivance.core.common.model.ApplicationTask
import com.bangersoul.aivance.core.common.model.TimelineEvent
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.domain.repository.ApplicationWorkflowRepository
import com.bangersoul.aivance.core.domain.usecase.UseCase
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class FollowUpRequest(
    val application: Application,
    val userTone: String = "Professional"
)

data class FollowUpPlan(
    val isDue: Boolean,
    val suggestedMessage: String?,
    val recommendedWaitDays: Int,
    val taskId: Long? = null
)

class FollowUpAgentUseCase @Inject constructor(
    private val workflowRepository: ApplicationWorkflowRepository,
    private val providerManager: ProviderManager
) : UseCase<FollowUpRequest, CoreResult<FollowUpPlan>>() {

    override suspend operator fun invoke(input: FollowUpRequest): CoreResult<FollowUpPlan> = runCatchingCore {
        val app = input.application
        val lastActivity = app.lastModified
        val daysSinceActivity = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastActivity).toInt()

        val threshold = if (app.currentStageId == "INTERVIEW") 3 else 5
        
        if (daysSinceActivity < threshold) {
            return@runCatchingCore FollowUpPlan(isDue = false, suggestedMessage = null, recommendedWaitDays = threshold - daysSinceActivity)
        }

        val message = generateFollowUpMessage(app, input.userTone)

        val task = ApplicationTask(
            applicationId = app.id,
            title = "Follow up on ${app.job?.company ?: "Application"}",
            description = "The AI suggests following up now. Suggested message: $message",
            priority = "HIGH",
            dueDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)
        )
        val taskId = workflowRepository.addTask(task).getOrNull()

        FollowUpPlan(
            isDue = true,
            suggestedMessage = message,
            recommendedWaitDays = 0,
            taskId = taskId
        )
    }

    private suspend fun generateFollowUpMessage(app: Application, tone: String): String {
        val provider = providerManager.getBestProviderFor(ProviderCapability.AI.Chat) as? AIProvider
            ?: return "Hi, I'm checking in on my application for the ${app.job?.title} role."

        val prompt = "You are a professional career agent. Draft a polite follow-up message for a job application. Context: Company ${app.job?.company}, Role ${app.job?.title}, Stage ${app.currentStageId}, Tone $tone. Goal: Get a status update without sounding desperate. Keep it extremely concise."

        return provider.generateText(prompt).getOrNull() ?: "Error generating message."
    }
}
