package com.bangersoul.aivance.core.domain.agent

import com.bangersoul.aivance.core.common.events.AgentEvent
import com.bangersoul.aivance.core.common.events.CareerEventBus
import com.bangersoul.aivance.core.common.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central orchestrator managing the Career Agent Runtime.
 * Decomposes high-level [CareerGoal]s into multi-step [AgentPlan]s, executes autonomous
 * low-risk tasks, and integrates with [HumanApprovalGate] to suspend execution
 * whenever human authorization is required.
 */
@Singleton
class CareerAgentEngine @Inject constructor(
    private val humanApprovalGate: HumanApprovalGate,
    private val actionExecutor: AgentActionExecutor,
    private val eventBus: CareerEventBus? = null,
    private val safetyPolicy: AgentSafetyPolicy = AgentSafetyPolicy()
) {

    private val plans = ConcurrentHashMap<String, AgentPlan>()
    private val goals = ConcurrentHashMap<String, CareerGoal>()

    /**
     * Decomposes a [CareerGoal] into a structured, executable [AgentPlan].
     * Marks sensitive operations (e.g. document tailoring) as requiring approval.
     */
    fun createPlanFromGoal(goal: CareerGoal): AgentPlan {
        val promptToCheck = buildString {
            append(goal.targetRole).append(" ")
            append(goal.targetCompanyTypes.joinToString(" ")).append(" ")
            append(goal.constraints.joinToString(" "))
        }
        val safetyCheck = safetyPolicy.evaluatePromptSafety(promptToCheck)
        if (safetyCheck is SafetyEvaluationResult.Blocked) {
            throw IllegalArgumentException("Goal rejected by safety policy: ${safetyCheck.reason}")
        }
        val planId = "plan_${UUID.randomUUID()}"
        val steps = listOf(
            AgentStep(
                id = "${planId}_step_1",
                title = "Analyze Resume for ${goal.targetRole}",
                description = "Assess resume fit, keyword coverage, and qualifications against market standards for ${goal.targetRole}.",
                stepType = AgentStepType.ANALYZE_RESUME,
                status = AgentStepStatus.PENDING,
                requiresApproval = false
            ),
            AgentStep(
                id = "${planId}_step_2",
                title = "Discover Target Opportunities",
                description = "Scan and evaluate job openings matching ${goal.targetCompanyTypes.joinToString().ifEmpty { "target sectors" }} with minimum salary of $${goal.minSalary.toLong()}.",
                stepType = AgentStepType.FIND_OPPORTUNITIES,
                status = AgentStepStatus.PENDING,
                requiresApproval = false
            ),
            AgentStep(
                id = "${planId}_step_3",
                title = "Tailor Career Documents for ${goal.targetRole}",
                description = "Generate customized resume bullet points and targeted cover letter highlighting relevant strengths.",
                stepType = AgentStepType.TAILOR_DOCUMENT,
                status = AgentStepStatus.PENDING,
                requiresApproval = true // Human Approval Gate required!
            ),
            AgentStep(
                id = "${planId}_step_4",
                title = "Prepare Interview Strategy & STAR Pack",
                description = "Synthesize behavioral questions and STAR coaching packs tailored for target opportunities.",
                stepType = AgentStepType.PREPARE_INTERVIEW,
                status = AgentStepStatus.PENDING,
                requiresApproval = false
            )
        )

        val plan = AgentPlan(
            id = planId,
            goalId = goal.id,
            title = "Autonomous Career Plan for ${goal.targetRole}",
            createdAt = System.currentTimeMillis(),
            steps = steps,
            currentStepIndex = 0
        )

        goals[goal.id] = goal
        plans[planId] = plan

        eventBus?.tryEmit(AgentEvent.GoalCreated(goalId = goal.id, targetRole = goal.targetRole))
        eventBus?.tryEmit(AgentEvent.PlanCreated(planId = planId, goalId = goal.id, stepCount = plan.steps.size))

        return plan
    }

    /**
     * Advances the plan step-by-step.
     * Emits reactive [AgentStepUpdate]s as execution proceeds.
     * If a step requires approval, execution halts at that step until the human approves it.
     */
    fun advancePlan(planId: String): Flow<AgentStepUpdate> = flow {
        var plan = plans[planId] ?: run {
            emit(
                AgentStepUpdate.StepFailed(
                    step = AgentStep(
                        id = "invalid",
                        title = "Unknown Plan",
                        description = "",
                        stepType = AgentStepType.CUSTOM,
                        requiresApproval = false
                    ),
                    error = "Plan with ID '$planId' not found."
                )
            )
            return@flow
        }

        val planSafety = safetyPolicy.evaluatePlanInvariants(plan)
        if (planSafety is SafetyEvaluationResult.Blocked) {
            val failedStep = plan.steps.getOrNull(plan.currentStepIndex) ?: plan.steps.firstOrNull() ?: AgentStep(
                id = "plan_safety_violation",
                title = "Safety Invariant Violation",
                description = planSafety.reason,
                stepType = AgentStepType.CUSTOM,
                requiresApproval = false
            )
            emit(AgentStepUpdate.StepFailed(step = failedStep, error = "Plan blocked by safety policy: ${planSafety.reason}"))
            return@flow
        }

        if (plan.currentStepIndex >= plan.steps.size) {
            emit(AgentStepUpdate.PlanCompleted(planId = planId, totalSteps = plan.steps.size))
            return@flow
        }

        while (plan.currentStepIndex < plan.steps.size) {
            val currentStep = plan.steps[plan.currentStepIndex]

            // If already completed or skipped, increment and proceed
            if (currentStep.status == AgentStepStatus.COMPLETED || currentStep.status == AgentStepStatus.SKIPPED) {
                val updatedPlan = plan.copy(currentStepIndex = plan.currentStepIndex + 1)
                plans[planId] = updatedPlan
                plan = updatedPlan
                continue
            }

            if (currentStep.requiresApproval) {
                val existingProposal = humanApprovalGate.getProposalsForStep(currentStep.id).lastOrNull()

                if (existingProposal == null) {
                    // 1. Submit proposal to HumanApprovalGate
                    val proposal = humanApprovalGate.proposeAction(
                        stepId = currentStep.id,
                        title = currentStep.title,
                        actionType = currentStep.stepType.name,
                        description = currentStep.description,
                        riskLevel = ActionRiskLevel.HIGH,
                        evidence = "Step mutates user career assets and requires explicit human gate confirmation.",
                        payload = """{"stepId":"${currentStep.id}","stepType":"${currentStep.stepType.name}"}""",
                        diff = "+ Tailored professional summary\n+ Aligned skills with ${plan.title}"
                    )

                    // 2. Mark step as AWAITING_APPROVAL and persist
                    val awaitingStep = currentStep.copy(status = AgentStepStatus.AWAITING_APPROVAL)
                    val updatedSteps = plan.steps.toMutableList().also { it[plan.currentStepIndex] = awaitingStep }
                    val updatedPlan = plan.copy(steps = updatedSteps)
                    plans[planId] = updatedPlan
                    plan = updatedPlan

                    eventBus?.tryEmit(
                        AgentEvent.ActionProposed(
                            proposalId = proposal.id,
                            actionType = proposal.actionType,
                            riskLevel = proposal.riskLevel.name
                        )
                    )

                    // 3. Emit awaiting approval and SUSPEND execution
                    emit(AgentStepUpdate.StepAwaitingApproval(awaitingStep, proposal))
                    return@flow
                } else if (humanApprovalGate.isApproved(existingProposal.id)) {
                    // Proposal is approved by human user! Proceed to execute
                    val runningStep = currentStep.copy(status = AgentStepStatus.IN_PROGRESS)
                    val updatedSteps = plan.steps.toMutableList().also { it[plan.currentStepIndex] = runningStep }
                    val runningPlan = plan.copy(steps = updatedSteps)
                    plans[planId] = runningPlan
                    plan = runningPlan

                    emit(AgentStepUpdate.StepStarted(runningStep))

                    val execResult = actionExecutor.executeAction(existingProposal)
                    when (execResult) {
                        is Result.Success -> {
                            val receipt = execResult.data
                            humanApprovalGate.markExecuted(existingProposal.id)

                            eventBus?.tryEmit(
                                AgentEvent.ActionExecuted(
                                    proposalId = existingProposal.id,
                                    receiptId = receipt.receiptId,
                                    status = "SUCCESS"
                                )
                            )

                            val completedStep = runningStep.copy(status = AgentStepStatus.COMPLETED)
                            val completedSteps = plan.steps.toMutableList().also { it[plan.currentStepIndex] = completedStep }
                            val nextPlan = plan.copy(steps = completedSteps, currentStepIndex = plan.currentStepIndex + 1)
                            plans[planId] = nextPlan
                            plan = nextPlan

                            emit(AgentStepUpdate.StepCompleted(completedStep, receipt))
                        }
                        is Result.Failure -> {
                            val failedStep = runningStep.copy(status = AgentStepStatus.FAILED)
                            val failedSteps = plan.steps.toMutableList().also { it[plan.currentStepIndex] = failedStep }
                            val failedPlan = plan.copy(steps = failedSteps)
                            plans[planId] = failedPlan
                            plan = failedPlan

                            emit(AgentStepUpdate.StepFailed(failedStep, execResult.error.message))
                            return@flow
                        }
                    }
                } else if (existingProposal.state == ProposalState.REJECTED) {
                    // Proposal was rejected by the human user
                    val skippedStep = currentStep.copy(status = AgentStepStatus.SKIPPED)
                    val skippedSteps = plan.steps.toMutableList().also { it[plan.currentStepIndex] = skippedStep }
                    val nextPlan = plan.copy(steps = skippedSteps, currentStepIndex = plan.currentStepIndex + 1)
                    plans[planId] = nextPlan
                    plan = nextPlan

                    eventBus?.tryEmit(
                        AgentEvent.ActionRejected(
                            proposalId = existingProposal.id,
                            reason = existingProposal.rejectionReason ?: "Rejected by human user"
                        )
                    )

                    emit(AgentStepUpdate.StepRejected(skippedStep, existingProposal))
                } else {
                    // Proposal is still in PROPOSED, EXPLAINED, or MODIFIED (not yet approved)
                    val awaitingStep = currentStep.copy(status = AgentStepStatus.AWAITING_APPROVAL)
                    val updatedSteps = plan.steps.toMutableList().also { it[plan.currentStepIndex] = awaitingStep }
                    val updatedPlan = plan.copy(steps = updatedSteps)
                    plans[planId] = updatedPlan
                    plan = updatedPlan

                    emit(AgentStepUpdate.StepAwaitingApproval(awaitingStep, existingProposal))
                    return@flow
                }
            } else {
                // Autonomous low-risk step (does not require human approval)
                val runningStep = currentStep.copy(status = AgentStepStatus.IN_PROGRESS)
                val updatedSteps = plan.steps.toMutableList().also { it[plan.currentStepIndex] = runningStep }
                val runningPlan = plan.copy(steps = updatedSteps)
                plans[planId] = runningPlan
                plan = runningPlan

                emit(AgentStepUpdate.StepStarted(runningStep))

                val autoProposal = ActionProposal(
                    id = "auto_${UUID.randomUUID()}",
                    stepId = currentStep.id,
                    title = currentStep.title,
                    actionType = currentStep.stepType.name,
                    description = currentStep.description,
                    changesDiff = null,
                    riskLevel = ActionRiskLevel.LOW,
                    evidenceCitation = "Autonomous execution for low-risk step.",
                    payloadJson = """{"stepId":"${currentStep.id}","stepType":"${currentStep.stepType.name}"}""",
                    state = ProposalState.APPROVED
                )

                val execResult = actionExecutor.executeAction(autoProposal)
                when (execResult) {
                    is Result.Success -> {
                        val receipt = execResult.data
                        eventBus?.tryEmit(
                            AgentEvent.ActionExecuted(
                                proposalId = autoProposal.id,
                                receiptId = receipt.receiptId,
                                status = "SUCCESS"
                            )
                        )
                        val completedStep = runningStep.copy(status = AgentStepStatus.COMPLETED)
                        val completedSteps = plan.steps.toMutableList().also { it[plan.currentStepIndex] = completedStep }
                        val nextPlan = plan.copy(steps = completedSteps, currentStepIndex = plan.currentStepIndex + 1)
                        plans[planId] = nextPlan
                        plan = nextPlan

                        emit(AgentStepUpdate.StepCompleted(completedStep, receipt))
                    }
                    is Result.Failure -> {
                        val failedStep = runningStep.copy(status = AgentStepStatus.FAILED)
                        val failedSteps = plan.steps.toMutableList().also { it[plan.currentStepIndex] = failedStep }
                        val failedPlan = plan.copy(steps = failedSteps)
                        plans[planId] = failedPlan
                        plan = failedPlan

                        emit(AgentStepUpdate.StepFailed(failedStep, execResult.error.message))
                        return@flow
                    }
                }
            }
        }

        if (plan.currentStepIndex >= plan.steps.size) {
            emit(AgentStepUpdate.PlanCompleted(planId = planId, totalSteps = plan.steps.size))
        }
    }

    /**
     * Retrieves an [AgentPlan] by ID.
     */
    fun getPlan(planId: String): AgentPlan? = plans[planId]

    /**
     * Retrieves all active plans.
     */
    fun getAllPlans(): List<AgentPlan> = plans.values.toList()

    /**
     * Retrieves a [CareerGoal] by ID.
     */
    fun getGoal(goalId: String): CareerGoal? = goals[goalId]

    /**
     * Manually registers or updates an [AgentPlan] (useful in testing or external persistence).
     */
    fun registerPlan(plan: AgentPlan) {
        plans[plan.id] = plan
    }

    /**
     * Clears in-memory plans and goals.
     */
    fun clear() {
        plans.clear()
        goals.clear()
    }
}
