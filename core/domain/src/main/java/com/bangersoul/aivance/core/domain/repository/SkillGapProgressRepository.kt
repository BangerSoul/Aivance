package com.bangersoul.aivance.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * How far the user has moved toward closing a specific skill gap.
 *
 * This tracks *intent/engagement* signals, not authoritative skill possession.
 * Whether a skill is actually demonstrated is still derived from the Career
 * Knowledge Graph (the profile's `HAS_SKILL` edges). This repository only
 * records that the user acted on a gap — so the dashboard can show momentum
 * ("you started closing this") distinct from the graph-derived match.
 */
enum class SkillGapEngagement {
    /** The user has taken no action on this skill gap. */
    NONE,

    /** The user explored jobs demanding the skill. */
    EXPLORED_JOBS,

    /** The user opened targeted learning for the skill. */
    STARTED_LEARNING
}

/**
 * A recorded engagement action against one skill gap.
 */
data class SkillGapProgress(
    /** Normalized skill key (lowercased, trimmed) — the stable identity. */
    val skillKey: String,
    val engagement: SkillGapEngagement,
    val updatedAt: Long
)

/**
 * Persists the user's engagement with the dashboard's missing-skill chips.
 *
 * Deliberately small and local: it stores engagement signals only, keyed by a
 * normalized skill string, so acting on a chip produces visible, durable
 * momentum on the dashboard without introducing a second source of truth for
 * skill possession (that stays graph-derived).
 */
interface SkillGapProgressRepository {

    /** Live view of all recorded skill-gap engagements. */
    fun observeProgress(): Flow<List<SkillGapProgress>>

    /**
     * Records that the user engaged with [skillKey] at the given [engagement]
     * level. Monotonic: a stronger engagement is never downgraded by a later
     * weaker action (exploring jobs after starting learning keeps
     * [SkillGapEngagement.STARTED_LEARNING]).
     */
    suspend fun recordEngagement(skillKey: String, engagement: SkillGapEngagement)
}
