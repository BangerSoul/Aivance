---
id: uesf-co-review
name: Engineering Review
version: 1.0.0
category: co
kind: core
purpose: Review a change for correctness, design quality, and risk before merge — with prioritized findings and a bounded fix loop.
description: |
  Use when a change is ready for review, before merge or release. Produces prioritized
  findings mapped to the change's acceptance criteria, a verdict, and evidence. Reviews
  behavior and design (tests prove behavior; review checks design). Applies to code,
  plans, docs, and infrastructure changes. Do not use to re-verify tests — that is
  uesf-co-testing's job.
triggers:
  - condition: "A change is proposed for merge or release"
  - condition: "A design or plan needs a second set of eyes before execution"
  - example_prompt: "Review the sync feature branch against its plan before we merge"
inputs:
  - "The change (diff, plan, or design document)"
  - "The acceptance criteria or plan it claims to satisfy"
  - "Context: repository conventions, related code"
outputs:
  - "Prioritized findings (blocker / major / minor / nit)"
  - "Verdict: approve, approve-with-nits, or request-changes"
  - "Fix-loop evidence when changes were requested"
dependencies:
  - "uesf-co-testing"
context_requirements:
  - "The change is diffable against a base (or the plan is a document)"
  - "Test results for the change are available as evidence"
quality_gates:
  - "Every acceptance criterion is traced to code and evidence"
  - "Findings are specific, actionable, and prioritized"
  - "Fix loop is bounded (circuit breaker) — no unbounded review churn"
validation:
  - unit
  - documentation
  - security
rollback: "Review produces no code changes: nothing to roll back. The bounded fix loop guards against review-induced churn."
failure_recovery: "If the review surfaces a blocker, the change returns to implementation with a single consolidated finding set; re-review is scoped to the delta."
acceptance_criteria:
  - "All acceptance criteria traced to evidence in the change"
  - "Findings prioritized as blocker/major/minor/nit with rationale"
  - "No blocker findings remain at merge time"
  - "Review verdict and rationale are recorded"
automation_hooks:
  - "CI static checks (lint, format, typecheck) run before human/agent review"
  - "Dependency and security scans wired into the review gate"
mcp_tools:
  - "none"
cost:
  input_tokens: "~12k"
  output_tokens: "~4k"
  runtime_minutes: "10–30"
complexity: 2
maintainability_score: 5
scalability_score: 5
production_readiness: 5
related_skills:
  - "uesf-se-security-audit"
  - "uesf-mk-skill-reviewer"
documentation: "docs/skill-spec.md"
---

# Engineering Review

## Overview
Review is the quality gate between implementation and merge. Good review is cheap; bad
review is expensive (churn, missed defects, ego-driven nits). This skill makes review
systematic: trace the change to its acceptance criteria, read with specific lenses, and
deliver prioritized findings with a bounded fix loop — so review is rigorous without
becoming an endless ping-pong.

## Execution Workflow
1. **Scope the change** — Load the diff or document, its base, and the acceptance
   criteria it claims to satisfy. Confirm the change is complete (no WIP).
2. **Verify evidence first** — Check that tests exist, ran, and passed (from
   `uesf-co-testing` evidence). A change whose claims lack evidence cannot be reviewed —
   return it for evidence, not for fixes.
3. **Trace to criteria** — Map every acceptance criterion to the code/test that
   satisfies it. Untraced criteria = either missing implementation or missing test.
4. **Read with lenses** — Pass the change through the applicable lenses, each in its own
   pass: correctness and edge cases; design and maintainability; security (input
   handling, secrets, injection); performance (hot paths, unbounded work); accessibility
   (UI changes); documentation (user-facing behavior changes).
5. **Produce findings** — Every finding is specific, actionable, and prioritized:
   - **Blocker**: incorrect behavior, data loss, security issue — must fix before merge.
   - **Major**: likely defect or design debt that will bite — should fix.
   - **Minor**: improvement with limited impact — may fix or file.
   - **Nit**: style/preference — never blocks.
6. **Deliver verdict** — approve / approve-with-nits / request-changes, with rationale
   tied to the findings.
7. **Bound the fix loop** — When changes are requested, the author fixes and re-reviews
   the *delta only* (not the whole change). Circuit breaker: after 5 rounds without
   convergence, escalate to a fresh reviewer or a design conversation — never
   rubber-stamp, never re-litigate settled points.

## Quality Gates
- Every acceptance criterion is traced to code and evidence.
- Findings are specific (file/line/behavior), actionable, and prioritized.
- The fix loop has a hard bound; rounds are documented.
- The verdict is explicit and recorded.

## Validation
- **Unit**: each finding references concrete evidence (line, test, log).
- **Integration**: the change is re-run against its tests after fixes.
- **Security**: secrets, injection, and input-validation passes on every change.
- **Documentation**: user-visible behavior changes update docs (checked by the review).

## Rollback
Review produces no code. The only rollback concern is the fix loop: each round's fixes
are small commits, individually revertible.

## Failure Recovery
- Blocker found: return the change with one consolidated finding set; re-review is scoped
  to the fix delta, drastically reducing cost (re-review-prompt pattern).
- Disagreement on a finding: resolve by evidence (a test, a doc reference), not authority;
  if unresolved after one exchange, escalate to governance.

## Acceptance Criteria
- [ ] All acceptance criteria traced to evidence.
- [ ] Findings prioritized with rationale; blockers fixed before merge.
- [ ] Verdict and rationale recorded with the change.
- [ ] Fix-loop rounds bounded and documented.

## Examples
### Example 1 — Sync feature branch review
Reviewer loads the branch against the plan, confirms tests passed (evidence file), traces
each AC to code, then runs the security lens: finds the sync client logs the auth token
(blocker), the retry path has an unbounded backoff (major), a variable name is misleading
(minor). Verdict: request-changes with the blocker + major. Author fixes both; reviewer
re-views only the delta; merge approved.

## Anti-patterns
- **Nit-storming**: blocking on style instead of behavior — nits never block.
- **Rubber-stamping**: approving without tracing criteria — the most common review
  failure; evidence is mandatory.
- **Re-litigating**: re-opening settled design points each round — the fix loop reviews
  the delta only.
- **Review as gate theater**: green CI treated as sufficient — CI is input evidence, not
  the review itself.

## Testing Strategy
Validated with review-fixture diffs containing planted defects (correctness, security,
perf) scored against detection rates. See `docs/testing-strategy.md`.

## Future Extensions
- Lens checklists per change type (API, UI, data migration, infra).
- Blocker-defect detection benchmarks against planted-defect corpora.
