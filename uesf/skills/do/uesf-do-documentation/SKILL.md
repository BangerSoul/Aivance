---
id: uesf-do-documentation
name: Documentation Craft
version: 1.0.0
category: do
kind: engineering
purpose: Write and maintain documentation that is accurate, findable, and verifiable — APIs, guides, runbooks, and changelogs.
description: |
  Use when writing or updating docs: API reference, user guides, architecture docs,
  runbooks, changelogs, or contributing guides. Produces documentation verified against
  the code it describes, with a freshness strategy. Every claim is checked; examples
  are runnable.
triggers:
  - condition: "User-visible behavior, APIs, or commands change"
  - condition: "A component or workflow lacks documentation"
  - example_prompt: "Document the sync feature's API and add a troubleshooting runbook"
inputs:
  - "The code, behavior, or workflow to document"
  - "Existing docs and style conventions"
outputs:
  - "Accurate documentation (guide/API/runbook) with verified examples"
  - "Freshness metadata (last-verified date, owning skill/team)"
  - "Changelog entries when behavior changed"
dependencies:
  - "uesf-ra-repository-analysis"
context_requirements:
  - "Ability to run commands/examples to verify claims"
  - "Access to the code being documented"
quality_gates:
  - "Every claim, command, and example is verified against the code"
  - "Examples runnable by a fresh reader with the stated prerequisites"
  - "Docs live next to or link to their code (proximity principle)"
validation:
  - unit
  - documentation
rollback: "Docs are versioned files: revert the doc commit; no code or data affected."
failure_recovery: "When behavior and docs diverge, the doc is wrong until updated — fix it in the same change as the behavior, or file a tracking task immediately."
acceptance_criteria:
  - "All claims and examples verified"
  - "A reader can achieve the documented outcome following only the doc"
  - "Changelog updated when behavior changed"
  - "Freshness and ownership metadata present"
automation_hooks:
  - "CI check: changed public symbols must update docs (doc-coverage diff)"
  - "Link checker on docs builds"
mcp_tools:
  - "none"
cost:
  input_tokens: "~10k"
  output_tokens: "~6k"
  runtime_minutes: "15–60"
complexity: 2
maintainability_score: 4
scalability_score: 5
production_readiness: 4
related_skills:
  - "uesf-mk-skill-doc-generator"
  - "uesf-re-release-engineering"
documentation: "docs/skill-spec.md"
---

# Documentation Craft

## Overview
The source-repository research shows two documentation truths: shared language makes
agents dramatically more effective (mattpocock's CONTEXT.md), and stale docs are worse
than no docs (they are trusted and wrong). This skill produces documentation that is
verified against the code, proximity-located, freshness-stamped, and covered by CI —
so it stays true.

## Execution Workflow
1. **Know the audience and outcome** — For each doc: who reads it, and what must they
   be able to do afterward? A guide teaches; an API reference specifies; a runbook
   restores service.
2. **Verify against the code** — Read the actual implementation/commands. Never document
   from memory or from other docs. Run every command and example.
3. **Write with structure** — Lead with the outcome and prerequisites; use the minimum
   words that are correct; one idea per section; code blocks are runnable from a clean
   checkout.
4. **Place with proximity** — Docs live next to the code they describe (READMEs,
   doc-comments, module folders) or link precisely from a central index.
5. **Stamp freshness** — Record last-verified date and the owning skill/team. Stale
   docs get flagged by the CI freshness check, not by readers.
6. **Update changelog** — When behavior changed, add a changelog entry in the same
   change (Keep a Changelog style).

## Quality Gates
- Every claim, command, and example verified against the code.
- A fresh reader can complete the documented outcome using only the doc.
- Docs are proximity-placed or precisely linked.
- Freshness and ownership metadata present.

## Validation
- **Unit**: spot-verify 3 claims/examples against the code.
- **Integration**: follow the doc end-to-end from a clean state (the "fresh reader"
  test).
- **Documentation**: links resolve; examples run.

## Rollback
Documentation is versioned. Reverting a doc commit restores the previous state; no code
or data is touched.

## Failure Recovery
When behavior changes without docs, the doc is wrong until updated. Fix docs in the same
change as the behavior; if that's impossible, file a tracking task immediately — stale
docs decay silently.

## Acceptance Criteria
- [ ] All claims and examples verified.
- [ ] Fresh-reader test passes for the primary documented flow.
- [ ] Changelog updated when behavior changed.
- [ ] Freshness and ownership metadata present.

## Examples
### Example 1 — Sync feature docs
The skill documents the sync API by reading the client code and running each command:
request/response examples verified, an error table extracted from the actual error codes,
a troubleshooting runbook with the real log lines, and a "last verified: 2026-08-07"
stamp. The CI doc-coverage check fails if a new public endpoint ships without a doc
entry.

## Anti-patterns
- **Document-from-memory**: writing docs without reading the code — the #1 source of
  stale docs.
- **Example theater**: code blocks that were never run — every example runs.
- **Doc graveyards**: central doc directories disconnected from code — use proximity.
- **Copy-paste drift**: duplicated content that drifts — link instead of duplicate.

## Testing Strategy
Validated with seeded doc-defect fixtures (wrong commands, fabricated examples) scored on
detection. The fresh-reader test is the integration verification. See
`docs/testing-strategy.md`.

## Future Extensions
- Doc-coverage diff enforcement in CI (public symbols → doc entries).
- Automatic freshness alerts and link checking.
