package com.bangersoul.aivance.core.common.model

import com.bangersoul.aivance.core.common.enums.AIModel
import com.bangersoul.aivance.core.common.enums.ApplicationStatus
import com.bangersoul.aivance.core.common.enums.EmploymentType
import com.bangersoul.aivance.core.common.enums.ExperienceLevel
import com.bangersoul.aivance.core.common.enums.InterviewDifficulty
import com.bangersoul.aivance.core.common.enums.JobSortOrder
import com.bangersoul.aivance.core.common.enums.JobType
import com.bangersoul.aivance.core.common.enums.LetterTone
import com.bangersoul.aivance.core.common.enums.MessageRole
import com.bangersoul.aivance.core.common.enums.MessageSender
import com.bangersoul.aivance.core.common.enums.ProviderState
import com.bangersoul.aivance.core.common.enums.ProviderType
import com.bangersoul.aivance.core.common.enums.RemotePolicy
import com.bangersoul.aivance.core.common.enums.RemoteType
import com.bangersoul.aivance.core.common.enums.ResumeStatus
import kotlinx.serialization.Serializable

@Serializable
data class Resume(
    val id: Long = 0,
    val name: String,
    val primaryVersionId: Long? = null,
    val fileName: String? = null,
    val fileUri: String? = null,
    val rawText: String? = null,
    val dateCreated: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val versions: List<ResumeVersion> = emptyList()
)

@Serializable
data class ResumeVersion(
    val id: Long = 0,
    val resumeId: Long,
    val versionName: String,
    val templateId: String = "modern",
    val lastModified: Long = System.currentTimeMillis(),
    val sections: List<ResumeSection> = emptyList()
)

@Serializable
data class ResumeSection(
    val id: Long = 0,
    val versionId: Long = 0,
    val sectionType: String,
    val title: String,
    val content: String,
    val sectionOrder: Int = 0
)

@Serializable
data class AtsReport(
    val id: Long = 0,
    val resumeVersionId: Long,
    val jobDescriptionId: Long,
    val overallScore: Int,
    val matchPercentage: Int,
    val matchedKeywords: List<String> = emptyList(),
    val missingKeywords: List<String> = emptyList(),
    val sectionScores: Map<String, Int> = emptyMap(),
    val optimizationTips: List<OptimizationTip> = emptyList(),
    val dateGenerated: Long = System.currentTimeMillis()
)

@Serializable
data class OptimizationTip(
    val category: String,
    val description: String,
    val priority: String = "MEDIUM" // "HIGH", "MEDIUM", "LOW"
)

@Serializable
data class JobDescription(
    val id: Long = 0,
    val companyName: String? = null,
    val jobTitle: String? = null,
    val rawText: String,
    val extractedSkills: List<String> = emptyList(),
    val dateCreated: Long = System.currentTimeMillis()
)


@Serializable
data class CoverLetter(
    val id: Long = 0,
    val resumeVersionId: Long?,
    val jobId: Long?,
    val recruiterId: String?,
    val primaryVersionId: Long? = null,
    val company: String,
    val role: String,
    val dateCreated: Long = System.currentTimeMillis(),
    val versions: List<CoverLetterVersion> = emptyList()
)

@Serializable
data class CoverLetterVersion(
    val id: Long = 0,
    val coverLetterId: Long,
    val versionName: String,
    val templateId: String = "modern",
    val writingStyle: String = "PROFESSIONAL",
    val state: String = "DRAFT",
    val lastModified: Long = System.currentTimeMillis(),
    val sections: List<CoverLetterSection> = emptyList()
)

@Serializable
data class CoverLetterSection(
    val id: Long = 0,
    val versionId: Long = 0,
    val sectionType: String,
    val title: String,
    val content: String,
    val sectionOrder: Int = 0
)

@Serializable
data class InterviewSession(
    val id: String,
    val resumeVersionId: Long? = null,
    val jobId: Long? = null,
    val targetRole: String,
    val type: String = "BEHAVIORAL",
    val companyName: String = "",
    val difficulty: InterviewDifficulty = InterviewDifficulty.MEDIUM,
    val messages: List<InterviewMessage> = emptyList(),
    val questions: List<InterviewQuestion> = emptyList(),
    val feedback: InterviewFeedback? = null,
    val startTime: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false
)

@Serializable
data class InterviewQuestion(
    val id: Long = 0,
    val text: String,
    val category: String,
    val difficulty: String,
    val expectedKeyPoints: List<String> = emptyList(),
    val idealAnswer: String? = null,
    val isFavorite: Boolean = false
)

@Serializable
data class InterviewMessage(
    val id: String,
    val sessionId: String,
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val evaluation: InterviewEvaluation? = null
)

@Serializable
data class InterviewEvaluation(
    val id: Long = 0,
    val messageId: String,
    val scoreClarity: Int,
    val scoreAccuracy: Int,
    val scoreTone: Int,
    val starMethodScore: Int? = null,
    val feedback: String,
    val improvementTips: List<String> = emptyList()
)

@Serializable
data class InterviewFeedback(
    val overallScore: Int,
    val strengths: List<String> = emptyList(),
    val improvements: List<String> = emptyList(),
    val detailedSummary: String = ""
)

/**
 * Minimal job context handed to the AI Assistant when it is surfaced from a
 * job-related screen (saved jobs, job details), so answers can be tailored to
 * the specific role the user is looking at.
 */
@Serializable
data class AssistantJobContext(
    val jobId: String,
    val title: String,
    val company: String,
    val description: String? = null
)

@Serializable
data class JobListing(
    val id: String,
    val title: String,
    val company: String,
    val companyLogoUrl: String? = null,
    val location: String = "",
    val salaryMin: Double? = null,
    val salaryMax: Double? = null,
    val currency: String? = "USD",
    val salaryRange: String? = null,
    val employmentType: EmploymentType = EmploymentType.FULL_TIME,
    val experienceLevel: ExperienceLevel = ExperienceLevel.NOT_SPECIFIED,
    val remoteType: RemoteType = RemoteType.ON_SITE,
    val jobType: JobType = JobType.FULL_TIME,
    val isRemote: Boolean = false,
    val description: String,
    val descriptionHtml: String? = null,
    val url: String,
    val sourceUrl: String? = null,
    val sourceProvider: String,
    val postedDate: Long = System.currentTimeMillis(),
    val matchScore: Int? = null
)

@Serializable
data class Company(
    val id: String,
    val name: String,
    val domain: String? = null,
    val logoUrl: String? = null,
    val websiteUrl: String? = null,
    val location: String = "",
    val industry: String = "",
    val description: String = "",
    val headquarters: String? = null,
    val socialLinks: Map<String, String> = emptyMap()
)

@Serializable
data class Recruiter(
    val id: String,
    val name: String,
    val companyId: String,
    val title: String? = null,
    val department: String? = null,
    val linkedinUrl: String? = null,
    val contacts: List<RecruiterContact> = emptyList()
)

@Serializable
data class RecruiterContact(
    val id: String,
    val recruiterId: String,
    val email: String,
    val confidence: Int = 0,
    val isVerified: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Serializable
data class OutreachDraft(
    val id: Long = 0,
    val recruiterId: String,
    val jobId: String,
    val type: String, // "COLD_EMAIL", "LINKEDIN_REQUEST", etc.
    val content: String,
    val dateCreated: Long = System.currentTimeMillis()
)

@Serializable
data class CommunicationHistory(
    val id: Long = 0,
    val recruiterId: String,
    val messageType: String,
    val content: String,
    val sentDate: Long,
    val status: String, // "DRAFT", "SENT", "RESPONDED"
    val notes: String? = null
)

@Serializable
data class JobApplication(
    val id: Long = 0,
    val company: String,
    val role: String,
    val status: ApplicationStatus = ApplicationStatus.SAVED,
    val dateApplied: Long = System.currentTimeMillis(),
    val salaryRange: String = "",
    val notes: String = "",
    val lastModified: Long = System.currentTimeMillis()
)

@Serializable
data class UserProfile(
    val id: String = "user_default",
    val fullName: String,
    val email: String,
    val phone: String = "",
    val targetRole: String = "",
    val currentRole: String = "",
    val company: String = "",
    val linkedinUrl: String = "",
    val githubUrl: String = "",
    val dateOfBirth: Long? = null,
    val profilePictureUrl: String? = null,
    val bio: String = "",
    val location: String = "",
    val skills: List<String> = emptyList(),
    val experienceYears: Int = 0,
    val preferredIndustries: List<String> = emptyList(),
    val salaryExpectation: String = "",
    val workPreference: String = "REMOTE",
    val visaRequired: Boolean = false,
    val noticePeriod: String = "",
    val createdDate: Long = System.currentTimeMillis()
)

@Serializable
data class CareerRoadmap(
    val id: Long = 0,
    val targetRole: String,
    val currentLevel: String,
    val description: String,
    val steps: List<RoadmapStep> = emptyList()
)

@Serializable
data class RoadmapStep(
    val id: Long = 0,
    val roadmapId: Long = 0,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val stepOrder: Int = 0
)

@Serializable
data class AIConversation(
    val id: String,
    val title: String,
    val providerId: String,
    val modelName: String,
    val createdDate: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val messages: List<AIMessage> = emptyList()
)

@Serializable
data class AIMessage(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tokenCount: Int = 0
)

@Serializable
data class ProviderInfo(
    val id: String,
    val name: String,
    val type: ProviderType,
    val state: ProviderState = ProviderState.UNCONFIGURED,
    val supportedModels: List<AIModel> = emptyList(),
    val capabilities: List<ProviderCapability> = emptyList()
)

@Serializable
data class ProviderCapability(
    val name: String,
    val isSupported: Boolean = true,
    val maxContextTokens: Int = 8192,
    val supportsStreaming: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsJsonOutput: Boolean = true
)

@Serializable
data class JobSearchFilter(
    val query: String = "",
    val location: String = "",
    /** Structured location — free-text [location] takes precedence when set. */
    val country: String = "",
    val state: String = "",
    val city: String = "",
    val remoteType: RemoteType? = null,
    val employmentTypes: List<EmploymentType> = emptyList(),
    val experienceLevels: List<ExperienceLevel> = emptyList(),
    /** Numeric experience bounds (years). Null = no bound. */
    val minExperienceYears: Int? = null,
    val maxExperienceYears: Int? = null,
    val minSalary: Double? = null,
    val maxSalary: Double? = null,
    val currency: String = "USD",
    /**
     * Company-level remote policy (R-02) — only listings from catalog companies
     * with a matching policy pass. Null = no catalog policy constraint.
     */
    val remotePolicy: RemotePolicy? = null,
    /**
     * Company tech-stack terms (R-02, lowercased) — only listings from catalog
     * companies whose technologies intersect pass. Empty = no constraint.
     */
    val technologies: List<String> = emptyList(),
    /**
     * Whitelist keywords (R-07) — the listing must contain ALL of these in its
     * title, company or description. Empty = no constraint.
     */
    val includedKeywords: List<String> = emptyList(),
    /**
     * Blacklist keywords (R-07) — the listing must contain NONE of these in its
     * title, company or description (e.g. "unpaid", "commission-only", "senior").
     * Empty = no constraint.
     */
    val excludedKeywords: List<String> = emptyList()
) {
    /** Combines the structured location parts into a single human-readable string. */
    val structuredLocation: String
        get() = listOf(city, state, country).filter { it.isNotBlank() }.joinToString(", ")

    /** True when any structured location dimension is set. */
    val hasStructuredLocation: Boolean
        get() = country.isNotBlank() || state.isNotBlank() || city.isNotBlank()
}

@Serializable
data class SearchFilter(
    val keywords: String = "",
    val location: String = "",
    val isRemote: Boolean = false,
    val jobType: JobType? = null,
    val minSalary: Int? = null,
    val maxSalary: Int? = null,
    val sourceProvider: String? = null
)

@Serializable
data class SearchResult(
    val id: String,
    val query: String,
    val totalResults: Int,
    val page: Int,
    val items: List<JobListing> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AnalyticsEvent(
    val id: String,
    val eventName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val properties: Map<String, String> = emptyMap(),
    val category: String = "GENERAL"
)

@Serializable
data class AiProviderConfig(
    val providerId: String,
    val apiKey: String,
    val selectedModel: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val customBaseUrl: String? = null,
    val isEnabled: Boolean = true
)

@Serializable
data class JobScraperConfig(
    val providerId: String,
    val apifyToken: String,
    val activeActorId: String,
    val syncIntervalHours: Int = 24,
    val cacheRetentionDays: Int = 7
)
