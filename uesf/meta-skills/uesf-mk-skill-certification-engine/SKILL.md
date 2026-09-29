---
id: uesf-mk-skill-certification-engine
name: Skill Certification Engine
version: 1.0.0
category: mk
kind: meta
purpose: Certify skills and framework releases against the acceptance criteria — validator, tests, review, and benchmark evidence in one auditable record.
description: |
  Use when a skill is promoted, a release is cut, or a quality claim needs formal
  backing. Produces a certification record: per-gate evidence, verdict, residual
  risks, and revocation conditions. The top of the framework's quality stack —
  everything below it (validator, tests, review, benchmarks) feeds this gate.
triggers:
  - condition: "A skill or framework release needs formal certification"
  - condition: "A quality claim (v1.0, production-ready) must be evidenced"
  - example_prompt: "Certify framework v1.0.0 against all acceptance criteria"
inputs:
  - "The asset (skill, release, framework version) and its declared gates"
  - "Access to the validator, tests, review records, and benchmarks"
outputs:
  - "Certification record: gate-by-gate evidence and verdict"
  - "Residual risks and revocation conditions"
  - "Certificate artifact (committed, machine- and human-readable)"
dependencies:
  - "uesf-mk-skill-validator"
  - "uesf-mk-skill-reviewer"
  - "uesf-mk-skill-benchmarker"
context_requirements:
  - "The asset is at a certifiable state (commit/tag)"
  - "All lower gates runnable in the certification environment"
quality_gates:
  - "Every declared gate has recorded evidence — no assertion-based certification"
  - "Verdict per gate plus overall; residual risks explicit"
  - "Certificate carries its revocation condition"
validation:
  - unit
  - integration
  - regression
  - security
  - certification
rollback: "Certificates are revoked, not edited: publish a revocation notice with the failing evidence when a gate fails post-certification."
failure_recovery: "A failing gate at certification time = not certified. Fix, re-run the full certification, issue a new record. Partial certificates are contradictions."
acceptance_criteria:
  - "Gate-by-gate evidence recorded"
  - "Overall verdict justified"
  - "Residual risks and revocation conditions documented"
  - "Certificate artifact committed and linked"
automation_hooks:
  - "Certification checklist enforced in the release pipeline"
  - "Revocation check wired to gate re-runs (validator, scans)"
mcp_tools:
  - "none"
cost:
  input_tokens: "~8k"
  output_tokens: "~3k"
  runtime_minutes: "15–45"
complexity: 3
maintainability_score: 5
scalability_score: 5
production_readiness: 5
related_skills:
  - "uesf-ce-certification-audit"
  - "uesf-re-release-engineering"
documentation: "docs/certification-strategy.md"
---

# Skill Certification Engine

## Overview
The framework's core promise — "every skill is validated, versioned, testable, and
production-ready" — needs a formal closing gate. This meta-skill is that gate: it
aggregates the validator, tests, review records, and benchmarks into one auditable
certification record with a verdict, residual risks, and a revocation condition. It
is the capstone of the quality stack.

## Execution Workflow
1. **Declare the gates** — Enumerate the criteria for this certification: validator
   zero-errors, test suite green, review passed, benchmark targets met, security
   gates green. The asset's declared gates define the certification.
2. **Collect evidence** — Run each gate and capture raw output: validator report,
   test results, review records, benchmark numbers, scan reports. Evidence is
   timestamped and tied to the commit/tag.
3. **Assess per gate** — Pass/fail with evidence references. No evidence, no pass —
   the rule is absolute.
4. **Overall verdict** — Aggregate per-gate results. Any failed gate → not certified.
5. **Document residual risk** — Accepted risks, plus the revocation condition (the
   event that invalidates the certificate, e.g., "validator reports errors on the
   certified tag").
6. **Issue the record** — Commit the certificate (markdown + JSON record) and link it
   from the release/changelog. Publish revocation checks.

## Quality Gates
- Every gate has recorded evidence.
- Verdict per gate + overall, justified.
- Residual risks and revocation conditions documented.
- Certificate artifact committed and linked.

## Validation
- **Unit**: each gate's evidence reproducible on demand.
- **Integration**: the certified state is a re-verifiable commit/tag.
- **Security**: security gates re-run for certification.
- **Certification**: the record itself passes the framework's checks.

## Rollback
Certificates are revoked, not edited: a revocation notice supersedes the certificate,
with the failing evidence. The revocation condition defines when this happens
automatically.

## Failure Recovery
A failing gate means "not certified": fix, re-run the full certification, issue a new
record. Waivers are not a certification tool — if a risk is accepted, it belongs in
residual risks with governance sign-off, visibly.

## Acceptance Criteria
- [ ] Gate-by-gate evidence recorded.
- [ ] Overall verdict justified.
- [ ] Residual risks and revocation conditions documented.
- [ ] Certificate artifact committed and linked.

## Examples
### Example 1 — Framework v1.0.0 certification
Gates: validator (41 skills, 0 errors), test suite (unittest, 14/14 green), review
records (all intakes reviewed), benchmarks (core loop task set passing), security
(no findings open). Record issued: `certification/v1.0.0-certificate.md`, verdict
"certified", revocation condition "auto-revoke on validator errors or critical
security finding against the v1.0.0 tag".

## Anti-patterns
- **Certification by confidence**: "we tested it" with no raw evidence — gates are
  evidenced.
- **Partial certificates**: certifying with a known failure — all-or-nothing.
- **Frozen certificates**: no revocation condition — certificates self-expire or
  self-revoke.
- **Waiver-as-certification**: certifying around a gate with a silent waiver —
  residual risk is visible and governed.

## Testing Strategy
Validated with fixture assets with planted failing gates; scoring measures correct
refusals to certify. See `docs/testing-strategy.md`.

## Future Extensions
- Machine-readable certificate records consumed by tooling.
- Scheduled revocation checks in CI.
