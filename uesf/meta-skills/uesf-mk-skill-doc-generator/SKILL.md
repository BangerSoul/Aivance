---
id: uesf-mk-skill-doc-generator
name: Skill Documentation Generator
version: 1.0.0
category: mk
kind: meta
purpose: Generate and maintain documentation for skills — indexes, guides, and changelogs — verified against the skills they describe.
description: |
  Use when a skill is created, changed, or released, or when framework documentation
  drifts from the inventory. Produces accurate documentation: indexes, per-skill
  entries, guides, and changelog entries — verified against the actual skill files.
  Documentation is generated, not hand-remembered.
triggers:
  - condition: "A skill is created, versioned, or changed"
  - condition: "Documentation does not match the skill inventory"
  - example_prompt: "Update the skill index and docs after the intake"
inputs:
  - "The skill inventory (frontmatter) and the changes made"
  - "Existing documentation structure"
outputs:
  - "Updated skill indexes and per-skill entries"
  - "Changelog and release notes entries"
  - "Documentation verification (claims match skills)"
dependencies:
  - "uesf-mk-skill-validator"
context_requirements:
  - "Access to the skill inventory (frontmatter is the source of truth)"
  - "The documentation conventions (docs/ structure)"
quality_gates:
  - "Every doc claim verifiable against a skill's frontmatter/body"
  - "Indexes complete: every skill listed with its category and version"
  - "Changelog entries match actual version records"
validation:
  - unit
  - documentation
rollback: "Docs are versioned files; revert the doc commit."
failure_recovery: "When docs and skills diverge, regenerate from the inventory — the inventory is the source of truth, not the docs."
acceptance_criteria:
  - "Indexes complete and accurate"
  - "Doc claims verified against skills"
  - "Changelog matches version records"
  - "Generation source documented"
automation_hooks:
  - "Doc-freshness check in CI (inventory vs. docs diff)"
  - "Index generation on release"
mcp_tools:
  - "none"
cost:
  input_tokens: "~6k"
  output_tokens: "~3k"
  runtime_minutes: "10–30"
complexity: 2
maintainability_score: 5
scalability_score: 5
production_readiness: 4
related_skills:
  - "uesf-do-documentation"
  - "uesf-mk-skill-generator"
documentation: "docs/skill-spec.md"
---

# Skill Documentation Generator

## Overview
Documentation that drifts from the inventory is worse than none — it's trusted and
wrong. This meta-skill treats the skill frontmatter as the source of truth and
generates/regenerates documentation from it: indexes, per-skill entries, and
changelogs — with verification that doc claims match the skills. Documentation is
generated, not remembered.

## Execution Workflow
1. **Read the inventory** — Parse every skill's frontmatter (id, name, version,
   category, kind, purpose). This is the source of truth.
2. **Generate indexes** — Regenerate the skill indexes (READMEs, taxonomy
   assignment tables, category listings): every skill listed with category, version,
   and purpose.
3. **Generate/update entries** — Per-skill doc entries derive from frontmatter
   (purpose, triggers, dependencies) plus the body's key sections. Entries never
   contradict the skill.
4. **Generate changelog** — Assemble changelog entries from version records
   (version manager's output); entries match actual version bumps.
5. **Verify** — Cross-check every doc claim against the inventory; fix any drift.
6. **Record the generation** — Document how docs are regenerated (the source +
   command) so future updates are mechanical.

## Quality Gates
- Every doc claim verifiable against a skill's frontmatter/body.
- Indexes complete: every skill listed with category and version.
- Changelog entries match version records.

## Validation
- **Unit**: spot-check doc entries against frontmatter.
- **Documentation**: freshness check (inventory vs. docs) passes.

## Rollback
Docs are versioned files — revert the doc commit. Regeneration from the inventory
makes drift correction cheap.

## Failure Recovery
Drift means regenerate from the inventory — the inventory is truth, docs follow. If
the inventory itself is stale, the validator catches it before docs are trusted.

## Acceptance Criteria
- [ ] Indexes complete and accurate.
- [ ] Doc claims verified against skills.
- [ ] Changelog matches version records.
- [ ] Generation source documented.

## Examples
### Example 1 — Post-intake docs
After intake adds two skills and bumps three, the generator regenerates the skill
index (41 skills listed with versions), updates per-skill entries for the bumped
skills, assembles the changelog from version records, and the freshness check passes.

## Anti-patterns
- **Hand-remembered docs**: editing indexes by memory — regenerate from inventory.
- **Drift-tolerant docs**: accepting "close enough" mismatches — freshness is checked.
- **Changelog fiction**: entries that don't match version records — they must.
- **Unverifiable claims**: doc statements with no frontmatter source.

## Testing Strategy
Validated with inventory/doc drift fixtures; scored on drift detection. See
`docs/testing-strategy.md`.

## Future Extensions
- Automated doc generation into the release pipeline.
- Per-category doc bundles for the marketplace.
