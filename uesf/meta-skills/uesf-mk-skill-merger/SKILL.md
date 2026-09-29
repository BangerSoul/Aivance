---
id: uesf-mk-skill-merger
name: Skill Merger
version: 1.0.0
category: mk
kind: meta
purpose: Merge overlapping or related skills into a superior unified skill — preserving capabilities, removing duplication, and validating the result.
description: |
  Use when two or more skills overlap, when intake candidates duplicate existing
  skills, or when related capabilities belong together. Produces one merged skill
  that covers the union of capabilities without duplication, with equivalence
  evidence. This is how the framework stays lean as it grows.
triggers:
  - condition: "Two skills overlap in purpose or duplicate content"
  - condition: "An intake candidate duplicates an existing skill (merge instead of add)"
  - example_prompt: "Merge the two profiling skills into one unified performance skill"
inputs:
  - "The skills to merge (and any intake candidates)"
  - "Validator, benchmark, and dependency information"
outputs:
  - "Merged skill (unified, de-duplicated, spec-conformant)"
  - "Equivalence evidence across all merged capabilities"
  - "Dependency updates and changelog"
dependencies:
  - "uesf-mk-skill-validator"
  - "uesf-mk-skill-dependency-resolver"
  - "uesf-mk-skill-benchmarker"
context_requirements:
  - "Benchmark task sets covering each merged skill's capabilities"
  - "The dependency graph (who references the merged skills)"
quality_gates:
  - "Union of capabilities preserved (each source's benchmark passes on the merge)"
  - "No duplicated content survives the merge"
  - "Validator green; dependencies updated"
validation:
  - unit
  - regression
  - certification
rollback: "The merge is a versioned commit set; revert to the pre-merge state via the version manager. Source skills are deprecated, not deleted, until adoption is verified."
failure_recovery: "If a source capability fails on the merged skill, restore that source (un-deprecate) and fix the merge — never ship a merge that loses capability."
acceptance_criteria:
  - "Every source capability benchmarked on the merged skill"
  - "Duplication eliminated"
  - "Validator green; dependency graph consistent"
  - "Deprecation + migration notes published"
automation_hooks:
  - "Dependency resolution after the merge (reference updates)"
  - "Validator re-run on the whole framework"
mcp_tools:
  - "none"
cost:
  input_tokens: "~12k"
  output_tokens: "~5k"
  runtime_minutes: "20–60"
complexity: 4
maintainability_score: 4
scalability_score: 5
production_readiness: 4
related_skills:
  - "uesf-le-continuous-learning"
  - "uesf-mk-skill-version-manager"
documentation: "docs/skill-spec.md"
---

# Skill Merger

## Overview
The anti-duplication rule ("do NOT duplicate identical skills — merge intelligently")
is a core acceptance criterion of the framework. This meta-skill executes it:
overlapping skills are merged into a superior unified skill whose union of
capabilities is proven by benchmarks, whose duplication is eliminated, and whose
dependencies are updated — with the sources deprecated, never deleted, until adoption
is verified.

## Execution Workflow
1. **Inventory the overlap** — For each skill in the merge set: its capabilities,
   its benchmark task set, and its dependents (who references it).
2. **Design the unified skill** — Map the union of capabilities to one coherent
   structure (sections, phases). Duplicate content appears once; distinct content is
   unified with a common vocabulary. Record the design.
3. **Build the merge** — Create the merged skill following the spec and template;
   carry over the strongest content from each source (per repository-analyzer
   evidence where sources are external).
4. **Prove the union** — Run each source's benchmark task set against the merged
   skill: every capability must pass. This is the merge's equivalence proof.
5. **Resolve dependencies** — Update references: dependents point at the merged
   skill; the dependency resolver validates the new graph.
6. **Validate** — Validator green across the framework.
7. **Deprecate and migrate** — Mark sources deprecated (`uesf-mk-skill-version-manager`),
   publish migration notes, and keep them readable until dependents have moved.

## Quality Gates
- Union of capabilities proven by per-source benchmarks.
- No duplicated content survives.
- Validator green; dependency graph consistent.
- Sources deprecated (not deleted) with migration notes.

## Validation
- **Unit**: per-source benchmark task sets pass on the merge.
- **Regression**: framework validator green; dependents resolve.
- **Certification**: re-certify merged skills on promotion.

## Rollback
The merge is a versioned commit set — revert to the pre-merge state via the version
manager. Because sources are deprecated (not deleted) until adoption is verified,
restoring a source is un-deprecation, not resurrection.

## Failure Recovery
A source capability failing on the merged skill is a failed merge: restore that
source, fix the merge, re-run all benchmarks. Never ship a merge that loses
capability — "we'll re-add it later" is how merges destroy skills.

## Acceptance Criteria
- [ ] Every source capability benchmarked on the merged skill.
- [ ] Duplication eliminated.
- [ ] Validator green; graph consistent.
- [ ] Deprecation + migration notes published.

## Examples
### Example 1 — Profiling merge
`uesf-pf-performance-optimization` and an intake candidate on request profiling
overlap 60%. The merge produces one skill covering both (profiling + request-level
analysis); each source's benchmark task set passes; dependents repointed; the source
candidate is rejected at intake (capability absorbed) and the framework skill is
bumped. Net: one skill instead of two, zero capability loss.

## Anti-patterns
- **Merge-as-delete**: dropping a source's unique capability in the union — per-source
  benchmarks are the proof.
- **Merge-as-copy**: concatenating both skills into a super-document — duplication
  eliminated means unified structure.
- **Orphaned dependents**: repointing nothing — the resolver validates.
- **Instant deletion**: deleting sources before adoption is verified — deprecate first.

## Testing Strategy
Validated with overlap fixtures; scoring measures union preservation and duplication
elimination. See `docs/testing-strategy.md`.

## Future Extensions
- Automated overlap detection (semantic similarity over the inventory).
- Merge candidate recommendations from the coverage matrix.
