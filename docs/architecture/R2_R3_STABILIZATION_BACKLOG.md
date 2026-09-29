# R2–R8 STABILIZATION BACKLOG — AiVance

**Status:** R2 and R3 are **implemented and verified** — see `R2_R3_EXECUTION_REPORT.md`. R4–R8 remain specification only.
**Ordering principle (per gate):** blocks journey → corrupts user-visible truth → breaks persistence → breaks navigation → duplicate ownership → unnecessary hot-path work → dormant infra → cosmetic.
**Definition of done per stage:** `SOURCE PROOF → UNIT TEST → ANDROID VM RUNTIME TEST → REGRESSION → DOCUMENTATION → COMMIT`. Compilation alone is never PASS.

Reference: `PRODUCT_TOPOLOGY_MASTER.md` (findings), `MINIMAL_STABLE_ARCHITECTURE.md` (target), `PROVIDER_GATE_DECISION.md` (open R4 decision).

## Implemented

| Item | Status | Where |
|---|---|---|
| R2-1 hot-path graph persistence | **DONE** — async conflating writer + content-signature dedupe + persisted-revision read-after-write | `R2_R3_EXECUTION_REPORT.md` § R2 |
| R3-1 fabricated Career Score | **DONE** — evidence-gated composite; `null` at zero data | § R3-1 |
| R3-2 fabricated Skill Match | **DONE** — `matchRatio` is `Float?`; no 100% without evidence | § R3-2 |
| R3-3 duplicate Interview Readiness | **DONE** — single owner `InterviewReadinessCalculator` | § R3-3 |
| R3-4 `savedJobsCount` from application stages | **DONE** — now owned by `saved_jobs` | § R3-4 |
| R3-5 interview date from `dateApplied` | **DONE** — derived from the session's real `startTime` | § R3-5 |
| R3-6 fabricated agent state | **DONE** — literals removed | § R3-6 |
| Zero-data metric guard | **DONE** — JVM value+completeness guard, instrumented UI guard, runtime dump | § Guard suite |
| R2-2 single-writer guard | **DONE (by construction + test)** — the writer is the only `persist` caller and only it bumps the revision | § R2 |

### New follow-ups discovered while executing

| ID | Problem | Evidence |
|---|---|---|
| R3-7 | `KPIEngine.calculateInterviewRate` returns `0.0` for `0/0`; Pipeline renders "interview conversion is 0%" with no applications | `[SRC]` `KPIEngine`; `[RT]` Pipeline tab dump |
| R6-3 | `DashboardUiState.agentMissions`/`activeTask` are now permanently empty/null dead state | `[SRC]` `DashboardUiState` |

---

---

## Stage R2 — State ownership + hot-path graph persistence

| ID | R2-1 |
|---|---|
| Problem | `careerGraphEngine.persist(graph)` runs a full rebuild + transactional upsert on **every** emission of 12 combined flows |
| Evidence | `[SRC]` `CareerStateEngine:95`; `[SRC]` `CareerGraphEngine.buildGraph/persist` |
| Root cause | Persistence coupled inside the reactive `combine` body |
| Files | `core/domain/.../engine/CareerStateEngine.kt`, `core/domain/.../careergraph/CareerGraphEngine.kt` |
| Owner | `CareerStateEngine` |
| Required change | Decouple persistence: emit state, persist on a debounced/low-frequency trigger (or on explicit mutation events only) |
| Tests | unit: persist called once per change-burst; no persist on no-op emission |
| Runtime proof | emulator: navigate tabs, confirm graph rows stable + no jank |
| Dependencies | none |
| Risk | medium — must not let the entity slice go stale for the dashboard read |

| ID | R2-2 |
|---|---|
| Problem | Duplicate state owner risk: graph must remain single-writer |
| Evidence | `[SRC]` M05 KDoc; `[TEST]` `CareerEventReplayRuntimeTest` |
| Required change | Add a guard/test asserting only `CareerGraphEngine` writes the entity slice |
| Risk | low |

---

## Stage R3 — Metric integrity + authoritative calculations

| ID | R3-1 (highest priority) |
|---|---|
| Problem | Career Score renders **18** at zero data — fabricated |
| Evidence | `[SRC]` `AnalyticsRepositoryImpl:174-177` (readiness `?: 75`); `[RT]` "18" on screen |
| Root cause | Hardcoded default presented as a measured value inside a /4 composite |
| Files | `core/data/.../AnalyticsRepositoryImpl.kt`, `core/domain/.../analytics/CareerScoreEngine.kt` |
| Owner | Analytics |
| Required change | Zero-data ⇒ score is empty/insufficient, not 18; remove the 75 default or make it explicit and labelled |
| Tests | unit: 0 sessions ⇒ readiness not fabricated; composite reflects absence |
| Runtime proof | fresh-state dashboard shows no fabricated score |
| Risk | medium — touches a widely-read field |

| ID | R3-2 |
|---|---|
| Problem | Skill Match **100%** with "0 of 0 target-job skills demonstrated" |
| Evidence | `[SRC]` `CareerGraphEngine:336-340` (`else 1.0f`); `[RT]` "100%" + 🎯 copy |
| Required change | `totalTarget == 0` ⇒ no-data state (or 0%), never 100% |
| Tests | unit: 0 target skills ⇒ not 100% |
| Runtime proof | zero-data dashboard shows no false positive |
| Risk | low |

| ID | R3-3 |
|---|---|
| Problem | Interview Readiness has two owners (`18`-embedding 75 vs Prep Studio `1%`) |
| Evidence | `[SRC]` `AnalyticsRepositoryImpl:148-154`; `[RT]` two distinct numbers |
| Required change | One calculation owner consumed by both screens |
| Tests | unit: one source feeds both |
| Runtime proof | both screens agree at same data |
| Risk | medium |

| ID | R3-4 |
|---|---|
| Problem | `savedJobsCount` derived from `applications.currentStageId == "SAVED"`, ignoring `saved_jobs` |
| Evidence | `[SRC]` `CareerStateEngine:119`; `[SRC]` `JobDao.getSavedJobIds` |
| Required change | Count from `saved_jobs` (or rename the metric) |
| Tests | unit: saved job with no application counts |
| Risk | low |

| ID | R3-5 |
|---|---|
| Problem | Upcoming interview date = `application.dateApplied` |
| Evidence | `[SRC]` `CareerStateEngine:123-128` |
| Required change | Use the real interview date |
| Risk | medium (needs a real date field) |

| ID | R3-6 |
|---|---|
| Problem | Dead + fabricated agent state in `DashboardViewModel`; a test asserts it |
| Evidence | `[SRC]` `DashboardViewModel:75-80`; `DashboardViewModelTest:134`; not rendered `[SRC]` |
| Required change | Delete the hardcoded literals (and the test assertion) or make them real |
| Risk | low |

---

## Stage R4 — Provider / auth / onboarding enforcement

| ID | R4-1 |
|---|---|
| Problem | Dashboard reachable with zero providers (gate does not bind) |
| Evidence | `[SRC]` `OnboardingViewModel.skipAll():294`, `AuthViewModel:115`; `[RT]` dashboard w/ 0 providers |
| Root cause | `onboardingCompleted` overloaded as both "onboarded" and "providers configured" |
| Files | `feature/profile/.../OnboardingViewModel.kt`, `AuthViewModel.kt`, `AuthenticationViewModel.kt`, nav graph gate |
| Required change | Separate "onboarded" from "provider-configured"; enforce the product invariant (or explicitly define a provider-optional mode) |
| Tests | unit: no validated provider ⇒ Dashboard gated |
| Runtime proof | clean install → cannot reach Dashboard without a provider (or documented optional mode) |
| Risk | high — changes the primary journey; needs product sign-off |

| ID | R4-2 |
|---|---|
| Problem | Two writers to `provider_configurations` |
| Evidence | `[SRC]` `SettingsRepositoryImpl:36` + `ProviderRepositoryImpl:48` |
| Required change | Collapse to one repository path |
| Risk | medium |

---

## Stage R5 — Navigation / entry points

| ID | R5-1 |
|---|---|
| Problem | `JobComparison` unreachable (route + screen exist, no entry point) |
| Evidence | `[SRC]` `AivanceNavGraph:393`; zero `onNavigate(JobComparison)` callers |
| Required change | Decide: wire a compare entry point from Discovery **or** remove the route + screen |
| Risk | low (wire) / low (remove) |

| ID | R5-2 |
|---|---|
| Problem | 10 of 13 dispatcher producer methods unused |
| Evidence | `[SRC]` `CareerEventDispatcher` vs only 3 production callers |
| Required change | Wire the producers the product needs (e.g. `onApplicationCreated`, `onJobSaved`) or remove the unused ones |
| Risk | low |

---

## Stage R6 — Persistence / table ownership

| ID | R6-1 |
|---|---|
| Problem | Dead state/fields and any orphan tables post-R1 |
| Evidence | `[SRC]` DAO/entity census; `[RT]` v28 schema |
| Required change | Confirm each table has a production writer **and** reader; remove or document orphans |
| Risk | low |

| ID | R6-2 |
|---|---|
| Problem | Two never-enqueued workers |
| Evidence | `[SRC]` `ResumeAnalysisWorker`, `NotificationWorker` have no enqueue site |
| Required change | Enqueue deliberately or remove |
| Risk | low |

---

## Stage R7 — Dormant V2 reconnection (only what the product needs)

- Replay: needs a **deliberate** trigger, never implicit. Prerequisite: R2-1.
- Memory: owner decision required before any wiring.
- Context: fold `AiContextEngine2` into the live context path, then remove the duplicate — only after the live path is stable.
- Agents: **never wired without `HumanApprovalGate` enforced + audit event.** `AutonomousApplyUseCase` writes on `invoke()` — it must stay unwired until the gate is proven.

---

## Stage R8 — Runtime regression + release gate

- Full JVM suite + instrumented suite on `emulator-5554`.
- Fresh-install journey traversal (all 5 tabs + secondary destinations).
- Process death + relaunch verification.
- Metric integrity assertion on a zero-data install.

---

## Blockers

1. **Product decision required** on the provider gate (R4-1): enforce, or define provider-optional mode. Changes the primary journey.
2. **Product decision required** on `JobComparison` (R5-1): wire or delete.
3. R7 replay depends on R2-1 landing first.

## Explicitly NOT to touch in R2/R3

- Do not wire agents / autonomous apply / outreach / HumanApprovalGate UI.
- Do not migrate the Assistant to `AiContextEngine2`.
- Do not make the event log canonical.
- Do not implement M07 entity rehydration.
- Do not delete orphan tables (specify, do not delete).
- Do not redesign the 5-root navigation.
