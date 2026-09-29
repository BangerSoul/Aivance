---
id: uesf-le-continuous-learning
name: Continuous Learning & Skill Discovery
version: 1.0.0
category: le
kind: engineering
purpose: Discover, evaluate, and intake external skills and knowledge so the framework improves continuously without architectural churn.
description: |
  Use when new skills, patterns, or sources appear in the ecosystem (marketplace,
  repos, community), or when a gap in the framework is observed. Produces evaluated
  intake candidates: either merged into the framework or rejected with reasons.
  This is the intake valve of UESF's continuous-evolution requirement.
triggers:
  - condition: "A new skill/source is discovered that could improve the framework"
  - condition: "A repeated ad-hoc pattern suggests a missing skill"
  - example_prompt: "Survey the skills ecosystem for anything our framework is missing"
inputs:
  - "Candidate skills/sources (repo, marketplace entry, observed pattern)"
  - "Framework taxonomy and existing skills (to compare against)"
outputs:
  - "Intake evaluation per candidate (fit, overlap, quality, action)"
  - "Merged skills (via meta-skills) or documented rejections"
  - "Updated coverage notes and roadmap backlog"
dependencies:
  - "uesf-mk-skill-reviewer"
  - "uesf-mk-skill-merger"
  - "uesf-ra-repository-analysis"
context_requirements:
  - "Access to the candidates and the current framework state"
  - "A defined intake frequency (regular sweep, not ad-hoc)"
quality_gates:
  - "Every candidate evaluated against taxonomy, overlap, and quality criteria"
  - "No candidate merged without validation (the validator must pass)"
  - "Rejections are documented with reasons (rejecting is a finding)"
validation:
  - unit
  - integration
  - regression
  - certification
rollback: "Intake merges land as versioned skills — revert the skill's commit to undo an intake."
failure_recovery: "A merged skill that fails downstream validation is reverted or demoted to draft via uesf-mk-skill-version-manager, with the failure recorded."
acceptance_criteria:
  - "Candidate evaluation covers fit, overlap, and quality"
  - "All merged skills pass the framework validator"
  - "Rejections documented with reasons"
  - "Coverage backlog updated after each sweep"
automation_hooks:
  - "Scheduled ecosystem sweep (marketplace/repos) producing intake candidates"
  - "Validator re-run after every intake"
mcp_tools:
  - "none"
cost:
  input_tokens: "~10k"
  output_tokens: "~4k"
  runtime_minutes: "30–60 per sweep"
complexity: 3
maintainability_score: 4
scalability_score: 5
production_readiness: 4
related_skills:
  - "uesf-mk-skill-benchmarker"
  - "uesf-mk-skill-generator"
documentation: "docs/continuous-learning.md"
---

# Continuous Learning & Skill Discovery

## Overview
A framework that cannot ingest the ecosystem's best ideas becomes a museum. This skill
is UESF's intake valve: a disciplined, scheduled process for discovering, evaluating,
and merging external skills and patterns — and for documenting rejections. It is what
makes the framework "continuously extensible" in practice, not just in the README.

## Execution Workflow
1. **Sweep** — On schedule (or on trigger), survey the ecosystem: skill marketplaces
   (skills.sh, SkillsMP), curated lists (awesome-openclaw-skills), source repos, and
   internal observations of repeated ad-hoc patterns.
2. **Screen** — For each candidate, quick-fit check: category fit (taxonomy), overlap
   with existing skills (the anti-duplication rule), and quality signals (format
   compliance, validation evidence, maintenance state).
3. **Deep-evaluate** — For survivors, run the intake evaluation: what does it do that
   we don't? Is it generalizable? Can it be merged or does it need a rewrite?
4. **Intake via meta-skills** — Accept candidates through the meta layer: merge
   (`uesf-mk-skill-merger`), generate a superior version (`uesf-mk-skill-generator`),
   or adapt. Never copy wholesale — synthesize.
5. **Validate** — Run the framework validator and the skill's own verification; a
   merged skill that fails validation is reverted or demoted.
6. **Record** — Document intake decisions (merged / adapted / rejected + reason) and
   update the coverage backlog and roadmap.

## Quality Gates
- Every candidate evaluated against taxonomy, overlap, and quality criteria.
- No candidate merged without the validator passing.
- Rejections documented with reasons.
- The sweep is scheduled, not vibes-driven.

## Validation
- **Unit**: intake evaluation per candidate is reproducible.
- **Integration**: merged skills compose with existing skills (dependencies resolve).
- **Regression**: validator green across the whole framework after intake.
- **Certification**: intake doesn't invalidate the current certification.

## Rollback
Intake merges are versioned skills: revert the skill's commit. The validator re-run
after every intake makes failures visible immediately.

## Failure Recovery
A merged skill that fails downstream validation is reverted or demoted to draft via
`uesf-mk-skill-version-manager`, with the failure recorded in the intake log. Failing
fast is the design; hiding the failure is the sin.

## Acceptance Criteria
- [ ] Candidate evaluation covers fit, overlap, and quality.
- [ ] All merged skills pass the validator.
- [ ] Rejections documented with reasons.
- [ ] Coverage backlog updated after each sweep.

## Examples
### Example 1 — Ecosystem sweep Q3
Sweep finds 40 candidates across skills.sh and awesome-openclaw-skills. Screen: 6 pass
fit/quality. Deep-evaluate: 3 overlap existing skills (rejected with reasons: duplicate
capability), 2 are niche (deferred), 1 (an animation-review pattern) is generalized
into the framework via uesf-mk-skill-merger, validated, and logged. Coverage backlog
updated.

## Anti-patterns
- **Copy-paste intake**: importing skills wholesale — synthesize via meta-skills.
- **Rejection amnesia**: dropping candidates without recording why — rejections are
  findings.
- **Sweep-as-event**: intake only when someone remembers — it is scheduled.
- **Intake without validation**: merging skills that fail the validator — the gate is
  absolute.

## Testing Strategy
Validated with candidate fixtures (good, overlapping, and low-quality) scored on
correct accept/reject/merge decisions. See `docs/testing-strategy.md`.

## Future Extensions
- Automated marketplace sweeps with structured candidate extraction.
- Coverage heatmaps showing taxonomy gaps.
