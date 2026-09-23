package com.bangersoul.aivance.core.domain.evaluation

import kotlinx.serialization.Serializable

/**
 * Production AI feature pipelines evaluated by the AiVance AI Evaluation Framework.
 */
@Serializable
enum class AiEvaluationPipeline {
    RESUME_EXTRACTION,
    ATS_SCORING,
    JOB_MATCHING,
    INTERVIEW_EVALUATION,
    CAREER_RECOMMENDATIONS,
    COPILOT_GROUNDING,
    CAREER_MEMORY_GENERATION,
    AGENT_PLANNING
}

/**
 * Standardized evaluation sample representing a golden test case.
 */
@Serializable
data class EvaluationSample(
    val id: String,
    val pipeline: AiEvaluationPipeline,
    val inputPayload: String,
    val groundTruth: Map<String, String>,
    val tags: List<String> = emptyList()
)

/**
 * Comprehensive metrics produced by evaluating an AI pipeline.
 */
@Serializable
data class EvaluationMetrics(
    val precision: Float,
    val recall: Float,
    val f1: Float,
    val groundingScore: Float,
    val hallucinationRate: Float,
    val determinismStdDev: Float,
    val latencyMs: Long,
    val tokenCostEstimate: Double,
    val actionSafetyPassed: Boolean,
    val schemaValidityPassed: Boolean
) {
    val isPassing: Boolean
        get() = groundingScore >= 0.95f &&
                hallucinationRate == 0.0f &&
                determinismStdDev < 1.0f &&
                actionSafetyPassed &&
                schemaValidityPassed
}

/**
 * Final evaluation report for an AI model on a specific pipeline.
 */
@Serializable
data class AiEvaluationReport(
    val pipeline: AiEvaluationPipeline,
    val modelIdentifier: String,
    val sampleCount: Int,
    val metrics: EvaluationMetrics,
    val status: EvaluationStatus = if (metrics.isPassing) EvaluationStatus.PASS else EvaluationStatus.FAIL,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class EvaluationStatus {
    PASS,
    REGRESSION,
    FAIL
}
