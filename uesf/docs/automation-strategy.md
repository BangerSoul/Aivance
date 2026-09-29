# UESF Automation Strategy

*Version 1.0.0 · Deliverable 15*

Automation is where the framework's guarantees become *continuous* instead of
*aspirational*. This document maps what is automated today, what each automation
hooks into, and the roadmap for deepening automation.

## Automation inventory (v1.0.0)

| Automation | Where | What it guarantees |
|------------|-------|--------------------|
| Framework validator | `tools/validate_framework.py` — CLI, CI-ready (exit codes), `--json` output | Every skill conforms to the spec; graph is resolved and acyclic |
| Test suite | `python3 -m unittest discover -s tests` | The validator itself behaves correctly |
| Skill scaffolder | `tools/skill_scaffold.py` | New skills start spec-conformant |
| Graph printer | `--graph` flag | Dependency graph is a regenerable artifact |
| Skill index | `--list` flag | Machine-checked inventory |
| Intake screen | `uesf-le-continuous-learning` (scheduled) | Ecosystem gaps/overlaps discovered systematically |
| Fixture generation | `uesf-mk-skill-test-generator` | Skills ship with verification |
| Version records | `uesf-mk-skill-version-manager` | Changelogs and deprecations are mandatory |
| Certification records | `uesf-mk-skill-certification-engine` | Releases carry evidence-backed certificates |
| MCP exposure | `mcp/uesf.server.json` | Validator/scaffolder reachable as agent tools |

## Hook points

- **Pre-merge (CI):** validator + suite + (where configured) fixture coverage.
- **Pre-release:** full validation + changelog consistency + certification run.
- **Post-release:** revocation checks against certified tags.
- **Scheduled:** ecosystem sweeps (quarterly), doc freshness, benchmark re-runs.

## Automation principles

1. **Stdlib-only tooling.** No dependency installation — automation must run in any
   CI, offline.
2. **Deterministic output.** Same input → same report; exit codes are meaningful
   (0 pass / 1 fail / 2 error).
3. **Human-auditable.** Reports list per-skill, per-rule findings; nothing is a
   black box.
4. **The framework automates itself.** Meta-skills automate the meta layer; the
   tooling automates the spec.

## Roadmap

- **CI wiring templates** per platform (GitHub Actions, GitLab CI) — `de` skill.
- **Benchmark harness** scheduled re-runs with archived results.
- **Doc freshness check** (inventory ↔ docs diff) as a CI gate.
- **MCP server implementation** (stdlib JSON-RPC) beyond the manifest.
- **Release pipeline** that assembles certified release bundles automatically.

## Anti-patterns

- Automation that gates nothing (pipeline theater) — every job informs a decision.
- Automation that requires network/deps in CI — stdlib-only by policy.
- Reports nobody reads — each automation's output feeds a named decision.
