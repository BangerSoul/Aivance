---
id: uesf-co-debugging
name: Root-Cause Debugging
version: 1.0.0
category: co
kind: core
purpose: Find and fix the root cause of a failure through reproduction, isolation, and hypothesis verification — never by guessing.
description: |
  Use when a test fails, a bug is reported, or behavior diverges from expectation.
  Produces a confirmed root cause, a minimal fix, and a regression test that would have
  caught the bug. Systematic and evidence-driven; works for code, builds, and
  integration failures. Do not use for design problems — those belong to uesf-co-review
  or uesf-ar-solution-architecture.
triggers:
  - condition: "A test or production behavior fails without an obvious cause"
  - condition: "Intermittent, environment-dependent, or race-condition failures"
  - example_prompt: "The sync job occasionally duplicates records under load — debug it"
inputs:
  - "Failure report or failing test"
  - "Reproduction steps or environment description"
outputs:
  - "Confirmed root cause with evidence"
  - "Minimal fix (via uesf-co-implementation)"
  - "Regression test that fails on the old code"
dependencies:
  - "uesf-co-testing"
context_requirements:
  - "Ability to run the failing test or scenario"
  - "Access to logs, stack traces, or state dumps where relevant"
quality_gates:
  - "Reproduction is deterministic or has a documented probabilistic reproduction"
  - "Root cause is proven by evidence, not asserted"
  - "Regression test fails without the fix and passes with it"
validation:
  - unit
  - integration
  - regression
rollback: "The fix is a small scoped commit; revert it with a single git revert if it introduces regressions."
failure_recovery: "If the hypothesis is disproven, log the disproof (it narrows the space), form the next hypothesis, and continue — never patch around the symptom."
acceptance_criteria:
  - "Root cause stated in one sentence, backed by reproduced evidence"
  - "Fix removes the cause, not just the symptom"
  - "Regression test added that fails on the pre-fix code"
automation_hooks:
  - "CI rerun of the failing test before/after the fix for evidence"
  - "Fault-injection fixture for intermittent failures"
mcp_tools:
  - "none"
cost:
  input_tokens: "~10k"
  output_tokens: "~5k"
  runtime_minutes: "15–60"
complexity: 3
maintainability_score: 4
scalability_score: 4
production_readiness: 5
related_skills:
  - "uesf-co-implementation"
documentation: "docs/skill-spec.md"
---

# Root-Cause Debugging

## Overview
Debugging by inspection ("I can see the bug") is unreliable; debugging by hypothesis is
reliable. This skill mandates a reproduction-first loop: reproduce, isolate, hypothesize,
verify, fix, and lock the fix in with a regression test. It is adapted from the strongest
systematic-debugging patterns (superpowers' systematic-debugging and root-cause tracing)
and generalizes them to any failure domain.

## Execution Workflow
1. **Reproduce** — Get the failure to occur on demand. If intermittent, instrument and
   document the reproduction rate and conditions rather than moving on.
   - A failure that cannot be reproduced cannot be fixed or verified.
2. **Isolate** — Narrow the failing component by bisection: disable halves, use minimal
   reproducers, or diff configurations. Reduce until the smallest set of inputs/state
   that still fails.
   - For distributed/timing issues, use condition-based waiting and logging of state
     transitions instead of blind sleeps.
3. **Form a hypothesis** — State the suspected root cause in one sentence with a
   mechanism ("X happens because Y under condition Z").
4. **Verify the hypothesis** — Instrument, add a focused test, or inspect state to prove
   the mechanism. A disproof is progress: record it and iterate.
5. **Fix the root cause** — Implement the minimal fix via `uesf-co-implementation`
   (test-first). Fix the *cause*; treat symptom suppression as forbidden.
6. **Add the regression test** — A test that fails on the pre-fix code and passes after.
   This is the deliverable that prevents recurrence.
7. **Sweep for siblings** — Check for the same root cause elsewhere (other modules,
   similar call sites) and either fix them as separate tasks or file them explicitly.

## Quality Gates
- Reproduction is deterministic, or its probability/conditions are documented.
- The root cause is supported by reproduced evidence, not asserted.
- The regression test fails without the fix and passes with it.
- No symptom-masking changes (retries hiding an error, suppressed exceptions).

## Validation
- **Unit**: the regression test is the unit proof of the fix.
- **Integration**: the original failing scenario passes end-to-end.
- **Regression**: full suite green; no new failures introduced by the fix.

## Rollback
The fix is a small, scoped commit. If it regresses, `git revert` it and return to the
debugging loop with the new information. Nothing else depends on the change.

## Failure Recovery
- Hypothesis disproven: record the disproof, form the next hypothesis. The search space
  shrinks with each disproof — that is success, not failure.
- Reproduction lost: add targeted instrumentation (logging, state dumps) until
  reproduction returns; never proceed blind.
- Time-box exceeded: escalate with evidence — the current hypothesis set, the isolation
  boundary, and a recommendation for a spike.

## Acceptance Criteria
- [ ] Root cause stated in one sentence with reproduced evidence.
- [ ] Fix removes the cause; no symptom masking.
- [ ] Regression test added that fails on pre-fix code.
- [ ] Sibling occurrences checked and either fixed or filed.

## Examples
### Example 1 — Duplicate records under load
Failure: the sync job duplicates records under load. Reproduction: a stress test with
concurrent workers reproduces it ~30% of the time. Isolation: disabling the retry path
stops the duplicates. Hypothesis: the retry handler re-enqueues after commit, causing
double processing. Verification: a log line proves re-enqueue after commit. Fix: move the
ack before re-enqueue. Regression test: a unit test with a commit-failure stub that
asserts exactly one enqueue.

## Anti-patterns
- **Shotgun changes**: editing many files hoping one fixes it — only change after a
  verified hypothesis.
- **Symptom patching**: wrapping the failure in a retry/ignore so tests pass — the root
  cause remains and will resurface.
- **Rationalizing away**: blaming flakiness, timing, or the environment without evidence.
- **Sleep-based waiting**: fixed sleeps for timing issues — use condition-based waits on
  observable state.

## Testing Strategy
Validated with fixture failures (deliberately broken modules in `tests/fixtures/`) and
time-boxed debugging exercises that score hypothesis-to-fix efficiency. See
`docs/testing-strategy.md`.

## Future Extensions
- Structured reproduction reports (one per bug) as a reusable artifact.
- Fault-injection harness templates for concurrency and I/O failures.
