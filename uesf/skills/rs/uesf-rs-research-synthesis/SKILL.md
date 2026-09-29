---
id: uesf-rs-research-synthesis
name: Research & Synthesis
version: 1.0.0
category: rs
kind: engineering
purpose: Answer a question or choose a direction with evidence gathered from authoritative sources, synthesized with provenance.
description: |
  Use when a decision depends on external facts: library choice, API semantics,
  ecosystem trends, standards, or pricing. Produces an evidence-backed brief with
  sourced claims, confidence levels, and a recommendation. Every claim carries a
  source; opinions are separated from facts.
triggers:
  - condition: "A technical decision depends on facts outside the repository"
  - condition: "An architecture or migration choice needs evidence"
  - example_prompt: "Research the current state of cross-platform sync libraries for our stack"
inputs:
  - "The question or decision and its constraints"
  - "Known context (stack, versions, requirements)"
outputs:
  - "Research brief: findings with sources, confidence, and dates"
  - "Options comparison and recommendation"
  - "Explicit unknowns and verification steps"
dependencies:
  - "uesf-co-planning"
context_requirements:
  - "Web access or access to the authoritative docs"
  - "Time-box agreed for the research (research without a time-box expands)"
quality_gates:
  - "Every factual claim carries a source and a retrieval date"
  - "Primary sources (docs, standards) preferred over secondary summaries"
  - "Recommendation is traced to the findings; unknowns are explicit"
validation:
  - unit
  - documentation
rollback: "Research produces a document; revert the doc commit. No code or data affected."
failure_recovery: "When sources conflict, present the conflict with dates and versions rather than picking a side — conflicting sources are a finding."
acceptance_criteria:
  - "Claims sourced with retrieval dates; primary sources used where possible"
  - "Options compared against the stated constraints"
  - "Recommendation with explicit confidence and unknowns"
  - "Time-box respected"
automation_hooks:
  - "Research brief template enforced by templates/"
  - "Follow-up verification tasks generated for high-stakes claims"
mcp_tools:
  - "none"
cost:
  input_tokens: "~15k"
  output_tokens: "~6k"
  runtime_minutes: "15–60"
complexity: 3
maintainability_score: 4
scalability_score: 5
production_readiness: 4
related_skills:
  - "uesf-ar-solution-architecture"
  - "uesf-mk-skill-benchmarker"
documentation: "docs/skill-spec.md"
---

# Research & Synthesis

## Overview
Agents confidently cite hallucinations; the antidote is provenance. This skill forces
every claim to carry a source and a date, prefers primary sources, separates facts from
opinion, and time-boxes the effort — so a decision brief is trustworthy and finished.
It generalizes the research discipline (deep research patterns, the RAG-in-repo
approach of anthropics' claude-code-docs-rag, OpenClaw's search category) to any
evidence-backed decision.

## Execution Workflow
1. **Frame the question** — One decision, its constraints, and the confidence needed.
   Agree a time-box. A research question is answerable; "learn about X" is not.
2. **Plan the sources** — Primary sources first (official docs, standards, release
   notes, source code); secondary (blogs, summaries) only as pointers.
3. **Gather with provenance** — For each claim: source, retrieval date, version
   context. Note when sources conflict and why (version drift, scope difference).
4. **Separate fact from opinion** — Mark opinions, vendor claims, and community
   consensus distinctly from verifiable facts.
5. **Compare options** — Map findings onto the decision's constraints; score options
   with the evidence, not the enthusiasm.
6. **Recommend with confidence** — State the recommendation, its confidence, the
   decisive evidence, and the explicit unknowns. High-stakes claims get verification
   tasks (re-run a benchmark, check a version).
7. **Write the brief** — A document with sources inline, a retrieval date, and a
   recommendation the team can act on.

## Quality Gates
- Every factual claim sourced with retrieval date.
- Primary sources preferred; conflicts reported with dates and versions.
- Recommendation traced to findings; unknowns explicit.
- Time-box respected.

## Validation
- **Unit**: spot-check 3–5 claims against their sources.
- **Documentation**: brief is committed and linked from the decision (e.g., ADR).

## Rollback
Research is a document. Reverting the doc commit restores prior state; nothing else is
affected.

## Failure Recovery
- Sources conflict: report the conflict as a finding with dates/versions; recommend a
  verification experiment when the decision is high-stakes.
- Time-box exceeded: freeze the brief at its current state, list the unfinished
  questions explicitly, and schedule a follow-up — never "just a few more minutes".

## Acceptance Criteria
- [ ] Claims sourced with retrieval dates; primary sources used where possible.
- [ ] Options compared against the stated constraints.
- [ ] Recommendation with explicit confidence and unknowns.
- [ ] Time-box respected.

## Examples
### Example 1 — Sync library choice
Question: "Which sync approach for offline-first job data?" The brief compares
operation-log, full-state, and CRDT approaches against constraints (device storage,
battery, API cost), each claim sourced to docs/benchmarks with dates, separates vendor
marketing from measured facts, recommends operation-log with confidence "high" and a
verification task to spike the API contract. Committed next to the ADR it informs.

## Anti-patterns
- **Sourcing theater**: citations that don't support the claim — every claim checked.
- **Date-blind research**: citing 2023 blog posts for a 2026 decision — dates are
  mandatory.
- **Echo-chamber synthesis**: aggregating secondary summaries instead of primary docs.
- **Unbounded research**: no time-box, no freeze — research expands to fill the session.

## Testing Strategy
Validated with brief fixtures containing fabricated citations and stale claims; scored
on detection. See `docs/testing-strategy.md`.

## Future Extensions
- Automatic source-freshness re-check hooks for long-lived briefs.
- Template integration with the ADR flow.
