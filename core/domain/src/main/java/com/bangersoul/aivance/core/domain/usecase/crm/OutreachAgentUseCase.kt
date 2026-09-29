package com.bangersoul.aivance.core.domain.usecase.crm

import com.bangersoul.aivance.core.common.model.Recruiter
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.getOrNull
import com.bangersoul.aivance.core.common.result.runCatchingCore
import com.bangersoul.aivance.core.domain.usecase.UseCase
import com.bangersoul.aivance.core.domain.usecase.crm.FindRecruitersUseCase
import com.bangersoul.aivance.sdk.api.AIProvider
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import javax.inject.Inject

data class OutreachAgentRequest(
    val job: JobListing,
    val selectedRecruiter: Recruiter? = null,
    val userProfile: String
)

data class OutreachPlan(
    val recruiters: List<Recruiter>,
    val suggestedMessage: String,
    val bestContact: Recruiter?
)

class OutreachAgentUseCase @Inject constructor(
    private val findRecruitersUseCase: FindRecruitersUseCase,
    private val providerManager: ProviderManager
) : UseCase<OutreachAgentRequest, CoreResult<OutreachPlan>>() {

    override suspend operator fun invoke(input: OutreachAgentRequest): CoreResult<OutreachPlan> = runCatchingCore {
        val domain = extractDomain(input.job.company)
        val recruiters = findRecruitersUseCase(domain).getOrNull() 
            ?: throw Exception("Could not discover recruiters for ${input.job.company}")

        val bestRecruiter = input.selectedRecruiter ?: recruiters.find { 
            it.title?.contains("Technical", ignoreCase = true) == true || 
            it.title?.contains("Talent", ignoreCase = true) == true || 
            it.title?.contains("Engineering", ignoreCase = true) == true
        } ?: recruiters.firstOrNull()

        val message = generateTailoredMessage(input.job, bestRecruiter, input.userProfile)

        OutreachPlan(
            recruiters = recruiters,
            suggestedMessage = message,
            bestContact = bestRecruiter
        )
    }

    private fun extractDomain(companyName: String): String {
        return "${companyName.lowercase().replace(" ", "")}.com"
    }

    private suspend fun generateTailoredMessage(job: JobListing, recruiter: Recruiter?, profile: String): String {
        val provider = providerManager.getBestProviderFor(ProviderCapability.AI.Chat) as? AIProvider
            ?: return "Hi ${recruiter?.name ?: "there"}, I'm interested in the ${job.title} role!"

        val prompt = "You are an expert Career Agent. Draft a high-conversion, professional cold outreach message on LinkedIn. Target Recruiter: ${recruiter?.name ?: "Recruiter"}, ${recruiter?.title ?: "Talent Acquisition"}. Target Job: ${job.title} at ${job.company}. Candidate Profile: $profile. Guidelines: Be concise, mention a specific role detail, connect a core strength, and end with a clear CTA."

        return provider.generateText(prompt).getOrNull() ?: "Error generating message."
    }
}
