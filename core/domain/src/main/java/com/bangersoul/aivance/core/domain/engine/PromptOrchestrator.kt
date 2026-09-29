package com.bangersoul.aivance.core.domain.engine

import com.bangersoul.aivance.core.common.model.AssistantJobContext
import com.bangersoul.aivance.core.common.model.CareerState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Constructs complex, multi-modal prompts by combining context, history, and system instructions.
 */
@Singleton
class PromptOrchestrator @Inject constructor(
    private val contextEngine: ContextEngine
) {

    fun buildCopilotPrompt(
        userMessage: String,
        state: CareerState,
        intent: CareerIntent,
        jobContext: AssistantJobContext? = null
    ): String {
        val systemContext = contextEngine.generateAssistantContext(state)
        val instruction = getInstructionForIntent(intent)
        val jobBlock = jobContext?.let { ctx ->
            buildJobContextBlock(ctx)
        }.orEmpty()

        return """
            $instruction

            USER CONTEXT:
            $systemContext

            USER INTENT: $intent

            $jobBlock
            USER MESSAGE:
            $userMessage

            Provide a helpful, actionable response. If the user needs to take a specific action (like optimizing their resume or searching for jobs), clearly state it.
        """.trimIndent()
    }

    /** Renders the job the user is currently looking at so answers stay on-target. */
    private fun buildJobContextBlock(ctx: AssistantJobContext): String {
        return buildString {
            append("CURRENT JOB CONTEXT:\n")
            append("Title: ").append(ctx.title).append('\n')
            append("Company: ").append(ctx.company).append('\n')
            if (!ctx.description.isNullOrBlank()) {
                append("Job Description:\n").append(ctx.description).append('\n')
            }
        }
    }

    private fun getInstructionForIntent(intent: CareerIntent): String {
        return when (intent) {
            CareerIntent.RESUME_HELP -> "You are a Professional Resume Writer. Help the user improve their resume content, formatting, and impact."
            CareerIntent.ATS_OPTIMIZATION -> "You are an ATS Expert. Explain how Applicant Tracking Systems work and help the user optimize their keywords for specific roles."
            CareerIntent.JOB_SEARCH -> "You are a Career Discovery Agent. Help the user find the best opportunities matching their skills and goals."
            CareerIntent.RECRUITER_DISCOVERY -> "You are a Networking Coach. Help the user identify and reach out to the right people at their target companies."
            CareerIntent.COVER_LETTER_GEN -> "You are a Persuasive Writer. Help the user craft a compelling cover letter that highlights their unique value."
            CareerIntent.INTERVIEW_PRACTICE -> "You are an Interview Coach. Conduct mock interviews and provide granular feedback on communication and technical depth."
            CareerIntent.APPLICATION_FOLLOWUP -> "You are a Professional Assistant. Help the user manage their application status and draft effective follow-up messages."
            CareerIntent.CAREER_STRATEGY -> "You are a Career Strategist. Help the user define their long-term goals and build a roadmap to achieve them."
            else -> "You are AiVance, an expert AI Career Copilot. Help the user with any aspect of their career journey."
        }
    }
}
