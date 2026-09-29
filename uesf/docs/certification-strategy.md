# UESF Certification Strategy

*Version 1.0.0 · Deliverable 13*

Certification is the formal closing gate that turns the framework's quality promises
into checked facts. It follows the discipline of
`uesf-mk-skill-certification-engine` (and the engineering twin
`uesf-ce-certification-audit` for arbitrary systems).

## Principles

1. **Evidence, not assertion.** A gate passes only with recorded raw output
   (validator report, test results, scan reports, review records, benchmark numbers).
2. **All-or-nothing.** Any failed gate ⇒ *not certified*. Partial certificates are
   contradictions. Waivers are not a certification tool — accepted risk belongs in
   residual risk, visibly.
3. **Reversible.** Certificates are revoked, never edited. Every certificate carries
   its revocation condition.
4. **Tied to a state.** A certificate names the exact commit/tag it certifies and is
   re-verifiable against it.

## Certification levels

| Level | Scope | Gates |
|-------|-------|-------|
| **L1 Skill** | a single skill | validator 0 errors; fixtures green; review passed |
| **L2 Category** | a category bundle | all skills L1; cross-skill deps resolved |
| **L3 Release** | a framework version | L2 for all categories; full suite green; security review closed; benchmarks met; changelog consistent |
| **L4 Production** | an external system | `uesf-ce-certification-audit` (threat model, scans, load, docs) |

## The certification record

Every certificate contains:

- Certified asset + commit/tag + date.
- Gate-by-gate evidence with references (file paths, run outputs).
- Per-gate pass/fail + overall verdict.
- Residual risks (accepted, with owners).
- Revocation condition (the event that auto-invalidates).
- Validity scope (what it does and does not claim).

Example: [`certification/v1.0.0-certificate.md`](../certification/v1.0.0-certificate.md).

## Revocation

A certificate is revoked (with a revocation notice + failing evidence) when:

- its revocation condition fires (e.g., validator errors on the certified tag);
- a security finding against the certified state is confirmed;
- a gate regression is discovered post-hoc.

Revocation supersedes; re-certification requires a new full run — never a delta
certificate.

## Automation

- Certification checklists run in the release pipeline.
- Revocation checks run in CI (validator, scans) against certified tags.
- Certificates are machine-readable (JSON record alongside the human doc) for tooling.

## Relationship to testing

Certification **consumes** the testing tiers (spec validation, fixtures, benchmarks);
it does not replace them. A certification is only as strong as the evidence it
aggregates.
