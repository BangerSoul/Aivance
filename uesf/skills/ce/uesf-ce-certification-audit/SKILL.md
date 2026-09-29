---
id: uesf-ce-certification-audit
name: Certification Audit
version: 1.0.0
category: ce
kind: engineering
purpose: Verify that a system or framework asset meets its stated quality gates, and issue a certification record with evidence.
description: |
  Use when a release, framework version, or deliverable must be certified against
  defined criteria before sign-off. Produces a certification record: gate-by-gate
  evidence, verdict, residual risks, and a revocation condition. Turns "it should be
  fine" into "it is verified."
triggers:
  - condition: "A release, framework version, or deliverable needs formal sign-off"
  - condition: "A quality claim ('production-ready', 'certified') must be evidenced"
  - example_prompt: "Certify the framework v1.0.0 against the acceptance criteria"
inputs:
  - "The asset under certification and its declared gates"
  - "Access to run the verification (tests, scans, validators)"
outputs:
  - "Certification record: gate-by-gate evidence and verdict"
  - "Residual risks and revocation conditions"
  - "Certificate artifact (machine- and human-readable)"
dependencies:
  - "uesf-co-testing"
  - "uesf-co-review"
  - "uesf-se-security-audit"
context_requirements:
  - "The asset is in a certifiable state (branch/tag/commit)"
  - "Verification tools can run in the certification environment"
quality_gates:
  - "Every declared gate has recorded evidence — no gate certified by assertion"
  - "Verdict is per-gate plus overall; residual risks listed explicitly"
  - "Certificate includes its own validity scope and revocation condition"
validation:
  - unit
  - integration
  - regression
  - security
  - certification
rollback: "A certificate is a document and a revocation is its rollback: publish a revocation notice superseding the certificate when a gate fails post-hoc."
failure_recovery: "A failing gate at certification time means the asset is not certified — fix the failure, re-run the full certification, never issue a partial certificate."
acceptance_criteria:
  - "Certificate records evidence for every declared gate"
  - "Overall verdict justified by per-gate results"
  - "Residual risks and revocation condition documented"
  - "Certificate artifact committed and linked"
automation_hooks:
  - "Certification checklist enforced by the validator (tools/validate_framework.py) for framework assets"
  - "Revocation check: gate re-run triggers when dependencies change"
mcp_tools:
  - "none"
cost:
  input_tokens: "~10k"
  output_tokens: "~4k"
  runtime_minutes: "20–60"
complexity: 3
maintainability_score: 5
scalability_score: 5
production_readiness: 5
related_skills:
  - "uesf-mk-skill-certification-engine"
  - "uesf-gv-project-governance"
documentation: "docs/certification-strategy.md"
---

# Certification Audit

## Overview
"Production-ready" is a claim; a certificate is the evidence trail behind it. This
skill certifies an asset against its declared gates — recording per-gate evidence,
an overall verdict, residual risks, and a revocation condition. It is the formal gate
that turns the framework's quality promises into checked facts (the same discipline
Aivance applies to its own database/security certifications).

## Execution Workflow
1. **Declare the gates** — Enumerate the criteria this certification covers (tests,
   security scans, validation runs, documentation, acceptance criteria). The asset
   being certified must have declared gates to certify against.
2. **Run the evidence** — Execute each gate's verification and record raw output:
   test results, scan reports, validator runs, manual checks with timestamps.
3. **Assess per gate** — For each gate: pass/fail with the evidence reference. A gate
   is never certified by assertion — no evidence, no pass.
4. **Overall verdict** — Aggregate per-gate results into the verdict. Any failed gate
   = not certified (a partial certificate is a contradiction in terms).
5. **Document residual risk** — List accepted risks and the condition under which the
   certificate is revoked (e.g., a new critical dependency vulnerability, a gate
   regression).
6. **Issue the record** — Commit the certificate artifact (human-readable doc + JSON
   record) and link it from the asset's release/changelog.

## Quality Gates
- Every declared gate has recorded evidence.
- Verdict per gate + overall, justified.
- Residual risks and revocation condition documented.
- Certificate artifact committed and linked.

## Validation
- **Unit**: each gate's evidence is reproducible on demand.
- **Integration**: the certified state is a specific commit that can be re-verified.
- **Security**: security gates re-run for the certification.
- **Certification**: the certificate itself is checked by the framework validator when
  certifying framework assets.

## Rollback
Certificates are revoked, not edited: publish a revocation notice superseding the
certificate, with the failing evidence. The certificate's revocation condition defines
when this happens automatically.

## Failure Recovery
A gate fails at certification time → the asset is not certified, period. Fix the
failure, re-run the entire certification, and issue a new record. Never certify with a
waiver (waivers belong to project governance, explicitly, and are visible).

## Acceptance Criteria
- [ ] Evidence recorded for every declared gate.
- [ ] Overall verdict justified by per-gate results.
- [ ] Residual risks and revocation condition documented.
- [ ] Certificate artifact committed and linked.

## Examples
### Example 1 — Framework v1.0.0 certification
The audit runs the validator (41 skills, 0 errors), the test suite (14/14 green),
security review findings (closed), and the documentation checks. Produces
certification/v1.0.0-certificate.md with per-gate evidence, verdict "certified", and a
revocation condition: "revoked automatically if the validator reports errors on the
v1.0.0 tag."

## Anti-patterns
- **Certification by confidence**: "we tested it, trust us" — every gate has raw
  evidence.
- **Partial certificates**: certifying with a known failure — a certificate is
  all-or-nothing.
- **Frozen certificates**: no revocation condition — certificates carry their own
  expiry/revocation logic.
- **Certificate theater**: gating on the absence of evidence ("nothing broke") rather
  than the presence of passed gates.

## Testing Strategy
Validated on fixture assets with planted failing gates; scoring measures whether the
audit refuses certification correctly. See `docs/testing-strategy.md`.

## Future Extensions
- Machine-readable certificate records (JSON) consumed by tooling.
- Automatic revocation checks in CI on dependency changes.
