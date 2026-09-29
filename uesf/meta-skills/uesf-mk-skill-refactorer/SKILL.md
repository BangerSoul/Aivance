---
id: uesf-mk-skill-refactorer
name: Skill Refactorer
version: 1.0.0
category: mk
kind: meta
purpose: Restructure a skill without changing its capability — split, merge sections, reorganize references — verified by the validator and benchmarks.
description: |
  Use when a skill's structure impedes maintenance or composition: too long, tangled
  sections, misplaced references, duplicated content. Produces a behavior-preserving
  restructure with unchanged capability and verified equivalence. The skill-level
  twin of uesf-co-refactoring.
triggers:
  - condition: "A skill exceeds healthy size or mixes concerns"
  - condition: "Two skills share duplicated content that should be shared"
  - example_prompt: "Refactor the debugging skill to externalize its reference material"
inputs:
  - "The skill(s) to restructure"
  - "Validator and benchmark access for equivalence verification"
outputs:
  - "Restructured skill (same capability, better structure)"
  - "Equivalence evidence (validator + benchmark unchanged)"
  - "Version bump and changelog"
dependencies:
  - "uesf-mk-skill-validator"
  - "uesf-mk-skill-benchmarker"
context_requirements:
  - "A benchmark task set that exercises the skill's capability"
  - "The spec and template for structural rules"
quality_gates:
  - "Capability unchanged: benchmark results equivalent within tolerance"
  - "Validator green before and after"
  - "Structural goals met (size, section cohesion, reference layout)"
validation:
  - unit
  - regression
  - certification
rollback: "Restructures are versioned commits; revert to the previous version via the version manager."
failure_recovery: "If the benchmark shifts beyond tolerance, revert and re-approach with smaller structural steps."
acceptance_criteria:
  - "Benchmark equivalence within tolerance"
  - "Validator green before and after"
  - "Structural goals met and recorded"
  - "Changelog documents the restructure"
automation_hooks:
  - "Validator + benchmark re-run on any restructure"
  - "Size and duplication checks in the validator (warnings)"
mcp_tools:
  - "none"
cost:
  input_tokens: "~8k"
  output_tokens: "~3k"
  runtime_minutes: "15–45"
complexity: 3
maintainability_score: 5
scalability_score: 5
production_readiness: 4
related_skills:
  - "uesf-mk-skill-optimizer"
  - "uesf-co-refactoring"
documentation: "docs/skill-spec.md"
---

# Skill Refactorer

## Overview
Structure decays; capabilities shouldn't. This meta-skill restructures skills —
splitting oversized ones, consolidating duplicated content, externalizing references —
with the same behavior-preservation discipline code refactoring demands: benchmark
equivalence and validator green before and after.

## Execution Workflow
1. **Characterize the capability** — Run the skill's benchmark and the validator on
   the current version; record the baseline. This is the "tests" for the restructure.
2. **Plan the structure** — Identify the structural goals: split an oversized
   SKILL.md, externalize reference material to `references/`, extract shared content
   into a dependency skill, or reorganize sections. One structural goal per pass.
3. **Restructure stepwise** — Each step: move content, update cross-references,
   keep every required section and frontmatter key. Re-run the validator after each
   step.
4. **Verify equivalence** — Re-run the benchmark; capability must be equivalent
   within tolerance. The restructure changed structure, not behavior.
5. **Version and record** — Bump the version (patch/minor per the version policy),
   write the changelog, and record the structural goals met.

## Quality Gates
- Benchmark equivalence within tolerance.
- Validator green before and after.
- Structural goals met and recorded.
- Changelog documents the restructure.

## Validation
- **Unit**: validator green per step.
- **Regression**: benchmark equivalence.
- **Certification**: re-certify after substantial restructures.

## Rollback
Restructures are versioned commits — revert to the previous version through the
version manager. Stepwise commits make partial reverts possible.

## Failure Recovery
A benchmark shift beyond tolerance means the "restructure" changed capability: revert
and re-approach with smaller steps. Structure must never be bought with behavior.

## Acceptance Criteria
- [ ] Benchmark equivalence within tolerance.
- [ ] Validator green before and after.
- [ ] Structural goals met.
- [ ] Changelog documents the restructure.

## Examples
### Example 1 — Debugging skill split
The debugging skill's SKILL.md exceeds 500 lines with deep reference material inline.
Refactor: externalize root-cause-tracing and condition-based-waiting guidance into
`references/`, leaving a lean main workflow. Benchmark (planted-bug exercise) stays
equivalent; validator green; size drops 40%. Version 1.0.0 → 1.1.0.

## Anti-patterns
- **Restructure-as-rewrite**: changing capability while "restructuring" — equivalence
  is the gate.
- **Unreferenced externalization**: moving content to references/ with no links from
  the workflow.
- **Structure for structure's sake**: goals with no maintenance benefit — goals are
  recorded and justified.
- **Skipping the benchmark**: "the instructions look the same" — measure it.

## Testing Strategy
Validated with fixture skills containing structural defects (oversize, tangles,
duplication) scored on post-restructure equivalence. See `docs/testing-strategy.md`.

## Future Extensions
- Automated duplication detection across the skill inventory.
- Size and complexity linters as validator warnings.
