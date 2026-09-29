---
id: uesf-mk-skill-validator
name: Skill Validator
version: 1.0.0
category: mk
kind: meta
purpose: Verify that every skill in the framework conforms to the specification — schema, taxonomy, dependencies, and required content — and report failures.
description: |
  Use when a skill is authored, modified, merged, or before any certification or
  release. Runs the framework validator (tools/validate_framework.py) plus deeper
  checks, and produces a pass/fail report. No skill enters the framework without
  passing validation — this is the guarantee behind "no skill without validation".
triggers:
  - condition: "A skill is authored, edited, or merged"
  - condition: "A release or certification is pending"
  - example_prompt: "Validate the framework after the new intake"
inputs:
  - "The framework tree (or the changed skills)"
  - "The spec schema and taxonomy"
outputs:
  - "Validation report: per-skill pass/fail with specific errors"
  - "Dependency graph verification (resolution + no cycles)"
  - "Overall verdict"
dependencies: []
context_requirements:
  - "Python 3 (stdlib only) and the validator script"
quality_gates:
  - "Zero errors: frontmatter schema, taxonomy codes, dependency resolution, cycles, body sections"
  - "Warnings reviewed (unresolved related skills, deprecated skills)"
  - "Verdict recorded in the report"
validation:
  - unit
  - integration
  - regression
  - certification
rollback: "Validation is read-only: it never modifies skills. Reverting a failed skill's commit is the only action needed."
failure_recovery: "Failures are reported per skill per rule with exact locations, so fixes are mechanical; re-run until zero errors."
acceptance_criteria:
  - "Every skill validated against the full spec"
  - "Report lists each error with the skill and rule"
  - "Zero errors for a passing verdict"
  - "Dependency graph verified (resolution + acyclicity)"
automation_hooks:
  - "Validator wired into CI and the intake pipeline"
  - "Pre-release gate requires a passing validation"
mcp_tools:
  - "none"
cost:
  input_tokens: "~4k"
  output_tokens: "~2k"
  runtime_minutes: "1–5"
complexity: 2
maintainability_score: 5
scalability_score: 5
production_readiness: 5
related_skills:
  - "uesf-mk-skill-certification-engine"
  - "uesf-mk-skill-reviewer"
documentation: "docs/testing-strategy.md"
---

# Skill Validator

## Overview
"Every skill is validated" is only meaningful if validation is automated and
objective. This meta-skill wraps the framework's validator (`tools/validate_framework.py`)
— a stdlib-only checker that enforces the spec: frontmatter schema, taxonomy codes,
ID format, dependency resolution, cycle detection, required body sections, and score
ranges. It is the framework's immune system.

## Execution Workflow
1. **Run the validator** — Execute `python3 tools/validate_framework.py` (or the
   deep variant `--deep` with content checks). Collect the full report.
2. **Interpret the report** — Errors (must fix) vs. warnings (should review):
   - Errors: schema violations, unknown categories, malformed IDs, unresolvable
     dependencies, dependency cycles, missing required body sections, score ranges.
   - Warnings: unresolved related-skills, deprecated skills, near-duplicate IDs.
3. **Verify the graph** — Confirm dependency resolution and acyclicity from the
   validator's graph output.
4. **Fix or reject** — Failed skills are fixed by the author (or rejected by
   governance); the validator is never waived.
5. **Record the verdict** — Save the report artifact; a passing verdict is the
   prerequisite for certification and release.

## Quality Gates
- Zero errors for a passing verdict.
- Warnings reviewed and dispositioned (fixed, accepted, or tracked).
- Verdict recorded with the report artifact.

## Validation
- **Unit**: the validator's own test suite (`tests/test_validator.py`) is green.
- **Integration**: validator runs on the whole framework tree.
- **Regression**: previous passing states stay green (fixtures).
- **Certification**: a passing validation is a certification gate.

## Rollback
Validation is read-only. The only "rollback" is reverting a failed skill's commit —
which the report's error list makes mechanical.

## Failure Recovery
Each error is reported with the skill and rule, so fixes are mechanical: fix, re-run,
repeat to zero. An error that resists fixing is a spec problem — escalate to the spec,
don't patch the validator to mask it.

## Acceptance Criteria
- [ ] Every skill validated against the full spec.
- [ ] Report lists each error with skill and rule.
- [ ] Zero errors for a passing verdict.
- [ ] Dependency graph verified.

## Examples
### Example 1 — Post-intake validation
After a meta-skill intake, the validator runs: 40 skills scanned, 0 errors, 2 warnings
(two unresolved related-skill references). The author fixes the references; re-run
gives 0 errors / 0 warnings; the report is archived as the intake's validation record.

## Anti-patterns
- **Validator theater**: running the validator and ignoring warnings — warnings are
  reviewed.
- **Spec patching**: relaxing rules to make a skill pass — rules change via governance.
- **Unreported runs**: validating without an archived report — the report is the
  evidence.
- **Partial validation**: validating only new skills — the whole framework must stay
  green.

## Testing Strategy
The validator has its own suite with fixtures (valid skill, schema-violating skill,
cycle graph, unknown category). See `docs/testing-strategy.md`.

## Future Extensions
- Content-quality checks (description length/style, example presence) as warnings.
- Cross-version compatibility validation.
