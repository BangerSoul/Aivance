package com.bangersoul.aivance.core.common.enums

import kotlinx.serialization.Serializable

@Serializable
enum class JobType {
    FULL_TIME,
    PART_TIME,
    CONTRACT,
    INTERNSHIP,
    APPRENTICESHIP,
    TEMPORARY,
    FREELANCE,
    OTHER
}

@Serializable
enum class RemoteType {
    ON_SITE,
    REMOTE,
    HYBRID,
    OTHER
}

/**
 * Company-level remote-work policy from the bundled remote-company catalog
 * (R-02). Mirrors the remoteintech/remote-jobs dataset values:
 * `fully-remote`, `remote-first`, `remote-friendly`, `hybrid`.
 *
 * Matching semantics: FULLY_REMOTE and REMOTE_FIRST are treated as the same
 * bucket (both mean the company is distributed-first), while REMOTE_FRIENDLY
 * and HYBRID match their exact policy.
 */
@Serializable
enum class RemotePolicy {
    FULLY_REMOTE,
    REMOTE_FIRST,
    REMOTE_FRIENDLY,
    HYBRID,
    UNKNOWN;

    companion object {
        fun fromDatasetString(raw: String?): RemotePolicy = when (raw?.trim()?.lowercase()) {
            "fully-remote" -> FULLY_REMOTE
            "remote-first" -> REMOTE_FIRST
            "remote-friendly" -> REMOTE_FRIENDLY
            "hybrid" -> HYBRID
            else -> UNKNOWN
        }
    }
}

@Serializable
enum class EmploymentType {
    FULL_TIME,
    PART_TIME,
    CONTRACT,
    INTERNSHIP,
    APPRENTICESHIP,
    TEMPORARY,
    FREELANCE,
    OTHER
}

@Serializable
enum class ExperienceLevel {
    ENTRY_LEVEL,
    MID_LEVEL,
    SENIOR_LEVEL,
    EXECUTIVE,
    NOT_SPECIFIED
}

@Serializable
enum class JobSortOrder {
    RELEVANCE,
    DATE_DESC,
    SALARY_DESC,
    COMPANY_ASC
}

@Serializable
enum class ResumeStatus {
    DRAFT,
    PARSED,
    ANALYZED,
    ARCHIVED,
    PRIMARY
}

@Serializable
enum class ProviderState {
    ACTIVE,
    INACTIVE,
    DEPRECATED,
    UNCONFIGURED,
    ERROR
}

@Serializable
enum class ProviderType {
    AI,
    JOB_SCRAPER,
    STORAGE,
    ANALYTICS
}

@Serializable
enum class AIModel {
    GEMINI_1_5_FLASH,
    GEMINI_1_5_PRO,
    GPT_4O,
    GPT_4O_MINI,
    CLAUDE_3_5_SONNET,
    GROQ_LLAMA_3,
    OLLAMA_LOCAL
}

@Serializable
enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Serializable
enum class Language {
    ENGLISH,
    SPANISH,
    FRENCH,
    GERMAN,
    CHINESE,
    JAPANESE
}

@Serializable
enum class ApplicationStatus {
    SAVED,
    APPLIED,
    INTERVIEWING,
    OFFER,
    REJECTED,
    WITHDRAWN
}

@Serializable
enum class PermissionState {
    GRANTED,
    DENIED,
    NOT_REQUESTED,
    RATIONALE_REQUIRED
}

@Serializable
enum class LetterTone {
    PROFESSIONAL,
    ENTHUSIASTIC,
    CONFIDENT,
    CREATIVE
}

@Serializable
enum class InterviewDifficulty {
    EASY,
    MEDIUM,
    HARD
}

@Serializable
enum class MessageSender {
    USER,
    AI_INTERVIEWER
}
