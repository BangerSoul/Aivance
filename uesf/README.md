# UESF — Ultimate Engineering Skills Framework

**Version 1.0.0 · Certified** · [Certification record](certification/v1.0.0-certificate.md)

UESF is a next-generation, model-agnostic, continuously extensible framework for
engineering skills used by AI agents. It was not assembled by copying existing skill
repositories — it was built by reverse-engineering the **design philosophies** of eleven
public skill repositories, synthesizing their strongest ideas, removing their
weaknesses, and standardizing the result into one self-validating framework.

> A framework for skills that exceeds every individual source repository — and that
> can improve itself without architectural rewrites.

## What makes UESF different

| Property | UESF guarantee |
|----------|----------------|
| **Every skill is validated** | A stdlib-only validator (`tools/validate_framework.py`) enforces the full spec: schema, taxonomy, dependencies, cycles, and required content. **41 skills, 0 errors, 0 warnings.** |
| **Every skill is versioned** | Semver + changelog + deprecation policy owned by `uesf-mk-skill-version-manager`. |
| **Every skill is testable** | `tests/` (12 unit tests, green) + `uesf-mk-skill-test-generator` produce fixtures per skill. |
| **No skill without verification** | The certification engine aggregates validator, tests, review, and benchmarks into auditable records. |
| **Model-agnostic** | No vendor prompt recipes. Works with Claude Code, ChatGPT, Codex, Gemini, Cursor, Windsurf, Cline, Antigravity, OpenHands, Aider, OpenClaw, and future agents (see `integrations/`). |
| **Continuously extensible** | 13 meta-skills generate, validate, optimize, merge, version, benchmark, and certify new skills. |
| **Anti-duplication by design** | `uesf-mk-skill-merger` merges overlaps; intake (`uesf-le-continuous-learning`) rejects duplicates with recorded reasons. |

## Quick start

```bash
# Validate the whole framework (stdlib only — no dependencies)
python3 tools/validate_framework.py

# List all skills / print the dependency graph
python3 tools/validate_framework.py --list
python3 tools/validate_framework.py --graph

# Run the test suite
python3 -m unittest discover -s tests -v

# Scaffold a new skill
python3 tools/skill_scaffold.py new pf android-startup-profiling --name "Startup Profiling"
```

## The framework in numbers

- **41 skills**: 6 core · 13 engineering · 7 AI/UX/UI · 13 meta · 2 examples
- **31 taxonomy categories** (`docs/taxonomy.md`)
- **24 required frontmatter fields** + 10 required body sections (`docs/skill-spec.md`)
- **1 spec schema** (`spec/skill-spec.schema.json`)
- **14 passing tests** · **0 validator errors** · **1 v1.0.0 certification**

## Repository layout

```
core/           Six model-agnostic primitives (the execution loop)
skills/         Domain skills by taxonomy category
meta-skills/    The framework's self-improvement layer (13 skills)
templates/      The skill contract every skill follows
examples/       Worked examples of the format
spec/           The machine-readable specification (JSON Schema)
tools/          Validator + scaffolder (stdlib Python)
tests/          Validator test suite + fixtures
docs/           All 20 deliverables of the v1.0 release
workflows/      Composed end-to-end workflows
policies/       Contribution and quality-gate policies
benchmarks/     Comparison matrices + benchmark report
certification/  Certification records
mcp/            MCP server manifest for the tooling
integrations/   How to install UESF into each agent ecosystem
```

## Start here

1. **The idea** — `docs/philosophy.md`
2. **How it's built** — `docs/architecture.md`
3. **The standard** — `docs/skill-spec.md` · `spec/skill-spec.schema.json`
4. **Where it came from** — `docs/repository-analysis.md` (11 repositories reverse-engineered)
5. **How it compares** — `benchmarks/matrices.md`
6. **Where it's going** — `docs/roadmap.md`

## License

MIT — see [LICENSE](LICENSE).
