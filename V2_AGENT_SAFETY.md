# AiVance V2 — Controlled Agent Runtime Safety & Governance

**Document Type:** AI Safety Policy Specification & Autonomous Agent Governance  
**Target Repository:** `IamAzmathullaShaikh/Aivance`  
**Package Root:** `com.bangersoul.aivance.core.domain.agent`  
**Status:** Approved AI Safety Standard  

---

## 1. Core AI Governance Philosophy

AiVance rejects uncontrolled, fully autonomous agent loops. In a high-stakes domain such as a person's career—where actions directly impact professional reputation, legal contracts, and personal relationships—artificial intelligence must operate under strict, deterministic, and auditable human supervision:

$$\text{LLM Reasoning} \longrightarrow \text{Structured Proposal} \longrightarrow \text{Safety Scanned} \longrightarrow \mathbf{\text{Human Approval Gate}} \longrightarrow \text{Execution} \longrightarrow \text{Audit Receipt}$$

Under no circumstances may arbitrary LLM text completions or tool outputs directly execute destructive or external mutations.

---

## 2. The 4 Action Capability Classifications

Every agent action is categorized at creation into one of four capability classes:

```
┌─────────────────────────┐
│        READ_ONLY        │ ──► Automatically Permitted
│ (Queries, inspections)  │     (No user intervention needed)
└─────────────────────────┘
            │
┌─────────────────────────┐
│     LOCAL_MUTATION      │ ──► Optionally Automatic (Configurable)
│ (Bookmarks, preferences)│     (Safe local state updates)
└─────────────────────────┘
            │
┌─────────────────────────┐
│  EXTERNAL_SIDE_EFFECT   │ ──► ALWAYS Requires Explicit Human Approval
│ (Emails, applications)  │     (Halts execution; awaits user confirmation)
└─────────────────────────┘
            │
┌─────────────────────────┐
│       DESTRUCTIVE       │ ──► ALWAYS Requires Explicit Human Approval + Confirmation Prompt
│ (Wiping history, delete)│     (Double-confirmation gate with impact analysis)
└─────────────────────────┘
```

---

## 3. The Human Approval Gate State Machine

The `HumanApprovalGate` manages deterministic state transitions for all proposed actions:

```
                  ┌──────────────┐
                  │   PROPOSED   │
                  └──────┬───────┘
                         │
                         ▼
                  ┌──────────────┐
                  │  EXPLAINED   │
                  └──────┬───────┘
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
  │  APPROVED   │ │  REJECTED   │ │  MODIFIED   │
  └──────┬──────┘ └─────────────┘ └─────────────┘
         │
         ▼
  ┌─────────────┐
  │  EXECUTED   │
  └─────────────┘
```

### Safety Invariants Enforced by Code:
1. **No Execution Without Approval**: Calling `executeAction()` on any proposal that is in `PROPOSED`, `EXPLAINED`, `REJECTED`, or `MODIFIED` state throws an immediate `IllegalStateException`.
2. **Idempotent Single Execution**: A proposal in `EXECUTED` state cannot be executed again.
3. **Audit Immutability**: Executed or rejected proposals cannot be reverted or approved retroactively.

---

## 4. Prompt Injection & Malicious Content Defenses (`AgentSafetyPolicy.kt`)

* **Heuristic Pattern Defense**: Inspects user prompts and external documents for injection keywords (`ignore previous instructions`, `disregard prior system prompt`, `reveal all api keys`, `system prompt override`).
* **Content Sanitization**: External job descriptions and incoming recruiter communications have script tags, SQL injection fragments, and prompt override commands replaced with `[BLOCKED_SUSPICIOUS_CONTENT]` before entering the AI context pipeline.
* **Runaway Plan & Loop Defense**:
  - `MAX_PLAN_STEPS_THRESHOLD = 30`: Any plan generated with >30 steps is blocked immediately.
  - `MAX_REPEATED_ACTIONS_THRESHOLD = 3`: Any plan attempting the identical action >3 times is halted as a loop.

---

## 5. Tamper-Evident Execution Receipts & Undo Tokens

Every successful action generates an immutable `ExecutionReceipt`:
* `receiptId: String` (Unique trace identifier)
* `proposalId: String` (Associated approval proposal)
* `timestamp: Long`
* `status: ExecutionStatus` (`SUCCESS`, `FAILURE`, `CANCELLED`)
* `undoToken: String?` (Deterministic rollback token enabling one-click undo of local mutations)
* `details: String` (Action execution log summary)
