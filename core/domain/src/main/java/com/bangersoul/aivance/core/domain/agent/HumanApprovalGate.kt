package com.bangersoul.aivance.core.domain.agent

import com.bangersoul.aivance.core.common.result.DomainError
import com.bangersoul.aivance.core.common.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe coordinator managing Human Approval Gates.
 * Ensures the strict invariant: unapproved actions can never be marked executed.
 *
 * State flow:
 * PROPOSED -> EXPLAINED -> APPROVED / REJECTED / MODIFIED -> EXECUTED
 */
@Singleton
class HumanApprovalGate @Inject constructor() {

    private val lock = Any()
    private val _proposals = MutableStateFlow<Map<String, ActionProposal>>(emptyMap())
    val proposalsFlow = _proposals.asStateFlow()

    /**
     * Submits a new action proposal to the approval gate in [ProposalState.PROPOSED] state.
     */
    fun proposeAction(
        stepId: String,
        title: String,
        actionType: String,
        description: String,
        riskLevel: ActionRiskLevel,
        evidence: String,
        payload: String,
        diff: String? = null
    ): ActionProposal = synchronized(lock) {
        val proposal = ActionProposal(
            id = "prop_${UUID.randomUUID()}",
            stepId = stepId,
            title = title,
            actionType = actionType,
            description = description,
            changesDiff = diff,
            riskLevel = riskLevel,
            evidenceCitation = evidence,
            payloadJson = payload,
            state = ProposalState.PROPOSED
        )
        _proposals.update { current -> current + (proposal.id to proposal) }
        proposal
    }

    /**
     * Explains the action proposal to the user, providing reasoning and diff preview.
     * Transitions from [ProposalState.PROPOSED] to [ProposalState.EXPLAINED].
     */
    fun explainAction(proposalId: String, updatedDiff: String? = null): Result<ActionProposal> = synchronized(lock) {
        val proposal = _proposals.value[proposalId]
            ?: return Result.Failure(DomainError("Proposal not found: $proposalId"))

        if (proposal.state == ProposalState.EXECUTED) {
            return Result.Failure(DomainError("Cannot explain already executed proposal: $proposalId"))
        }

        val explained = proposal.copy(
            changesDiff = updatedDiff ?: proposal.changesDiff,
            state = ProposalState.EXPLAINED
        )
        _proposals.update { current -> current + (proposalId to explained) }
        Result.Success(explained)
    }

    /**
     * Explicitly approves the proposal by human user.
     * Transitions [ProposalState.PROPOSED], [ProposalState.EXPLAINED], or [ProposalState.MODIFIED]
     * to [ProposalState.APPROVED].
     */
    fun approveAction(proposalId: String): Result<ActionProposal> = synchronized(lock) {
        val proposal = _proposals.value[proposalId]
            ?: return Result.Failure(DomainError("Proposal not found: $proposalId"))

        if (proposal.state == ProposalState.EXECUTED) {
            return Result.Failure(DomainError("Cannot approve already executed proposal: $proposalId"))
        }
        if (proposal.state == ProposalState.REJECTED) {
            return Result.Failure(DomainError("Cannot approve rejected proposal: $proposalId"))
        }

        val approved = proposal.copy(state = ProposalState.APPROVED)
        _proposals.update { current -> current + (proposalId to approved) }
        Result.Success(approved)
    }

    /**
     * Rejects the proposal with a designated reason.
     * Transitions to [ProposalState.REJECTED].
     */
    fun rejectAction(proposalId: String, reason: String): Result<ActionProposal> = synchronized(lock) {
        val proposal = _proposals.value[proposalId]
            ?: return Result.Failure(DomainError("Proposal not found: $proposalId"))

        if (proposal.state == ProposalState.EXECUTED) {
            return Result.Failure(DomainError("Cannot reject already executed proposal: $proposalId"))
        }

        val rejected = proposal.copy(
            state = ProposalState.REJECTED,
            rejectionReason = reason
        )
        _proposals.update { current -> current + (proposalId to rejected) }
        Result.Success(rejected)
    }

    /**
     * Modifies the payload of an unexecuted proposal.
     * Transitions to [ProposalState.MODIFIED]. User must approve the modified proposal
     * before execution can proceed.
     */
    fun modifyAction(proposalId: String, modifiedPayload: String): Result<ActionProposal> = synchronized(lock) {
        val proposal = _proposals.value[proposalId]
            ?: return Result.Failure(DomainError("Proposal not found: $proposalId"))

        if (proposal.state == ProposalState.EXECUTED) {
            return Result.Failure(DomainError("Cannot modify executed proposal: $proposalId"))
        }
        if (proposal.state == ProposalState.REJECTED) {
            return Result.Failure(DomainError("Cannot modify rejected proposal: $proposalId"))
        }

        val modified = proposal.copy(
            payloadJson = modifiedPayload,
            state = ProposalState.MODIFIED
        )
        _proposals.update { current -> current + (proposalId to modified) }
        Result.Success(modified)
    }

    /**
     * Returns true if the proposal exists and has been explicitly approved by the human user.
     */
    fun isApproved(proposalId: String): Boolean {
        val proposal = _proposals.value[proposalId] ?: return false
        return proposal.state == ProposalState.APPROVED
    }

    /**
     * Enforces invariant: an unapproved action cannot be marked executed.
     * Transitions [ProposalState.APPROVED] to [ProposalState.EXECUTED].
     */
    fun markExecuted(proposalId: String): Result<ActionProposal> = synchronized(lock) {
        val proposal = _proposals.value[proposalId]
            ?: return Result.Failure(DomainError("Proposal not found: $proposalId"))

        if (!isApproved(proposalId)) {
            return Result.Failure(
                DomainError("Invariant violation: Unapproved action cannot be marked executed. Current state: ${proposal.state}")
            )
        }
        if (proposal.state == ProposalState.EXECUTED) {
            return Result.Failure(DomainError("Action is already executed: $proposalId"))
        }

        val executed = proposal.copy(state = ProposalState.EXECUTED)
        _proposals.update { current -> current + (proposalId to executed) }
        Result.Success(executed)
    }

    /**
     * Reactive stream of proposals pending human review (PROPOSED, EXPLAINED, or MODIFIED).
     */
    fun getPendingProposals(): Flow<List<ActionProposal>> {
        return _proposals.map { map ->
            map.values.filter {
                it.state == ProposalState.PROPOSED ||
                    it.state == ProposalState.EXPLAINED ||
                    it.state == ProposalState.MODIFIED
            }.toList()
        }
    }

    /**
     * Retrieves a proposal by its ID.
     */
    fun getProposal(proposalId: String): ActionProposal? {
        return _proposals.value[proposalId]
    }

    /**
     * Retrieves all proposals associated with a specific step ID.
     */
    fun getProposalsForStep(stepId: String): List<ActionProposal> {
        return _proposals.value.values.filter { it.stepId == stepId }
    }

    /**
     * Retrieves all recorded proposals.
     */
    fun getAllProposals(): List<ActionProposal> {
        return _proposals.value.values.toList()
    }

    /**
     * Resets the gate state (useful in testing or session teardown).
     */
    fun reset() = synchronized(lock) {
        _proposals.value = emptyMap()
    }
}
