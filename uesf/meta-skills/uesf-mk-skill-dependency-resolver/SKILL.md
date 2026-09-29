---
id: uesf-mk-skill-dependency-resolver
name: Skill Dependency Resolver
version: 1.0.0
category: mk
kind: meta
purpose: Analyze, validate, and repair the skill dependency graph — resolution, cycles, versions, and compatibility.
description: |
  Use when skills change (new, merged, versioned), when a release bundles skills, or
  when dependency errors appear. Produces a validated dependency graph (resolved,
  acyclic, version-compatible) with repairs and a report. The dependency graph is a
  first-class deliverable — it must stay sound as the framework grows.
triggers:
  - condition: "Skills are added, merged, versioned, or deprecated"
  - condition: "A release or certification needs the graph verified"
  - example_prompt: "Resolve and verify the dependency graph after the intake"
inputs:
  - "The skill inventory and their declared dependencies"
  - "Version information (for compatibility checks)"
outputs:
  - "Validated dependency graph (resolution + acyclicity + compatibility)"
  - "Repair actions (missing deps, cycles, version conflicts)"
  - "Graph report (text or JSON)"
dependencies:
  - "uesf-mk-skill-validator"
context_requirements:
  - "Access to the skill inventory (frontmatter dependencies)"
  - "The version manager's compatibility rules"
quality_gates:
  - "Graph fully resolved (no missing dependencies)"
  - "Graph acyclic (no dependency cycles)"
  - "Version compatibility verified across the graph"
validation:
  - unit
  - regression
  - certification
rollback: "Resolver repairs are edits to skill frontmatter — revert per commit. The graph report makes each repair auditable."
failure_recovery: "An unresolvable dependency blocks the affected skill: fix or remove the dependency, then re-resolve. Never ship an unresolved graph."
acceptance_criteria:
  - "Resolution complete with no missing dependencies"
  - "Acyclicity verified"
  - "Compatibility verified (or conflicts documented)"
  - "Report generated and committed"
automation_hooks:
  - "Resolver run by the validator on every validation pass"
  - "Graph report generated at release time"
mcp_tools:
  - "none"
cost:
  input_tokens: "~5k"
  output_tokens: "~2k"
  runtime_minutes: "1–5"
complexity: 2
maintainability_score: 5
scalability_score: 5
production_readiness: 5
related_skills:
  - "uesf-mk-skill-version-manager"
  - "uesf-mk-skill-merger"
documentation: "docs/dependency-graph.md"
---

# Skill Dependency Resolver

## Overview
Skills compose through declared dependencies; composition needs a sound graph. This
meta-skill owns the dependency graph: resolution (every dependency exists), acyclicity
(no cycles), and compatibility (versions and deprecations). It is the graph-analysis
half of the framework's dependency discipline — the validator checks it, this skill
repairs it.

## Execution Workflow
1. **Extract the graph** — Parse every skill's `dependencies` and `related_skills`
   from frontmatter; build the graph (nodes = skills, edges = dependencies).
2. **Resolve** — Check every declared dependency exists in the inventory. Missing
   dependencies are errors: fix the reference or create the missing skill.
3. **Check acyclicity** — Run cycle detection. A cycle (A depends on B, B on A) is
   a design error: break it by restructuring the shared capability into a lower-level
   skill.
4. **Verify compatibility** — Check version constraints and deprecations: skills
   referencing a deprecated skill get a compatibility flag; conflicting versions are
   reported.
5. **Repair** — Apply fixes per the failure types (reference fix, extraction,
   re-versioning), each as an auditable edit.
6. **Report** — Generate the graph report (text or JSON) and commit it. The graph is
   a deliverable (`docs/dependency-graph.md` shows the current state).

## Quality Gates
- Graph fully resolved.
- Graph acyclic.
- Version compatibility verified (conflicts documented).
- Report committed.

## Validation
- **Unit**: resolution and cycle checks per skill subset.
- **Regression**: whole-framework graph stays sound after changes.
- **Certification**: graph verified at certification time.

## Rollback
Repairs are edits to skill frontmatter — individually revertible commits. The report
documents each repair's reasoning.

## Failure Recovery
An unresolved dependency blocks its skill from the framework's valid state: fix the
reference or remove the dependency, then re-resolve. A cycle is broken by extracting
the shared capability. An incompatible version is resolved by the version manager's
compatibility rules — never by ignoring the conflict.

## Acceptance Criteria
- [ ] Resolution complete.
- [ ] Acyclicity verified.
- [ ] Compatibility verified or conflicts documented.
- [ ] Report generated and committed.

## Examples
### Example 1 — Intake graph check
After intake, the resolver runs: 41 skills, 47 dependency edges. One missing
dependency found (a merged skill still referenced by an old ID — fixed), one cycle
flagged between two meta-skills (broken by extracting the shared validator step),
compatibility clean. Report committed; graph diagram updated.

## Anti-patterns
- **Ignoring missing deps**: "it works anyway" — resolution is absolute.
- **Cycles by convenience**: cross-references that loop — extract the shared piece.
- **Version-blind composition**: depending on skills whose major versions are
  incompatible — compatibility is checked.
- **Unreported graphs**: graph state living only in memory — the report is committed.

## Testing Strategy
Validated with graph fixtures (missing deps, cycles, conflicts) scored on correct
detection and repair. See `docs/testing-strategy.md`.

## Future Extensions
- Automated cycle-breaking suggestions.
- Graph visualization generation as a tool.
