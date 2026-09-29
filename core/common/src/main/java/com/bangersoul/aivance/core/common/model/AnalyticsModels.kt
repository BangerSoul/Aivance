package com.bangersoul.aivance.core.common.model

import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsSnapshot(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val kpis: Map<String, Double> = emptyMap(),
    val careerScore: Int,
    val dimensionScores: Map<String, Int> = emptyMap()
)

@Serializable
data class CareerRecommendation(
    val id: Long = 0,
    val title: String,
    val description: String,
    val priority: String,
    val category: String,
    val actionDeepLink: String? = null,
    val evidence: Map<String, String>? = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class CareerGoal(
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val targetValue: Double,
    val currentValue: Double = 0.0,
    val unit: String,
    val deadline: Long? = null,
    val isCompleted: Boolean = false,
    val type: String = "SYSTEM"
)

@Serializable
data class CareerIntelligence(
    /**
     * Mean of the evidence-backed score dimensions, or `null` when nothing has been measured.
     * Never a fabricated default (R3-1).
     */
    val careerScore: Int?,
    val dimensionScores: Map<String, Int>,
    val predictions: PredictiveMetrics,
    val health: List<HealthDimension>,
    val weeklyReview: String? = null
)

@Serializable
data class PredictiveMetrics(
    val interviewProbability: Int,
    val offerProbability: Int,
    val successExplanation: String
)

@Serializable
data class HealthDimension(
    val category: String,
    val score: Int,
    val trend: String, // "UP", "DOWN", "STABLE"
    val recommendation: String
)
