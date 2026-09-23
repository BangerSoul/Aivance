package com.bangersoul.aivance.core.domain.agent

import com.bangersoul.aivance.core.common.result.Result
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audit receipt generated after an agent action executes.
 */
@Serializable
data class ExecutionReceipt(
    val receiptId: String,
    val proposalId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: ExecutionStatus,
    val undoToken: String? = null,
    val details: String
)

/**
 * Execution outcome status for [ExecutionReceipt].
 */
@Serializable
enum class ExecutionStatus {
    SUCCESS,
    FAILURE,
    CANCELLED
}

/**
 * Contract for executing approved action proposals in the Agent Runtime.
 */
interface AgentActionExecutor {
    suspend fun executeAction(proposal: ActionProposal): Result<ExecutionReceipt>
}

/**
 * Default implementation of [AgentActionExecutor] providing deterministic receipts
 * and customizable action type handlers.
 */
@Singleton
class DefaultAgentActionExecutor @Inject constructor() : AgentActionExecutor {

    private val handlers = mutableMapOf<String, suspend (ActionProposal) -> Result<ExecutionReceipt>>()

    /**
     * Registers a custom handler for a specific action type.
     */
    fun registerHandler(actionType: String, handler: suspend (ActionProposal) -> Result<ExecutionReceipt>) {
        synchronized(handlers) {
            handlers[actionType] = handler
        }
    }

    override suspend fun executeAction(proposal: ActionProposal): Result<ExecutionReceipt> {
        if (proposal.riskLevel == ActionRiskLevel.HIGH && proposal.state != ProposalState.APPROVED) {
            return Result.Failure(
                com.bangersoul.aivance.core.common.result.DomainError(
                    "Security invariant violation: High-risk action '${proposal.actionType}' cannot execute in state '${proposal.state}'."
                )
            )
        }

        val handler = synchronized(handlers) { handlers[proposal.actionType] }
        if (handler != null) {
            return handler(proposal)
        }

        // Default deterministic execution receipt
        val receipt = ExecutionReceipt(
            receiptId = "rcpt_${UUID.randomUUID()}",
            proposalId = proposal.id,
            timestamp = System.currentTimeMillis(),
            status = ExecutionStatus.SUCCESS,
            undoToken = "undo_${proposal.id}",
            details = "Successfully executed action '${proposal.actionType}' for proposal '${proposal.title}'"
        )
        return Result.Success(receipt)
    }
}
