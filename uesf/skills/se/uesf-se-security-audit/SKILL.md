---
id: uesf-se-security-audit
name: Security Audit
version: 1.0.0
category: se
kind: engineering
purpose: Identify and fix security weaknesses in a codebase or change — threat modeling, dependency scanning, and hardening — with verified remediation.
description: |
  Use when reviewing a change for security, auditing a codebase, onboarding to a
  security-sensitive system, or before release. Produces a prioritized finding list
  (threat-model driven), verified fixes, and regression tests. Complements
  uesf-co-review's security lens and uesf-ce-certification-audit's formal gate.
triggers:
  - condition: "A change or codebase handles untrusted input, secrets, or PII"
  - condition: "A release or certification gate requires a security review"
  - example_prompt: "Audit the sync feature for injection, secrets handling, and authz gaps"
inputs:
  - "Code, change diff, or repository"
  - "Threat context: data sensitivity, exposure surface, trust boundaries"
outputs:
  - "Threat model (assets, trust boundaries, attack paths)"
  - "Prioritized findings (severity × exploitability) with evidence"
  - "Verified fixes with regression tests"
dependencies:
  - "uesf-co-review"
  - "uesf-co-testing"
context_requirements:
  - "Ability to run the build and tests for verification"
  - "Knowledge of what the system exposes (endpoints, inputs, stored data)"
quality_gates:
  - "Every finding maps to a threat-modeled attack path"
  - "No secret or credential appears in code, logs, or committed artifacts"
  - "Remediation is verified by a test that fails on the vulnerable code"
validation:
  - unit
  - integration
  - security
  - regression
rollback: "Each fix is a small scoped commit; revert individually. Secret rotation, when required, follows the operational runbook, not the audit."
failure_recovery: "High-severity findings halt the change (blocker). Fix with uesf-co-implementation, re-audit the delta, and only then resume."
acceptance_criteria:
  - "Threat model documents assets, trust boundaries, and top attack paths"
  - "All findings prioritized by severity with evidence and remediation"
  - "No secrets in code, logs, or history (scan verified)"
  - "Critical findings fixed and regression-tested"
automation_hooks:
  - "Secret scanning and dependency vulnerability scan in CI"
  - "SAST in the pre-merge gate"
mcp_tools:
  - "none"
cost:
  input_tokens: "~15k"
  output_tokens: "~6k"
  runtime_minutes: "30–120"
complexity: 4
maintainability_score: 4
scalability_score: 4
production_readiness: 5
related_skills:
  - "uesf-ce-certification-audit"
  - "uesf-co-review"
documentation: "docs/skill-spec.md"
---

# Security Audit

## Overview
Security is a property of the whole system, not a checklist. This skill centers the
threat model: assets, trust boundaries, and attack paths — then audits against it,
produces prioritized, evidence-backed findings, and demands verified remediation. It
synthesizes the strongest patterns from the security skill of the source ecosystem
(google/skills' gke-platform-security, the security scans in OpenClaw curation) into a
model-agnostic process.

## Execution Workflow
1. **Model the threat** — Enumerate assets (data, credentials, keys, compute), trust
   boundaries (network, user input, third parties), and the top attack paths (OWASP
   Top 10 mapped to the system). This is the audit's spine.
2. **Scan the surface** — Run automated gates: dependency vulnerability scan, secret
   scan (including git history), SAST if available. Treat results as input, not verdict.
3. **Audit the paths** — Walk each attack path against the code: input validation,
   injection (SQL/command/template), authn/authz, secrets handling, logging of sensitive
   data, insecure defaults, and error disclosure.
4. **Prioritize findings** — Severity × exploitability × exposure. Blocker = exploitable
   now or data loss. Record evidence (file, line, scenario) per finding.
5. **Remediate** — Fix blockers and majors via `uesf-co-implementation` (test-first:
   a test that fails on the vulnerable code). Minor findings are filed, not dropped.
6. **Verify** — Re-run scans; confirm each fix has its regression test; re-audit the
   delta only.
7. **Report** — Findings, severity, status, and residual risk, as the audit artifact.

## Quality Gates
- Every finding traces to a threat-modeled attack path.
- No secrets in code, logs, or history (verified by scan).
- Remediation is proven by a test that fails on the vulnerable code.
- Residual risk is explicitly accepted or escalated, never ignored.

## Validation
- **Unit**: injection/validation tests per vulnerable entry point.
- **Integration**: end-to-end exploit scenarios (e.g., unauthenticated request) blocked.
- **Security**: scans re-run green after remediation.
- **Regression**: full suite green; no behavior regressions from fixes.

## Rollback
Fixes are small scoped commits, individually revertible. Where remediation involves
rotating a leaked secret, the rotation follows the operational runbook — the audit
flagging it is not the rotation itself.

## Failure Recovery
A blocker finding halts the change. Stop, remediate with evidence, re-audit the delta,
and only then resume. Never ship with a known blocker and a promise to fix later — that
is an explicit escalation to governance, not a default.

## Acceptance Criteria
- [ ] Threat model documents assets, trust boundaries, and top attack paths.
- [ ] Findings prioritized with evidence and remediation status.
- [ ] Secret/dependency scans verified green (or findings remediated).
- [ ] Blocker and major findings fixed with regression tests.

## Examples
### Example 1 — Sync client audit
Threat model: device → cloud API (boundary), auth token stored on device (asset), PII in
job data (asset). Audit finds: token logged at debug level (blocker), no authz check on
the sync endpoint (blocker), backoff using user-controlled values (major). Fixes: log
redaction + regression test, authz check + test, input bounds + test. Re-scan green;
residual: PII at-rest encryption deferred with governance sign-off.

## Anti-patterns
- **Checklist theater**: running scanners and calling it an audit — findings must trace
  to the threat model.
- **Severity inflation/deflation**: every issue labeled critical (or "won't fix") — use
  the severity × exploitability rubric.
- **Fix-without-proof**: changing code and claiming remediation without a failing
  regression test.
- **Secrets in the fix**: committing keys or tokens while "fixing" an audit finding.

## Testing Strategy
Validated on planted-vulnerability corpora; scoring measures detection rate and
false-positive rate. See `docs/testing-strategy.md`.

## Future Extensions
- Auto-generation of the threat-model document from dependency and exposure metadata.
- Integration with secrets scanners and SBOM tooling as MCP tools.
