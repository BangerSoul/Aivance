# Workflow: Triage → Implement → Verify

*The default end-to-end delivery workflow. A sequence of skill invocations —
not a new skill.*

## When to use

Any feature, bugfix, or change entering the repository. Entry: a request.
Exit: merged, verified, and recorded.

## Flow

```
REQUEST
  │
  ▼
[1] uesf-co-planning ──────────────► plan doc (tasks + ACs + risks)
  │  (skip for trivial one-step changes)
  ▼
[2] uesf-ra-repository-analysis ───► (only for unfamiliar repos) repo map
  │
  ▼
[3] uesf-co-implementation ────────► per task: failing test → code → green
  │   (each task: uesf-co-testing for the test design)
  ▼
[4] uesf-co-review ────────────────► findings + verdict + bounded fix loop
  │   (uesf-se-security-audit / uesf-ax-accessibility-audit /
  │    uesf-pf-performance-optimization as lenses when applicable)
  ▼
[5] MERGE
  │
  ▼
[6] uesf-gv-project-governance ────► status snapshot, risk update, decision log
  │
  ▼
[7] uesf-do-documentation ─────────► docs + changelog updated with the change
```

## Gates

1. **Plan gate:** no implementation before an approved plan (non-trivial work).
2. **RED gate:** no production code before an observed failing test.
3. **Evidence gate:** no merge without recorded test output.
4. **Review gate:** no blockers at merge; verdict recorded.
5. **Freshness gate:** docs and changelog updated in the same change.

## Time-boxing

- Planning: 5–15 min. Spike tasks get explicit time-boxes.
- If a debugging step exceeds 60 min without a verified root cause: escalate with
  evidence (per `uesf-co-debugging` failure recovery).

## Artifacts produced

- `docs/plans/<name>.md` (plan)
- test evidence (recorded output)
- review verdict + findings
- governance status snapshot
- changelog entry
