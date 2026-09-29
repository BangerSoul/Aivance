---
id: uesf-ai-agent-design
name: Agent Architecture & Collaboration
version: 1.0.0
category: ai
kind: ai
purpose: Design multi-agent systems — roles, orchestration, hand-offs, and verification — with explicit failure and cost control.
description: |
  Use when designing a system with one or more AI agents: orchestrators, subagents,
  parallel workers, or agent-assisted workflows. Produces an agent architecture with
  roles, boundaries, context budgets, verification points, and cost estimates.
  Borrows the strongest subagent patterns (superpowers' SDD, dispatch-parallel-agents)
  and generalizes them model-agnostically.
triggers:
  - condition: "A workflow will use multiple agents, subagents, or agent-as-a-service"
  - condition: "An existing single-agent flow needs parallelization or isolation"
  - example_prompt: "Design an agent system that reviews every PR with an implementer and a reviewer subagent"
inputs:
  - "The workflow/goal and its constraints (latency, cost, quality)"
  - "Available agent capabilities and models"
outputs:
  - "Agent architecture: roles, orchestration, hand-off protocol"
  - "Context budget and cost estimate per agent"
  - "Verification points and failure/escalation design"
dependencies:
  - "uesf-pe-prompt-engineering"
  - "uesf-ai-evaluation"
context_requirements:
  - "A clear workflow description with measurable success criteria"
  - "Knowledge of the agents/models available"
quality_gates:
  - "Every agent has a single responsibility and an exit/verification criterion"
  - "Context budgets are explicit (per-agent token ceilings, isolation)"
  - "Failure and escalation paths designed before implementation"
validation:
  - unit
  - integration
  - performance
  - security
rollback: "Agent design is a document (architecture + prompt set); revert the design commit or the per-agent prompt versions."
failure_recovery: "When an agent misbehaves in production, the escalation path (re-run, re-dispatch fresh, human-in-the-loop) is already designed — execute it, then post-incident."
acceptance_criteria:
  - "Roles, orchestration, and hand-offs defined"
  - "Context and cost budgets explicit per agent"
  - "Verification points and escalation paths designed"
  - "Success criteria measurable and evaluated"
automation_hooks:
  - "Agent runs wired to observability (traces, token counts)"
  - "Evaluation harness re-run on agent prompt changes"
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
  - "uesf-ai-model-integration"
  - "uesf-gv-project-governance"
documentation: "docs/skill-spec.md"
---

# Agent Architecture & Collaboration

## Overview
The research shows the winning agent designs are about *isolation and verification*,
not clever prompts: fresh subagents per task with isolated context (superpowers'
subagent-driven development), single-responsibility roles, progress ledgers outside
conversation history, and bounded review loops. This skill turns those patterns into a
general, model-agnostic architecture discipline for any multi-agent system.

## Execution Workflow
1. **Define the workflow and success** — The goal, its measurable success criteria,
   and the constraints (latency, cost, quality floor). If success isn't measurable,
   the agent system can't be evaluated.
2. **Assign roles** — Decompose into agents with single responsibilities: an
   orchestrator routes and tracks; workers execute isolated tasks; a verifier checks
   outputs. Each role has an exit criterion (when is its job done?).
3. **Design hand-offs** — Every hand-off is an explicit artifact: a task brief with
   inputs, expected outputs, and acceptance criteria; a result report. No agent
   depends on another's conversation history.
4. **Budget context** — Per-agent token ceilings and context isolation (fresh context
   per task where possible). Estimate cost per run and per stage.
5. **Place verification points** — After each role's exit, a check (test run, eval,
   reviewer) gates the pipeline. The verifier is never the same instance that
   produced the output.
6. **Design failure paths** — For each stage: what happens on failure (re-run with
   evidence, re-dispatch fresh, escalate to human) and the circuit breaker (bounded
   retries, then escalation). Never infinite loops.
7. **Evaluate and iterate** — Run the success criteria through `uesf-ai-evaluation`;
   iterate on the weakest stage. Ship with observability (traces, token counts).

## Quality Gates
- Every agent has a single responsibility and an exit criterion.
- Context budgets explicit; isolation by design.
- Failure/escalation paths designed before implementation.
- Success criteria measurable and evaluated.

## Validation
- **Unit**: each agent's exit criterion testable in isolation.
- **Integration**: end-to-end run of the orchestrated workflow.
- **Performance**: token/cost budget measured per run.
- **Security**: prompt-injection and data-boundary review of hand-offs.

## Rollback
The design is a document plus versioned prompts. Revert the design commit or the
offending agent's prompt version. Because hand-offs are artifacts (not shared context),
stages are independently revertible.

## Failure Recovery
Agent misbehavior follows the designed escalation path — re-run with evidence,
re-dispatch to a fresh instance, or escalate to human — then post-incident
root-causing (`uesf-co-debugging` on the agent's output traces). The circuit breaker
guarantees bounded cost on failure.

## Acceptance Criteria
- [ ] Roles, orchestration, and hand-offs defined with exit criteria.
- [ ] Context and cost budgets explicit per agent.
- [ ] Verification points and escalation paths designed.
- [ ] Success criteria measured by the evaluation harness.

## Examples
### Example 1 — PR review agent system
Orchestrator reads the PR, dispatches an implementer-reproducer subagent (isolated
context, task brief with diff + tests), then a verifier subagent (fresh context)
checks behavior and design, with a bounded fix loop (3 rounds, then human).
Hand-offs: brief.md and findings.md artifacts. Token budget: ~40k per PR stage,
measured. Eval set: 50 planted-defect PRs, scored on detection.

## Anti-patterns
- **Shared-context sprawl**: agents passing conversation history — hand-offs are
  artifacts.
- **Verifier-of-its-own-work**: the same instance that produced output verifies it.
- **Unbounded retries**: no circuit breaker — cost explodes on persistent failure.
- **Agent theater**: agents with no exit criteria and no measured success.

## Testing Strategy
Validated with orchestration fixtures (workflow sketches with missing exit criteria and
unbounded loops) scored on detection. See `docs/testing-strategy.md`.

## Future Extensions
- Cost-optimization patterns (caching, context recycling) catalogued by stage.
- Cross-harness portability notes per agent platform.
