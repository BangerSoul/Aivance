package com.bangersoul.aivance.core.common.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CoverLetterDto(
    @SerialName("company") val company: String,
    @SerialName("role") val role: String,
    @SerialName("content") val content: String,
    @SerialName("tone") val tone: String = "PROFESSIONAL"
)

@Serializable
data class InterviewFeedbackDto(
    @SerialName("overall_score") val overallScore: Int,
    @SerialName("strengths") val strengths: List<String> = emptyList(),
    @SerialName("improvements") val improvements: List<String> = emptyList(),
    @SerialName("detailed_summary") val detailedSummary: String = ""
)

@Serializable
data class ApifyJobScraperDto(
    @SerialName("id") val id: String,
    @SerialName("position_name") val positionName: String,
    @SerialName("company_name") val companyName: String,
    @SerialName("location") val location: String = "",
    @SerialName("salary") val salary: String? = null,
    @SerialName("url") val url: String,
    @SerialName("posted_at") val postedAt: String? = null,
    @SerialName("description") val description: String = ""
)

@Serializable
data class AiMessageDto(
    @SerialName("id") val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("role") val role: String,
    @SerialName("content") val content: String,
    @SerialName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerialName("token_count") val tokenCount: Int = 0
)

@Serializable
data class UserProfileDto(
    @SerialName("id") val id: String = "user_default",
    @SerialName("full_name") val fullName: String,
    @SerialName("email") val email: String,
    @SerialName("phone") val phone: String = "",
    @SerialName("target_role") val targetRole: String = "",
    @SerialName("bio") val bio: String = "",
    @SerialName("location") val location: String = "",
    @SerialName("skills") val skills: List<String> = emptyList(),
    @SerialName("experience_years") val experienceYears: Int = 0,
    @SerialName("created_date") val createdDate: Long = System.currentTimeMillis()
)

@Serializable
data class ProviderInfoDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("type") val type: String,
    @SerialName("state") val state: String = "UNCONFIGURED",
    @SerialName("supported_models") val supportedModels: List<String> = emptyList()
)

@Serializable
data class AnalyticsEventDto(
    @SerialName("id") val id: String,
    @SerialName("event_name") val eventName: String,
    @SerialName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerialName("properties") val properties: Map<String, String> = emptyMap(),
    @SerialName("category") val category: String = "GENERAL"
)

@Serializable
data class JobListingDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("company") val company: String,
    @SerialName("company_logo_url") val companyLogoUrl: String? = null,
    @SerialName("location") val location: String = "",
    @SerialName("salary_min") val salaryMin: Double? = null,
    @SerialName("salary_max") val salaryMax: Double? = null,
    @SerialName("currency") val currency: String? = "USD",
    @SerialName("salary_range") val salaryRange: String? = null,
    @SerialName("employment_type") val employmentType: String = "FULL_TIME",
    @SerialName("experience_level") val experienceLevel: String = "NOT_SPECIFIED",
    @SerialName("remote_type") val remoteType: String = "ON_SITE",
    @SerialName("job_type") val jobType: String = "FULL_TIME",
    @SerialName("is_remote") val isRemote: Boolean = false,
    @SerialName("description") val description: String = "",
    @SerialName("description_html") val descriptionHtml: String? = null,
    @SerialName("url") val url: String,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("source_provider") val sourceProvider: String = "APIFY",
    @SerialName("posted_date") val postedDate: Long = System.currentTimeMillis(),
    @SerialName("match_score") val matchScore: Int? = null
)

@Serializable
data class SettingsDto(
    @SerialName("provider_id") val providerId: String,
    @SerialName("api_key") val apiKey: String,
    @SerialName("selected_model") val selectedModel: String,
    @SerialName("temperature") val temperature: Float = 0.7f,
    @SerialName("max_tokens") val maxTokens: Int = 2048,
    @SerialName("custom_base_url") val customBaseUrl: String? = null,
    @SerialName("is_enabled") val isEnabled: Boolean = true
)


// Request Models
@Serializable
data class CoverLetterRequest(
    @SerialName("resume_text") val resumeText: String,
    @SerialName("job_description") val jobDescription: String,
    @SerialName("tone") val tone: String = "PROFESSIONAL"
)

@Serializable
data class InterviewPromptRequest(
    @SerialName("role") val role: String,
    @SerialName("company") val company: String,
    @SerialName("difficulty") val difficulty: String,
    @SerialName("conversation_history") val history: List<AiMessageDto> = emptyList()
)

@Serializable
data class JobSearchQueryRequest(
    @SerialName("keywords") val keywords: String,
    @SerialName("location") val location: String = "",
    @SerialName("is_remote") val isRemote: Boolean = false,
    @SerialName("page") val page: Int = 1,
    @SerialName("limit") val limit: Int = 20
)

// Response Models
@Serializable
data class PageResponse<T>(
    @SerialName("items") val items: List<T> = emptyList(),
    @SerialName("page") val page: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_items") val totalItems: Int = items.size,
    @SerialName("has_next") val hasNext: Boolean = false
)

@Serializable
data class HealthCheckResponse(
    @SerialName("provider_id") val providerId: String,
    @SerialName("is_healthy") val isHealthy: Boolean,
    @SerialName("latency_ms") val latencyMs: Long,
    @SerialName("error_message") val errorMessage: String? = null
)
