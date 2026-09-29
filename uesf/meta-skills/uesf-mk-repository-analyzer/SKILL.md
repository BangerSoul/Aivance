---
id: uesf-mk-repository-analyzer
name: Repository Analyzer
version: 1.0.0
category: mk
kind: meta
purpose: Analyze any repository — source or skill — to extract its design patterns, strengths, and gaps, and propose framework improvements.
description: |
  Use when evaluating an external skills repository, a codebase, or a candidate source
  for framework intake. Produces a structured analysis (purpose, architecture,
  patterns, strengths, weaknesses, hidden designs) and concrete improvement proposals
  for UESF. This is the front end of the framework's self-improvement loop.
triggers:
  - condition: "A source repository or skill collection needs deep analysis"
  - condition: "Framework improvement proposals are being generated from external sources"
  - example_prompt: "Reverse-engineer the superpowers skill framework and propose what we should adopt"
inputs:
  - "The repository (path or URL) and access to its contents"
outputs:
  - "Structured repository analysis report"
  - "Extracted patterns mapped to the UESF knowledge graph"
  - "Prioritized improvement proposals (adopt/adapt/merge/reject)"
dependencies:
  - "uesf-ra-repository-analysis"
  - "uesf-mk-skill-reviewer"
context_requirements:
  - "Read access to the repository under analysis"
  - "The current UESF taxonomy and skill inventory for comparison"
quality_gates:
  - "Every claim about the analyzed repo cites real files/skills"
  - "Patterns are mapped to the UESF knowledge graph, not listed in isolation"
  - "Each proposal has a disposition (adopt/adapt/merge/reject) with rationale"
validation:
  - unit
  - documentation
rollback: "Analysis is a document; revert the doc commit. No repository is modified by the analysis."
failure_recovery: "When a repository resists analysis (large, undocumented), time-box the pass and record the coverage limits explicitly rather than generalizing from a corner."
acceptance_criteria:
  - "Analysis covers purpose, architecture, patterns, strengths, weaknesses, hidden designs"
  - "Claims cite real artifacts from the analyzed repo"
  - "Proposals mapped to existing taxonomy with dispositions"
  - "Coverage limits documented"
automation_hooks:
  - "Analysis report template in templates/"
  - "Proposal backlog feed into docs/roadmap.md"
mcp_tools:
  - "none"
cost:
  input_tokens: "~20k"
  output_tokens: "~8k"
  runtime_minutes: "30–90"
complexity: 4
maintainability_score: 4
scalability_score: 5
production_readiness: 4
related_skills:
  - "uesf-le-continuous-learning"
  - "uesf-mk-skill-merger"
documentation: "docs/repository-analysis.md"
---

# Repository Analyzer

## Overview
The framework's self-improvement starts with honest, evidence-backed analysis of the
ecosystem — never vibe-based imitation. This meta-skill produces the structured,
citable reverse-engineering reports that drive intake (this framework's own
`docs/repository-analysis.md` was produced by this discipline). It is the analysis
front end of the adopt → synthesize → validate loop.

## Execution Workflow
1. **Inventory** — List the repository's structure: folders, skill files, docs,
   manifests. Record what actually exists (cite paths).
2. **Read the philosophy** — Extract the stated purpose and philosophy from READMEs and
   design docs; separate marketing from substance.
3. **Analyze the architecture** — Organization, naming, metadata format, skill
   structure, trigger/discovery design, workflow and verification patterns.
4. **Map to the knowledge graph** — Classify extracted patterns against the UESF
   taxonomy and knowledge graph: which categories, which concepts, which edges.
5. **Assess strengths and weaknesses** — What does it do well? Where does it fail
   (validation, versioning, generalization, discoverability)?
6. **Extract hidden patterns** — The non-obvious designs (meta-skills that write
   skills, trigger-description hygiene, plan-scoped workspaces, circuit breakers).
7. **Propose** — For each candidate pattern: disposition (adopt / adapt / merge /
   reject) with rationale and the target skill/taxonomy location. Feed the proposal
   backlog.

## Quality Gates
- Every claim cites real files/skills from the analyzed repository.
- Patterns map to the knowledge graph, not isolated lists.
- Each proposal has a disposition and rationale.
- Coverage limits documented (what wasn't analyzed).

## Validation
- **Unit**: spot-verify 5 claims against the source repository.
- **Documentation**: the report is committed with the extraction date.

## Rollback
Analysis is a document. Reverting the doc commit restores prior state; the analyzed
repository is never modified.

## Failure Recovery
Large/undocumented repositories get a time-boxed pass with explicit coverage limits —
generalizing from a single corner is how frameworks adopt cargo cults. Partial
analysis with documented limits beats comprehensive-looking fiction.

## Acceptance Criteria
- [ ] Analysis covers purpose, architecture, patterns, strengths, weaknesses, hidden designs.
- [ ] Claims cite real artifacts.
- [ ] Proposals mapped to taxonomy with dispositions.
- [ ] Coverage limits documented.

## Examples
### Example 1 — Reverse-engineering a skills framework
The skill analyzes obra/superpowers: inventories skills/, reads writing-skills,
brainstorming, subagent-driven-development; extracts the trigger-description hygiene
rule, the red-green-refactor iron law, and the circuit-breaker review loop; maps them
to taxonomy nodes (pe, co-testing, co-review); proposes: adopt trigger hygiene
(adapt into uesf-pe-prompt-engineering), adopt circuit breaker (already in
uesf-co-review), reject the full plan-scoped workspace (overhead for small teams,
recorded).

## Anti-patterns
- **Cargo-cult adoption**: copying features without analysis — every adoption has a
  mapped rationale.
- **Uncited claims**: "they do X" without a path — claims cite artifacts.
- **Comprehensive fiction**: claiming full coverage of an unread repository — limits
  are documented.
- **Isolated pattern lists**: patterns with no knowledge-graph mapping.

## Testing Strategy
Validated on fixture repositories with planted patterns; scoring measures extraction
accuracy and claim citability. See `docs/testing-strategy.md`.

## Future Extensions
- Automated repo-clone + inventory tooling.
- Pattern database with cross-repo provenance.
