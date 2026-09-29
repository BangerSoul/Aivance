# UESF Meta Skills

The meta layer is what makes UESF *self-improving*: skills that create, validate,
optimize, merge, version, benchmark, and certify other skills. Every meta-skill is
itself a spec-conformant skill, so the framework's rules apply to the framework.

```
                    ┌─────────────────────────────┐
                    │  uesf-mk-repository-analyzer │  analyze the ecosystem
                    └──────────────┬──────────────┘
                                   ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│ skill-generator  │───▶│ skill-validator  │◀───│ skill-reviewer   │
└─────────┬────────┘    └────────┬─────────┘    └────────┬─────────┘
          │                     │                        │
          ▼                     ▼                        ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│ skill-test-      │    │ dependency-      │    │ skill-           │
│ generator        │    │ resolver         │    │ benchmarker      │
└─────────┬────────┘    └────────┬─────────┘    └────────┬─────────┘
          │                     │                        │
          ▼                     ▼                        ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│ skill-doc-       │    │ skill-version-   │    │ skill-           │
│ generator        │    │ manager          │    │ certification-   │
└──────────────────┘    └──────────────────┘    └────engine────────┘
          ▲                     ▲                        │
          └──────── optimizer / refactorer / merger ◀────┘
                            (change skills safely)
```

| Skill | Responsibility |
|-------|----------------|
| `uesf-mk-repository-analyzer` | Reverse-engineers external repos into citable analyses + proposals |
| `uesf-mk-skill-generator` | Produces new spec-conformant skills from gaps/requirements |
| `uesf-mk-skill-validator` | Enforces the spec (wraps `tools/validate_framework.py`) |
| `uesf-mk-skill-reviewer` | Reviews skills for executability, clarity, and compliance |
| `uesf-mk-skill-optimizer` | Improves skills with benchmarked before/after evidence |
| `uesf-mk-skill-refactorer` | Restructures skills without changing capability |
| `uesf-mk-skill-merger` | Merges overlapping skills into superior unified ones |
| `uesf-mk-skill-benchmarker` | Measures skills with frozen task sets and matrices |
| `uesf-mk-skill-version-manager` | Owns the skill lifecycle (semver, deprecation, migration) |
| `uesf-mk-skill-dependency-resolver` | Keeps the dependency graph resolved, acyclic, compatible |
| `uesf-mk-skill-doc-generator` | Regenerates documentation from the inventory |
| `uesf-mk-skill-test-generator` | Generates validation fixtures so every skill is testable |
| `uesf-mk-skill-certification-engine` | Issues evidence-backed certification records |

## The self-improvement loop

1. **Analyze** — `repository-analyzer` studies the ecosystem (`continuous-learning`
   drives the sweeps).
2. **Generate** — `skill-generator` + `test-generator` + `doc-generator` build the
   new skill.
3. **Validate** — `skill-validator` (and the tool) enforce the spec; `skill-reviewer`
   checks executability.
4. **Improve** — `optimizer` / `refactorer` / `merger` maintain quality over time.
5. **Govern** — `version-manager` and `dependency-resolver` keep the lifecycle sound.
6. **Prove** — `benchmarker` measures; `certification-engine` issues the record.

All meta-skills run through the same validator as the rest of the framework — the
framework has no exceptions for itself.
