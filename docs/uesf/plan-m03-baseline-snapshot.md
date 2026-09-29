# Plan — M-03/P2-01: Baseline snapshot so the analytics timeline is never empty

*Executed per `uesf-co-planning` · 2026-08-07 · UESF workflow: triage → implement → verify*

## Goal (one sentence)

Ensure every user — including new users with no history — has at least one real
`AnalyticsSnapshot` on the analytics timeline, so the Trends charts render from real
data instead of empty.

## Root cause (evidence)

- `AnalyticsSnapshotWorker` (app module) is the **only** producer of
  `analytics_snapshots` rows: it is a **weekly** periodic worker
  (`AivanceApp.kt:281`, `TimeUnit.DAYS.toPeriodicWorkRequest<AnalyticsSnapshotWorker>(7)`).
- `AnalyticsRepositoryImpl.createSnapshot()` derives the snapshot entirely from real
  state (applications, interview sessions, ATS results, recruiters) — the pipeline
  is honest; the *trigger* is missing.
- Therefore a new user's `historicalSnapshots` is empty for up to 7 days →
  `CareerTrendsTab` renders an empty LineChart. This is KNOWN_ISSUES **M-03**
  (TODO **P2-01**).

## Tasks (each 2–20 minutes)

1. **Test plumbing (5 min)** — In `AnalyticsViewModelTest`, stub
   `createSnapshot()` on the two existing empty-snapshot tests so the suite stays
   honest once baseline capture exists. AC: suite compiles and is green in RED
   phase.
2. **RED test: baseline captured when none exists (10 min)** — New test: repository
   returns `Success(emptyList())` for snapshots; assert `createSnapshot()` is
   invoked on load. AC: test FAILS before the fix (behavior absent).
3. **Guard test: no duplicate when a snapshot exists (5 min)** — New test:
   repository returns one snapshot; assert `createSnapshot()` is NOT invoked. AC:
   passes before and after the fix (guards over-capture).
4. **Fix: `ensureBaselineSnapshot()` in `AnalyticsViewModel.loadData` (10 min)** —
   If the current snapshot list is empty (read succeeded), call
   `analyticsRepository.createSnapshot()`. Read failure skips (weekly worker / next
   view retries). Idempotent by construction. AC: RED test turns green; guard test
   stays green; no duplicate rows for users with history.
5. **Verify (10–20 min)** — `./gradlew :feature:analytics:testDebugUnitTest` green;
   UESF validator + suite green.

## Scope discipline

- **In scope:** `feature:analytics` ViewModel + its test.
- **Out of scope:** app-startup capture, dashboard/CareerStateEngine snapshot usage,
  worker cadence, UI changes. (Noted as follow-ups — see risks.)

## Risks

| Risk | Mitigation |
|------|------------|
| Baseline capture adds a write on first dashboard view | Guarded to "no snapshot exists"; runs once per install, ~ms on real data |
| Read failure → no baseline | Skip-and-retry is safe; the weekly worker remains the backstop |
| Timeline accumulates weekly points after baseline | Intended behavior (progress tracking) |

## Acceptance criteria

- [ ] New user with real activity sees ≥1 point on the Trends timeline (from real
      data, no fabricated values).
- [ ] `AnalyticsViewModelTest` covers: baseline created when empty; not duplicated
      when history exists.
- [ ] Full `:feature:analytics` unit suite green.
- [ ] UESF validator + framework suite green (CI gate `.github/workflows/uesf.yml`).

## Follow-up executed (same day): data-layer guarantee

The ViewModel-level fix above shipped and was reviewed, then refactored per the
`uesf-co-refactoring` discipline: the guarantee moved from the ViewModel into the
repository so it is **owner-agnostic**.

- **Why:** `AnalyticsViewModel.ensureBaselineSnapshot()` performed a write-on-read
  that healed only the dashboard consumer, and `AnalyticsRepositoryImpl` already
  duplicated the same ATS-report/readiness/recruiter derivation inside
  `getCareerIntelligence()`. The review filed a repo-level honesty test as a
  follow-up.
- **Change:**
  - `AnalyticsRepositoryImpl.getSnapshots()` is now self-healing — when the
    snapshot list is empty it calls `createSnapshot()` (Mutex-guarded; emptiness
    re-checked inside the lock) before forwarding the Room flow.
  - Shared derivation extracted: `ResumeAnalysisEntity.toAtsReport()` (mapper) +
    private `toAtsReports` / `calculateReadiness` / `collectRecruiters` helpers,
    reused by both `createSnapshot()` and `getCareerIntelligence()`.
  - `ensureBaselineSnapshot()` removed from the ViewModel along with its two
    tests; `createSnapshot()` stubs cleaned from remaining ViewModel tests.
- **Evidence:** new `AnalyticsRepositoryImplTest` (5 tests — self-heal, honest
  empty-state, no-heal-with-history, no double-insert on repeated collection,
  derived kpis/score persisted) + `AnalyticsViewModelTest` 4/4 green;
  `:core:data` 86/86, `:feature:analytics` 4/4; UESF validator 41/0/0.

## Owner

UESF adoption run — first end-to-end execution of the triage → implement → verify
workflow.
