package com.bangersoul.aivance.core.domain.analytics

import com.bangersoul.aivance.core.common.model.Application
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single owner for application-pipeline conversion metrics (R2/R4).
 *
 * A conversion rate is a ratio over *applied* applications (anything past the SAVED bookmark
 * stage). When there is nothing applied the ratio is mathematically undefined (0/0), so these
 * functions return `null` rather than a literal `0.0`. Callers must render "not enough data"
 * for a `null` — a `0/0` must never masquerade as a measured 0% conversion (R2).
 */
@Singleton
class KPIEngine @Inject constructor() {

    /** Fraction (0..100) of applications currently in [targetStageId], or `null` with no applications. */
    fun calculateConversionRate(applications: List<Application>, targetStageId: String): Double? {
        if (applications.isEmpty()) return null
        val targetCount = applications.count { it.currentStageId == targetStageId }
        return (targetCount.toDouble() / applications.size.toDouble()) * 100.0
    }

    /**
     * Fraction (0..100) of applied applications that reached the interview stage or beyond,
     * or `null` when nothing has been applied yet (the ratio would be 0/0).
     */
    fun calculateInterviewRate(applications: List<Application>): Double? {
        val totalApplied = totalApplied(applications)
        if (totalApplied == 0) return null
        val reachedInterview = applications.count {
            it.currentStageId == INTERVIEWING || it.currentStageId == INTERVIEW || it.currentStageId == OFFER
        }
        return (reachedInterview.toDouble() / totalApplied.toDouble()) * 100.0
    }

    /**
     * Fraction (0..100) of applied applications that reached an offer, or `null` when nothing
     * has been applied yet (the ratio would be 0/0).
     */
    fun calculateOfferRate(applications: List<Application>): Double? {
        val totalApplied = totalApplied(applications)
        if (totalApplied == 0) return null
        val offers = applications.count { it.currentStageId == OFFER }
        return (offers.toDouble() / totalApplied.toDouble()) * 100.0
    }

    /** Applications that have moved past the SAVED bookmark stage. */
    private fun totalApplied(applications: List<Application>): Int =
        applications.count { it.currentStageId != SAVED }

    private companion object {
        // Canonical stage IDs (see ApplicationWorkflowRepositoryImpl.DEFAULT_STAGES). Both
        // "INTERVIEW" (default catalog) and the legacy "INTERVIEWING" alias are treated as the
        // interview stage so historical rows still count.
        const val SAVED = "SAVED"
        const val INTERVIEW = "INTERVIEW"
        const val INTERVIEWING = "INTERVIEWING"
        const val OFFER = "OFFER"
    }
}
