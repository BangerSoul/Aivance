package com.bangersoul.aivance.core.domain.agent

import com.bangersoul.aivance.core.common.result.getOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HumanApprovalGateTest {

    private lateinit var gate: HumanApprovalGate

    @Before
    fun setUp() {
        gate = HumanApprovalGate()
    }

    @Test
    fun `proposeAction creates proposal in PROPOSED state with correct properties`() = runTest {
        val proposal = gate.proposeAction(
            stepId = "step_1",
            title = "Tailor Resume",
            actionType = "TAILOR_DOCUMENT",
            description = "Tailoring resume for Lead Android Engineer",
            riskLevel = ActionRiskLevel.HIGH,
            evidence = "Target job description requires Jetpack Compose and Coroutines",
            payload = """{"action":"tailor","targetRole":"Lead Android Engineer"}""",
            diff = "+ Lead Android Engineer summary\n+ Jetpack Compose mastery"
        )

        assertNotNull(proposal.id)
        assertTrue(proposal.id.startsWith("prop_"))
        assertEquals("step_1", proposal.stepId)
        assertEquals("Tailor Resume", proposal.title)
        assertEquals("TAILOR_DOCUMENT", proposal.actionType)
        assertEquals("Tailoring resume for Lead Android Engineer", proposal.description)
        assertEquals(ActionRiskLevel.HIGH, proposal.riskLevel)
        assertEquals("Target job description requires Jetpack Compose and Coroutines", proposal.evidenceCitation)
        assertEquals("""{"action":"tailor","targetRole":"Lead Android Engineer"}""", proposal.payloadJson)
        assertEquals("+ Lead Android Engineer summary\n+ Jetpack Compose mastery", proposal.changesDiff)
        assertEquals(ProposalState.PROPOSED, proposal.state)
        assertNull(proposal.rejectionReason)

        // Verifies not yet approved
        assertFalse(gate.isApproved(proposal.id))

        // Verifies listed in pending proposals
        val pending = gate.getPendingProposals().first()
        assertEquals(1, pending.size)
        assertEquals(proposal.id, pending[0].id)
    }

    @Test
    fun `explainAction transitions proposal to EXPLAINED state`() = runTest {
        val proposal = gate.proposeAction(
            stepId = "step_1",
            title = "Send Application",
            actionType = "SEND_APPLICATION",
            description = "Submitting application to Acme Corp",
            riskLevel = ActionRiskLevel.HIGH,
            evidence = "Match score 94%",
            payload = "{}"
        )

        val explainResult = gate.explainAction(proposal.id, updatedDiff = "+ Direct submission diff")
        assertTrue(explainResult.isSuccess)

        val explained = explainResult.getOrNull()!!
        assertEquals(ProposalState.EXPLAINED, explained.state)
        assertEquals("+ Direct submission diff", explained.changesDiff)
        assertFalse(gate.isApproved(proposal.id))

        val pending = gate.getPendingProposals().first()
        assertEquals(1, pending.size)
        assertEquals(ProposalState.EXPLAINED, pending[0].state)
    }

    @Test
    fun `approveAction transitions proposal to APPROVED and removes from pending`() = runTest {
        val proposal = gate.proposeAction(
            stepId = "step_1",
            title = "Tailor Document",
            actionType = "TAILOR_DOCUMENT",
            description = "Tailoring resume",
            riskLevel = ActionRiskLevel.HIGH,
            evidence = "Keyword gap identified",
            payload = "{}"
        )

        assertFalse(gate.isApproved(proposal.id))

        val approveResult = gate.approveAction(proposal.id)
        assertTrue(approveResult.isSuccess)

        val approved = approveResult.getOrNull()!!
        assertEquals(ProposalState.APPROVED, approved.state)
        assertTrue(gate.isApproved(proposal.id))

        // Verifies proposal is no longer in pending list
        val pending = gate.getPendingProposals().first()
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `rejectAction transitions proposal to REJECTED with reason and removes from pending`() = runTest {
        val proposal = gate.proposeAction(
            stepId = "step_1",
            title = "Send Message to Recruiter",
            actionType = "EXTERNAL_OUTREACH",
            description = "Cold outreach email",
            riskLevel = ActionRiskLevel.HIGH,
            evidence = "Found recruiter email",
            payload = "{}"
        )

        val rejectResult = gate.rejectAction(proposal.id, "I want to apply directly via portal instead.")
        assertTrue(rejectResult.isSuccess)

        val rejected = rejectResult.getOrNull()!!
        assertEquals(ProposalState.REJECTED, rejected.state)
        assertEquals("I want to apply directly via portal instead.", rejected.rejectionReason)
        assertFalse(gate.isApproved(proposal.id))

        val pending = gate.getPendingProposals().first()
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `modifyAction updates payload and subsequent approval transitions to APPROVED`() = runTest {
        val originalPayload = """{"salary":120000,"location":"Remote"}"""
        val modifiedPayload = """{"salary":150000,"location":"Remote"}"""

        val proposal = gate.proposeAction(
            stepId = "step_1",
            title = "Set Preferences",
            actionType = "UPDATE_PREFERENCES",
            description = "Updating salary expectations",
            riskLevel = ActionRiskLevel.MEDIUM,
            evidence = "User career goal",
            payload = originalPayload
        )

        // Modify proposal payload
        val modifyResult = gate.modifyAction(proposal.id, modifiedPayload)
        assertTrue(modifyResult.isSuccess)

        val modified = modifyResult.getOrNull()!!
        assertEquals(ProposalState.MODIFIED, modified.state)
        assertEquals(modifiedPayload, modified.payloadJson)
        // Modified proposal is still pending approval
        assertFalse(gate.isApproved(proposal.id))

        // Approve modified proposal
        val approveResult = gate.approveAction(proposal.id)
        assertTrue(approveResult.isSuccess)

        val approved = approveResult.getOrNull()!!
        assertEquals(ProposalState.APPROVED, approved.state)
        assertEquals(modifiedPayload, approved.payloadJson)
        assertTrue(gate.isApproved(proposal.id))
    }

    @Test
    fun `invariant check - unapproved actions cannot be executed`() = runTest {
        // 1. PROPOSED cannot be executed
        val propProposed = gate.proposeAction("s1", "T1", "A1", "D1", ActionRiskLevel.HIGH, "E1", "{}")
        val execResult1 = gate.markExecuted(propProposed.id)
        assertTrue(execResult1.isFailure)

        // 2. EXPLAINED cannot be executed
        gate.explainAction(propProposed.id)
        val execResult2 = gate.markExecuted(propProposed.id)
        assertTrue(execResult2.isFailure)

        // 3. REJECTED cannot be executed
        val propRejected = gate.proposeAction("s2", "T2", "A2", "D2", ActionRiskLevel.HIGH, "E2", "{}")
        gate.rejectAction(propRejected.id, "Rejected by user")
        val execResult3 = gate.markExecuted(propRejected.id)
        assertTrue(execResult3.isFailure)

        // 4. MODIFIED (unapproved) cannot be executed
        val propModified = gate.proposeAction("s3", "T3", "A3", "D3", ActionRiskLevel.HIGH, "E3", "{}")
        gate.modifyAction(propModified.id, """{"new":"val"}""")
        val execResult4 = gate.markExecuted(propModified.id)
        assertTrue(execResult4.isFailure)
    }

    @Test
    fun `markExecuted succeeds only for APPROVED proposal and blocks repeat execution`() = runTest {
        val proposal = gate.proposeAction("s1", "T1", "A1", "D1", ActionRiskLevel.HIGH, "E1", "{}")
        gate.approveAction(proposal.id)

        // First execution succeeds
        val execResult = gate.markExecuted(proposal.id)
        assertTrue(execResult.isSuccess)
        val executed = execResult.getOrNull()!!
        assertEquals(ProposalState.EXECUTED, executed.state)

        // Repeat execution is strictly blocked
        val secondExecResult = gate.markExecuted(proposal.id)
        assertTrue(secondExecResult.isFailure)
    }

    @Test
    fun `cannot approve, reject, or modify already EXECUTED proposal`() = runTest {
        val proposal = gate.proposeAction("s1", "T1", "A1", "D1", ActionRiskLevel.HIGH, "E1", "{}")
        gate.approveAction(proposal.id)
        gate.markExecuted(proposal.id)

        // Cannot approve
        val approveResult = gate.approveAction(proposal.id)
        assertTrue(approveResult.isFailure)

        // Cannot reject
        val rejectResult = gate.rejectAction(proposal.id, "Too late")
        assertTrue(rejectResult.isFailure)

        // Cannot modify
        val modifyResult = gate.modifyAction(proposal.id, """{"changed":true}""")
        assertTrue(modifyResult.isFailure)
    }

    @Test
    fun `cannot approve or modify REJECTED proposal`() = runTest {
        val proposal = gate.proposeAction("s1", "T1", "A1", "D1", ActionRiskLevel.HIGH, "E1", "{}")
        gate.rejectAction(proposal.id, "Not interested")

        val approveResult = gate.approveAction(proposal.id)
        assertTrue(approveResult.isFailure)

        val modifyResult = gate.modifyAction(proposal.id, "{}")
        assertTrue(modifyResult.isFailure)
    }

    @Test
    fun `operations on non-existent proposal return Failure`() = runTest {
        val fakeId = "prop_non_existent"
        assertTrue(gate.approveAction(fakeId).isFailure)
        assertTrue(gate.rejectAction(fakeId, "reason").isFailure)
        assertTrue(gate.modifyAction(fakeId, "payload").isFailure)
        assertTrue(gate.explainAction(fakeId).isFailure)
        assertTrue(gate.markExecuted(fakeId).isFailure)
        assertFalse(gate.isApproved(fakeId))
        assertNull(gate.getProposal(fakeId))
    }

    @Test
    fun `getProposalsForStep returns all matching proposals`() = runTest {
        val stepId = "target_step"
        gate.proposeAction(stepId, "Action 1", "A1", "D1", ActionRiskLevel.LOW, "E1", "{}")
        gate.proposeAction(stepId, "Action 2", "A2", "D2", ActionRiskLevel.HIGH, "E2", "{}")
        gate.proposeAction("other_step", "Action 3", "A3", "D3", ActionRiskLevel.LOW, "E3", "{}")

        val stepProposals = gate.getProposalsForStep(stepId)
        assertEquals(2, stepProposals.size)
        assertTrue(stepProposals.all { it.stepId == stepId })
    }

    @Test
    fun `thread safety - concurrent proposals and approvals do not corrupt state`() = runTest {
        val count = 50
        val createdProposals = (1..count).map { i ->
            async {
                gate.proposeAction("step_$i", "Title $i", "ACTION", "Desc $i", ActionRiskLevel.MEDIUM, "E", "{}")
            }
        }.awaitAll()

        assertEquals(count, gate.getAllProposals().size)

        // Approve half concurrently
        val half = createdProposals.take(count / 2)
        half.map { p ->
            async {
                gate.approveAction(p.id)
            }
        }.awaitAll()

        val pending = gate.getPendingProposals().first()
        assertEquals(count - (count / 2), pending.size)

        val approvedCount = createdProposals.count { gate.isApproved(it.id) }
        assertEquals(count / 2, approvedCount)
    }
}
