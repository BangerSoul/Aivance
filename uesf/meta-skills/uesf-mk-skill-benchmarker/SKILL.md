---
id: uesf-mk-skill-benchmarker
name: Skill Benchmarker
version: 1.0.0
category: mk
kind: meta
purpose: Measure skill quality and change impact with benchmark task sets — baselines, deltas, and comparison matrices.
description: |
  Use when a skill changes, when comparing skills (ours vs. sources), or when
  certifying. Produces benchmark task sets, baseline/delta scores, and comparison
  matrices. Every framework skill that claims quality must be able to point at a
  benchmark. This is the measurement engine of the framework.
triggers:
  - condition: "A skill is optimized, merged, or refactored (before/after needed)"
  - condition: "Framework comparison or certification needs measurements"
  - example_prompt: "Benchmark our review skill against the source frameworks' approach"
inputs:
  - "The skill(s) under benchmark and their capabilities"
  - "A task-set plan or existing sets (coverage of the capability)"
outputs:
  - "Benchmark task sets (labeled, frozen)"
  - "Baseline/delta scores with methodology"
  - "Comparison matrices (skills vs. sources, pre/post changes)"
dependencies:
  - "uesf-mk-skill-validator"
context_requirements:
  - "A runnable harness for the benchmark (agent, eval, or scripted scoring)"
  - "Labeled expectations for the task set"
quality_gates:
  - "Task sets frozen between runs (no moving goalposts)"
  - "Methodology explicit: metrics, scoring, tolerances"
  - "Deltas attributable (one variable changed per comparison)"
validation:
  - unit
  - regression
  - certification
rollback: "Benchmarks are versioned artifacts; revert the task-set commit. Skill changes are reverted separately."
failure_recovery: "If scores are unstable, investigate the harness or environment first — an unstable benchmark is a bug, not a skill problem."
acceptance_criteria:
  - "Frozen, labeled task sets per benchmarked skill"
  - "Baseline and methodology recorded"
  - "Comparison matrices produced where requested"
  - "Unstable results investigated and documented"
automation_hooks:
  - "Benchmark harness in CI on skill changes"
  - "Results archived per release"
mcp_tools:
  - "none"
cost:
  input_tokens: "~10k"
  output_tokens: "~4k"
  runtime_minutes: "30–120"
complexity: 3
maintainability_score: 4
scalability_score: 5
production_readiness: 4
related_skills:
  - "uesf-ai-evaluation"
  - "uesf-mk-skill-optimizer"
documentation: "docs/testing-strategy.md"
---

# Skill Benchmarker

## Overview
"Better" needs a number. This meta-skill is the framework's measurement engine:
frozen task sets, explicit methodology, baselines and deltas, and comparison
matrices — including the framework-vs-source comparisons that are a deliverable of
this framework. It is the reason "exceeds every individual repository" can be
asserted with evidence rather than vibes.

## Execution Workflow
1. **Define the capability under test** — What must the skill demonstrably achieve?
   The benchmark measures this, not style.
2. **Build the task set** — Labeled tasks covering the capability's range (happy
   paths, edge cases, failure modes). Freeze the set: it is the ruler, and rulers
   don't move.
3. **Fix the methodology** — Metrics, scoring rules, tolerances, and the harness
   (agent run, eval, or scripted scoring). Record the environment (model, versions).
4. **Baseline** — Run the task set on the current skill; record scores.
5. **Measure deltas** — After any skill change: re-run the identical set; attribute
   the delta to the single change made.
6. **Build comparison matrices** — For framework comparisons: same task set, skills
   under comparison, scores in a matrix (this is the format behind
   `benchmarks/matrices.md`).
7. **Archive** — Version the task set and results; archive per release.

## Quality Gates
- Task sets frozen between runs.
- Methodology explicit (metrics, scoring, tolerances, environment).
- Deltas attributable to one change.
- Results archived.

## Validation
- **Unit**: scoring logic itself is tested.
- **Regression**: a re-run of an unchanged skill reproduces the baseline.
- **Certification**: certified skills point at passing benchmarks.

## Rollback
Benchmarks are versioned artifacts; revert the task-set commit if a set is wrong.
Skill changes revert separately — the two are independent.

## Failure Recovery
Unstable scores are a harness/environment bug, not a skill conclusion: investigate
(flakiness, model variance, environment drift) and document before drawing
conclusions. Never "adjust" the task set to stabilize the score — that's moving the
ruler.

## Acceptance Criteria
- [ ] Frozen, labeled task sets per benchmarked skill.
- [ ] Baseline and methodology recorded.
- [ ] Comparison matrices produced where requested.
- [ ] Unstable results investigated and documented.

## Examples
### Example 1 — Review-skill comparison
Task set: 10 planted-defect diffs (correctness, security, perf). Same set run against
"plain agent" vs. "agent with uesf-co-review": detection 4/10 → 9/10, methodology
recorded (same model, same harness, tolerance ±1). Matrix produced for the benchmark
report; results archived.

## Anti-patterns
- **Moving goalposts**: editing the task set to make a change look better.
- **Methodology-by-memory**: scores without recorded metrics/environment — not
  reproducible.
- **Multi-variable deltas**: changing skill + model + harness and claiming the skill
  caused the delta.
- **Benchmark theater**: scores with no task set anyone can inspect — sets are
  committed.

## Testing Strategy
The benchmarker's scoring harness has its own unit tests and stability fixtures. See
`docs/testing-strategy.md`.

## Future Extensions
- Cross-model benchmark matrices (same set, multiple models).
- Cost-weighted scores (quality per token).
