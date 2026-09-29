package com.bangersoul.aivance.core.common.mapper

import com.bangersoul.aivance.core.common.dto.AiMessageDto
import com.bangersoul.aivance.core.common.dto.AnalyticsEventDto
import com.bangersoul.aivance.core.common.dto.ApifyJobScraperDto
import com.bangersoul.aivance.core.common.dto.CoverLetterDto
import com.bangersoul.aivance.core.common.dto.InterviewFeedbackDto
import com.bangersoul.aivance.core.common.dto.JobListingDto
import com.bangersoul.aivance.core.common.dto.ProviderInfoDto
import com.bangersoul.aivance.core.common.dto.SettingsDto
import com.bangersoul.aivance.core.common.dto.UserProfileDto
import com.bangersoul.aivance.core.common.enums.AIModel
import com.bangersoul.aivance.core.common.enums.JobType
import com.bangersoul.aivance.core.common.enums.MessageRole
import com.bangersoul.aivance.core.common.enums.ProviderState
import com.bangersoul.aivance.core.common.enums.ProviderType
import com.bangersoul.aivance.core.common.model.AIMessage
import com.bangersoul.aivance.core.common.model.AiProviderConfig
import com.bangersoul.aivance.core.common.model.AnalyticsEvent
import com.bangersoul.aivance.core.common.model.CoverLetter
import com.bangersoul.aivance.core.common.model.InterviewFeedback
import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.model.ProviderInfo
import com.bangersoul.aivance.core.common.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreMappersTest {


    fun coverLetter_dtoAndDomain_mappingIsBidirectional() {
        val dto = CoverLetterDto(
            company = "Google",
            role = "Staff Android Engineer",
            content = "Dear Hiring Manager...",
            tone = "CONFIDENT"
        )
        val domain = dto.toDomain(id = 12)
        assertEquals(12L, domain.id)
        assertEquals("Google", domain.company)
        assertEquals("Staff Android Engineer", domain.role)

        // Phase 6 restructured CoverLetter to versioned sections; content/tone live
        // at version level and are defaulted on round-trip. The identity fields survive.
        val mappedBack = domain.toDto()
        assertEquals(dto.company, mappedBack.company)
        assertEquals(dto.role, mappedBack.role)
        assertEquals("PROFESSIONAL", mappedBack.tone)
    }

    @Test
    fun interviewFeedback_dtoAndDomain_mappingIsBidirectional() {
        val dto = InterviewFeedbackDto(
            overallScore = 85,
            strengths = listOf("System Design", "Coroutines"),
            improvements = listOf("Concurrency edge cases"),
            detailedSummary = "Solid performance"
        )
        val domain = dto.toDomain()
        assertEquals(85, domain.overallScore)

        val mappedBack = domain.toDto()
        assertEquals(dto, mappedBack)
    }

    @Test
    fun apifyJobScraperDto_toDomain_mapsCorrectly() {
        val scraperDto = ApifyJobScraperDto(
            id = "apify_job_1",
            positionName = "Android Lead",
            companyName = "JetBrains",
            location = "Remote, Munich",
            salary = "$120k-$150k",
            url = "https://jetbrains.com/careers/1",
            description = "Lead Android development"
        )
        val domain = scraperDto.toDomain()
        assertEquals("apify_job_1", domain.id)
        assertEquals("Android Lead", domain.title)
        assertEquals("JetBrains", domain.company)
        assertEquals(true, domain.isRemote)
        assertEquals("APIFY", domain.sourceProvider)
    }

    @Test
    fun aiMessage_dtoAndDomain_mappingIsBidirectional() {
        val dto = AiMessageDto(
            id = "msg_1",
            conversationId = "conv_1",
            role = "ASSISTANT",
            content = "Hello! How can I help with your resume today?",
            timestamp = 1700000000L,
            tokenCount = 15
        )
        val domain = dto.toDomain()
        assertEquals(MessageRole.ASSISTANT, domain.role)

        val mappedBack = domain.toDto()
        assertEquals(dto, mappedBack)
    }

    @Test
    fun userProfile_dtoAndDomain_mappingIsBidirectional() {
        val dto = UserProfileDto(
            id = "user_42",
            fullName = "Jane Developer",
            email = "jane@example.com",
            phone = "+19876543210",
            targetRole = "Principal Engineer",
            skills = listOf("Kotlin", "Java", "C++"),
            experienceYears = 10
        )
        val domain = dto.toDomain()
        assertEquals("Jane Developer", domain.fullName)

        val mappedBack = domain.toDto()
        assertEquals(dto, mappedBack)
    }

    @Test
    fun providerInfo_dtoAndDomain_mappingIsBidirectional() {
        val dto = ProviderInfoDto(
            id = "GEMINI",
            name = "Google Gemini",
            type = "AI",
            state = "ACTIVE",
            supportedModels = listOf("GEMINI_1_5_FLASH", "GEMINI_1_5_PRO")
        )
        val domain = dto.toDomain()
        assertEquals(ProviderType.AI, domain.type)
        assertEquals(ProviderState.ACTIVE, domain.state)
        assertEquals(listOf(AIModel.GEMINI_1_5_FLASH, AIModel.GEMINI_1_5_PRO), domain.supportedModels)

        val mappedBack = domain.toDto()
        assertEquals(dto, mappedBack)
    }

    @Test
    fun analyticsEvent_dtoAndDomain_mappingIsBidirectional() {
        val dto = AnalyticsEventDto(
            id = "evt_100",
            eventName = "resume_uploaded",
            timestamp = 1700000000L,
            properties = mapOf("file_size" to "1024"),
            category = "RESUME"
        )
        val domain = dto.toDomain()
        assertEquals("resume_uploaded", domain.eventName)

        val mappedBack = domain.toDto()
        assertEquals(dto, mappedBack)
    }

    @Test
    fun jobListing_dtoAndDomain_mappingIsBidirectional() {
        // Only fields the mapper actually preserves participate in the round-trip;
        // salaryRange/jobType/currency/experienceLevel/remoteType are enrichment
        // fields not yet carried by the domain mapping.
        val dto = JobListingDto(
            id = "job_50",
            title = "Senior Android Dev",
            company = "Tech Inc",
            location = "New York, NY",
            isRemote = false,
            description = "Build amazing features",
            url = "https://techinc.com/job/50",
            sourceProvider = "APIFY",
            postedDate = 1700000000L,
            matchScore = 90
        )
        val domain = dto.toDomain()
        assertEquals(JobType.FULL_TIME, domain.jobType)

        val mappedBack = domain.toDto()
        assertEquals(dto, mappedBack)
    }

    @Test
    fun settings_dtoAndDomain_mappingIsBidirectional() {
        val dto = SettingsDto(
            providerId = "GROQ",
            apiKey = "gsk_dummy_key",
            selectedModel = "GROQ_LLAMA_3",
            temperature = 0.5f,
            maxTokens = 4096,
            customBaseUrl = null,
            isEnabled = true
        )
        val domain = dto.toDomain()
        assertEquals("GROQ", domain.providerId)

        val mappedBack = domain.toDto()
        assertEquals(dto, mappedBack)
    }
}
