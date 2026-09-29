---
id: uesf-ai-evaluation
name: AI Evaluation
version: 1.0.0
category: ai
kind: ai
purpose: Measure and improve AI system behavior with evaluation sets, baselines, and regression gates — evidence instead of impressions.
description: |
  Use when an AI feature, prompt, agent, or model change needs to be proven better (or
  not worse). Produces an eval harness: labeled test set, baseline scores, deltas, and
  a regression gate. The measurement spine for uesf-pe-prompt-engineering and
  uesf-ai-agent-design.
triggers:
  - condition: "An AI feature or prompt is changing and must not regress"
  - condition: "A choice between models/prompts needs measured comparison"
  - example_prompt: "Build an eval harness for the triage agent's classification accuracy"
inputs:
  - "The AI behavior to evaluate and its expected outcomes"
  - "Access to the model/harness and a way to run the eval"
outputs:
  - "Labeled evaluation set (with edge and adversarial cases)"
  - "Baseline and delta scores with methodology"
  - "Regression gate wired into CI"
dependencies:
  - "uesf-co-testing"
context_requirements:
  - "A concrete behavior with labelable expected outcomes"
  - "Runnable eval harness or the means to build one"
quality_gates:
  - "Eval set covers happy paths, edge cases, and known failure modes"
  - "Scoring methodology is explicit (metrics, thresholds, human-in-loop rules)"
  - "No eval-gaming: the set is frozen between iterations and split train/test"
validation:
  - unit
  - regression
  - certification
rollback: "Evals are versioned with the behavior they test; revert the eval set or behavior to the last green baseline."
failure_recovery: "A regression on the gate blocks the change: revert or iterate with evidence — never merge an AI change that fails its own eval."
acceptance_criteria:
  - "Labeled eval set with edge/adversarial coverage"
  - "Baseline and methodology recorded"
  - "Regression gate green in CI"
  - "Known failure modes documented"
automation_hooks:
  - "Eval harness run in CI on AI-related changes"
  - "Regression gate failing the build on threshold breach"
mcp_tools:
  - "none"
cost:
  input_tokens: "~15k"
  output_tokens: "~5k"
  runtime_minutes: "30–120"
complexity: 4
maintainability_score: 4
scalability_score: 4
production_readiness: 4
related_skills:
  - "uesf-pe-prompt-engineering"
  - "uesf-mk-skill-benchmarker"
documentation: "docs/testing-strategy.md"
---

# AI Evaluation

## Overview
AI behavior can't be reviewed by eye — it needs measurement. This skill builds the
measurement: a labeled eval set, an explicit scoring methodology, baselines, and a
regression gate. It applies the evaluation-driven-development pattern the ecosystem
recommends (Anthropic's skill-authoring evals, agent-platform eval flywheels) to any
AI behavior, prompt, or agent change.

## Execution Workflow
1. **Define the behavior and outcomes** — The behavior under evaluation and its
   labelable expected outcomes. If outcomes can't be labeled, the behavior isn't
   ready to evaluate (or to ship).
2. **Build the eval set** — Golden cases across happy paths, edge cases, and known
   failure modes — plus adversarial cases (prompt injection attempts, out-of-scope
   requests). Aim for enough cases that the score is stable (min ~20–30; more for
   high-stakes behaviors).
3. **Fix the methodology** — Metrics (accuracy, F1, rubric scores), thresholds, and
   the human-in-loop rule for ambiguous cases. Freeze the set and split train/test so
   iteration cannot game the score.
4. **Baseline** — Run the current behavior; record the baseline and methodology.
5. **Iterate with deltas** — Change one thing; re-run; record deltas. Log every
   change's effect (the flywheel).
6. **Wire the regression gate** — The eval runs in CI on AI-related changes; a
   threshold breach fails the build.
7. **Document failure modes** — What the eval can't capture (qualitative judgment,
   latency/cost trade-offs) and how those are reviewed instead.

## Quality Gates
- Eval set covers happy, edge, and adversarial cases.
- Methodology explicit: metrics, thresholds, human-in-loop rules.
- Set frozen between iterations; train/test split prevents gaming.
- Regression gate wired and green.

## Validation
- **Unit**: eval-set coverage and labeling spot-checked.
- **Regression**: gate green on the shipped baseline.
- **Certification**: high-stakes behaviors certified with their evals.

## Rollback
Evals and the behaviors they test are versioned together. Reverting to the last green
baseline is a version control operation.

## Failure Recovery
A gate breach blocks the change by design. Revert or iterate with evidence — the
failure output (which cases regressed) is the debugging input. Never merge an AI
change that fails its own eval "because it felt better."

## Acceptance Criteria
- [ ] Labeled eval set with edge/adversarial coverage.
- [ ] Baseline and methodology recorded.
- [ ] Regression gate green in CI.
- [ ] Known failure modes documented.

## Examples
### Example 1 — Triage classifier eval
30 labeled cases (happy/ambiguous/adversarial) frozen with a 70/30 train/test split.
Metrics: accuracy + rubric for ambiguous cases with a human tiebreak rule. Baseline
82%. Each prompt change re-runs the set; the shipped prompt scores 94%; the CI gate
fails any change dropping below 90%. Failure modes documented: sarcasm subset flagged
as residual risk.

## Anti-patterns
- **Eval-gaming**: iterating against the same set until the score inflates — frozen
  set + split.
- **Vibes-based iteration**: "the model seems better now" — deltas are measured.
- **Score theater**: one number with no methodology or coverage — methodology is
  mandatory.
- **Unlabeled labels**: ambiguous cases adjudicated by whoever — human-in-loop rules
  are explicit.

## Testing Strategy
Validated with fixture eval sets containing planted scoring-methodology holes; scored
on detection. See `docs/testing-strategy.md`.

## Future Extensions
- Multi-model eval matrices (same set, multiple models).
- Cost-weighted scores (quality per token).
