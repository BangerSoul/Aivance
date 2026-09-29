---
id: uesf-mk-skill-test-generator
name: Skill Test Generator
version: 1.0.0
category: mk
kind: meta
purpose: Generate validation fixtures and tests for skills — what working, failing, and edge-case behavior looks like — so every skill is testable.
description: |
  Use when a skill is created or significantly changed, or when a skill lacks
  verification. Produces test fixtures and test plans per skill (happy path, failure
  path, edge cases) plus the expected outcomes. This is how "every skill is testable"
  is made concrete.
triggers:
  - condition: "A skill is generated, merged, or refactored"
  - condition: "A skill has no verification fixtures"
  - example_prompt: "Generate validation fixtures for the new skill"
inputs:
  - "The skill's purpose, workflow, and acceptance criteria"
  - "The testing conventions (docs/testing-strategy.md)"
outputs:
  - "Test fixtures: working/broken/edge inputs with expected outcomes"
  - "Test plan mapping the skill's ACs to checks"
  - "Integration points with the framework test suite"
dependencies:
  - "uesf-mk-skill-validator"
context_requirements:
  - "Access to the skill's acceptance criteria and workflow"
  - "The framework's test conventions"
quality_gates:
  - "Every acceptance criterion maps to at least one test/fixture"
  - "Fixtures include failing and edge cases, not just happy paths"
  - "Fixtures are runnable in the framework test suite"
validation:
  - unit
  - regression
  - certification
rollback: "Test fixtures are versioned files; revert their commit independently of the skill."
failure_recovery: "A fixture that doesn't match the skill's behavior is a signal: either the fixture is wrong or the skill's ACs are unverifiable — fix the weaker one with evidence."
acceptance_criteria:
  - "AC-to-test mapping complete"
  - "Happy/failure/edge fixtures present and runnable"
  - "Fixtures integrated with the test suite"
  - "Fixture expectations labeled"
automation_hooks:
  - "Generated fixtures wired into tests/test_validator.py and per-skill test dirs"
  - "Coverage check: skills without fixtures flagged"
mcp_tools:
  - "none"
cost:
  input_tokens: "~8k"
  output_tokens: "~4k"
  runtime_minutes: "15–45"
complexity: 3
maintainability_score: 5
scalability_score: 5
production_readiness: 4
related_skills:
  - "uesf-mk-skill-certification-engine"
  - "uesf-co-testing"
documentation: "docs/testing-strategy.md"
---

# Skill Test Generator

## Overview
"No skill may exist without validation" — this meta-skill makes that sentence
executable. It generates the fixtures and test plans that prove each skill works:
happy paths, failure paths, and edge cases, mapped to acceptance criteria, runnable
in the framework's test suite. Tests are generated with the skill, not retrofitted
later.

## Execution Workflow
1. **Extract testable claims** — From the skill's acceptance criteria and workflow,
   enumerate what must be demonstrable: the skill's outputs under its stated inputs.
2. **Design the fixture matrix** — Per claim: a working case (expected pass), a
   failing case (expected fail, with the failure reason), and edge cases (boundaries
   the skill's triggers and gates mention).
3. **Write fixtures** — Concrete fixture inputs with labeled expected outcomes,
   following the framework's fixture conventions (valid/invalid dirs in `tests/`).
4. **Map ACs to tests** — Produce the mapping table: each acceptance criterion →
   which fixture/check proves it.
5. **Integrate** — Wire fixtures into the framework test suite so `python3 -m unittest`
   runs them; run to green.
6. **Maintain with the skill** — Any skill change that alters behavior updates the
   fixtures in the same change.

## Quality Gates
- Every AC maps to at least one test/fixture.
- Happy/failure/edge cases present — never happy-path-only.
- Fixtures runnable in the suite, labeled with expected outcomes.

## Validation
- **Unit**: each fixture runs and its expected outcome matches.
- **Regression**: suite stays green across skill changes.
- **Certification**: certified skills have passing fixture suites.

## Rollback
Fixtures are versioned files — revert independently of the skill. Skill and fixture
changes travel together in the same commit so partial states don't occur.

## Failure Recovery
A fixture whose expected outcome doesn't match the skill is a signal, not a
misfortune: either the fixture misreads the skill, or the skill's ACs are not
verifiable. Fix the weaker one with evidence — a fixture dispute is how unverifiable
ACs get found.

## Acceptance Criteria
- [ ] AC-to-test mapping complete.
- [ ] Happy/failure/edge fixtures present and runnable.
- [ ] Fixtures integrated with the test suite.
- [ ] Expectations labeled.

## Examples
### Example 1 — Planning-skill fixtures
ACs: "tasks have measurable criteria" / "no placeholders". Fixtures: a valid plan
(expect pass), a plan with a TBD step (expect fail, reason: placeholder), an
over-scoped plan (expect fail, reason: task too large). All wired into the suite;
mapping table recorded.

## Anti-patterns
- **Happy-path-only fixtures**: proving the skill works when nothing goes wrong —
  failure and edge cases are mandatory.
- **Fixture fiction**: fixtures that never run — they're in the suite.
- **Unlabeled expectations**: fixture outcomes that nobody can check.
- **Retrofit testing**: adding fixtures long after the skill shipped — generated with
  the skill.

## Testing Strategy
The generator's own output is validated by the framework suite (its fixtures run in
CI). See `docs/testing-strategy.md`.

## Future Extensions
- Property-based fixture generation for skill workflows.
- Coverage reporting per skill (ACs → tests).
