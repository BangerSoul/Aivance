# UESF Continuous Learning Strategy

*Version 1.0.0 · Deliverable 14*

A framework that cannot ingest the ecosystem's best ideas becomes a museum. UESF's
continuous-learning strategy is the *operating system* for that ingestion: scheduled
discovery, disciplined evaluation, synthesis over copying, and recorded decisions.

## The intake loop

```
Sweep (scheduled) → Screen (fit/overlap/quality) → Deep-evaluate → Synthesize
   (generator/merger/optimizer) → Validate → Record (decisions + backlog)
```

Owned by `uesf-le-continuous-learning`; the heavy lifting is done by the meta layer.

## Sources

- Skill marketplaces and registries (skills.sh, SkillsMP).
- Curated indexes (awesome-openclaw-skills style) — with the curation caveats
  documented in `docs/repository-analysis.md`.
- Source repositories (the eleven + future ones), via `uesf-mk-repository-analyzer`.
- Internal signal: repeated ad-hoc patterns observed in real workflows (gap signal).

## Screen criteria (adapted from the OpenClaw curation research)

1. **Spam/bot** — reject.
2. **Duplicate/near-duplicate** — merge or reject with recorded reason.
3. **Quality** — format compliance, validation evidence, maintenance state.
4. **Security** — flagged/malicious or unverifiable provenance → reject.
5. **Fit** — taxonomy relevance and framework philosophy (model-agnostic, verifiable).

## Decision dispositions

| Disposition | Meaning |
|-------------|---------|
| **Adopt** | pattern/idea integrated (with provenance) |
| **Adapt** | idea generalized into an existing UESF skill |
| **Merge** | overlap folded via `uesf-mk-skill-merger` |
| **Reject** | recorded with reason (rejection is a finding) |
| **Defer** | backlog with trigger condition |

## Versioning of knowledge

- Skills version via the version manager; adoptions bump the target skill.
- The knowledge graph (`docs/knowledge-graph.md`) records concept/pattern nodes with
  provenance — the framework's institutional memory.
- The roadmap backlog (`docs/roadmap.md`) is updated after every sweep.

## Cadence

- **Continuous:** intake candidates filed as found.
- **Scheduled:** full ecosystem sweep per release cycle (default: quarterly, at the
  start of each minor release).
- **Triggered:** before adopting a new category or before a spec change, a focused
  sweep of the relevant domain.

## Success criteria

1. Taxonomy gaps shrink each cycle (coverage heatmap trend).
2. Rejected candidates are traceable to recorded reasons.
3. No capability is adopted without validator-green synthesis.
4. The framework's own docs stay current with the ecosystem (freshness checks).
