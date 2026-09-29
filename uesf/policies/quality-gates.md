# UESF Quality Gates

*Version 1.0.0 · The non-negotiable gates of the framework*

These gates apply to **everything** in the framework — skills, tools, docs, and the
framework itself. A gate is binary: it passes or the work stops.

## G1 — Spec conformance

- **Command:** `python3 tools/validate_framework.py`
- **Pass:** 0 errors (warnings reviewed and dispositioned).
- **Stops:** any skill entering, changing, or being released.

## G2 — Framework tests

- **Command:** `python3 -m unittest discover -s tests`
- **Pass:** all tests green.
- **Stops:** any tooling change, spec change, or release.

## G3 — Evidence

- **Pass:** claims of completion are backed by recorded output (test logs, scan
  reports, validator reports, measurements).
- **Stops:** any "done" claim without evidence.

## G4 — Review

- **Pass:** `uesf-co-review` (or `uesf-mk-skill-reviewer` for skills) with no
  blockers; verdict recorded.
- **Stops:** merge, promotion, release.

## G5 — Security

- **Pass:** no open blocker/major findings on the change (`uesf-se-security-audit`
  lens); secrets never in code/logs/history.
- **Stops:** merge, release, certification.

## G6 — Certification

- **Pass:** `uesf-mk-skill-certification-engine` (framework) /
  `uesf-ce-certification-audit` (systems) with gate-by-gate evidence.
- **Stops:** release tagging, "production-ready" claims.

## G7 — Lifecycle hygiene

- **Pass:** every version change has a changelog entry; deprecations carry
  `superseded_by` + migration notes; the dependency graph is resolved and acyclic.
- **Stops:** release; warns on drift.

## Escalation

- A gate can be *explicitly waived* only by governance, in writing, with the
  residual risk recorded — never by silence. Waivers are visible and expire.
- Gate failures always produce a finding for the root cause, never a quiet
  adjustment of the gate.
