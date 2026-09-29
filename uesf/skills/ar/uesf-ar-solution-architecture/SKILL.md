---
id: uesf-ar-solution-architecture
name: Solution Architecture
version: 1.0.0
category: ar
kind: engineering
purpose: Design a solution architecture that satisfies requirements with explicit trade-offs, recorded decisions, and verifiable quality attributes.
description: |
  Use when a system, feature, or integration needs a design before implementation:
  choosing components, boundaries, data flow, and failure behavior. Produces an
  architecture with option analysis, an ADR record, and quality-attribute targets.
  Generalizes cloud, on-prem, and hybrid designs. Do not use for task-level planning —
  that is uesf-co-planning.
triggers:
  - condition: "A new system, subsystem, or non-trivial integration is being designed"
  - condition: "A choice between significant alternatives (components, protocols, storage) must be made"
  - example_prompt: "Design the architecture for syncing local job data with the cloud API"
inputs:
  - "Requirements and constraints (scale, latency, budget, compliance)"
  - "Existing system context and integration points"
  - "Team conventions and technology baseline"
outputs:
  - "Architecture design: components, boundaries, data flow, failure modes"
  - "Option analysis with trade-offs (ADR)"
  - "Quality-attribute targets and how they will be measured"
dependencies:
  - "uesf-co-planning"
  - "uesf-rs-research-synthesis"
context_requirements:
  - "A written understanding of requirements (spec, ticket, or clarified notes)"
  - "Access to the existing codebase or system description"
quality_gates:
  - "Every significant alternative is documented with a recorded decision (ADR)"
  - "Failure modes for each component are identified with mitigations"
  - "Quality attributes (perf, security, cost) are measurable targets, not adjectives"
validation:
  - unit
  - documentation
  - security
  - performance
rollback: "Architecture is documentation: ADRs and design docs are versioned; reverting a decision reverts the document, not the code."
failure_recovery: "When a decision proves wrong during implementation, write a new ADR superseding the old one and re-plan the affected tasks — architecture is revisable, not frozen."
acceptance_criteria:
  - "Component diagram or description with clear boundaries and data flow"
  - "One ADR per significant decision, each with options, trade-offs, and rationale"
  - "Top failure modes listed with mitigations"
  - "Quality attributes expressed as measurable targets"
automation_hooks:
  - "ADR template enforced by tools/skill_scaffold.py and policies/contribution.md"
  - "Architecture review gate wired into the delivery workflow"
mcp_tools:
  - "none"
cost:
  input_tokens: "~15k"
  output_tokens: "~6k"
  runtime_minutes: "30–90"
complexity: 4
maintainability_score: 4
scalability_score: 4
production_readiness: 4
related_skills:
  - "uesf-gv-project-governance"
  - "uesf-ai-agent-design"
documentation: "docs/skill-spec.md"
---

# Solution Architecture

## Overview
Architecture is the set of decisions that are expensive to change later. This skill makes
those decisions explicit, compared, and recorded — borrowing the ADR discipline and the
"survey not rescue" humility of the strongest source frameworks (mattpocock's
improve-codebase-architecture; google/skills' well-architected recipes) and generalizing
them to any system, not just cloud products.

## Execution Workflow
1. **Anchor on requirements** — Restate the functional requirements and the measurable
   quality attributes (latency, throughput, cost, availability, security). Refuse to
   design against adjectives.
2. **Map the context** — Identify the existing system, integration points, constraints
   (budget, compliance, team skills), and the 2–3 hardest problems.
3. **Generate alternatives** — For each significant decision (storage, communication,
   deployment topology, data flow), list 2–3 genuine alternatives with their trade-offs.
   Use `uesf-rs-research-synthesis` when alternatives depend on external facts.
4. **Decide with evidence** — Choose the alternative that best meets the quality-attribute
   targets; record the decision as an ADR (context, options, trade-offs, decision,
   consequences).
5. **Design the components** — Define components, boundaries, interfaces, and data flow.
   Identify failure modes per component and their mitigations (retries, queues, fallbacks,
   circuit breakers).
6. **Set verification** — Define how each quality attribute will be measured (load test,
   security scan, cost model) and wire those into the plan as tasks.
7. **Review** — Pass the design through `uesf-co-review` before implementation starts.

## Quality Gates
- Every significant alternative has a recorded decision with rationale.
- Each component's failure modes are identified with mitigations.
- Quality attributes are measurable targets (e.g., "p95 < 500ms under 1k RPS"), not
  adjectives.
- No undecided "open architecture" items block implementation.

## Validation
- **Unit**: each decision's rationale is checkable against the requirement it serves.
- **Integration**: the design traces end-to-end against every requirement.
- **Security**: threat model (STRIDE) reviewed for the chosen components.
- **Performance**: targets are load-testable; a spike task validates the riskiest target.
- **Documentation**: ADRs and the design doc are committed and referenced from the plan.

## Rollback
Architecture decisions are documents. Reverting a decision means superseding the ADR —
zero code or data is touched.

## Failure Recovery
A decision that fails during implementation is normal. Write a new ADR superseding the
old one (with the evidence of failure), re-plan the affected tasks via
`uesf-co-planning`, and continue from the last verified state.

## Acceptance Criteria
- [ ] Components, boundaries, interfaces, and data flow are explicit.
- [ ] One ADR per significant decision: options, trade-offs, rationale, consequences.
- [ ] Top failure modes per component listed with mitigations.
- [ ] Quality attributes are measurable targets with verification tasks.

## Examples
### Example 1 — Local sync architecture
Requirement: "sync local job data with the cloud API; offline-first." The skill produces
an ADR choosing an operation-log + idempotent API design over full-state sync (trade-off:
more moving parts vs. 100x less bandwidth), a component diagram (local store → sync
worker → API client), failure mitigations (idempotency keys, exponential backoff, conflict
resolution), and a target: "p95 sync of 500 records < 30s on a mid-range device" with a
benchmark task.

## Anti-patterns
- **Architecture-by-vibes**: picking components with no recorded comparison — ADRs are
  mandatory.
- **Undecided systems**: leaving the "hard part" open — the hardest decision goes first.
- **Over-abstraction**: designing for hypothetical scale (Karpathy's overcomplication
  failure mode) — design for the stated targets and record what would trigger re-design.
- **Architecture as diagram art**: diagrams with no failure analysis or verification plan.

## Testing Strategy
Validated with architecture-review fixtures: seeded designs with missing trade-offs or
unmeasurable attributes, scored on detection. See `docs/testing-strategy.md`.

## Future Extensions
- Cloud-specific recipe libraries (adapted from well-architected frameworks).
- Automated architecture-to-task traceability.
