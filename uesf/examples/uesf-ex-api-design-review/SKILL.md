---
id: uesf-ex-api-design-review
name: API Design Review
version: 1.0.0
category: ex
kind: example
purpose: Demonstrate a richer, composed skill — reviewing API designs against contract, security, and evolution criteria.
description: |
  Use when learning how a real UESF skill composes core skills (review, testing,
  security) into a domain workflow. Produces an API design review with prioritized
  findings. This example shows the full depth of the format: composed dependencies,
  multiple validation strategies, and a complete worked example.
triggers:
  - condition: "A learner wants a complete worked example of a composed UESF skill"
  - example_prompt: "Show me a full skill that composes review and security"
inputs:
  - "An API design (endpoints, schemas, auth model)"
  - "The consumers and the change history (for evolution review)"
outputs:
  - "Design review with prioritized findings (blocker/major/minor)"
  - "Contract and security assessment"
  - "Versioning and evolution assessment"
dependencies:
  - "uesf-co-review"
  - "uesf-se-security-audit"
context_requirements:
  - "The design is written down (spec, OpenAPI, or proposal)"
quality_gates:
  - "Every finding traces to a specific endpoint/schema element"
  - "Contract and security assessed separately"
  - "Evolution path (versioning strategy) assessed"
validation:
  - unit
  - integration
  - security
  - documentation
rollback: "The review is a document; revert the doc commit. No code is touched."
failure_recovery: "If the design has blockers, the review returns them with evidence for a design revision — it never rubber-stamps a design it found broken."
acceptance_criteria:
  - "Endpoints and schemas reviewed against contract rules"
  - "Auth and data-handling security reviewed"
  - "Versioning/evolution strategy reviewed"
  - "Findings prioritized and actionable"
automation_hooks:
  - "OpenAPI lint as an automated pre-pass to the review"
mcp_tools:
  - "none"
cost:
  input_tokens: "~10k"
  output_tokens: "~4k"
  runtime_minutes: "15–45"
complexity: 3
maintainability_score: 5
scalability_score: 5
production_readiness: 4
related_skills:
  - "uesf-ex-hello-world"
  - "uesf-ar-solution-architecture"
documentation: "docs/skill-spec.md"
---

# API Design Review

## Overview
A worked example of composition: this skill combines the review discipline
(`uesf-co-review`) with the security lens (`uesf-se-security-audit`) for one domain —
API design. It shows how a real skill states its workflow concretely while delegating
general discipline to its dependencies instead of reimplementing it.

## Execution Workflow
1. **Pre-pass** — Run automated API linting (if available) to clear the mechanical
   issues; record the output as pre-pass evidence.
2. **Contract review** — Check naming, status codes, error model, pagination,
   idempotency, and schema consistency against the project's API conventions.
3. **Security review** — Trace the auth model, authorization per endpoint, input
   validation, and data exposure through the design (the security skill's threat
   lens).
4. **Evolution review** — Assess the versioning strategy, additive-change rules, and
   the migration path for consumers.
5. **Findings** — Prioritize (blocker/major/minor/nit) with the exact endpoint or
   schema element each finding cites.
6. **Verdict** — Approve / approve-with-nits / request-changes, with rationale.

## Quality Gates
- Findings cite specific endpoints or schema elements.
- Contract and security assessed separately.
- Evolution path assessed.

## Validation
- **Unit**: each finding references a specific design element.
- **Integration**: the review traces end-to-end through every endpoint.
- **Security**: auth/authz and data exposure pass.
- **Documentation**: the review is committed for the design's history.

## Rollback
A document review — revert the commit. The design itself is unchanged.

## Failure Recovery
Blockers return the design for revision with consolidated evidence; the review never
rubber-stamps a broken design. Re-review is scoped to the delta.

## Acceptance Criteria
- [ ] Endpoints and schemas reviewed against contract rules.
- [ ] Auth and data-handling security reviewed.
- [ ] Versioning/evolution strategy reviewed.
- [ ] Findings prioritized and actionable.

## Examples
### Example 1 — Sync API design review
A proposed `POST /sync` endpoint: contract pass finds no idempotency key (major);
security pass finds the auth token accepted in a query parameter (blocker); evolution
pass finds no versioning strategy for the response schema (minor). Verdict:
request-changes. The design revision adds an idempotency header, moves the token to a
header, and documents a versioned response envelope; delta re-review approves.

## Anti-patterns
- **Reviewing without the spec**: judging a design from chat context — the design must
  be written down.
- **Contract-only reviews**: skipping the security and evolution lenses.
- **Vague findings**: "this endpoint feels wrong" — every finding cites an element.

## Testing Strategy
Covered by the framework validator and the test suite's fixture validations; review
lenses are exercised by the core review skill's fixtures.

## Future Extensions
- OpenAPI-to-findings automation as an MCP tool.
