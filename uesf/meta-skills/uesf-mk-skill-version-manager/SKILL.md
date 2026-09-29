---
id: uesf-mk-skill-version-manager
name: Skill Version Manager
version: 1.0.0
category: mk
kind: meta
purpose: Own the skill lifecycle — semantic versioning, deprecation, supersession, and migration — with a consistent versioning strategy.
description: |
  Use when a skill is created, changed, deprecated, or superseded. Produces version
  bumps with changelog entries, deprecation notices, and migration notes. Implements
  the framework's versioning strategy so every skill's lifecycle is auditable and
  reversible.
triggers:
  - condition: "A skill is created, modified, merged, or removed"
  - condition: "A deprecation or migration needs to be announced"
  - example_prompt: "Version the merged profiling skill and deprecate its sources"
inputs:
  - "The skill change and its type (fix, behavior change, merge, deprecation)"
  - "Current version and dependents"
outputs:
  - "Version bump (semver-consistent) and changelog entry"
  - "Deprecation/supersession records with migration notes"
  - "Compatibility notes for dependents"
dependencies:
  - "uesf-mk-skill-validator"
  - "uesf-mk-skill-dependency-resolver"
context_requirements:
  - "Knowledge of the change's impact (breaking or not)"
  - "The dependents that reference the skill"
quality_gates:
  - "Bump type consistent with the change (patch/minor/major)"
  - "Changelog entry written for every version change"
  - "Deprecations record supersession and migration path"
validation:
  - unit
  - regression
  - certification
rollback: "Version records are commits: revert the bump or un-deprecate to restore the prior state."
failure_recovery: "A bad version (wrong bump type, broken compatibility) is corrected by a follow-up version record — versions are immutable once released, so corrections are new versions."
acceptance_criteria:
  - "Bump consistent with change type"
  - "Changelog written"
  - "Deprecation records complete (reason, supersession, migration)"
  - "Dependents informed (compatibility notes)"
automation_hooks:
  - "Version consistency check in the validator (pattern + bump policy)"
  - "Changelog generation at release"
mcp_tools:
  - "none"
cost:
  input_tokens: "~5k"
  output_tokens: "~2k"
  runtime_minutes: "5–15"
complexity: 2
maintainability_score: 5
scalability_score: 5
production_readiness: 5
related_skills:
  - "uesf-re-release-engineering"
  - "uesf-mk-skill-merger"
documentation: "docs/versioning.md"
---

# Skill Version Manager

## Overview
Skills evolve; evolution needs versioning. This meta-skill implements the
framework's versioning strategy (`docs/versioning.md`): semver-consistent bumps,
mandatory changelogs, explicit deprecation with supersession, and migration notes —
so every skill's lifecycle is auditable, reversible, and dependency-safe. It is the
lifecycle owner that other meta-skills call when they change skills.

## Execution Workflow
1. **Classify the change** — Determine the bump type:
   - **patch (x.y.Z)**: corrections, clarifications, no behavior change.
   - **minor (x.Y.0)**: behavior-affecting improvements, additions, optimizations.
   - **major (X.0.0)**: breaking changes, merges that change capability, rewrites.
2. **Bump and record** — Update the version in frontmatter; write the changelog entry
   (what changed, why, migration impact).
3. **Handle deprecations** — For removed/superseded skills: mark `deprecated: true`
   with `superseded_by`, state the reason, and publish migration notes. Deprecated
   skills remain readable for a grace period.
4. **Notify dependents** — Resolve who references the skill; publish compatibility
   notes (breaking changes require dependents to update).
5. **Validate** — The validator checks version format and the dependency resolver
   re-checks compatibility.

## Quality Gates
- Bump type consistent with the change.
- Changelog entry for every version change.
- Deprecations complete: reason, supersession, migration path.
- Dependents informed.

## Validation
- **Unit**: version pattern and bump-policy checks.
- **Regression**: dependents resolve against the new version.
- **Certification**: released skill versions are certified.

## Rollback
Version records are commits: revert the bump to restore the previous record.
Released versions are immutable — corrections are new versions, not edits.

## Failure Recovery
A wrong bump or broken compatibility is corrected by a follow-up version record
(patch or minor as appropriate) — the audit trail preserves the mistake and its
correction, which is exactly what versioning is for.

## Acceptance Criteria
- [ ] Bump consistent with change type.
- [ ] Changelog written.
- [ ] Deprecation records complete.
- [ ] Dependents informed.

## Examples
### Example 1 — Profiling merge lifecycle
The merge lands as a minor bump to `uesf-pf-performance-optimization` (1.2.0,
changelog: "absorbed request-profiling capability"); the intake candidate is rejected
at intake; the duplicate internal skill is deprecated 1.0.0 → deprecated:true,
superseded_by the merged skill, with migration notes. Resolver confirms dependents
repoint; validator green.

## Anti-patterns
- **Version-by-feel**: bumping patch for breaking changes — the policy table is
  applied.
- **Silent deprecations**: removing skills with no record — deprecation is explicit.
- **Changelog silence**: version changes with no entries — entries are mandatory.
- **Immutable-violations**: editing released versions — corrections are new versions.

## Testing Strategy
Validated with lifecycle fixtures (wrong bump types, missing changelogs) scored on
detection. See `docs/testing-strategy.md`.

## Future Extensions
- Automatic changelog assembly from version records.
- Deprecation grace-period enforcement.
