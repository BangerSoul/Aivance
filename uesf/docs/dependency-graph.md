# UESF Skill Dependency Graph

*Version 1.0.0 · Deliverable 5 · machine-checked*

Regenerate at any time:

```bash
python3 tools/validate_framework.py --graph
```

41 skills · 31 dependency edges · resolved · acyclic · version-compatible
(verified by the validator and `uesf-mk-skill-dependency-resolver`).

## Layer diagram (dependencies point downward)

```
CORE (root layer)
  uesf-co-planning
  uesf-co-testing
  uesf-co-implementation ──► planning, testing
  uesf-co-debugging ────────► testing
  uesf-co-review ───────────► testing
  uesf-co-refactoring ──────► testing, implementation

ENGINEERING ──────────────────────────────────────────────
  uesf-ar-solution-architecture ──► planning, research
  uesf-ra-repository-analysis ────► planning
  uesf-se-security-audit ─────────► review, testing
  uesf-pf-performance-optimization ► testing, implementation
  uesf-ax-accessibility-audit ────► testing, review
  uesf-do-documentation ──────────► repository-analysis
  uesf-re-release-engineering ────► review, testing
  uesf-de-devops-automation ──────► testing, security
  uesf-da-data-modeling ──────────► testing, planning
  uesf-rs-research-synthesis ─────► planning
  uesf-ce-certification-audit ────► testing, review, security
  uesf-gv-project-governance ─────► planning
  uesf-le-continuous-learning ────► reviewer, merger, repository-analysis

AI / UX / UI
  uesf-pe-prompt-engineering ─────► ai-evaluation
  uesf-ai-agent-design ───────────► prompt-engineering, ai-evaluation
  uesf-ai-evaluation ─────────────► testing
  uesf-ai-rag-systems ────────────► ai-evaluation, data-modeling
  uesf-ai-model-integration ──────► ai-evaluation, prompt-engineering
  uesf-ux-ux-audit ───────────────► research
  uesf-ui-ui-implementation ──────► implementation, accessibility

META (top layer — everything flows to them, they flow to each other)
  uesf-mk-repository-analyzer ────► ra-repository-analysis, mk-skill-reviewer
  uesf-mk-skill-generator ────────► mk-skill-validator, mk-skill-test-generator,
                                    mk-skill-doc-generator
  uesf-mk-skill-validator ────────► (root of the meta layer)
  uesf-mk-skill-reviewer ─────────► mk-skill-validator
  uesf-mk-skill-optimizer ────────► mk-skill-benchmarker, mk-skill-validator,
                                    mk-skill-version-manager
  uesf-mk-skill-refactorer ───────► mk-skill-validator, mk-skill-benchmarker
  uesf-mk-skill-merger ───────────► mk-skill-validator, mk-skill-dependency-resolver,
                                    mk-skill-benchmarker
  uesf-mk-skill-benchmarker ──────► mk-skill-validator
  uesf-mk-skill-version-manager ──► mk-skill-validator, mk-skill-dependency-resolver
  uesf-mk-skill-dependency-resolver► mk-skill-validator
  uesf-mk-skill-doc-generator ────► mk-skill-validator
  uesf-mk-skill-test-generator ───► mk-skill-validator
  uesf-mk-skill-certification-engine► mk-skill-validator, mk-skill-reviewer,
                                      mk-skill-benchmarker

EXAMPLES
  uesf-ex-hello-world ────────────► (no dependencies)
  uesf-ex-api-design-review ──────► co-review, se-security-audit
```

## Properties

- **Acyclicity:** proven by the validator's cycle detection on every run.
- **Layering:** no upward edges (architecture rule).
- **Fan-out:** core skills are the most depended-upon nodes
  (`uesf-co-testing`: 8 dependents; `uesf-co-planning`: 6) — the composition
  substrate works as designed.
- **Meta cohesion:** the thirteen meta-skills form a tight, validated graph with
  `uesf-mk-skill-validator` as their root — the framework's rules apply to the
  framework.

## Edge governance

- New edges are declared in frontmatter `dependencies` and must exist
  (validator error otherwise).
- Missing/unresolvable edges and cycles are repaired by
  `uesf-mk-skill-dependency-resolver`.
- Version compatibility across edges is checked by `uesf-mk-skill-version-manager`.
