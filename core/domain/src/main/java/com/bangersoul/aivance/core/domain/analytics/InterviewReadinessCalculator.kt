package com.bangersoul.aivance.core.domain.analytics

import com.bangersoul.aivance.core.common.model.InterviewSession
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The **single authoritative owner of Interview Readiness** (R3-3).
 *
 * Readiness is how prepared the candidate demonstrably is, measured from the feedback the user
 * actually earned in mock interview sessions. It returns `null` — never `0` and never a
 * hardcoded default — when no session has produced feedback, so every consumer renders "not
 * measured yet" instead of an invented number.
 *
 * Before this class existed the concept had two disagreeing owners that were visible on the same
 * device: the analytics path divided a hardcoded readiness of `75` into the career composite
 * (rendering `18` at zero data) while Prep Studio added `careerScore / 10` (rendering `1%` from
 * that same fabricated 18). Both paths now call this.
 */
@Singleton
class InterviewReadinessCalculator @Inject constructor() {

    /**
     * Mean of the overall scores across sessions that produced feedback, or `null` when no
     * session has any — i.e. when readiness has genuinely not been measured yet.
     */
    fun calculate(sessions: List<InterviewSession>): Int? {
        val earnedScores = sessions.mapNotNull { it.feedback?.overallScore }
        if (earnedScores.isEmpty()) return null
        return earnedScores.average().toInt().coerceIn(0, 100)
    }
}
