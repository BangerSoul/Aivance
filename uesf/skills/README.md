# UESF Skills Catalog

Domain skills are organized by taxonomy category (see `docs/taxonomy.md`). Every
skill follows the spec in `docs/skill-spec.md` and passes the validator
(`tools/validate_framework.py`).

## Engineering

| Category | Skill | Purpose |
|----------|-------|---------|
| `ar` Architecture | `uesf-ar-solution-architecture` | Design with explicit trade-offs and ADRs |
| `ra` Repository Intelligence | `uesf-ra-repository-analysis` | Map a codebase before planning |
| `se` Security | `uesf-se-security-audit` | Threat-modeled audit with verified remediation |
| `pf` Performance | `uesf-pf-performance-optimization` | Profiled, evidence-driven optimization |
| `ax` Accessibility | `uesf-ax-accessibility-audit` | WCAG conformance, scans + manual passes |
| `do` Documentation | `uesf-do-documentation` | Verified, freshness-stamped documentation |
| `re` Release Engineering | `uesf-re-release-engineering` | Immutable candidates, staged rollout, proven rollback |
| `de` DevOps | `uesf-de-devops-automation` | Verified, secret-safe pipelines and automation |
| `da` Data | `uesf-da-data-modeling` | Traced models, reversible migrations |
| `rs` Research | `uesf-rs-research-synthesis` | Provenance-backed research briefs |
| `ce` Certification | `uesf-ce-certification-audit` | Evidence-backed certification records |
| `gv` Governance | `uesf-gv-project-governance` | Milestones, risks, decisions, status |
| `le` Learning | `uesf-le-continuous-learning` | Ecosystem intake for the framework |

## AI Engineering & Prompting

| Category | Skill | Purpose |
|----------|-------|---------|
| `pe` Prompt Engineering | `uesf-pe-prompt-engineering` | Evaluation-driven prompt design |
| `ai` Agents | `uesf-ai-agent-design` | Roles, orchestration, context budgets, escalation |
| `ai` Evaluation | `uesf-ai-evaluation` | Eval sets, baselines, regression gates |
| `ai` RAG | `uesf-ai-rag-systems` | Measured retrieval + enforced grounding |
| `ai` Integration | `uesf-ai-model-integration` | Provider-agnostic model integration |

## UX & UI

| Category | Skill | Purpose |
|----------|-------|---------|
| `ux` UX | `uesf-ux-ux-audit` | Evidence/opinion-tagged usability findings |
| `ui` UI | `uesf-ui-ui-implementation` | Token-based UI, all states, runtime-verified |

## Core (in `../core`)

The six model-agnostic primitives — planning, implementation, testing, debugging,
review, refactoring — are the composition substrate for everything above.

## Meta (in `../meta-skills`)

The thirteen skills about skills — the framework's self-improvement layer.

## Examples (in `../examples`)

Two worked skills: `uesf-ex-hello-world` (minimal) and `uesf-ex-api-design-review`
(composed).

Run `python3 tools/validate_framework.py --list` for the full machine-checked
inventory.
