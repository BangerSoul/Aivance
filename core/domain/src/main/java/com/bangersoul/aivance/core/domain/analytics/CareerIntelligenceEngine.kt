package com.bangersoul.aivance.core.domain.analytics

import com.bangersoul.aivance.core.common.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CareerIntelligenceEngine @Inject constructor(
    private val kpiEngine: KPIEngine,
    private val scoreEngine: CareerScoreEngine
) {

    /**
     * Projects the live career-intelligence read model.
     *
     * [interviewReadiness] is `null` when readiness has not been measured (see
     * [InterviewReadinessCalculator]); the composite, the health dimensions and the narrative
     * then report the absence honestly instead of substituting a default.
     */
    fun calculateIntelligence(
        latestAtsReports: List<AtsReport>,
        recruiters: List<Recruiter>,
        applications: List<Application>,
        interviewReadiness: Int?
    ): CareerIntelligence {
        val breakdown = scoreEngine.calculateCompositeScore(
            latestAtsReports, recruiters, applications.size, interviewReadiness
        )
        val dimensions = breakdown.dimensions
        val overallScore = breakdown.overall

        val interviewRate = kpiEngine.calculateInterviewRate(applications)

        val interviewProb = calculateInterviewProbability(latestAtsReports.firstOrNull(), recruiters.size)
        val offerProb = calculateOfferProbability(interviewReadiness, interviewRate)

        return CareerIntelligence(
            careerScore = overallScore,
            dimensionScores = buildMap {
                putAll(dimensions)
                // The composite is published alongside its inputs for consumers that chart the
                // full breakdown. It is present only when it was actually measurable.
                overallScore?.let { put(CareerScoreEngine.DIM_OVERALL, it) }
            },
            predictions = PredictiveMetrics(
                interviewProbability = interviewProb,
                offerProbability = offerProb,
                successExplanation = generateExplanation(overallScore, interviewProb, offerProb)
            ),
            // Only measured dimensions become health rows — a dimension with no evidence is
            // simply absent rather than shown as a 0 health score.
            health = buildList {
                dimensions[CareerScoreEngine.DIM_ATS_READINESS]?.let {
                    add(HealthDimension("Resume", it, "STABLE", "Optimize for your target role."))
                }
                dimensions[CareerScoreEngine.DIM_NETWORKING]?.let {
                    add(HealthDimension("Networking", it, "UP", "Keep connecting with recruiters."))
                }
                dimensions[CareerScoreEngine.DIM_INTERVIEW_READINESS]?.let {
                    add(HealthDimension("Interview", it, "STABLE", "Practice more mock sessions."))
                }
                dimensions[CareerScoreEngine.DIM_CONSISTENCY]?.let {
                    add(HealthDimension("Consistency", it, "STABLE", "Apply to more jobs weekly."))
                }
            }
        )
    }

    private fun calculateInterviewProbability(latestAts: AtsReport?, networkingCount: Int): Int {
        val atsWeight = (latestAts?.overallScore ?: 0) * 0.7
        val networkingWeight = (networkingCount * 10).coerceAtMost(30)
        return (atsWeight + networkingWeight).toInt().coerceIn(0, 100)
    }

    private fun calculateOfferProbability(readiness: Int?, interviewRate: Double): Int {
        return (((readiness ?: 0) * 0.6) + (interviewRate * 0.4)).toInt().coerceIn(0, 100)
    }

    private fun generateExplanation(overall: Int?, intProb: Int, offerProb: Int): String {
        if (overall == null) {
            return "Not enough data yet — add a resume or complete a mock interview to generate a forecast."
        }
        return "Based on your $overall career score, you have a $intProb% chance of landing an interview. Improving your technical readiness could boost your offer probability ($offerProb%)."
    }
}
