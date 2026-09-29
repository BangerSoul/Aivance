---
id: uesf-pe-prompt-engineering
name: Prompt Engineering
version: 1.0.0
category: pe
kind: ai
purpose: Design, evaluate, and iterate instructions for AI systems — model-agnostic prompting that is measurable, not magical.
description: |
  Use when authoring prompts, system instructions, or agent instructions that must
  perform reliably. Produces a prompt with a test set, baseline, and measured
  improvements. Model-agnostic: works across Claude, GPT, Gemini, and others, with
  explicit model-compatibility notes. No prompt ships without an evaluation.
triggers:
  - condition: "A prompt, system prompt, or instruction set is being authored or improved"
  - condition: "Prompt behavior is inconsistent and needs measurement"
  - example_prompt: "Write and evaluate the system prompt for our triage agent"
inputs:
  - "Task definition and expected behaviors"
  - "Evaluation set (golden examples) or a plan to build one"
outputs:
  - "Prompt (instruction set) with version"
  - "Evaluation: test set, baseline, and measured deltas"
  - "Known failure modes and mitigations"
dependencies:
  - "uesf-ai-evaluation"
context_requirements:
  - "Access to a model or harness to run evaluations"
  - "A task concrete enough to define expected behaviors"
quality_gates:
  - "Every prompt has an evaluation set and measured baseline"
  - "Instructions are task-specific and minimize degrees of freedom where correctness matters"
  - "Changes are versioned and compared (delta evaluated, not vibes)"
validation:
  - unit
  - regression
  - certification
rollback: "Prompts are versioned artifacts: revert the prompt file to the previous evaluated version."
failure_recovery: "When a prompt regresses on the eval set, revert to the last good version and iterate from there — never ship an unevaluated change."
acceptance_criteria:
  - "Evaluation set exists with labeled expected behaviors"
  - "Baseline and final scores recorded"
  - "Known failure modes documented with mitigations"
  - "Prompt versioned and compatible with the target models"
automation_hooks:
  - "Eval harness run in CI on prompt changes"
  - "Regression guard on the evaluation set"
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
  - "uesf-ai-evaluation"
  - "uesf-mk-skill-generator"
documentation: "docs/skill-spec.md"
---

# Prompt Engineering

## Overview
Prompts are code: they need versions, tests, and regressions — not vibes. This skill
applies the ecosystem's best instruction-design lessons (the Anthropic authoring
guides: pushy trigger descriptions, degree-of-freedom control, avoiding time-sensitive
content; superpowers' description hygiene: "use when" not workflow summaries) into a
model-agnostic, evaluation-driven discipline.

## Execution Workflow
1. **Define behaviors** — Enumerate the expected behaviors and their boundaries
   (what the prompt must produce, must refuse, must ask). Concrete task, not vibes.
2. **Build the eval set** — Create golden examples with labeled expected outcomes
   covering happy paths, edge cases, and the known failure modes. If you can't label
   expected outcomes, the task isn't well-defined enough to prompt.
3. **Write the prompt** — Structure: role/context (minimal), task, constraints,
   output format, and what to do when information is missing. Match degrees of freedom
   to the task: exact scripts/commands for fragile operations, instructions for
   judgment calls.
   - Trigger descriptions: describe *when to use*, never summarize the workflow.
   - Avoid time-sensitive facts (dates, model versions) — keep them external.
4. **Baseline** — Run the eval set against the current prompt; record scores.
5. **Iterate with deltas** — Change one thing at a time; re-run the eval; keep changes
   that improve scores. Log the delta evidence.
6. **Document failure modes** — Record known failure modes and their mitigations
   (edge cases the eval can't cover).
7. **Version and ship** — Version the prompt; note model compatibility (tested on
   which models and versions). Wire the eval into CI as a regression guard.

## Quality Gates
- Every prompt has an eval set and a measured baseline.
- Degrees of freedom are tuned to the task (tight where correctness matters).
- Changes are versioned and delta-evaluated.
- Model compatibility is explicit and tested.

## Validation
- **Unit**: eval-set pass rates per behavior class.
- **Regression**: eval-set scores don't drop on iteration.
- **Certification**: prompts that gate releases are certified with their evals.

## Rollback
Prompts are versioned files. Reverting to the last evaluated version is a revert of a
file — the eval set makes the rollback decision objective.

## Failure Recovery
A regression on the eval set is a revert signal: revert to the last good version and
iterate from there. Never ship an unevaluated prompt change — "trust me, it's better"
is not evidence.

## Acceptance Criteria
- [ ] Evaluation set with labeled expected behaviors.
- [ ] Baseline and final scores recorded.
- [ ] Known failure modes documented with mitigations.
- [ ] Prompt versioned; model compatibility tested.

## Examples
### Example 1 — Triage agent system prompt
Behaviors: classify request as bug/feature/question; ask one clarifying question when
ambiguous; never fabricate statuses. Eval set: 30 labeled cases (happy, ambiguous,
adversarial). Baseline 82%; iteration: adding an explicit "if the request is
ambiguous, ask, do not guess" clause with a "what if" example → 94%. Failure mode
documented: sarcastic requests misclassified; mitigation: an adversarial subset in the
eval. Versioned, CI-regressed.

## Anti-patterns
- **Prompt by vibes**: shipping prompts with no eval set — forbidden.
- **Workflow-summary descriptions**: triggers that summarize the workflow — agents
  shortcut to the summary (superpowers' tested finding).
- **Hardcoded time-sensitivity**: dates and version numbers baked into instructions.
- **One-size-freedom**: high freedom where correctness matters (exact commands should
  be exact).

## Testing Strategy
Validated with eval-set fixtures and planted prompt defects (ambiguous triggers,
time-sensitive content) scored on detection. See `docs/testing-strategy.md`.

## Future Extensions
- Prompt-per-version eval matrices across multiple models.
- Automated prompt regression reporting in CI.
