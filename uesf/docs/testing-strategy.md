# UESF Testing Strategy

*Version 1.0.0 · Deliverable 12*

The framework's promise — *no skill may exist without validation* — is executed by a
four-tier testing strategy. Tier 1 runs in CI today; tiers 2–4 are meta-skills that
schedule and record deeper verification.

## Tier 1 — Automated spec validation (always on)

- **Tool:** `tools/validate_framework.py` (stdlib Python, no dependencies).
- **Scope:** every SKILL.md under `core/`, `skills/`, `meta-skills/`, `examples/`.
- **Checks:** frontmatter schema (24 fields, types, score ranges), taxonomy
  membership, id↔category agreement, kind constraints, semver, dependency resolution,
  cycle detection, required body sections, description length.
- **Suite:** `tests/test_validator.py` (14 tests) with `tests/fixtures/` covering
  valid and each invalid class.
- **Gate:** `python3 -m unittest discover -s tests` must be green; validator must
  report 0 errors for any release or certification.

## Tier 2 — Per-skill verification fixtures

- **Skill:** `uesf-mk-skill-test-generator`.
- **Mechanism:** every skill gets a fixture matrix — happy path, failure path, edge
  cases — with labeled expected outcomes, mapped to its acceptance criteria.
- **Trigger:** generated with the skill; updated in the same change as behavior changes.

## Tier 3 — Skill benchmarks

- **Skill:** `uesf-mk-skill-benchmarker`.
- **Mechanism:** frozen, labeled task sets per skill; baselines and deltas; comparison
  matrices. Used for optimizer/merger/refactorer before/after proof and for
  framework-vs-source comparisons (`benchmarks/matrices.md`).

## Tier 4 — Certification

- **Skill:** `uesf-mk-skill-certification-engine`.
- **Mechanism:** aggregates validator, tests, review records, and benchmarks into an
  auditable certificate with a revocation condition. No certificate without evidence.

## Coverage policy

| Artifact | Required verification | Owned by |
|----------|----------------------|----------|
| Every skill | Tier 1 (spec) + Tier 2 (fixtures) | validator, test-generator |
| Every merged/optimized/refactored skill | Tier 1 + Tier 3 (equivalence/delta) | benchmarker |
| Every framework release | Tier 1 + Tier 4 | certification-engine |
| Every prompt/agent change | Eval-set regression (in `uesf-ai-evaluation`) | ai-evaluation |

## Testing the tools

The validator is itself covered by `tests/test_validator.py`; the parser is unit-
tested against the full frontmatter feature set (scalars, lists, list-of-maps,
block scalars, nested maps, integer coercion). The scaffolder is exercised by the
scaffold-then-validate workflow in `workflows/skills-authoring-loop.md`.

## Running everything

```bash
python3 -m unittest discover -s tests -v   # Tier 1 suite
python3 tools/validate_framework.py        # Tier 1 full pass
python3 tools/validate_framework.py --graph  # graph verification
```

## Anti-patterns

- Validating only new skills — the whole framework must stay green every run.
- "Fix the test to pass" — the suite's fixtures encode the spec; changing them to
  accommodate a skill is spec-patching, governed not permitted.
- Certifying without Tier 1 — certification consumes evidence; it does not replace it.
