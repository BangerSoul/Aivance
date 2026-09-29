---
id: uesf-gv-project-governance
name: Project Governance
version: 1.0.0
category: gv
kind: engineering
purpose: Keep work aligned with its goals — milestones, risk, scope, and decision records — without the ceremony.
description: |
  Use when a multi-task effort needs tracking: milestones, risk register, scope
  control, decision records, and progress reporting. Produces a lightweight governance
  state (milestones, risks, decisions, status) that any agent or human can read.
  Governance earns its keep by catching drift, not by bureaucracy.
triggers:
  - condition: "Work spans multiple tasks, sessions, or agents"
  - condition: "Scope, risk, or decision history needs to be tracked and communicated"
  - example_prompt: "Track the sync feature project: milestones, risks, and decisions"
inputs:
  - "Goal, milestones, and known risks"
  - "Ongoing status from planning/implementation runs"
outputs:
  - "Governance state: milestones, risks, decisions (ADR index), status"
  - "Scope-change and risk-escalation records"
dependencies:
  - "uesf-co-planning"
context_requirements:
  - "A goal statement and a place to persist governance state (repo docs or tracker)"
quality_gates:
  - "Every milestone has an exit criterion and a status"
  - "Risk register entries have owners, mitigations, and review dates"
  - "Decisions are recorded (ADR index), including scope changes"
validation:
  - unit
  - documentation
rollback: "Governance is documentation: revert the state file's commit; no code or data touched."
failure_recovery: "When a milestone slips or a risk materializes, governance records the change, re-baselines the plan, and escalates with evidence — never hides the slip."
acceptance_criteria:
  - "Milestones with exit criteria and current status"
  - "Risk register maintained with owners and mitigations"
  - "Decision log (ADR index) current, including scope changes"
  - "Status readable by a human or agent in minutes"
automation_hooks:
  - "Status snapshot generated at workflow checkpoints"
  - "Risk review reminder on milestone transitions"
mcp_tools:
  - "none"
cost:
  input_tokens: "~6k"
  output_tokens: "~3k"
  runtime_minutes: "5–15 per checkpoint"
complexity: 2
maintainability_score: 5
scalability_score: 5
production_readiness: 4
related_skills:
  - "uesf-co-planning"
  - "uesf-ce-certification-audit"
documentation: "docs/skill-spec.md"
---

# Project Governance

## Overview
Multi-task, multi-session work drifts without a lightweight steering mechanism. This
skill maintains a minimal governance state — milestones, risks, decisions, status —
that any agent or human can read and update. The point is to catch drift early, not to
create process. It borrows the discipline of plan-scoped tracking (superpowers'
progress ledgers, mattpocock's triage state machines) and generalizes it.

## Execution Workflow
1. **Establish the state** — Write the governance document: goal, milestones with exit
   criteria, initial risk register, decision log (ADR index). Keep it to one file that
   is the source of truth.
2. **Checkpoint on transitions** — At milestone transitions, plan changes, and scope
   changes: update status, re-baseline affected milestones, and refresh the risk
   register (review dates, owners, mitigations).
3. **Record decisions** — Every significant decision (including scope changes and
   reversals) appends to the decision log with date and rationale. Reversals are
   recorded as supersessions, never erased.
4. **Escalate with evidence** — When a milestone slips or a risk materializes: record
   it, adjust the plan (`uesf-co-planning` re-baseline), and escalate the evidence to
   the stakeholders — never hide a slip in status cosmetics.
5. **Report status** — Produce a status snapshot (milestones, risks, decisions,
   blockers) readable in minutes by a human or agent.

## Quality Gates
- Every milestone has an exit criterion and a current status.
- Risk register entries have owners, mitigations, and review dates.
- Decision log is current, including scope changes and reversals.
- Status is a single readable artifact, not scattered chat.

## Validation
- **Unit**: each milestone's exit criterion is checkable.
- **Integration**: the plan's tasks trace to milestones (no orphan work).
- **Documentation**: the state file is committed and linked from the plan.

## Rollback
Governance is documentation. Reverting the state file's commit restores the prior
state; nothing else is affected.

## Failure Recovery
Slips and materialized risks are governance's core job: record, re-baseline, escalate
with evidence. The status snapshot always shows the true state — governance fails only
when it prettifies reality.

## Acceptance Criteria
- [ ] Milestones with exit criteria and status.
- [ ] Risk register maintained with owners and mitigations.
- [ ] Decision log current, including scope changes.
- [ ] Status readable in minutes.

## Examples
### Example 1 — Sync feature tracking
Governance state records: milestone 1 (data model) done, milestone 2 (worker) in
progress with a slip (battery constraint discovered) recorded with a re-baseline, risk
register updated (network policy variance — owner: platform team, mitigation: cap
sync frequency, review date next sprint), decision log with the "operation-log over
full-state sync" ADR. Status snapshot read in under a minute by the PM.

## Anti-patterns
- **Process theater**: meetings and docs with no decisions — governance exists to catch
  drift.
- **Hiding slips**: smoothing over delays in status reports — slips are recorded and
  escalated with evidence.
- **Orphan milestones**: milestones with no task traceability — the plan maps to them.
- **Scattered decisions**: decisions living in chat instead of the decision log.

## Testing Strategy
Validated with governance fixtures: planted scope creep and hidden slips, scored on
detection and re-baseline quality. See `docs/testing-strategy.md`.

## Future Extensions
- Automatic status snapshots at workflow checkpoints (automation hook).
- Risk-review scheduling tied to milestone transitions.
