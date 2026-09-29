---
id: uesf-co-implementation
name: Incremental Implementation
version: 1.0.0
category: co
kind: core
purpose: Implement planned work in small, verified increments — a failing test first, then the minimal code that makes it pass.
description: |
  Use when executing a planned task or writing production code of any kind. Produces
  minimal, test-backed changes that are verified incrementally. Enforces test-first order
  and prohibits drive-by edits to unrelated code. Works in any language and repository.
  Pairs with uesf-co-planning (before) and uesf-co-review (after).
triggers:
  - condition: "A planned task is approved and ready to implement"
  - condition: "A bugfix or small feature is requested"
  - example_prompt: "Implement task 3 from the plan: add the cache-busting header to the client"
inputs:
  - "Approved plan or task description with acceptance criteria"
  - "Codebase access and test runner"
outputs:
  - "Minimal code change implementing exactly the task"
  - "Passing tests proving the behavior"
  - "Verification evidence (test output)"
dependencies:
  - "uesf-co-planning"
  - "uesf-co-testing"
context_requirements:
  - "The project's test command is known and runnable"
  - "A clean working tree or a clearly scoped branch"
quality_gates:
  - "Failing test written and observed before production code (red-green-refactor)"
  - "Change touches only files required by the task"
  - "Full relevant test suite passes with evidence"
validation:
  - unit
  - integration
  - regression
rollback: "Each increment is a small commit: revert the increment with a single git revert; nothing else is coupled to it."
failure_recovery: "If tests fail after a change, stop, diagnose with uesf-co-debugging, fix the smallest cause, and re-verify. Never mask a failure to get green."
acceptance_criteria:
  - "Test-first order was followed (failing test observed before implementation)"
  - "Diff is minimal and scoped to the task"
  - "Tests pass with recorded output; no skipped tests were added to hide failures"
  - "No unrelated refactors, formatting changes, or dependency bumps in the diff"
automation_hooks:
  - "Pre-commit hook running the task's test subset"
  - "CI job running the full suite on the branch"
mcp_tools:
  - "none"
cost:
  input_tokens: "~10k"
  output_tokens: "~6k"
  runtime_minutes: "10–40 per task"
complexity: 2
maintainability_score: 5
scalability_score: 5
production_readiness: 5
related_skills:
  - "uesf-co-debugging"
  - "uesf-co-refactoring"
documentation: "docs/skill-spec.md"
---

# Incremental Implementation

## Overview
Implementation is where agents most often drift: writing large speculative diffs,
"fixing" unrelated code, and claiming success without evidence. This skill enforces the
smallest verifiable increment, test-first order, and evidence-backed completion. It is the
second stage of the UESF core loop (Plan → Implement → Verify → Review).

## Execution Workflow
1. **Read the task** — Load the task's acceptance criteria and the files/interfaces it
   declares. If the task is not implementable as written, return it to planning instead
   of improvising scope.
2. **Write the failing test** — Write the smallest test that reproduces the desired
   behavior. Run it and *observe it fail* (RED). Recording the failure is mandatory:
   a test that never failed proves nothing.
   - For bugfixes, the test must reproduce the reported bug.
3. **Implement minimally** — Write only the production code required to make the test
   pass (GREEN). Resist adding abstractions, comments, or adjacent fixes.
4. **Verify** — Run the task's test subset, then the full relevant suite (REGRESSION).
   Capture output as evidence.
5. **Refactor only if needed** — Improve the just-written code without changing behavior;
   re-run tests after any refactor.
6. **Commit small** — One logical change per commit with a message that states the why.
   Push nothing without the reviewer's sign-off when a review is in the workflow.

## Quality Gates
- RED observed before GREEN: the failing test ran and failed before implementation.
- The diff touches only files the task declared (or a written justification for each extra).
- The full relevant suite is green with recorded evidence.
- No skipped/xfail tests were added to mask failures.

## Validation
- **Unit**: each helper/function added has direct test coverage.
- **Integration**: the feature is exercised through its real entry point (API, UI, CLI).
- **Regression**: the full suite plus the pre-existing tests of touched modules pass.
- **Performance/Security**: if the change touches hot paths or inputs, run the relevant
  lint/scan gates from `uesf-pf-performance-optimization` / `uesf-se-security-audit`.

## Rollback
Each increment is an isolated, small commit, so rollback is a single `git revert` of that
commit. Because increments are small and verified independently, reverting one does not
unravel others.

## Failure Recovery
- Test still failing after implementation: stop adding code. Diagnose with
  `uesf-co-debugging` (reproduce → isolate → fix), then re-verify.
- Environment problems (flaky runner, missing deps): fix the environment *as a separate
  task* with its own AC, never by disabling tests.

## Acceptance Criteria
- [ ] The failing test was run and observed red before implementation.
- [ ] The diff is minimal and strictly scoped to the task.
- [ ] Tests pass with recorded output; no masking skips were added.
- [ ] No unrelated changes (refactors, formatting, dependency bumps) in the diff.

## Examples
### Example 1 — Cache-busting header
Task: "add `Cache-Control: no-store` to the sync client." The skill writes a test asserting
the header on the client's request, watches it fail, adds one line to the request builder,
re-runs the test (green), then runs the module suite (regression green), and commits the
two-file change (test + client) with a message explaining why.

## Anti-patterns
- **Big-bang diff**: implementing many tasks at once destroys the ability to isolate
  failures — implement one task per increment.
- **Drive-by refactoring**: rewriting neighboring code "while we're here" (Karpathy's
  failure mode #1) — each extra change must be its own task.
- **Green-washing**: claiming success from memory instead of re-running tests — evidence
  is captured output, not recollection.
- **Test after implementation**: tests written post-hoc cannot fail red and therefore
  provide no protection.

## Testing Strategy
This skill is exercised by fixture tasks in `tests/` that verify ordering compliance and
diff scoping. See `docs/testing-strategy.md`.

## Future Extensions
- Increment size heuristics tuned per language (e.g., ≤100 lines per commit by default).
- Automatic evidence capture (test logs attached to the commit) as an automation hook.
