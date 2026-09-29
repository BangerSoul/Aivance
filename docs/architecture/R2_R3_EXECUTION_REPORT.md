# R2 + R3 Execution Report

**Baseline:** `master` @ `845e44b` (R1 consolidation uncommitted in the working tree).
**Gate:** R2 (state ownership + hot-path graph persistence) and R3 (metric integrity + authoritative calculations).
**Definition of done applied to every item:** source proof → unit test → emulator runtime test → regression → documentation. Compilation was never treated as PASS.

---

## R2 — Graph persistence off the state-emission hot path

### What was wrong

`CareerStateEngine.state` is a `combine` over twelve flows. Its transform called
`careerGraphEngine.persist(graph)` *inline*, so:

* **every** emission of **any** of the twelve sources — including emissions that cannot change the
  graph, such as a `CareerEventBus` event or a provider status ping — ran a full transactional
  `DELETE`+`INSERT` of every `graph_nodes`/`graph_edges` row;
* that transaction ran on the emitting coroutine, so the state that was being computed waited on
  the database.

### What changed

`CareerStateEngine` now separates *building* a projection from *writing* it:

1. The transform projects the graph as before, then hands it to a writer through a
   `MutableStateFlow` — a write that **never suspends**, so no database work can delay a state
   emission.
2. `persistProjections()` (its own coroutine on `Dispatchers.Default`) consumes the handoff. A
   `MutableStateFlow` conflates, so a burst collapses to its newest value rather than queueing
   rewrites.
3. Before writing, the writer compares the projection's **content signature**
   (`CareerGraph.contentSignature()`, new) against the last signature it wrote, and skips the write
   entirely when they match. The signature deliberately excludes `createdAt`/`updatedAt` (which
   `System.currentTimeMillis()`-default on every rebuild and carry no domain meaning) and the
   engine's random `CareerGraphEdge.id` (the durable row id is derived from
   `source|relation|target`).

### Why dedupe+conflate instead of a debounce timer

The backlog suggested "a debounced/low-frequency trigger". A timer was rejected because it adds
latency to the *reader* refresh for no user-visible benefit and makes the behaviour
time-dependent, whereas content dedupe is the semantically correct minimum: the table is rewritten
exactly when the graph's content changes, and never otherwise. Conflation covers the burst case
that a debounce would have covered.

### Read-after-write (the risk the backlog flagged)

Making the write asynchronous could have let a graph reader (`GetCareerGraphInsightsUseCase`,
driven by state emissions) observe a slice one revision stale. The writer therefore bumps
`persistedRevision` **after** a projection lands, and that revision is the fifth input to the
`combine`. The engine emits one follow-up state whose `CareerState.graphRevision` differs, so
readers re-read a slice that is guaranteed to be the one just written. The loop terminates
immediately: the re-projected graph is content-identical, so the writer skips it and does not bump
again.

Carrying the revision in `CareerState` rather than relying on `StateFlow` equality semantics was a
deliberate choice — it makes the post-write emission **structurally distinct**, so delivery does
not depend on how the flow conflates equal values.

### Proof

| Evidence | Result |
|---|---|
| `CareerStateEngineGraphPersistenceTest.a state is emitted while the graph write is still blocked in the store` | state reaches the UI with a node count **while `persist` is held open inside the store**; revision still `0`; after release, revision `1` |
| `...emissions that do not change the graph content never rewrite the table` | after 2 bus events + 2 unchanged saved-jobs re-emissions, `persist` call count unchanged |
| `...a genuine content change is persisted exactly once more` | new saved job → exactly one extra write, no follow-up write |
| Emulator, clean install | `select type, count(*) from graph_nodes group by type` → `PROFILE|1`; the Dashboard's Skill Match card renders (it only renders when `loadGraph()` returns nodes), proving the async write landed and the reader saw it |

---

## R3 — Metric integrity

### R3-1 Career Score `18` at zero data — **fixed**

**Root cause:** `AnalyticsRepositoryImpl.calculateReadiness` returned a hardcoded `75` when there
were no sessions, and `CareerScoreEngine` divided by four unconditionally:
`(0 + 0 + 0 + 75) / 4 = 18`. The number was a constant.

**Fix:** `CareerScoreEngine.calculateCompositeScore` is now evidence-aware. A dimension is scored
only from real input (`ATS_READINESS` ← at least one ATS report; `NETWORKING` ← at least one
recruiter; `CONSISTENCY` ← at least one application; `INTERVIEW_READINESS` ← measured readiness).
Dimensions without evidence are **absent**, and when none has evidence the composite is `null`.
`CareerIntelligence.careerScore`, `GrowthState.careerScore` and `IntelligenceState.atsScore` became
nullable, and the dashboard renders the absence (`—` + the pre-existing "Unlock your score" copy,
which was previously unreachable because the score was never null). A snapshot is now only
recorded when a score was actually measured.

**Runtime (clean install):** `Career Score —` / `Unlock your score` / `Not scored yet` /
`Upload a resume and run an ATS scan to unlock scoring.` — the `18` is gone. `ATS Score —` (was a
`0` presented as a measurement).

### R3-2 Skill Match `100%` at zero data — **fixed**

**Root cause:** `CareerGraphEngine.analyzeSkillGaps` returned `matchRatio = 1.0f` when
`totalTarget == 0`, and the dashboard rendered it beside "0 of 0 target-job skills demonstrated"
with the copy "You demonstrate every skill your target jobs ask for. 🎯".

**Fix:** `SkillGapAnalysis.matchRatio` is `Float?` — `null` when no target job demanded a
recognised skill. `CareerGraphInsights.skillMatchPercent` and `CareerGraphInsightsUi.skillMatchPercent`
are `Int?`. The card now shows `—` plus "Save target jobs to measure your skill match." when there
is nothing to match, and keeps the celebratory copy only when it is true.

**Runtime (clean install, scrolled to the card):** `Skill Match` / `0 of 0 target-job skills
demonstrated` / `—` / `Save target jobs to measure your skill match.` — no `100%`, no celebratory
copy.

### R3-3 Interview Readiness had two owners — **unified**

**Root cause:** `AnalyticsRepositoryImpl` computed readiness from a hardcoded `75`, while
`InterviewViewModel.calculateReadiness` computed `lastSessionScore + (careerScore / 10)`. The
fabricated career score of `18` therefore surfaced as **`1%`** in Prep Studio while the analytics
path reported `18` for the same data.

**Fix:** new `InterviewReadinessCalculator` (`core:domain/analytics`) is the single owner: the mean
of `feedback.overallScore` across sessions that produced feedback, or `null` when none has.
`AnalyticsRepositoryImpl` and `InterviewViewModel` both call it; `InterviewViewModel` recomputes it
whenever history changes, and no longer reads the career score at all.
`InterviewUiState.Idle.readinessScore` and `PrepStudioHero(readinessScore)` are nullable.

**Runtime (clean install):** Prep Studio renders `Readiness —` (was `1%`).

### R3-4 `savedJobsCount` came from an application stage — **fixed**

**Root cause:** `applications.count { it.currentStageId == "SAVED" }` ignored the `saved_jobs`
table, so a job bookmarked from Job Discovery without an application never counted; the
`saved_jobs` flow was already collected in the same `combine`.

**Fix:** `savedJobsCount = savedJobs.size`, owned by `JobRepository.getSavedJobs()`.

### R3-5 upcoming interview date was the application date — **fixed**

**Root cause:** `UpcomingInterviewShort.dateTime = application.dateApplied.toString()` — the date
the user *applied* rendered as the interview datetime.

**Fix:** upcoming interviews are derived from the interview sessions themselves
(`!isCompleted`, sorted by `startTime`), formatted with `DateUtils.formatDateDisplay` +
`formatTimeDisplay`, with company/role falling back to the linked application's job. `id` carries
the session's `jobId`, which is what Prep Studio's "prep for this role" action consumes (it was
previously being handed an *application* id).

### R3-6 fabricated agent state — **removed**

`DashboardViewModel` hardcoded three "missions" and a "Scraping LinkedIn for Senior Android
roles..." task that nothing rendered and no producer owned. The literals are gone; the fields stay
empty until a real producer exists, and the test that asserted them now asserts they are empty.

### Guard suite — a regression here is now a build failure

**`DashboardZeroDataMetricGuardTest`** (JVM, `feature:dashboard`) renders the dashboard from a
genuine zero-data install through the **real** `CareerStateEngine`, `CareerGraphEngine`,
`GetCareerGraphInsightsUseCase`, score engines and `DashboardViewModel`, then checks a declared
contract twice:

* **value** — every rated metric is `null`, every count is `0`, every collection is empty;
* **completeness** — the contract names exactly the fields that exist (via reflection), so adding
  a dashboard metric without declaring its zero-data meaning **fails the test**.

**`DashboardScreenTest`** (instrumented) is the UI half: with zero data on screen no rated number
may be rendered (`18`, `100%`, `0%` all asserted absent) and the explaining copy is asserted
present. The pre-existing stale version of this file referenced fields that no longer exist
(`recentActivity`, `ActivityItem`) — it could not have compiled, which is why
`feature:dashboard`'s instrumented suite had never run.

**Runtime (clean install)** is the third layer: the emulator dump is asserted above.

---

## Test and runtime summary

| Gate | Result |
|---|---|
| Full JVM suite (`testDebugUnitTest`, all modules) | **BUILD SUCCESSFUL** |
| `:core:domain` / `:core:data` / `:feature:dashboard` / `:feature:interview` JVM suites | **0 failures** |
| `:feature:dashboard:connectedDebugAndroidTest` on `emulator-5554` (`m06_test` AVD) | **5 tests, 0 skipped, 0 failures** |
| Clean install → Welcome → Auth → Onboarding → Skip All → Dashboard | no crash, no ANR, no blank screen |
| Zero-data dashboard metrics | no fabricated value; `—` + explaining copy |
| All five tabs | render, no crash |
| Process death → relaunch | lands on Dashboard, no crash, metrics still honest, data persisted |
| `PRAGMA user_version` on device | `28`; `job_applications` absent (R1 intact) |

## Follow-ups found while executing (not fixed — out of R3's stated scope)

1. **`KPIEngine.calculateInterviewRate` returns `0.0` for `0/0`.** The Pipeline tab renders
   "You have 0 active applications. Your interview conversion is 0%." Same defect class as R3-1/2;
   the surrounding sentence mitigates but does not remove the invalid rate. Nullable-ising it
   ripples into `PredictiveMetrics` and the snapshot KPIs, so it belongs with the next analytics
   pass.
2. **`DashboardUiState.agentMissions` / `activeTask`** are now permanently empty/null. They should
   be deleted (with `AgentMission`/`AgentTask`/`MissionStatus`) unless a real agent producer is
   wired — R6 (dead state) rather than R3.
3. **Provider gate** — RESOLVED. See the R2.2 section below and `PROVIDER_GATE_DECISION.md`
   (Option B selected). It is no longer reachable with zero providers by bypass; provider-free
   product entry now requires the explicit, persisted `providerOptional` choice.

---

## R2.2 — Provider gate (RESOLVED, Option B: explicit provider-optional mode)

### The contract, derived not invented

The decision doc left two options open. Option B was implemented because provider-free operation is
an **existing** supported product contract, not a new requirement: job discovery runs on free
keyless providers (Arbeitnow / Jobicy / Adzuna / USAJobs) and the assistant has a deterministic
local Copilot fallback (`GetAssistantResponseUseCase`). Removing `Skip All` would have destroyed a
supported workflow, so it was kept — but made **explicit and persisted** rather than a silent
bypass.

"Minimum provider configuration satisfied" therefore means: `onboardingCompleted && (a validated
AI provider is saved OR the user explicitly chose provider-optional mode)`.

### What was wrong

* `Skip All` reached Dashboard with zero providers and no record of *why* (deliberate vs. never got
  around to it).
* `AuthViewModel.continueWithEmail` (and the Google path) marked `onboardingCompleted = true`
  before any provider step, so the account step alone granted product entry — a hard bypass of the
  gate.
* Provider validity was never persisted or centrally evaluated; `onboardingCompleted` was doubling
  as "has a working provider", which it never tracked.

### What changed

1. **Split the flag.** New persisted `providerOptional` (DataStore `UserPreferences` +
   `UserPreferencesRepository.updateProviderOptional`). `onboardingCompleted` keeps its step-flow
   meaning only.
2. **One central authority.** `AuthenticationViewModel.evaluateProviderGate` is a pure function
   over `{onboarded, providerOptional, saved-AI-provider?, live-AI-statuses}` → `ProviderGateState`
   `{UNCONFIGURED, OPTIONAL, CONFIGURED, INVALID}`. The nav graph consults `providerGate`
   (a `StateFlow` over persisted + live sources), the splash cold-start path consults
   `resolveProviderGate()` (suspend, settled-data resolution), and a mid-session `LaunchedEffect`
   remediates a signed-in user whose gate flips to UNCONFIGURED/INVALID by moving them to provider
   setup. No onboarding UI flag grants entry.
3. **Fixed the bypass.** `AuthViewModel` no longer sets `onboardingCompleted`; a fresh email or
   Google account routes to `ProviderSetup`, and the persisted gate holds. Onboarding's `Finish`
   sets `onboardingCompleted=true, providerOptional=false`; the explicit
   `ContinueWithoutProviders` sets `onboardingCompleted=true, providerOptional=true`. The Welcome
   "skip" now routes to Auth, not the product.
4. **Race-safe.** The gate is `null` until its first evaluation lands; navigation defers on `null`
   so the async cold-start hydration (`AivanceApp.hydrateSavedProviderConfigs`) cannot flash a
   false lock-out. `registeredAiProviderIds` is declared before `providerGate` to avoid a
   construction-order NPE under `SharingStarted.Eagerly`. Live status is consulted only to detect
   invalidation (`InvalidConfiguration` / `AuthenticationFailed`); transient Offline/Error after a
   real network probe never hard-invalidates a configured user.

### Proof

| Evidence | Result |
|---|---|
| `ProviderGateTest` (JVM, `feature:profile`) | **16 tests, 0 failures** — fresh user, valid provider, invalid provider, no provider, provider-optional, onboarding-already-persisted, provider-removed-after-onboarding, logout-clears-gate, Google-new-user routing |
| Emulator `emulator-5554` (API 34, x86_64), clean install `pm clear` | Welcome → Auth (email) → Provider Setup shows `Skip All — I'll configure later` relabeled to the explicit **Continue without AI providers** copy; tapping it lands on Dashboard in OPTIONAL mode; **no bypass to Dashboard from the account step** |
| Process death (`am kill`) → relaunch | returns straight to Dashboard (gate re-evaluated OPTIONAL from persisted `providerOptional=true`), no crash, no re-onboarding |
| DataStore inspection | `user_preferences.json` present and encrypted on device (contract persisted, not UI-only) |

**Runtime classification: EXECUTED PASS** — the gate paths were exercised on the device, not only
compiled.
