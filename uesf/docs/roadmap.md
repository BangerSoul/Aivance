# UESF Roadmap

*Version 1.0.0 · Deliverable 17*

The roadmap is owned by the governance skill and updated by the intake loop. Items
are tracked as **R-IDs**; each carries a trigger condition so the roadmap stays
alive between releases.

## v1.1 — Hardening (next)

| ID | Item | Why | Trigger |
|----|------|-----|---------|
| R-01 | Publish to skills.sh / marketplace | distribution interop | any external user asks to install |
| R-02 | Quantitative benchmark task sets for the core loop | replace qualitative matrix scores with numbers | first optimization of a core skill |
| R-03 | CI wiring templates (GitHub Actions, GitLab) | automation gate out of the box | first external adoption |
| R-04 | Doc-freshness CI check (inventory ↔ docs) | docs can't drift | first doc drift found |

## v1.2 — Depth

| ID | Item | Why |
|----|------|-----|
| R-05 | Cloud + networking category skills | taxonomy coverage (currently referenced as extensions) |
| R-06 | MCP runtime server (stdlib JSON-RPC) exposing validator/scaffolder | agents call tools, not commands |
| R-07 | Skill marketplace packaging (per-skill bundles with metadata) | distribution |
| R-08 | Cross-model eval matrices for prompt/agent skills | honest model-agnosticism evidence |

## v2.0 — Scaling

| ID | Item | Why |
|----|------|-----|
| R-09 | Multi-repo framework mode (one framework, many consuming repos) | org-wide adoption |
| R-10 | Skill telemetry (usage, cost, failure rates per skill) | data-driven optimization |
| R-11 | Spec v2 (additive fields only): `profiles`, `extensions`, `localization` | needs-driven evolution |
| R-12 | Certification marketplace (third-party certified skills) | trust layer |

## Long-term themes

1. **The framework as a service:** CI/CD, certification, and telemetry for skills —
   the meta layer industrialized.
2. **Cross-agent runtime:** one skill graph, executed across any agent harness with
   consistent verification.
3. **Knowledge continuity:** the knowledge graph becomes machine-maintained from
   validation and benchmark data.

## Backlog intake

New roadmap items come from `uesf-le-continuous-learning` sweeps, governance
escalations, and observed gaps. Every item must state: the problem, the
disposition (build / adapt / defer / reject), and the trigger condition. Items
without triggers are aspirational and get parked.
