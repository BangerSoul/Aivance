package com.bangersoul.aivance.core.domain.analytics

import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.common.model.Recruiter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The career-score composite, split into the dimensions that were actually measured and the
 * derived overall value.
 *
 * [dimensions] contains **only** dimensions with real evidence; a dimension with no input is
 * omitted rather than reported as `0`. When no dimension has evidence at all, [overall] is
 * `null` so consumers can render "not scored yet" instead of a number nobody measured.
 */
data class ScoreBreakdown(
    val overall: Int?,
    val dimensions: Map<String, Int>
) {
    fun dimension(key: String): Int? = dimensions[key]
}

/**
 * Single owner of the compound career score.
 *
 * **Evidence rule (R3-1).** A dimension is scored only from real input:
 *
 *  | Dimension          | Evidence required                                   |
 *  |--------------------|-----------------------------------------------------|
 *  | `ATS_READINESS`    | at least one ATS report                             |
 *  | `NETWORKING`       | at least one known recruiter                        |
 *  | `CONSISTENCY`      | at least one application                            |
 *  | `INTERVIEW_READINESS` | readiness measured from real session feedback ([InterviewReadinessCalculator]) |
 *
 * [overall] is the mean of the evidence-backed dimensions, or `null` when there are none.
 *
 * This replaces a composite that always divided by four and was fed a hardcoded readiness of
 * `75`, so a brand-new install with no data at all rendered a confident **18** on the
 * dashboard — a fabricated number derived entirely from a constant.
 */
@Singleton
class CareerScoreEngine @Inject constructor() {

    fun calculateCompositeScore(
        latestAtsReports: List<AtsReport>,
        recruiters: List<Recruiter>,
        applicationCount: Int,
        interviewReadiness: Int?
    ): ScoreBreakdown {
        val dimensions = buildMap {
            if (latestAtsReports.isNotEmpty()) {
                put(DIM_ATS_READINESS, latestAtsReports.map { it.overallScore }.average().toInt().coerceIn(0, 100))
            }
            if (recruiters.isNotEmpty()) {
                put(DIM_NETWORKING, (recruiters.size * 5).coerceAtMost(100))
            }
            if (applicationCount > 0) {
                put(DIM_CONSISTENCY, (applicationCount * 2).coerceAtMost(100))
            }
            interviewReadiness?.let { put(DIM_INTERVIEW_READINESS, it.coerceIn(0, 100)) }
        }

        val overall = if (dimensions.isEmpty()) null else dimensions.values.average().toInt().coerceIn(0, 100)
        return ScoreBreakdown(overall = overall, dimensions = dimensions)
    }

    companion object {
        const val DIM_OVERALL = "OVERALL"
        const val DIM_ATS_READINESS = "ATS_READINESS"
        const val DIM_NETWORKING = "NETWORKING"
        const val DIM_CONSISTENCY = "CONSISTENCY"
        const val DIM_INTERVIEW_READINESS = "INTERVIEW_READINESS"
    }
}
