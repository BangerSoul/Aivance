---
id: uesf-re-release-engineering
name: Release Engineering
version: 1.0.0
category: re
kind: engineering
purpose: Plan and execute a release — versioning, packaging, verification, rollout, and rollback — with a repeatable, evidence-based process.
description: |
  Use when cutting a release: version bump, changelog, build, verify, deploy/rollout,
  and rollback plan. Produces a release candidate with recorded evidence, a staged
  rollout plan, and a proven rollback path. Applies to apps, libraries, and services.
triggers:
  - condition: "A versioned artifact is about to be cut (app, package, service)"
  - condition: "A release process is being set up or improved"
  - example_prompt: "Cut release v2.1.0 of the app with staged rollout and rollback plan"
inputs:
  - "Codebase at the release point, changelog, version history"
  - "Release process and environment access (CI, stores, registries)"
outputs:
  - "Release candidate with build/verification evidence"
  - "Version bump + changelog (semver-consistent)"
  - "Staged rollout plan with go/no-go gates and rollback steps"
dependencies:
  - "uesf-co-review"
  - "uesf-co-testing"
context_requirements:
  - "Ability to build the artifact and run verification in the release environment"
  - "Documented or discoverable release process"
quality_gates:
  - "Version bump is semver-consistent with the changelog"
  - "Release candidate is the exact artifact verified (immutable build)"
  - "Rollback path proven, not assumed"
validation:
  - unit
  - integration
  - regression
  - certification
rollback: "The rollback path is part of the plan: previous artifact re-deployable, data migrations reversible, feature flags off. It is tested before go-live."
failure_recovery: "On release failure: stop the rollout, execute the rollback path, and post-incident the cause — never continue a degraded release."
acceptance_criteria:
  - "Immutable release candidate with recorded build/verification evidence"
  - "Semver-consistent version + changelog reviewed"
  - "Staged rollout with go/no-go gates"
  - "Rollback path tested and documented"
automation_hooks:
  - "CI/CD pipeline builds and verifies the candidate immutably"
  - "Release checklist enforced as a policy (policies/quality-gates.md)"
mcp_tools:
  - "none"
cost:
  input_tokens: "~12k"
  output_tokens: "~5k"
  runtime_minutes: "30–120"
complexity: 4
maintainability_score: 4
scalability_score: 5
production_readiness: 5
related_skills:
  - "uesf-gv-project-governance"
  - "uesf-de-devops-automation"
  - "uesf-mk-skill-version-manager"
documentation: "docs/skill-spec.md"
---

# Release Engineering

## Overview
Releases fail in predictable ways: unverified artifacts, drifted environments, and
rollback paths that exist only on paper. This skill makes the release a repeatable,
evidence-based process: immutable candidate, verified gates, staged rollout, and a
proven rollback path — every step auditable.

## Execution Workflow
1. **Freeze and scope** — Confirm the release point: branch/tag, merged changes, known
   issues that are *in* this release, and anything explicitly deferred.
2. **Version and changelog** — Apply semver to the version bump (breaking → major,
   feature → minor, fix → patch), with a changelog generated and human-reviewed against
   the actual merged changes.
3. **Build the candidate immutably** — The artifact is built once by CI with recorded
   inputs (commit hash, environment). The artifact itself is the release candidate —
   never rebuild at deploy time.
4. **Verify the candidate** — Run the release verification suite on the candidate:
   tests, security scans, smoke tests. Record evidence against the artifact hash.
5. **Stage the rollout** — Plan the rollout in stages (e.g., canary → 10% → 50% →
   100%, or internal → store review), each with a go/no-go gate and the metric that
   decides it.
6. **Prove the rollback** — Document and test the rollback path: previous artifact
   redeployable, data migrations reversible, feature flags off. If rollback is not
   proven, the release does not proceed.
7. **Execute with gates** — Walk the stages; at each gate, check the decision metrics
   and proceed or roll back. Record the whole run.

## Quality Gates
- Version bump semver-consistent with the changelog (reviewed).
- Release candidate is the exact verified artifact (immutable).
- Rollback path proven and documented before go-live.
- Every gate decision is recorded with evidence.

## Validation
- **Unit**: changelog ↔ merged-changes consistency check.
- **Integration**: smoke tests run against the candidate artifact.
- **Regression**: full suite + security scans on the candidate.
- **Certification**: release checklist completed and archived.

## Rollback
Rollback is designed into the release: previous artifact verified redeployable, data
migrations reversible, feature flags off. It is exercised (at least dry-run) before
go-live.

## Failure Recovery
On release failure: stop, execute the proven rollback path, then post-incident —
root-cause with `uesf-co-debugging` and feed the fix into the next release. Never push
a degraded release forward "to see if it settles."

## Acceptance Criteria
- [ ] Immutable release candidate with recorded build/verification evidence.
- [ ] Semver-consistent version and reviewed changelog.
- [ ] Staged rollout with documented go/no-go gates.
- [ ] Rollback path tested and documented.

## Examples
### Example 1 — v2.1.0 app release
CI builds the candidate from tag v2.1.0 (hash recorded); verification suite green
(tests + security scan). Rollout: internal testers → 10% → 50% → 100% with crash-free
rate as the gate metric. Rollback path: previous store build redeployable; a feature
flag disables the new sync client without a rebuild. At the 50% gate, crash-free rate
dips; rollback to 10%, root-cause, fix, re-cut. Recorded end-to-end.

## Anti-patterns
- **Release-by-rebuild**: rebuilding at deploy time — the artifact must be immutable and
  hash-verified.
- **Paper rollback**: a rollback plan nobody has run — must be proven.
- **Changelog fiction**: entries that don't match merged changes — verified against the
  diff.
- **Big-bang rollout**: skipping stages to save time — stages exist because they work.

## Testing Strategy
Validated with release-plan fixtures containing unverifiable artifacts and unproven
rollbacks; scored on detection. See `docs/testing-strategy.md`.

## Future Extensions
- Automated gate metrics wiring (crash-free rate, error budget).
- Multi-environment release matrix templates.
