---
id: uesf-ux-ux-audit
name: UX Audit
version: 1.0.0
category: ux
kind: ux
purpose: Evaluate a product's user experience against its goals — flows, clarity, and usability — with prioritized, evidence-backed findings.
description: |
  Use when a flow feels confusing, conversion is low, or before a redesign. Produces
  a UX assessment: flow walkthroughs, usability findings prioritized by impact, and
  concrete improvement recommendations. Distinguishes opinion from evidence
  (heuristics, user signals, task analysis).
triggers:
  - condition: "A flow or feature is underperforming or reported as confusing"
  - condition: "A redesign or new flow is being scoped"
  - example_prompt: "Audit the onboarding flow for friction"
inputs:
  - "The flows/screens under audit and the product goal"
  - "Available signals (analytics, feedback, usability notes)"
outputs:
  - "Flow walkthroughs with friction points"
  - "Prioritized usability findings (impact × effort)"
  - "Concrete improvement recommendations"
dependencies:
  - "uesf-rs-research-synthesis"
context_requirements:
  - "Access to the product (running app/screens) and its goal"
  - "Any available user signals"
quality_gates:
  - "Findings separate evidence from opinion (each tagged)"
  - "Every finding has an impact and a fix direction"
  - "Flow walkthroughs cover the full task, not isolated screens"
validation:
  - unit
  - documentation
  - accessibility
rollback: "UX audit is a document; revert the doc commit. Recommendations are implemented as separate tasks with their own verification."
failure_recovery: "When evidence is missing (no analytics, no users to test), the audit says so and proposes the cheapest way to get signal — it never fabricates user evidence."
acceptance_criteria:
  - "Flow walkthroughs with friction points identified"
  - "Findings prioritized by impact with evidence/opinion tags"
  - "Recommendations actionable as tasks"
  - "Evidence gaps explicitly listed"
automation_hooks:
  - "Findings exported as tasks into the tracker"
  - "Usability-check checklist for UI changes (link to uesf-ax-accessibility-audit)"
mcp_tools:
  - "none"
cost:
  input_tokens: "~10k"
  output_tokens: "~4k"
  runtime_minutes: "30–90"
complexity: 3
maintainability_score: 4
scalability_score: 4
production_readiness: 4
related_skills:
  - "uesf-ui-ui-implementation"
  - "uesf-ax-accessibility-audit"
documentation: "docs/skill-spec.md"
---

# UX Audit

## Overview
"Confusing" is a symptom; the audit finds the cause. This skill walks complete task
flows, applies usability heuristics, and — critically — separates evidence from
opinion, so the team can act on what's real and know what's judgment. It synthesizes
the design judgment of the source ecosystem (emilkowalski's design engineering,
MengTo's design craft) into a structured, honest process.

## Execution Workflow
1. **Anchor on the goal** — What is this product/flow for, and what is the target
   behavior? An audit without a goal judges everything and fixes nothing.
2. **Walk the flows** — Trace each complete task from entry to completion: steps,
   decisions, feedback, exits. Note friction points at each step (ambiguity, extra
   clicks, hidden states, error handling).
3. **Apply heuristics** — Nielsen-style heuristics as a lens: visibility of system
   status, match to user expectations, error prevention and recovery, consistency,
   recognition over recall, flexibility.
4. **Gather what evidence exists** — Analytics, feedback, support tickets, session
   recordings, prior usability notes. Tag each finding: **evidence** (data-backed)
   or **opinion** (heuristic/judgment) — both are valid, but they are different
   currencies.
5. **Prioritize** — Impact (how much the finding hurts the goal) × effort to fix.
   Blockers first; opinion findings clearly marked for validation.
6. **Recommend** — Concrete, task-shaped recommendations per finding. Hand off to
   `uesf-ui-ui-implementation` and `uesf-ax-accessibility-audit` as applicable.
7. **List evidence gaps** — What would sharpen the audit (A/B test, usability
   session, analytics event) — the audit never fabricates user evidence.

## Quality Gates
- Findings tagged evidence vs. opinion.
- Every finding has impact and a fix direction.
- Flow walkthroughs cover complete tasks.
- Evidence gaps explicitly listed.

## Validation
- **Unit**: each finding traceable to a flow step or data point.
- **Documentation**: the audit is committed and linked from the roadmap.
- **Accessibility**: usability findings cross-checked with the a11y lens.

## Rollback
The audit is a document; revert the commit. Recommendations become separate tasks with
their own verification and rollback.

## Failure Recovery
Missing evidence is a finding, not a blocker: the audit says exactly what signal is
missing and proposes the cheapest way to get it (an event, a 3-user session, a
fake-door test). Never present assumption as data.

## Acceptance Criteria
- [ ] Flow walkthroughs with friction points identified.
- [ ] Findings prioritized by impact, tagged evidence/opinion.
- [ ] Recommendations actionable as tasks.
- [ ] Evidence gaps explicitly listed.

## Examples
### Example 1 — Onboarding audit
Goal: activate new users within 7 days. Walkthrough finds: step 3 (permissions) has no
explanation of why — 38% drop (evidence: funnel), the theme picker is unreachable by
keyboard (opinion + a11y cross-check), success feedback is absent (opinion). Findings
prioritized: permission-step copy + defer → high impact/medium effort (task); keyboard
reachability → a11y task. Evidence gap: no session recordings → recommend adding.

## Anti-patterns
- **Opinion-as-evidence**: presenting judgment as user data — tags are mandatory.
- **Screen-by-screen audit**: reviewing isolated screens instead of complete flows.
- **Finding without direction**: "this is bad" with no fix direction — every finding
  is task-shaped.
- **Ignoring the goal**: auditing everything and prioritizing nothing — the goal
  anchors the priorities.

## Testing Strategy
Validated with fixture flows containing planted friction; scoring measures finding
completeness and evidence/opinion honesty. See `docs/testing-strategy.md`.

## Future Extensions
- Analytics-query templates per audit (funnel, drop-off, rage-click).
- Usability-session scripts as reusable templates.
