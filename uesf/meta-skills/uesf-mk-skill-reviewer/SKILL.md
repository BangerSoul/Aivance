---
id: uesf-mk-skill-reviewer
name: Skill Reviewer
version: 1.0.0
category: mk
kind: meta
purpose: Review skills as engineering artifacts — clarity, correctness, generalization, and spec compliance — with prioritized findings.
description: |
  Use when a skill draft or change needs a second pair of eyes before promotion.
  Produces prioritized findings (blocker/major/minor/nit), a verdict, and a bounded
  fix loop. Reviews the skill's instructions for correctness and testability, not
  just its formatting.
triggers:
  - condition: "A skill is drafted, modified, or being promoted"
  - condition: "A skill's instructions are unclear or have never been executed end-to-end"
  - example_prompt: "Review the new intake skill before promotion"
inputs:
  - "The skill (SKILL.md + supporting files)"
  - "Its dependencies and the spec"
outputs:
  - "Prioritized findings with specifics"
  - "Verdict and rationale"
  - "Fix-loop evidence (bounded rounds)"
dependencies:
  - "uesf-mk-skill-validator"
context_requirements:
  - "The validator passes (or the review starts from its errors)"
  - "Access to the skill's dependencies and examples"
quality_gates:
  - "Every finding cites the specific instruction it concerns"
  - "Executability checked: instructions are unambiguous enough to follow"
  - "Fix loop bounded (circuit breaker)"
validation:
  - unit
  - integration
  - certification
rollback: "Review is read-only; the bounded fix loop limits churn. Revert a review's resulting edits like any commit."
failure_recovery: "If a review finds instructions that cannot be executed as written, the skill returns to the generator with consolidated findings — never promoted 'as is'."
acceptance_criteria:
  - "Findings prioritized and specific"
  - "Executability assessed (walked through, not just read)"
  - "Verdict recorded"
  - "Fix loop bounded and documented"
automation_hooks:
  - "Validator runs before review; re-run after fixes"
  - "Review checklist enforced for promotions"
mcp_tools:
  - "none"
cost:
  input_tokens: "~8k"
  output_tokens: "~3k"
  runtime_minutes: "10–30"
complexity: 2
maintainability_score: 5
scalability_score: 5
production_readiness: 5
related_skills:
  - "uesf-co-review"
  - "uesf-mk-skill-benchmarker"
documentation: "docs/skill-spec.md"
---

# Skill Reviewer

## Overview
A skill is a document that instructs an agent; the highest-value review is
*executability* — can an agent actually follow these instructions to the stated
outcome? This meta-skill reviews skills as engineering artifacts: spec compliance,
clarity, correctness, generalization, and testability — with the same prioritized
findings and bounded fix loop as code review.

## Execution Workflow
1. **Pre-check** — Run the validator; review starts from zero-error or from the
   error list (unresolved errors are the first findings).
2. **Read for clarity** — Is the purpose one sentence? Are triggers discoverable
   (description says *when*, not *how*)? Are inputs/outputs concrete?
3. **Walk the workflow** — Simulate executing the skill step-by-step: can an agent
   with the stated inputs reach the stated outputs? Note ambiguities, missing
   branches, and unverifiable steps.
4. **Check generalization** — Is it model-agnostic and repository-independent? Does
   it leak vendor or repo assumptions?
5. **Verify composition** — Do declared dependencies and related skills exist and
   fit? Are overlapping skills cross-referenced?
6. **Produce findings** — Blocker (instructions unexecutable / wrong), major
   (ambiguity that will cause divergence), minor (improvement), nit (style). Each
   cites the specific instruction.
7. **Bound the fix loop** — Author fixes; re-review the delta only; circuit breaker
   after N rounds (escalate to governance).

## Quality Gates
- Findings cite specific instructions.
- Executability assessed by walking the workflow.
- Fix loop bounded.
- Verdict recorded.

## Validation
- **Unit**: spot-check findings against the skill text.
- **Integration**: the skill's examples are executable in principle (dependencies
  resolvable).
- **Certification**: a passing review is a promotion gate.

## Rollback
Review is read-only. Edits from the fix loop are ordinary commits, revertible
individually.

## Failure Recovery
Instructions that cannot be executed as written are blockers: return the skill to the
generator with consolidated findings. Never promote a skill whose workflow nobody has
walked through.

## Acceptance Criteria
- [ ] Findings prioritized and specific.
- [ ] Executability assessed.
- [ ] Verdict recorded.
- [ ] Fix loop bounded and documented.

## Examples
### Example 1 — Intake review
A new skill passes the validator; review walks the workflow and finds: the trigger
description summarizes the workflow (blocker — agents will shortcut), step 4 depends
on a tool not declared (major), example is stale (minor). Author fixes; reviewer
re-views the delta; promoted.

## Anti-patterns
- **Format-only review**: checking frontmatter and skipping executability — the walk
  is the point.
- **Rubber-stamp promotion**: promoting every draft — verdicts are earned.
- **Unbounded rounds**: re-reviewing the whole skill each round — delta only.
- **Vendor leaks**: promoting skills that assume one model or repo.

## Testing Strategy
Validated with fixture skills containing planted executability defects; scored on
detection. See `docs/testing-strategy.md`.

## Future Extensions
- Subagent-based execution trials (run the skill against a fixture task).
- Review checklists per kind (core/engineering/meta).
