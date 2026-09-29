---
id: uesf-pf-performance-optimization
name: Performance Optimization
version: 1.0.0
category: pf
kind: engineering
purpose: Improve measurable performance targets with profiling evidence, targeted fixes, and regression protection.
description: |
  Use when latency, throughput, memory, battery, or cost targets are missed, or when a
  change risks a hot path. Produces a profiled baseline, targeted optimizations with
  measured before/after evidence, and regression benchmarks. Evidence-driven: no
  optimization without a profile.
triggers:
  - condition: "A performance target is missed or a user reports slowness"
  - condition: "A change touches a hot path, critical loop, or large data transfer"
  - example_prompt: "The sync is slow on large lists — profile and fix it"
inputs:
  - "The performance target and observed gap"
  - "Access to runnable scenarios and profiling tools"
outputs:
  - "Profiled baseline (what is actually slow, with evidence)"
  - "Optimization plan ranked by expected impact"
  - "Verified fixes with before/after measurements"
  - "Regression benchmark or guard"
dependencies:
  - "uesf-co-testing"
  - "uesf-co-implementation"
context_requirements:
  - "A reproducible scenario for the slow behavior"
  - "Profiling/measurement capability (profiler, benchmarks, traces)"
quality_gates:
  - "Baseline measured before any optimization"
  - "Every optimization justified by profile evidence"
  - "Before/after measurements recorded; target met or gap explained"
validation:
  - unit
  - integration
  - performance
  - regression
rollback: "Optimizations are small scoped commits; revert individually. Performance work never changes behavior — any behavior change is a bug."
failure_recovery: "If an optimization regresses correctness or another target, revert it and keep the profile evidence for the next attempt."
acceptance_criteria:
  - "Baseline profile recorded (numbers, not impressions)"
  - "Changes are behavior-neutral and individually revertible"
  - "Target met, or a documented explanation of why not with next steps"
  - "Regression guard added where the hot path is stable"
automation_hooks:
  - "Benchmark job in CI comparing against the last committed baseline"
  - "Regression guard fails the build on a configurable threshold"
mcp_tools:
  - "none"
cost:
  input_tokens: "~12k"
  output_tokens: "~5k"
  runtime_minutes: "30–120"
complexity: 4
maintainability_score: 4
scalability_score: 4
production_readiness: 4
related_skills:
  - "uesf-co-debugging"
  - "uesf-co-refactoring"
documentation: "docs/skill-spec.md"
---

# Performance Optimization

## Overview
"Optimization" without a profile is guessing, and guessing is how agents introduce
complexity for no gain. This skill mandates measurement first, then targeted,
behavior-neutral fixes with before/after evidence, then a regression guard so the win
sticks. It generalizes the profiling discipline across latency, throughput, memory, and
cost.

## Execution Workflow
1. **Anchor the target** — Restate the measurable target (p95 latency, requests/sec,
   memory ceiling, monthly cost). A target is a number with a scenario.
2. **Baseline** — Profile the real scenario: where is time/memory actually spent?
   Record the numbers. If the scenario can't be reproduced, instrument until it can —
   never optimize blind.
3. **Rank the levers** — From the profile, list optimization candidates by expected
   impact ÷ risk. The profile, not intuition, picks the top 1–3.
4. **Optimize one at a time** — Implement each candidate as its own small change via
   `uesf-co-implementation` (test-first where behavior is affected). Re-measure after
   each. Behavior must remain identical.
5. **Verify** — Record before/after numbers; confirm the target is met or record the
   residual gap. Run the full suite for regressions.
6. **Guard** — Add a regression benchmark for the hot path and wire it into CI so
   future changes can't silently undo the win.

## Quality Gates
- Baseline measured before optimization begins.
- Each change is behavior-neutral and justified by profile evidence.
- Before/after numbers recorded; the target is met or the gap is explained.
- No micro-optimizations outside the profiled hotspots.

## Validation
- **Unit**: behavior tests still pass for optimized functions.
- **Integration**: real scenario measurements before/after.
- **Performance**: benchmark guard in CI.
- **Regression**: full suite green.

## Rollback
Each optimization is an isolated commit. Revert any that fails verification or regresses
a target. Because changes are behavior-neutral, rollback never risks correctness.

## Failure Recovery
- Optimization regresses correctness: revert immediately, keep the profile data, and
  attack the next lever.
- Profile shows no dominant hotspot (evenly distributed cost): stop optimizing; the
  target needs a different approach (architecture, batching) — escalate to
  `uesf-ar-solution-architecture` with the profile as evidence.

## Acceptance Criteria
- [ ] Baseline profile recorded.
- [ ] Optimizations behavior-neutral and individually revertible.
- [ ] Target met, or gap documented with next steps.
- [ ] Regression guard in CI for the hot path.

## Examples
### Example 1 — Slow sync on large lists
Target: p95 sync of 500 records < 30s. Baseline profile shows 80% of time in per-record
network round-trips. Optimization: batch the API calls (behavior-neutral), re-measure:
p95 drops to 12s. Benchmark guard added for the batching path. Full suite green.

## Anti-patterns
- **Optimization by suspicion**: "this looks slow" with no profile — forbidden.
- **Premature optimization**: optimizing cold paths for hypothetical scale.
- **Optimize-and-forget**: no regression guard — the win silently evaporates on the next
  refactor.
- **Complexity for microseconds**: adding caching layers that save 1ms on a 2s path —
  the profile decides, not the ego.

## Testing Strategy
Validated with seeded performance regressions (planted O(n²) hot spots) scored on
detection speed and fix quality. See `docs/testing-strategy.md`.

## Future Extensions
- Cost-modeling integration (compute + network cost per request).
- Automated profile-diff reporting between CI runs.
