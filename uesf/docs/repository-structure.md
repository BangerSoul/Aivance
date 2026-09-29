# UESF Repository Structure

*Version 1.0.0 · Deliverable 19: Repository for the Framework*

```
uesf/
├── README.md                      Framework overview, quick start, license
├── CHANGELOG.md                   Framework changelog (Keep a Changelog style)
├── LICENSE                        MIT
├── VERSION                        Current framework version (1.0.0)
│
├── core/                          L4 — the six model-agnostic primitives
│   ├── README.md
│   └── uesf-co-{planning,implementation,testing,debugging,review,refactoring}/SKILL.md
│
├── skills/                        L6/L7/L5 — domain skills by taxonomy category
│   ├── README.md                  Catalog index
│   ├── ar/ ra/ se/ pf/ ax/ do/ re/ de/ da/ ce/ rs/ gv/ le/
│   │     (engineering categories, one dir per skill)
│   ├── pe/ ai/                    (prompt engineering + AI engineering)
│   ├── ux/ ui/
│   └── <category>/uesf-<cc>-<slug>/SKILL.md   (the skill contract)
│
├── meta-skills/                   L15 — the self-improvement layer
│   ├── README.md                  Loop diagram + index
│   └── uesf-mk-{repository-analyzer,skill-generator,skill-optimizer,
│       skill-refactorer,skill-merger,skill-validator,skill-reviewer,
│       skill-benchmarker,skill-version-manager,skill-dependency-resolver,
│       skill-doc-generator,skill-test-generator,skill-certification-engine}/SKILL.md
│
├── templates/
│   └── skill-template/SKILL.md    The contract every new skill is scaffolded from
│
├── examples/                      Worked skills demonstrating the format
│   └── uesf-ex-{hello-world,api-design-review}/SKILL.md
│
├── spec/
│   └── skill-spec.schema.json     The machine-readable specification (L2)
│
├── tools/                         L13 — automation
│   ├── validate_framework.py      Validator (stdlib-only; parser + rules + CLI)
│   └── skill_scaffold.py          New-skill scaffolder
│
├── tests/                         L10 — the framework's test suite
│   ├── test_validator.py          unittest suite (14 tests)
│   └── fixtures/
│       ├── valid/                 Known-good skill
│       └── invalid/               Known-bad skills (missing field, bad version,
│                                  unknown category, missing dep, cycle, missing section)
│
├── docs/                          All v1.0.0 deliverables
│   ├── philosophy.md              Deliverable: design principles
│   ├── architecture.md            Deliverable 2: architecture comparison
│   ├── repository-analysis.md     Deliverable 1: the 11-repo analysis
│   ├── knowledge-graph.md         Deliverable 3: knowledge graph
│   ├── taxonomy.md                Deliverable 4: skill taxonomy
│   ├── dependency-graph.md        Deliverable 5: skill dependency graph
│   ├── skill-spec.md              Deliverable 8: specification standard
│   ├── migration-guide.md         Deliverable 10: migration guide
│   ├── versioning.md              Deliverable 11: versioning strategy
│   ├── testing-strategy.md        Deliverable 12: testing strategy
│   ├── certification-strategy.md  Deliverable 13: certification strategy
│   ├── continuous-learning.md     Deliverable 14: continuous learning strategy
│   ├── automation-strategy.md     Deliverable 15: automation strategy
│   ├── roadmap.md                 Deliverable 17: roadmap
│   ├── future-extensions.md       Deliverable 18: future extensions
│   └── repository-structure.md    Deliverable 19: this document
│
├── workflows/                     Composed, ready-to-run process definitions
│   ├── triage-implement-verify.md The default delivery workflow
│   └── skills-authoring-loop.md   The framework's own skill-creation workflow
│
├── policies/
│   ├── contribution.md            How to contribute skills (intake RFC)
│   └── quality-gates.md           The framework's non-negotiable gates
│
├── benchmarks/
│   ├── report.md                  Deliverable 16: benchmark report
│   └── matrices.md                The 10 comparison matrices
│
├── certification/
│   └── v1.0.0-certificate.md      Deliverable 20: v1.0.0 certification record
│
├── mcp/
│   ├── uesf.server.json           MCP server manifest exposing validator + scaffolder
│   └── README.md                  How to serve UESF tooling over MCP
│
└── integrations/
    └── README.md                  Install/consume UESF in each agent ecosystem
```

## Folder purpose rules

- **core/ — never empty, never bloated.** Only skills that every workflow composes.
  Additions require governance approval.
- **skills/<category>/** — one directory per taxonomy category; one skill directory
  per skill; skill dir name == skill id.
- **meta-skills/** — only skills whose output is *another skill or the framework
  itself*.
- **templates/** — the single source of truth for skill structure; scaffolder reads
  from here.
- **spec/** — machine-readable contracts only.
- **tools/** — stdlib-only executables (zero-dependency by policy).
- **tests/** — the validator's own proof; fixtures are small and readable.
- **docs/** — every v1.0.0 deliverable maps to a file here.
- **workflows/** — skill *compositions* (sequences of skill invocations), not new
  skills.
- **policies/** — governance text: what must happen, enforced by review, not code.
- **benchmarks/** — measurement artifacts: task sets, results, matrices.
- **certification/** — immutable certification records (revoked, never edited).
- **mcp/** — how UESF tooling is exposed to agents as MCP tools.
- **integrations/** — per-agent installation notes (the `.agents/skills/` era).

## Where new files go

| You are adding… | Goes in |
|-----------------|---------|
| A new capability skill | `skills/<category>/uesf-<cc>-<slug>/SKILL.md` via `tools/skill_scaffold.py` |
| A skill about skills | `meta-skills/uesf-mk-…/SKILL.md` |
| A taxonomy category | `docs/taxonomy.md` + validator `TAXONOMY` + schema enum (one commit) |
| A workflow composition | `workflows/` |
| A policy | `policies/` |
| A benchmark result | `benchmarks/` |
| A certification | `certification/` |
