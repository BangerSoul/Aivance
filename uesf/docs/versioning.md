# UESF Versioning Strategy

*Version 1.0.0 · Deliverable 11*

Versioning is what makes a framework safe to evolve. UESF versioning covers **skills**
(primary), the **framework release**, and the **spec**. The rules below are enforced
by `uesf-mk-skill-version-manager` and checked by the validator.

## 1. Skill versioning (semantic)

Every skill carries `version: x.y.z` in frontmatter.

| Bump | Meaning | Examples |
|------|---------|----------|
| **patch** `x.y.Z` | Correction/clarification; no behavior change | typo fix, rewritten example, trigger wording |
| **minor** `x.Y.0` | Behavior-affecting improvement, addition, optimization | new workflow phase, generalized trigger, cost reduction |
| **major** `X.0.0` | Breaking change | capability merge, removed workflow, different outputs |

Rules:

- Every version change ships with a changelog entry.
- A skill that changes behavior *without* a bump fails review — the bump is mandatory,
  not optional.
- Released versions are immutable: corrections are **new** versions, never edits.
- Dependents declare compatibility; `uesf-mk-skill-dependency-resolver` verifies the
  graph stays compatible.

## 2. Draft lifecycle

| State | Version | Meaning |
|-------|---------|---------|
| draft | `0.x` | scaffolded, being authored; may be deleted |
| candidate | `0.x` + review passed | validator + reviewer green; awaiting promotion |
| released | `≥1.0.0` | promoted; immutable |
| deprecated | `≥1.0.0`, `deprecated: true` | still readable; grace period; `superseded_by` set |
| removed | — | after the grace period, per governance |

## 3. Framework release versioning

The framework itself versions as **one release unit** (`VERSION` file, `CHANGELOG.md`):

- **patch** — tooling fixes, doc corrections, non-breaking policy edits.
- **minor** — new skills or categories (non-breaking), new workflows, improved tools.
- **major** — spec changes, breaking reorganization, removal of deprecated skills.

Releases are certified (`uesf-mk-skill-certification-engine`) before they are tagged.

## 4. Spec versioning

The spec (`docs/skill-spec.md` + `spec/skill-spec.schema.json`) versions independently:

- **minor** — additive fields (older skills remain valid).
- **major** — field removals/renames; a compat shim period where both shapes validate,
  then the migration runs.

## 5. Deprecation and migration

A skill being removed follows `uesf-mk-skill-version-manager`:

1. Mark `deprecated: true` + `superseded_by`.
2. Publish migration notes (what to use instead, what changed).
3. Notify dependents (resolver output) — breaking changes require dependents to move.
4. Grace period (default: 2 minor releases), then removal by governance.

## 6. Tooling

- Validator: checks semver format and consistency rules.
- `uesf-mk-skill-version-manager`: owns bump classification, changelog, deprecation.
- `uesf-re-release-engineering`: bundles skills into certified framework releases.
