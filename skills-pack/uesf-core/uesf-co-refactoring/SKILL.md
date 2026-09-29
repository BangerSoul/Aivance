---
id: uesf-co-refactoring
name: Safe Refactoring
version: 1.0.0
category: co
kind: core
purpose: Restructure code to improve design without changing observable behavior — every step verified by tests.
description: |
  Use when improving the structure of working code: renaming, extracting, inlining,
  moving, simplifying, or modernizing — without adding features or fixing bugs. Produces
  a behavior-preserving series of small, verified steps. The Iron Law: behavior is
  characterized by tests before the first step, and re-verified after every step.
triggers:
  - condition: "Working code is hard to read, extend, or test but behaves correctly"
  - condition: "A refactor is requested as part of a larger task and must not change behavior"
  - example_prompt: "Extract the retry logic into its own module without changing behavior"
inputs:
  - "The code to refactor and its current behavior"
  - "Existing test coverage (or the need to characterize behavior first)"
outputs:
  - "Behavior-preserving refactor as small verified steps"
  - "Characterization tests where coverage was missing"
  - "Step-by-step verification evidence"
dependencies:
  - "uesf-co-testing"
  - "uesf-co-implementation"
context_requirements:
  - "A runnable test command for the affected code"
  - "Working tree state that can be committed between steps"
quality_gates:
  - "Behavior characterized by tests before step one (write tests if missing)"
  - "Every step is behavior-neutral: tests pass before and after each step"
  - "No feature additions or bug fixes smuggled into the refactor"
validation:
  - unit
  - integration
  - regression
rollback: "Each step is its own commit: revert any single step with git revert without losing the others."
failure_recovery: "If a step breaks tests, stop at that step, diagnose with uesf-co-debugging, and either fix the step or revert it — never continue from a red state."
acceptance_criteria:
  - "Full test suite green before and after the refactor"
  - "Each step is a separate commit with a behavior-neutral message"
  - "No feature or fix mixed into the refactor commits"
  - "Resulting structure is measurably simpler (explicit rationale recorded)"
automation_hooks:
  - "CI suite runs after every refactor commit"
  - "Diff-size guard: refactor commits stay small enough to review"
mcp_tools:
  - "none"
cost:
  input_tokens: "~10k"
  output_tokens: "~5k"
  runtime_minutes: "15–45"
complexity: 3
maintainability_score: 4
scalability_score: 4
production_readiness: 5
related_skills:
  - "uesf-co-review"
  - "uesf-pf-performance-optimization"
documentation: "docs/skill-spec.md"
---

# Safe Refactoring

## Overview
Refactoring is where agents cause the most collateral damage: they "clean up" and break
behavior, or they hide a feature change inside a refactor. This skill makes refactoring
provably safe — behavior is pinned down by tests before the first step and re-verified
after every step, with strict scope discipline that keeps features and fixes out.

## Execution Workflow
1. **Characterize behavior** — Run the existing tests for the affected code. If coverage
   is thin (especially for the public surface being changed), write characterization
   tests that capture current behavior *as-is* — they document what must not change.
2. **Plan steps** — List the refactoring steps (rename, extract, inline, move, simplify)
   in dependency order, each small enough to verify in isolation.
3. **Step, then verify** — For each step:
   - Apply the single mechanical change.
   - Run the affected tests + full suite. Red = stop and fix or revert.
   - Commit the step with a behavior-neutral message.
4. **Check scope** — Every commit must be purely structural. If a real bug is discovered,
   stop refactoring, fix it as its own task (`uesf-co-implementation`), then continue.
5. **Verify the outcome** — Full suite green; the final structure is justified (why
   simpler / more testable), recorded in the change summary or an ADR when material.

## Quality Gates
- Behavior characterized by tests before step one (written, not assumed).
- Tests green before and after every step.
- No feature additions or bug fixes in refactor commits.
- Steps are individually reviewable and revertible.

## Validation
- **Unit**: characterization tests cover the changed public surface.
- **Integration**: the module's real entry points still pass.
- **Regression**: the full suite is green at the end.
- **Documentation**: public API or naming changes update the relevant docs (a refactor
  that renames a public symbol without doc updates is incomplete).

## Rollback
Every step is its own commit, so any single step can be reverted independently. The
characterization tests guarantee that reverting to any earlier state is safe and
detectable.

## Failure Recovery
- A step turns the suite red: stop immediately. Diagnose with `uesf-co-debugging`; if
  the diagnosis takes more than a few minutes, revert the step and re-approach it
  differently.
- A bug is discovered mid-refactor: extract it as a separate task. Mixing a fix into a
  refactor breaks both the review and the rollback story.

## Acceptance Criteria
- [ ] Full suite green before and after the refactor.
- [ ] Every step committed separately with a behavior-neutral message.
- [ ] No features or fixes in refactor commits.
- [ ] The resulting structure has a recorded, explicit simplification rationale.

## Examples
### Example 1 — Extract retry logic
A client class has 40 lines of retry logic inline. The skill writes characterization
tests for retry behavior, then extracts the logic into a `RetryPolicy` module in three
steps (move logic → extract class → introduce policy object), running the suite after
each, committing each step, and finishing with a module that is unit-testable in
isolation.

## Anti-patterns
- **Refactor-and-fix**: fixing a discovered bug inside the refactor — separate tasks.
- **Snapshot refactor**: one giant commit that rewrites the module — steps must be small.
- **Refactoring without tests**: changing structure where behavior is uncharacterized —
  write characterization tests first.
- **Improving during refactor**: adding the "obvious" new feature while restructuring —
  forbidden; refactor is behavior-neutral by definition.

## Testing Strategy
Validated with seeded refactor exercises that plant behavior-preservation traps and score
detection of smuggled behavior changes. See `docs/testing-strategy.md`.

## Future Extensions
- Language-specific step catalogs (extract, inline, move, rename recipes per language).
- Automated behavior-neutrality checks (diff of before/after coverage on public surface).
