---
id: uesf-da-data-modeling
name: Data Modeling
version: 1.0.0
category: da
kind: engineering
purpose: Design and evolve data models and migrations that are correct, validated, and reversible.
description: |
  Use when designing schemas, entities, storage layouts, or migrations — SQL or NoSQL,
  relational or document. Produces a validated schema design, a migration plan with
  rollback, and integrity tests. Pairs with uesf-ar-solution-architecture for storage
  choice.
triggers:
  - condition: "A new entity, schema, or migration is being designed or changed"
  - condition: "Data integrity or migration-safety risk is high"
  - example_prompt: "Model job alerts and their delivery history, with a safe migration"
inputs:
  - "Requirements: entities, relationships, access patterns, constraints"
  - "Existing schema and data (current state)"
outputs:
  - "Schema design (entities, keys, constraints, indexes)"
  - "Migration plan with validation and rollback steps"
  - "Integrity and migration tests"
dependencies:
  - "uesf-co-testing"
  - "uesf-co-planning"
context_requirements:
  - "Access to the current schema and a safe environment for migrations"
  - "Knowledge of the storage system's semantics"
quality_gates:
  - "Every entity and relationship traces to a requirement or access pattern"
  - "Migration is forward-tested and rollback-proven in a safe environment"
  - "Integrity constraints enforced in the schema, not only in the application"
validation:
  - unit
  - integration
  - regression
rollback: "Each migration is reversible by design (forward + backward scripts); rollback is tested in a safe environment before any production touch."
failure_recovery: "If a migration fails mid-way: stop, execute the tested backward migration, and diagnose with uesf-co-debugging before retrying."
acceptance_criteria:
  - "Schema traces to requirements; access patterns verified against indexes"
  - "Migration forward and rollback tested in a safe environment"
  - "Integrity constraints live in the schema"
  - "No data-loss path in the migration plan"
automation_hooks:
  - "Migration validation job in CI (forward + rollback in a disposable database)"
  - "Schema-diff check against the committed schema model"
mcp_tools:
  - "none"
cost:
  input_tokens: "~12k"
  output_tokens: "~5k"
  runtime_minutes: "30–90"
complexity: 4
maintainability_score: 4
scalability_score: 4
production_readiness: 4
related_skills:
  - "uesf-ar-solution-architecture"
  - "uesf-ai-rag-systems"
documentation: "docs/skill-spec.md"
---

# Data Modeling

## Overview
Data models outlive code: a bad schema is paid for in every future query and migration.
This skill centers three obligations: the model traces to real requirements and access
patterns; integrity lives in the schema, not the application; and every migration is
forward-tested and rollback-proven before it ever touches production data.

## Execution Workflow
1. **Gather access patterns** — List the reads and writes the model must serve (with
   volumes and cardinalities). The schema serves these; everything else is noise.
2. **Model entities and relationships** — Design entities, keys, and relationships.
   Normalize for integrity, denormalize only for measured access patterns — and record
   the trade-off.
3. **Add constraints and indexes** — Integrity constraints in the schema (uniqueness,
   foreign keys, check constraints); indexes chosen from the access patterns, with a
   note on write cost.
4. **Validate the design** — Walk each access pattern against the model; every query
   has an index path and a sensible cost. Trace entities to requirements.
5. **Design the migration** — Small, reversible steps. Each step: forward script,
   backward script, data backfill (if any), validation query, and a rollback trigger.
   Never a destructive step without a backup path.
6. **Test in a safe environment** — Run forward + rollback in a disposable database;
   run integrity tests and a data backfill smoke test. Record evidence.
7. **Ship through review** — The migration plan is reviewed like code
   (`uesf-co-review`); the CI migration job becomes the regression guard.

## Quality Gates
- Every entity and relationship traces to a requirement or access pattern.
- Migration forward + rollback proven in a safe environment.
- Integrity constraints in the schema, not only the application layer.
- No data-loss path in the plan.

## Validation
- **Unit**: integrity tests per constraint; migration step tests per step.
- **Integration**: forward + rollback + backfill in a disposable database.
- **Regression**: schema-diff check and re-run of prior migration tests.

## Rollback
Migrations are reversible by design: each step ships a backward script, and rollback is
tested before production. The CI migration job proves it continuously.

## Failure Recovery
A mid-way migration failure is handled by the tested backward path, then root-caused
with `uesf-co-debugging`. Never "fix forward" a partial migration by hand-editing data —
that is how production data diverges from the schema.

## Acceptance Criteria
- [ ] Schema traces to requirements; access patterns verified.
- [ ] Migration forward and rollback tested in a safe environment.
- [ ] Integrity constraints in the schema.
- [ ] No data-loss path in the plan.

## Examples
### Example 1 — Job alerts model
Access patterns: "list active alerts for a user", "count deliveries per alert". Model:
alerts + deliveries tables with FKs, a unique constraint on (user, alert-definition),
an index on the hot query. Migration: create tables, backfill from the legacy JSON
column with a validation query, rollback script tested in the disposable CI database.
Integrity test: no orphaned delivery rows.

## Anti-patterns
- **Schema-by-hunch**: designing tables without access patterns — every entity traces.
- **App-only integrity**: validating in the application and leaving the schema loose —
  constraints belong in the schema.
- **Destructive migrations**: dropping columns/tables without backup or rollback — each
  step is reversible.
- **Migration roulette**: running untested migrations in production — forward + rollback
  are proven first.

## Testing Strategy
Validated with migration fixtures containing planted data-loss paths and unrollbackable
steps; scored on detection. See `docs/testing-strategy.md`.

## Future Extensions
- Schema-drift detection across environments.
- Data-quality monitoring jobs generated from the model.
