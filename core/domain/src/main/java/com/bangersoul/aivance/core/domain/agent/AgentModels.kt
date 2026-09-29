package com.bangersoul.aivance.core.domain.agent

import kotlinx.serialization.Serializable

/**
 * High-level career objective defining target role, preferences, compensation,
 * and operational constraints for autonomous career workflows.
 */
@Serializable
data class CareerGoal(
    val id: String,
    val targetRole: String,
    val targetCompanyTypes: List<String> = emptyList(),
    val timeframeDays: Int,
    val minSalary: Double,
    val constraints: List<String> = emptyList(),
    val status: CareerGoalStatus = CareerGoalStatus.ACTIVE
)

/**
 * Status lifecycle of a [CareerGoal].
 */
@Serializable
enum class CareerGoalStatus {
    ACTIVE,
    PAUSED,
    COMPLETED
}

/**
 * Multi-step plan decomposed from a [CareerGoal], executed step-by-step
 * with human approval gates for sensitive or high-risk mutations.
 */
@Serializable
data class AgentPlan(
    val id: String,
    val goalId: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val steps: List<AgentStep>,
    val currentStepIndex: Int = 0
)

/**
 * Discrete actionable unit within an [AgentPlan].
 */
@Serializable
data class AgentStep(
    val id: String,
    val title: String,
    val description: String,
    val stepType: AgentStepType,
    val status: AgentStepStatus = AgentStepStatus.PENDING,
    val requiresApproval: Boolean
)

/**
 * Types of autonomous or gated operations performed by career agents.
 */
@Serializable
enum class AgentStepType {
    ANALYZE_RESUME,
    FIND_OPPORTUNITIES,
    TAILOR_DOCUMENT,
    PREPARE_INTERVIEW,
    SEND_APPLICATION,
    EXTERNAL_OUTREACH,
    CUSTOM
}

/**
 * Lifecycle states of an individual [AgentStep].
 */
@Serializable
enum class AgentStepStatus {
    PENDING,
    IN_PROGRESS,
    AWAITING_APPROVAL,
    COMPLETED,
    FAILED,
    SKIPPED
}

/**
 * Strict canonical action capability classification determining authorization policy:
 * - READ_ONLY ────────► Automatically permitted
 * - LOCAL_MUTATION ───► Optionally automatic (low-risk local state changes)
 * - EXTERNAL_SIDE_EFFECT ──► ALWAYS requires explicit human approval
 * - DESTRUCTIVE ──────► ALWAYS requires explicit human approval + confirmation prompt
 */
@Serializable
enum class ActionClassification {
    READ_ONLY,
    LOCAL_MUTATION,
    EXTERNAL_SIDE_EFFECT,
    DESTRUCTIVE;

    val requiresApproval: Boolean
        get() = this == EXTERNAL_SIDE_EFFECT || this == DESTRUCTIVE

    val isDestructive: Boolean
        get() = this == DESTRUCTIVE
}

/**
 * Risk classification determining whether an action can execute autonomously
 * or must halt execution pending explicit human authorization.
 */
@Serializable
enum class ActionRiskLevel {
    /** Read-only data queries, draft generation, recommendations. */
    LOW,
    /** Local state modification, saving bookmarks, updating preferences. */
    MEDIUM,
    /** External communications, document overwrites, submitting applications. */
    HIGH;

    fun toClassification(): ActionClassification = when (this) {
        LOW -> ActionClassification.READ_ONLY
        MEDIUM -> ActionClassification.LOCAL_MUTATION
        HIGH -> ActionClassification.EXTERNAL_SIDE_EFFECT
    }
}

/**
 * Concrete action proposed by an agent step that is submitted to the
 * [HumanApprovalGate] before execution.
 */
@Serializable
data class ActionProposal(
    val id: String,
    val stepId: String,
    val title: String,
    val actionType: String,
    val description: String,
    val changesDiff: String? = null,
    val riskLevel: ActionRiskLevel,
    val classification: ActionClassification = riskLevel.toClassification(),
    val evidenceCitation: String,
    val payloadJson: String,
    val state: ProposalState = ProposalState.PROPOSED,
    val rejectionReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Deterministic gate transitions:
 * PROPOSED -> EXPLAINED -> APPROVED / REJECTED / MODIFIED -> EXECUTED
 */
@Serializable
enum class ProposalState {
    PROPOSED,
    EXPLAINED,
    APPROVED,
    REJECTED,
    MODIFIED,
    EXECUTED
}

/**
 * Reactive step updates emitted while advancing an [AgentPlan].
 */
sealed interface AgentStepUpdate {
    data class StepStarted(val step: AgentStep) : AgentStepUpdate
    data class StepAwaitingApproval(val step: AgentStep, val proposal: ActionProposal) : AgentStepUpdate
    data class StepCompleted(val step: AgentStep, val receipt: ExecutionReceipt?) : AgentStepUpdate
    data class StepFailed(val step: AgentStep, val error: String) : AgentStepUpdate
    data class StepSkipped(val step: AgentStep, val reason: String) : AgentStepUpdate
    data class StepRejected(val step: AgentStep, val proposal: ActionProposal) : AgentStepUpdate
    data class PlanCompleted(val planId: String, val totalSteps: Int) : AgentStepUpdate
}
