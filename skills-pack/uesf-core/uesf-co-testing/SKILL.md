---
id: uesf-co-testing
name: Test-Driven Verification
version: 1.0.0
category: co
kind: core
purpose: Design and run the verification that proves a change behaves as specified — tests first, evidence always.
description: |
  Use when any code must be proven correct, before and during implementation, and before
  review or release. Produces test cases that fail for the right reason, coverage of
  behaviors (not lines), and recorded verification evidence. Language- and
  framework-agnostic. Do not use as a substitute for uesf-co-review — tests prove
  behavior, review checks design.
triggers:
  - condition: "Implementation, refactoring, or bugfix work is about to start"
  - condition: "A change must be proven safe before merge or release"
  - example_prompt: "Write the tests that prove the retry logic never exceeds three attempts"
inputs:
  - "Behavior specification or task acceptance criteria"
  - "Existing test suite and runner"
outputs:
  - "Test cases (unit, integration, regression) mapped to behaviors"
  - "Verification evidence: pass/fail output, coverage summary"
dependencies:
  - "uesf-co-planning"
context_requirements:
  - "A runnable test command exists or can be established as its own task"
quality_gates:
  - "Every test fails for the intended reason when the behavior is removed (mutation check)"
  - "Tests target behavior and boundaries, not implementation details"
  - "Full suite runs green; failures are real and diagnosed, never skipped"
validation:
  - unit
  - integration
  - regression
rollback: "Tests are additive and isolated: deleting the test file reverts cleanly; production code is never coupled to test-only constructs that break the build."
failure_recovery: "A failing test is always information: reproduce, diagnose the root cause with uesf-co-debugging, and fix either the test (if wrong) or the code (if right)."
acceptance_criteria:
  - "Each acceptance criterion of the task maps to at least one test"
  - "Mutation check passed for the critical path (test fails when behavior removed)"
  - "No test is skipped to achieve green"
  - "Evidence (runner output) recorded with the change"
automation_hooks:
  - "CI runs the suite on every push; coverage delta enforced on PRs"
  - "Pre-merge gate: suite green + critical-path mutation check"
mcp_tools:
  - "none"
cost:
  input_tokens: "~8k"
  output_tokens: "~5k"
  runtime_minutes: "5–30"
complexity: 2
maintainability_score: 5
scalability_score: 5
production_readiness: 5
related_skills:
  - "uesf-co-implementation"
  - "uesf-ce-certification-audit"
documentation: "docs/testing-strategy.md"
---

# Test-Driven Verification

## Overview
Verification is not a phase at the end; it is the discipline that makes every other skill
safe. This skill defines how to design tests that prove behavior, run them for evidence,
and refuse to accept green without meaning. It powers the RED step of the implementation
skill and the evidence requirement of every review.

## Execution Workflow
1. **Derive behaviors** — From the task's acceptance criteria, list the behaviors to
   prove. Prefer behavior boundaries (inputs, edge cases, failure modes) over line coverage.
   - For each behavior, define the setup, action, and expected observable result.
2. **Map tests to criteria** — Every acceptance criterion must trace to at least one test.
   Missing mapping means either a missing test or an untestable criterion (fix the latter).
3. **Write the smallest failing tests** — One behavior per test where practical. Test
   public interfaces; avoid asserting implementation details that invite brittleness.
4. **Run red** — Execute and record the failure. The failure reason must match the intent
   (e.g., assertion failed, not a compile error masking the test).
5. **Run green** — After implementation, re-run. If green without the code change, the
   test is vacuous — fix the test.
6. **Mutation spot-check** — For the critical path, remove or invert the behavior and
   confirm the test fails. This catches tests that assert nothing.
7. **Regression sweep** — Run the full suite; triage any unrelated failures honestly.

## Quality Gates
- Tests fail for the intended reason when behavior is removed (mutation check on critical path).
- Tests assert observable behavior and boundaries, not internal call counts or formatting.
- The full suite is green with recorded output; zero masking skips.

## Validation
- **Unit**: fast, isolated, deterministic tests for each behavior.
- **Integration**: real entry points and real dependencies where feasible.
- **Regression**: pre-existing tests of touched modules still pass.
- **Performance**: tests that involve timing use generous bounds and are isolated from CI load.

## Rollback
Test files are additive and isolated from production code. Deleting a test file (or
reverting its commit) restores the previous state with no side effects on the build.

## Failure Recovery
A failing test is information, not a problem. Follow `uesf-co-debugging`: reproduce,
isolate, hypothesize, fix the root cause. Fix the test only when the test itself is wrong
(the failure reason doesn't match intent), and say so explicitly in the change.

## Acceptance Criteria
- [ ] Every acceptance criterion maps to at least one test.
- [ ] Critical-path mutation check passed.
- [ ] No tests skipped to achieve green; no vacuous tests added.
- [ ] Runner evidence recorded with the change.

## Examples
### Example 1 — Retry limit
Criterion: "retry never exceeds three attempts." The skill writes a test with a stub that
always fails, asserting exactly three attempts were made, watches it fail (initial state),
then after implementation verifies it passes and that removing the retry guard flips it red.

## Anti-patterns
- **Coverage theater**: chasing line coverage with tests that assert nothing — use mutation
  checks and behavior mapping instead.
- **Implementation-pinning**: asserting internal calls/ordering — tests break on any
  refactor and discourage clean design.
- **Skipping to green**: `@skip` / `.only` to hide failures — forbidden; a skipped test is
  a deleted test with a lie attached.
- **Testing the framework**: asserting on mock call counts instead of outcomes.

## Testing Strategy
This skill itself is validated by fixture suites with deliberately broken behaviors
(`tests/fixtures/`), verifying the mutation-check procedure catches them. See
`docs/testing-strategy.md`.

## Future Extensions
- Automatic behavior-to-test traceability report as a CI artifact.
- Fuzz and property-based defaults for data-processing behaviors.
