# UESF Architecture

*Version 1.0.0 · Deliverable 2: Architecture Comparison + layer design*

## Layered architecture

UESF is organized into **fifteen layers**, mapped to the repository. The layers are
strictly directional: lower layers never depend on higher ones, which is what makes
the framework extensible without rewrites.

```
L15  Self-Improvement          meta-skills/            (generate, optimize, merge…)
L14  Continuous Learning       skills/le/…             (intake valve)
L13  Automation                tools/, workflows/      (validator, scaffolder, CI hooks)
L12  Release Engineering       skills/re/…             (immutable candidates, rollback)
L11  Certification             skills/ce/…, certification/
L10  Testing & Verification    skills/…, tests/        (fixtures, suite, gates)
L9   Project Governance        skills/gv/…
L8   Repository Intelligence   skills/ra/…
L7   AI Skills                 skills/ai/, skills/pe/  (agents, eval, RAG, prompting)
L6   Engineering Skills        skills/{ar,se,pf,ax,do,de,da,rs,…}/
L5   UX & UI                   skills/ux/, skills/ui/
L4   Core Skills               core/                   (plan·implement·test·debug·review·refactor)
L3   Knowledge Organization    docs/taxonomy.md, docs/knowledge-graph.md
L2   Specification             spec/, docs/skill-spec.md
L1   Foundation                templates/, policies/, docs/philosophy.md
```

**Direction rule:** a skill may depend on any skill in a lower layer. Dependencies
never point upward. The validator's cycle detection plus this rule keep the graph
acyclic by construction.

## Component view

```
                    ┌────────────────────────────────────────┐
                    │           SPECIFICATION (L2)           │
                    │  skill-spec.schema.json  ·  template   │
                    └───────────────┬────────────────────────┘
                                    │ conforms to
              ┌─────────────────────┼─────────────────────┐
              ▼                     ▼                     ▼
      ┌───────────────┐    ┌───────────────┐    ┌───────────────┐
      │  core/        │    │  skills/      │    │  meta-skills/ │
      │  (6 primitives)│   │  (20 domain)  │    │  (13 meta)    │
      └───────┬───────┘    └───────┬───────┘    └───────┬───────┘
              │                     │                     │
              └───────────┬─────────┴─────────┬───────────┘
                          ▼                   ▼
                 ┌────────────────┐   ┌────────────────┐
                 │   VALIDATOR    │   │    TEST SUITE  │
                 │ tools/ (L13)   │   │ tests/ (L10)   │
                 └───────┬────────┘   └────────┬───────┘
                         │                     │
                         ▼                     ▼
                 ┌────────────────────────────────────────┐
                 │  CERTIFICATION ENGINE (L11)            │
                 │  certification/*.certificate.md        │
                 └────────────────────────────────────────┘
```

## Architecture comparison with source repositories

| Aspect | anthropics/skills | obra/superpowers | google/skills | mattpocock/skills | **UESF** |
|--------|-------------------|------------------|---------------|-------------------|----------|
| Skill format | SKILL.md, 2 frontmatter fields | SKILL.md + refs, 2 fields | SKILL.md + references/ | SKILL.md, slash-command oriented | SKILL.md, **24 spec fields + 10 required sections** |
| Organization | Flat by domain folder | Flat skills/ + meta | Deep category tree | skills/{engineering,productivity} | **31-code taxonomy, layered** |
| Validation | None automated | Test-your-skills guidance | None | None | **Automated validator + test suite + certification** |
| Versioning | None | None | None | Plugin updates | **Semver + deprecation + migration (meta-skill)** |
| Dependency model | None | None | None | User-invoked → model-invoked | **Declared deps + resolver + cycle detection** |
| Extensibility | Manual authoring | Meta-skills (writing-skills) | Vendor-constrained | Fork/copy | **13 meta-skills + intake loop** |
| Agent scope | Claude-centric | Multi-harness | Claude/Codex/Gemini | Multi-model | **Model-agnostic by requirement** |

Full matrix: `benchmarks/matrices.md`.

## Why layered beats flat

1. **Extensibility without rewrites:** a new category slots into its layer; nothing
   above it changes.
2. **Composition without duplication:** domain skills delegate to core primitives
   (e.g., `uesf-ui-ui-implementation` depends on `uesf-co-implementation` +
   `uesf-ax-accessibility-audit`) instead of reimplementing them.
3. **Verification per layer:** each layer's skills validate independently, and the
   whole framework stays green as a single pass.

## Cross-cutting mechanisms

- **Discovery:** frontmatter `description` (when-to-use) is the trigger surface; the
  taxonomy is the index; `related_skills` provides lateral links.
- **Composition:** `dependencies` are the edges; the resolver guarantees resolution
  and acyclicity; the meta layer owns graph health.
- **Evolution:** version manager owns lifecycle; intake owns growth; optimizer and
  merger own quality over time.
- **Verification:** validator (spec) → tests (behavior) → benchmarks (quality) →
  certification (records).

See `docs/dependency-graph.md` for the current graph and `docs/repository-structure.md`
for the folder map.
