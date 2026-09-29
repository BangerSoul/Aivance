---
id: uesf-co-planning
name: Planning-First Execution
version: 1.0.0
category: co
kind: core
purpose: Decompose any non-trivial request into a verified, executable plan before any implementation begins.
description: |
  Use when starting a new feature, a multi-step task, an ambiguous request, or any work with
  more than ~3 logical steps. Produces an implementation-ready plan document with sequenced
  tasks, per-task acceptance criteria, and a risk register. Works in any repository, language,
  or agent harness. Do not use for trivial one-step changes where the plan is obvious.
triggers:
  - condition: "A new feature, refactor, bugfix with multiple causes, or research task is requested"
  - condition: "The request is ambiguous, under-specified, or spans multiple files/modules"
  - example_prompt: "Plan the implementation of a background sync feature across app, worker, and API"
inputs:
  - "Task statement or user request"
  - "Repository context (module map, conventions)"
  - "Constraints (time, platform, compatibility)"
outputs:
  - "Plan document: phases, tasks, dependencies, acceptance criteria"
  - "Risk register with mitigations"
  - "Explicitly stated assumptions and open questions"
dependencies: []
context_requirements:
  - "Access to the codebase or a reliable description of it"
  - "Clarifying questions allowed before the plan is finalized"
quality_gates:
  - "Every task has a measurable acceptance criterion"
  - "No TBD/TODO placeholder steps; anything unknown is an open question, not a plan item"
  - "Task sequence respects technical dependencies (topological order)"
validation:
  - unit
  - documentation
rollback: "The plan is a document: revert to the previous revision in version control; no code or data is touched."
failure_recovery: "If requirements change mid-execution, re-run the planning skill on the delta and re-validate affected tasks only."
acceptance_criteria:
  - "Plan decomposes the request into tasks of roughly 2–20 minutes each"
  - "Each task lists exact files/interfaces it consumes and produces"
  - "Reviewer (human or agent) can trace every requirement to at least one task"
  - "Risk register covers top 3 risks with mitigations"
automation_hooks:
  - "Pre-implementation gate in workflows/triage-implement-verify.md"
  - "Validator rule: tasks must not contain unresolved placeholders"
mcp_tools:
  - "none"
cost:
  input_tokens: "~8k"
  output_tokens: "~4k"
  runtime_minutes: "5–15"
complexity: 2
maintainability_score: 5
scalability_score: 5
production_readiness: 5
related_skills:
  - "uesf-gv-project-governance"
  - "uesf-ar-solution-architecture"
  - "uesf-rs-research-synthesis"
documentation: "docs/skill-spec.md"
---

# Planning-First Execution

## Overview
Planning is the highest-leverage step in agentic engineering. A verified plan converts an
ambiguous request into small, independently verifiable tasks — preventing scope drift,
rework, and the "guess-and-check" failure mode observed across agent coding benchmarks.
This skill is the entry point of the UESF core loop (Plan → Implement → Verify → Review).

## Execution Workflow
1. **Clarify** — Restate the request; ask one question at a time for ambiguity; capture
   explicit assumptions. Confirm success looks like *before* planning.
   - Identify the user's actual goal vs. the literal ask.
   - Record constraints: platform, compatibility, time, conventions.
2. **Gather context** — Map the affected area: files, modules, interfaces, tests, and
   existing conventions. Use `uesf-ra-repository-analysis` for unfamiliar codebases.
3. **Decompose** — Break the work into tasks of 2–20 minutes each. Each task states:
   exact files it touches, interfaces it consumes and produces, and how it will be verified.
   - Apply single-responsibility: one task = one verifiable outcome.
   - Prefer a breadth-first (thin vertical slice) sequence over depth-first.
4. **Sequence** — Order tasks topologically: dependencies first, risky/unknown items early.
   - Identify the spike or proof-of-concept task when the biggest risk is uncertainty.
5. **Define acceptance criteria** — Every task gets a measurable AC in "given/when/then"
   or checklist form. A task without an AC is not ready to be planned.
6. **Risk register** — List the top 3 risks (technical, integration, scope) with mitigations.
7. **Sign-off** — Present the plan for explicit approval. Open questions are listed
   separately and never embedded as plan items.

## Quality Gates
- Every task has a measurable acceptance criterion.
- No TBD/TODO/"implement later" placeholders — anything unknown is an open question.
- Task sequence respects technical dependencies (topological order).
- The plan fits on one screen: if it exceeds ~15 tasks, add a phase layer.

## Validation
- **Unit**: for each task, ask "can a fresh agent complete this with no further
  clarification?" If no, the task is under-specified — split or clarify it.
- **Integration**: walk the full plan end-to-end; every output of task N is consumed by
  task N+1 without missing links.
- **Regression**: verify the plan does not silently change unrelated behavior (scope trace).
- **Documentation**: the plan is a committed artifact (`docs/plans/<name>.md` or the
  workflow's designated location), reviewable by humans and agents alike.

## Rollback
The plan is a document only. Revert to the previous revision via version control. No code,
data, or configuration is touched, so rollback is instantaneous and zero-cost.

## Failure Recovery
- Requirements changed: re-plan only the delta; re-validate affected tasks; keep the
  original plan as a historical revision.
- Blocked by an unknown: convert the blocker into a spike task with a time-box and a
  go/no-go criterion, and run it before dependent tasks.

## Acceptance Criteria
- [ ] The request is decomposed into tasks of roughly 2–20 minutes each.
- [ ] Each task lists the exact files and interfaces it consumes and produces.
- [ ] Every requirement can be traced to at least one task (and vice versa).
- [ ] The top 3 risks are identified with concrete mitigations.
- [ ] The plan received explicit approval before implementation began.

## Examples
### Example 1 — Background sync feature
A user asks to "add background sync for the job alerts feature." The skill produces:
tasks for data-model delta (2 min), repository method + unit test (10 min), worker
registration (8 min), API contract update (10 min), E2E verification (15 min), each with
ACs, sequenced so the data-model change precedes the worker, with a risk register entry
for battery/network constraints.

## Anti-patterns
- **Plan-as-essay**: long prose with no tasks or ACs — nothing is verifiable; always
  produce discrete tasks with criteria.
- **Placeholder tasks**: "implement the rest later" hides risk; convert to open questions
  or spikes.
- **Over-planning trivia**: planning a one-line fix wastes tokens; skip this skill when
  the plan is obvious (the skill's own trigger condition says so).

## Testing Strategy
The skill itself is validated by dry-run exercises on real repos (`tests/` fixtures),
checking that produced plans satisfy the quality gates above. See
`docs/testing-strategy.md` for the framework-wide strategy.

## Future Extensions
- Time-boxed estimation: attach cost/effort ranges to tasks from historical execution data.
- Plan templates per delivery type (feature, bugfix, migration, research).
