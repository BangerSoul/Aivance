---
id: uesf-mk-skill-optimizer
name: Skill Optimizer
version: 1.0.0
category: mk
kind: meta
purpose: Improve an existing skill's quality — clarity, cost, generalization, and verifiability — with measured before/after comparison.
description: |
  Use when a skill works but is verbose, ambiguous, expensive, or too narrow. Produces
  an optimized skill version with benchmarked before/after evidence and changelog.
  Optimization is benchmark-driven: a change must improve the measured outcome.
triggers:
  - condition: "A skill is over-verbose, under-triggered, or costly to execute"
  - condition: "Benchmark results show a skill underperforming"
  - example_prompt: "Optimize the planning skill's description and cost profile"
inputs:
  - "The skill to optimize and its benchmark results (or a benchmark plan)"
  - "The optimization goal (cost, clarity, triggerability, generalization)"
outputs:
  - "Optimized skill (new version, semantic bump)"
  - "Before/after benchmark evidence"
  - "Changelog and migration notes"
dependencies:
  - "uesf-mk-skill-benchmarker"
  - "uesf-mk-skill-validator"
  - "uesf-mk-skill-version-manager"
context_requirements:
  - "A benchmarkable task set for the skill"
  - "The current skill version and its baseline"
quality_gates:
  - "Every change is benchmark-justified (before/after evidence)"
  - "Optimized skill passes the validator"
  - "Version bumped appropriately; changelog written"
validation:
  - unit
  - regression
  - certification
rollback: "Optimizations land as new versions; revert to the previous version via uesf-mk-skill-version-manager if the benchmark regresses."
failure_recovery: "If an optimization worsens the benchmark, revert and record the negative result — negative results are data."
acceptance_criteria:
  - "Benchmark before/after evidence recorded"
  - "Validator green after optimization"
  - "Version and changelog updated"
  - "No regression on the benchmark goal"
automation_hooks:
  - "Benchmark harness re-run in CI on skill changes"
  - "Version manager handles the semantic bump"
mcp_tools:
  - "none"
cost:
  input_tokens: "~10k"
  output_tokens: "~4k"
  runtime_minutes: "20–60"
complexity: 3
maintainability_score: 4
scalability_score: 5
production_readiness: 4
related_skills:
  - "uesf-mk-skill-refactorer"
  - "uesf-mk-skill-benchmarker"
documentation: "docs/skill-spec.md"
---

# Skill Optimizer

## Overview
Skills decay and bloat like code. This meta-skill optimizes them the way the framework
optimizes everything: benchmark-first, evidence-backed, and versioned. A skill change
without a measured before/after comparison is a guess — and this framework doesn't
ship guesses.

## Execution Workflow
1. **Anchor the goal** — The optimization goal: token cost, triggerability,
   clarity/ambiguity, generalization, or benchmark score. One goal per pass.
2. **Baseline** — Run the skill's benchmark (or build one via
   `uesf-mk-skill-benchmarker`) on the current version; record the numbers.
3. **Diagnose** — Identify the concrete problem: verbose instructions, weak trigger
   description, vendor-specific assumptions, unverifiable steps.
4. **Optimize** — Change one thing at a time (one change per benchmark run):
   tighten language, sharpen triggers, externalize reference material, generalize
   assumptions. Keep every required spec section.
5. **Re-benchmark** — Run the same benchmark; compare. Keep changes that improve the
   goal; revert or iterate on the rest.
6. **Validate and version** — Pass the validator; bump the version semantically
   (patch for edits, minor for behavior-affecting changes) via
   `uesf-mk-skill-version-manager`; write the changelog.
7. **Record** — Negative results are recorded too: what was tried, what the numbers
   said, what's next.

## Quality Gates
- Every change benchmark-justified with before/after evidence.
- Validator green after optimization.
- Version bumped; changelog written.
- No regression on the benchmark goal.

## Validation
- **Unit**: benchmark harness runs are reproducible.
- **Regression**: the benchmark goal doesn't regress.
- **Certification**: promotion paths re-certified.

## Rollback
Optimizations are versioned: revert to the previous version through the version
manager. The benchmark evidence makes the rollback decision objective.

## Failure Recovery
A regression on the benchmark goal → revert and record the negative result. Negative
results are how the framework learns what doesn't work — hiding them is the only real
failure.

## Acceptance Criteria
- [ ] Before/after benchmark evidence recorded.
- [ ] Validator green.
- [ ] Version and changelog updated.
- [ ] No regression on the goal.

## Examples
### Example 1 — Planning-skill cost pass
Goal: cut execution tokens 30%. Baseline: 8k input/4k output. Diagnosis: verbose
phase prose. Optimization: externalize the phase rationale into a reference doc,
tighten the workflow to one line per step. Re-benchmark: 6k/3k (−25%), clarity
benchmark unchanged, validator green. Version 1.0.0 → 1.1.0 with changelog.

## Anti-patterns
- **Optimization without measurement**: "I made it better" — benchmark first.
- **Multi-change passes**: changing five things and attributing the win to one.
- **Spec erosion**: cutting required sections to save tokens — all sections stay.
- **Hiding negative results**: unrecorded failed attempts — they are the learning.

## Testing Strategy
Validated with fixture skills with planted bloat; scoring measures whether
optimizations are benchmark-justified. See `docs/testing-strategy.md`.

## Future Extensions
- Token-cost modeling per section to target waste.
- Automated description A/B testing on triggerability.
