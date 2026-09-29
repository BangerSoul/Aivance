---
id: uesf-ra-repository-analysis
name: Repository Intelligence
version: 1.0.0
category: ra
kind: engineering
purpose: Build an accurate, current map of a codebase — modules, dependencies, ownership, and risk — before any planning or implementation.
description: |
  Use when starting work in an unfamiliar repository, planning a large change, or
  assessing maintenance risk. Produces a repository map: module overview, dependency
  graph, conventions, test/build commands, and risk hotspots. Works in any language.
  This is the reconnaissance step of uesf-co-planning for unfamiliar codebases.
triggers:
  - condition: "Work begins in a repository the agent has not analyzed"
  - condition: "A large refactor or migration requires knowing the full dependency surface"
  - example_prompt: "Map this repository so we can plan the background-sync feature"
inputs:
  - "Repository path"
  - "Build/test/run commands (discoverable from config files)"
outputs:
  - "Repository map document (modules, boundaries, conventions)"
  - "Dependency and risk analysis (hotspots, dead code, tech debt)"
  - "Verified build and test commands"
dependencies:
  - "uesf-co-planning"
context_requirements:
  - "Read access to the repository and its history"
  - "Ability to run build/test commands for verification"
quality_gates:
  - "Every claim in the map is verified against the code, not inferred"
  - "Build and test commands are run and recorded"
  - "Hotspots are ranked by risk with evidence"
validation:
  - unit
  - integration
  - documentation
rollback: "Analysis produces a document only; nothing is modified. Reverting is trivial."
failure_recovery: "If a module resists analysis, record it as an unknown with the blocker, and time-box deeper digging; never guess a module's behavior."
acceptance_criteria:
  - "Module map with responsibilities and boundaries"
  - "Dependency graph (or high-level edges) captured"
  - "Conventions, entry points, and build/test commands documented and verified"
  - "Top risk hotspots ranked with evidence"
automation_hooks:
  - "Repository-map artifact generated into docs/repository-map.md"
  - "Re-run hook when planning starts in a repo whose map is stale"
mcp_tools:
  - "none"
cost:
  input_tokens: "~20k"
  output_tokens: "~8k"
  runtime_minutes: "20–60"
complexity: 3
maintainability_score: 4
scalability_score: 5
production_readiness: 4
related_skills:
  - "uesf-gv-project-governance"
  - "uesf-mk-repository-analyzer"
documentation: "docs/skill-spec.md"
---

# Repository Intelligence

## Overview
Agents fail when they guess a codebase instead of reading it. This skill is a disciplined
reconnaissance pass: it verifies claims against code, records the module map and risk
surface, and produces an artifact that makes every downstream skill (planning,
implementation, review) dramatically cheaper and more accurate.

## Execution Workflow
1. **Discover the surface** — Read build configs (package manifest, gradle, cargo, go.mod),
   CI files, and the directory tree. Derive the module set, entry points, and conventions.
2. **Verify commands** — Run the build/test/lint commands and record real output. Commands
   that cannot run are marked as blockers, never assumed.
3. **Map modules** — For each module: responsibility, boundaries, key interfaces, and its
   tests. Record where modules depend on each other.
4. **Trace risk** — Identify hotspots: high coupling, dead code, duplicated logic, missing
   tests, outdated dependencies, TODO-laden modules. Rank by risk with evidence (file
   counts, dependency depth, test coverage gaps).
5. **Record conventions** — Naming, error handling, data flow patterns, and the
   "shared language" of the codebase (per mattpocock's CONTEXT.md pattern).
6. **Write the map** — Produce `docs/repository-map.md` as the durable artifact; update it
   when a subsequent analysis finds new facts.

## Quality Gates
- Every claim in the map is verifiable against the code (cite files).
- Build and test commands were run and their output recorded.
- Risk hotspots are ranked with evidence, not vibes.
- Unknowns are listed explicitly rather than guessed.

## Validation
- **Unit**: spot-check the map against 3–5 modules chosen at random.
- **Integration**: a fresh agent using only the map can locate any module's entry point.
- **Documentation**: the map is committed and linked from the repo README or plan.

## Rollback
Analysis modifies nothing but the map document. Reverting = deleting the doc or reverting
its commit. Zero risk.

## Failure Recovery
- A module is opaque (build fails, no docs): record it as a risk hotspot with the blocker,
  time-box deeper analysis, and flag it in the plan as a spike candidate.
- Commands cannot run (missing env): mark the map as "unverified" for those parts rather
  than fabricating results.

## Acceptance Criteria
- [ ] Module map with responsibilities and boundaries, citing files.
- [ ] Dependency edges captured (graph or list).
- [ ] Build/test/lint commands verified with recorded output.
- [ ] Top risk hotspots ranked with evidence.

## Examples
### Example 1 — Onboarding to a large app
A 22-module Android project. The skill produces: module responsibilities, the
navigation → feature → data layering, verified `./gradlew :app:testDebugUnitTest`
output, hotspots (the legacy `AtsResult` module with 3k lines and no tests, duplicated
retry logic in two workers), and the convention that all async work goes through a
coroutine scope in `AppScope`. The plan then avoids the unknown module and schedules a
spike for it.

## Anti-patterns
- **Read-the-README analysis**: trusting docs and config instead of verifying against code.
- **Claim without evidence**: "the app uses MVVM" with no file citations — every claim
  cites code.
- **Map-and-forget**: never re-running the map when the codebase drifts — maps have a
  freshness date and a re-run trigger.
- **Analysis paralysis**: endless tree-walking with no artifact — time-box the pass and
  produce the map.

## Testing Strategy
Validated on seeded repositories with planted hotspots; scoring measures hotspot
detection and claim accuracy. See `docs/testing-strategy.md`.

## Future Extensions
- Automatic freshness checks (map staleness warnings on re-run).
- Machine-generated dependency graphs via the tooling layer.
