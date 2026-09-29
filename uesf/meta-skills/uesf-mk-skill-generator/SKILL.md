---
id: uesf-mk-skill-generator
name: Skill Generator
version: 1.0.0
category: mk
kind: meta
purpose: Generate new UESF skills from a gap or requirement — spec-compliant, validated, and documented before intake.
description: |
  Use when the framework needs a skill that does not exist (taxonomy gap, repeated
  pattern, user request). Produces a draft skill: full spec-conformant SKILL.md,
  generated tests, and generated documentation — validated before it can be accepted.
  This is how UESF grows new capabilities.
triggers:
  - condition: "A taxonomy gap or repeated ad-hoc pattern signals a missing skill"
  - condition: "A new skill is requested for the framework"
  - example_prompt: "Generate a skill for profiling Android app startup performance"
inputs:
  - "The gap/requirement and the target category (from taxonomy)"
  - "Existing related skills (for composition and overlap checks)"
outputs:
  - "Draft SKILL.md conforming to the spec (frontmatter + body sections)"
  - "Generated validation fixtures and tests"
  - "Generated documentation entry"
dependencies:
  - "uesf-mk-skill-validator"
  - "uesf-mk-skill-test-generator"
  - "uesf-mk-skill-doc-generator"
context_requirements:
  - "The spec (docs/skill-spec.md), schema, and template are available"
  - "The taxonomy is consulted to place the skill"
quality_gates:
  - "Draft passes the framework validator (zero errors)"
  - "No overlap with existing skills (checked against the taxonomy)"
  - "Tests generated and runnable; docs generated"
validation:
  - unit
  - integration
  - regression
  - certification
rollback: "A generated skill is a new file set; deletion or revert of its commit removes it cleanly."
failure_recovery: "If the draft fails validation, iterate on the specific errors — never bypass the validator for a 'good enough' skill."
acceptance_criteria:
  - "Draft passes the validator with zero errors"
  - "Overlap analysis confirms the gap"
  - "Generated tests runnable; generated docs complete"
  - "Versioned and reviewed before promotion"
automation_hooks:
  - "tools/skill_scaffold.py provides the skeleton"
  - "Validator re-run in the intake pipeline"
mcp_tools:
  - "none"
cost:
  input_tokens: "~12k"
  output_tokens: "~6k"
  runtime_minutes: "20–60"
complexity: 3
maintainability_score: 5
scalability_score: 5
production_readiness: 4
related_skills:
  - "uesf-mk-skill-optimizer"
  - "uesf-le-continuous-learning"
documentation: "docs/skill-spec.md"
---

# Skill Generator

## Overview
Skills are the framework's products, and products need a build pipeline. This
meta-skill is that pipeline: given a gap or requirement, it produces a
spec-conformant skill draft, its tests, and its documentation — and refuses to call it
done until the validator passes. It is the generator half of the framework's
self-improvement loop.

## Execution Workflow
1. **Confirm the gap** — Check the taxonomy and inventory: does this capability already
   exist (perhaps under another name)? The anti-duplication rule is absolute.
2. **Spec the skill** — One purpose, concrete inputs/outputs, trigger conditions, and
   category placement. If the purpose can't be stated in one sentence, the skill is
   too broad — split it.
3. **Scaffold** — Use `tools/skill_scaffold.py` + the template to create the skeleton
   with the correct ID, category, and paths.
4. **Write the body** — Fill every required section (workflow, gates, validation,
   rollback, failure recovery, acceptance criteria, examples, anti-patterns, testing
   strategy, future extensions) following the template's structure.
5. **Generate tests** — Invoke `uesf-mk-skill-test-generator` for validation fixtures
   (what "working" and "broken" look like for this skill).
6. **Generate docs** — Invoke `uesf-mk-skill-doc-generator` for the skill's doc entry
   and index updates.
7. **Validate and review** — Run the validator (`uesf-mk-skill-validator`) to zero
   errors; pass through `uesf-mk-skill-reviewer`; then promote or iterate.

## Quality Gates
- Draft passes the validator with zero errors.
- Gap confirmed: no overlap with existing skills.
- Tests generated and runnable; docs generated.
- Versioned (0.x draft) until promoted.

## Validation
- **Unit**: validator rules pass; generated tests run green.
- **Integration**: the draft composes with its declared dependencies.
- **Regression**: validator green across the whole framework after intake.
- **Certification**: promotion runs the certification-engine gate.

## Rollback
A generated skill is a new file set — revert its commit or delete the directory. Its
draft version (0.x) signals that removal is expected until promoted.

## Failure Recovery
Validation failures are iterated, never waived: fix the specific errors and re-run.
A skill that cannot pass validation is a spec problem (or not needed) — record that
finding rather than shipping an invalid skill.

## Acceptance Criteria
- [ ] Validator passes with zero errors.
- [ ] Overlap analysis confirms the gap.
- [ ] Tests runnable; docs complete.
- [ ] Reviewed and versioned.

## Examples
### Example 1 — Startup-profiling skill
Gap: Android startup profiling exists nowhere in the taxonomy. The generator scaffolds
`uesf-pf-android-startup-profiling` (after confirming no overlap), writes the body
from the template, generates fixtures (a planted slow-start module + expected profile
outputs), generates the doc entry, runs the validator to zero errors, and hands the
draft to the reviewer for promotion.

## Anti-patterns
- **Overlap-blind generation**: generating a skill that duplicates an existing one —
  the gap check is absolute.
- **Validator bypass**: shipping drafts that fail validation "for now".
- **One-sentence-less purposes**: broad skills that try to do everything — split.
- **Skeleton theater**: scaffolded files with empty body sections.

## Testing Strategy
Validated with generation fixtures: gap statements that should produce drafts, and
overlap traps that must be rejected. See `docs/testing-strategy.md`.

## Future Extensions
- Generation from observed repeated patterns (log-driven gap detection).
- Template variants per category.
