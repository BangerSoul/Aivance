package com.bangersoul.aivance.core.domain.memory

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Explicit taxonomy of structured career memory entries.
 */
@Serializable
enum class CareerMemoryType {
    /** Explicit user-confirmed personal details or background credentials. */
    FACT,
    /** Candidate preferences regarding remote work, salary, culture, role. */
    PREFERENCE,
    /** Concrete empirical evidence demonstrating a skill. */
    SKILL_EVIDENCE,
    /** Identified technical or communication weakness in an interview session. */
    INTERVIEW_WEAKNESS,
    /** Validated technical or behavioral strength in an interview session. */
    INTERVIEW_STRENGTH,
    /** Target roles, milestone deadlines, and career objectives. */
    CAREER_GOAL,
    /** Longitudinal recurring patterns (e.g. repeated failure in system design questions). */
    CAREER_PATTERN,
    /** Decisions made by the candidate (e.g. rejected offer from company X). */
    CAREER_DECISION,
    /** Explicit user instructions directing AI agent behavior. */
    USER_INSTRUCTION
}

/**
 * Canonical Career Memory Entry.
 *
 * Enforces auditable evidence grounding and strictly distinguishes AI-generated inferences
 * from user-confirmed facts to prevent prompt hallucinations from corrupting persistent state.
 */
@Serializable
data class CareerMemoryEntry(
    val memoryId: String = "mem_${UUID.randomUUID()}",
    val type: CareerMemoryType,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val confidence: Float = 1.0f,
    val sourceEventIds: List<String> = emptyList(),
    val evidenceReferences: List<String> = emptyList(),
    val isUserConfirmed: Boolean = false,
    val expirationTimestamp: Long? = null
) {
    /**
     * Confirms an AI-generated inference as a permanent user-validated fact.
     */
    fun confirmByUser(): CareerMemoryEntry = copy(
        isUserConfirmed = true,
        confidence = 1.0f,
        updatedAt = System.currentTimeMillis()
    )

    val isExpired: Boolean
        get() = expirationTimestamp != null && System.currentTimeMillis() > expirationTimestamp
}
