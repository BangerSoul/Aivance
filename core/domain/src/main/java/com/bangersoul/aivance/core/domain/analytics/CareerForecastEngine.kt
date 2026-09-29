package com.bangersoul.aivance.core.domain.analytics

import com.bangersoul.aivance.core.common.model.CareerIntelligence
import com.bangersoul.aivance.core.common.model.PredictiveMetrics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CareerForecastEngine @Inject constructor() {

    fun simulate(
        current: CareerIntelligence,
        hypotheticalAts: Int? = null,
        hypotheticalReadiness: Int? = null
    ): CareerIntelligence {
        val newAts = hypotheticalAts ?: current.dimensionScores[CareerScoreEngine.DIM_ATS_READINESS] ?: 0
        val newReadiness = hypotheticalReadiness ?: current.dimensionScores[CareerScoreEngine.DIM_INTERVIEW_READINESS] ?: 0

        // Simple linear simulation
        val intProb = ((newAts * 0.7) + 20).toInt().coerceIn(0, 100)
        val offerProb = ((newReadiness * 0.8) + 10).toInt().coerceIn(0, 100)

        // A *simulated* score: when nothing has been measured yet the projection is built from
        // the user's own slider inputs on top of the unscored floor, which is legitimate here
        // because the screen labels the result "Simulated Outcome" — it is never presented as
        // the user's actual career score.
        return current.copy(
            careerScore = ((current.careerScore ?: 0) + (newAts - (current.dimensionScores[CareerScoreEngine.DIM_ATS_READINESS] ?: 0)) / 2).coerceIn(0, 100),
            predictions = current.predictions.copy(
                interviewProbability = intProb,
                offerProbability = offerProb,
                successExplanation = "Simulation shows that improving your ATS score to $newAts would increase interview probability by ${intProb - current.predictions.interviewProbability}%."
            )
        )
    }
}
