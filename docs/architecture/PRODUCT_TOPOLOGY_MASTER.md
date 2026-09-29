# PRODUCT TOPOLOGY MASTER — AiVance

**Status:** reconstruction complete, no production code modified.
**Baseline:** branch `master`, HEAD `845e44b`, tag `v2-foundation-baseline`, Room **v28**.
**Method:** source tracing (`class`/DI/caller greps) + live Android 14 / API 34 x86_64 emulator (`emulator-5554`).

Every claim below carries one of: `[SRC]` source reference, `[RT]` runtime evidence, `[TEST]` test evidence, `[INFER]` explicit inference.

---

## 1. Executive product flow

AiVance is a single-Activity Compose "Career OS" with a 5-root workspace shell (Nav3 multi-backstack).
The primary loop — **onboarding → provider config → Dashboard → Intelligence / Discovery / Pipeline / Prep Studio** — is real and runs `[RT]`. It boots, navigates, persists, and survives process death without a crash or ANR `[RT]`.

Two structural realities dominate:

1. **The graph *read* path is live; the rest of the V2 tier is dormant.** The entity graph is rebuilt and persisted on every `CareerStateEngine` emission `[SRC CareerStateEngine:~95]`, and the Dashboard now reads insights from it `[SRC DashboardViewModel:48-50]`. But replay, career memory, `AiContextEngine2`, and the entire agent tier have **no production caller** — they are DI-bound and test-covered only.
2. **Metric integrity is the top product risk, not wiring.** The Dashboard confidently renders numbers that are fabricated or inverted at zero data, and two screens compute "readiness" differently `[RT]`.

---

## 2. Startup / auth / onboarding flow

```
Process start
  → AivanceApp.onCreate()  [SRC app/AivanceApp.kt]
      scheduler.schedulePeriodicWork()  → 10 periodic workers + 1 one-time  [SRC AivanceApp.kt:142-302]
  → MainActivity  [SRC app/MainActivity.kt]
  → AivanceNavGraph: initialDestination  [SRC navigation/AivanceNavGraph.kt]
        not authenticated → Destination.Splash
        authenticated     → Destination.Dashboard
  → Auth gate: AuthenticationViewModel.isOnboarded = prefs.onboardingCompleted  [SRC AuthenticationViewModel:78]
  → Onboarding step 1 = ChooseAiProvider  [SRC OnboardingViewModel:195]
  → ... AI → Job → Enrichment(skippable) → Summary → Finish
  → Dashboard (root workspace)
```

**Authentication is local-only and simulated** `[SRC AuthenticationViewModel]`: a flag in DataStore, no server, no token. `AuthScreen` exposes New/R returning-user paths `[SRC AuthScreen]`.

### Gate bypasses (CONFIRMED, both at source; Dashboard-with-zero-providers at runtime `[RT]`)

| Bypass | Location | Effect |
|---|---|---|
| `OnboardingUiEvent.SkipAll` | `OnboardingViewModel.skipAll()` → `updateOnboardingCompleted(true)` `[SRC:298]` | Completes onboarding with **zero** providers configured |
| `AuthViewModel.continueWithEmail` | `updateOnboardingCompleted(true)` `[SRC AuthViewModel:115]` | Marks onboarded **before** provider setup; also `:199` `updateOnboardingCompleted(!isNew)` |
| Onboarding `Finish` | always sets `OnboardingUiState.Complete` in `finally` `[SRC:276]` | Never dead-ends even if persist throws |

**The provider gate does not bind at product level.** Validation gates *within* a step, but no product-level invariant requires ≥1 validated provider before Dashboard.

---

## 3. Provider configuration topology

Flow: `onboarding step → ProviderRegistry.getProvider(id) → metadata → config form → UpdateXConfig → ValidateXProvider → ProviderManager.validateProvider → registry provider init → persist → ProviderManager.providerStatuses → CareerStateEngine`

| Family | Providers present | UI | Config | Validated | Registered | Runtime caller | Classification |
|---|---|---|---|---|---|---|---|
| AI | Anthropic, Gemini, OpenAI, Ollama, Gemma (on-device) `[RT onboarding list]` | yes | yes | yes (per-step) | yes | `AiRepository`/provider bridge | REAL |
| Job | Apify, Remotive, RemoteOK, Lever, Greenhouse, Jobicy, Arbeitnow `[SRC core/job-providers]` | yes | yes | yes | yes | `JobRepository` | REAL (network-gated) |
| Enrichment | Hunter `[SRC core/enrichment-providers]` | yes | yes | yes | yes | dormant in prod path | PARTIAL |

**Two writers to one provider-config table** `[SRC]`:
- `SettingsRepositoryImpl:36 → AiLocalDataSource.saveProviderConfig → ProviderConfigurationEntity`
- `ProviderRepositoryImpl:48 saveProviderConfig → ProviderConfigurationEntity`

Both write `provider_configurations` via `AiAnalyticsDao`. This is a **duplicate persistence path** (competing source of truth for provider config).

Runtime: with no provider configured, Dashboard is reachable and all provider-dependent features degrade to empty states rather than erroring `[RT]`.

---

## 4. Main navigation graph

Implementation: **Navigation 3** multi-backstack shell `[SRC navigation/AivanceNavGraph.kt, AivanceAppShell.kt]`.

| Root workspace | Tab label `[RT]` | Owns (hub-switch targets) `[SRC AivanceNavGraph:131-153]` |
|---|---|---|
| `Dashboard` | Dashboard | quick actions → resume/jobs/interview/tracker |
| `Intelligence` | Intelligence | `Ats`, `ResumeDetail`, `ResumeEngine` |
| `Discovery` | Discovery | `CoverLetter`, `JobComparison`, `DiscoverBySkill`, `RecruiterDashboard` |
| `Pipeline` | Pipeline | `TrackApplication` |
| `PrepStudio` | Prep Studio | `LearnSkill` |

Auth destinations: `Splash, Welcome, Auth, Onboarding, ProviderSetup`. Root destinations: the 5 above.

**Unreachable UI (CONFIRMED):** `Destination.JobComparison` → `JobComparisonScreen` is routed `[SRC AivanceNavGraph:393]` and hub-mapped `[SRC:139]`, but **no UI element anywhere calls `onNavigate(JobComparison(...))`** — the only greps for a comparison entry point return zero `[SRC]`. The screen renders a real compare matrix but cannot be reached.

---

## 5. Dashboard topology + metric integrity

`DashboardScreen` ← `DashboardViewModel.uiState` ← `combine(CareerStateEngine.state, _insightsRefresh)` `[SRC DashboardViewModel:44-47]`.

| Metric (as rendered `[RT]`) | Source field | Formula / origin | Empty-state behavior | Classification |
|---|---|---|---|---|
| **Career Score 18** | `state.growth.careerScore` ← `AnalyticsRepositoryImpl.getCareerIntelligence` | `(ats + networking + consistency + readiness)/4`, `readiness` = **hardcoded 75** when no sessions `[SRC AnalyticsRepositoryImpl:174-177]` | **Fabricated** (0+0+0+75)/4 = 18 | **FABRICATED** |
| ATS Score 0 | `state.intelligence.atsScore` ← intelHub/snapshot | real | 0 | REAL |
| Active Apps 0 | `state.pipeline.activeApplications` | real | 0 | REAL |
| **Saved Jobs 0** | `state.discovery.savedJobsCount` ← `applications.count { currentStageId == "SAVED" }` `[SRC CareerStateEngine:119]` | derived from **applications**, ignores `saved_jobs` table | 0 | **WRONG OWNER** |
| **Skill Match 100%** | `graphInsights` ← `CareerGraphEngine.analyzeSkillGaps` | `matchRatio = if (totalTarget > 0) … else 1.0f` `[SRC CareerGraphEngine:336-340]` | **100% at zero data** + celebratory copy "You demonstrate every skill your target jobs ask for 🎯" `[RT]` | **FABRICATED** |
| Profile completion / greeting | `state.profile` | real (empty name → `"Hello, "`) `[RT]` | blank name | REAL |
| `agentMissions` (3 items) | **hardcoded literals in ViewModel** `[SRC DashboardViewModel:75-79]` | not rendered by `DashboardScreen` | — | **DEAD STATE + FABRICATED DATA** |
| `activeTask` | hardcoded literal `[SRC DashboardViewModel:80]` ("Scraping LinkedIn…") | not rendered | — | **DEAD STATE + FABRICATED DATA** |
| Next Best Action | `NavigationWorkflowEngine.getRecommendedDestination` | real | "Upload Resume" `[RT]` | REAL |

Quick Actions `[RT]`: Resume · Jobs · Interview · Assistant · Pipeline · Insights · Career Graph.

> `DashboardViewModelTest:134` asserts `state.agentMissions.isNotEmpty()` — **a test enshrines the fabricated data**.

---

## 6. Primary tab reconstruction

| Tab | Screen | ViewModel | Data source | Empty state `[RT]` | Runtime |
|---|---|---|---|---|---|
| Dashboard | `DashboardScreen` | `DashboardViewModel` | `CareerStateEngine` + `GetCareerGraphInsightsUseCase` | career-score/skill-match cards | renders, metrics wrong |
| Intelligence | resumes + ATS scan hub | `IntelligenceViewModel`-equivalent | Resume/Ats repos | "No resumes yet", "No ATS scans yet" | renders correctly |
| Discovery | job search | `JobsViewModel` | `JobRepository` + providers | full filter UI, "Best match" sort | renders (network-gated) |
| Pipeline | Kanban + analytics | `TrackerViewModel` | **`ApplicationWorkflowRepository`** (canonical `applications`) | "0 active applications", "0 of 5 applied today" | renders correctly |
| Prep Studio | Practice/Research/History/Question Bank/Learn | `InterviewViewModel`-equivalent | `InterviewRepository` + `CareerState` | **"Readiness 1%"** | renders, **contradicts Dashboard** |

### Interview Readiness — two calculations (CONFIRMED `[RT]`)

- Dashboard path: `AnalyticsRepositoryImpl.calculateReadiness` → **75** default → Career Score 18.
- Prep Studio path: its own readiness → **1%** `[RT]`.
Same concept, two owners, two different on-screen numbers at zero data.

---

## 7. Feature outcomes matrix

| Feature | Reachable | UI | VM | Domain | Data | DB/API | Nav | Runtime | Outcome | Class |
|---|---|---|---|---|---|---|---|---|---|---|
| Dashboard | yes | yes | yes | yes | yes | yes | yes | yes | PARTIAL (fake metrics) | YELLOW |
| Onboarding + providers | yes | yes | yes | yes | yes | yes | yes | yes | PARTIAL (gate bypassable) | YELLOW |
| Auth | yes | yes | yes | yes | prefs | local | yes | yes | WORKING (simulated) | GREEN |
| Intelligence (resume/ATS) | yes | yes | yes | yes | yes | yes | yes | yes | WORKING (empty) | GREEN |
| Job Discovery | yes | yes | yes | yes | yes | providers | yes | empty | WORKING (network-gated) | YELLOW |
| Pipeline / Tracker | yes | yes | yes | yes | yes | `applications` | yes | yes | WORKING | GREEN |
| Prep Studio | yes | yes | yes | yes | yes | yes | yes | yes | PARTIAL (readiness split) | YELLOW |
| Skill-gap insights | yes (dashboard) | yes | yes | yes | graph read | graph | yes | yes | WORKING | GREEN |
| CareerGraph entity projection | indirect | via card | — | yes | graph_nodes/edges | yes | — | yes | WORKING (hot path) | YELLOW |
| CareerEvent replay | **no** | no | — | yes | event log | yes | — | test only `[TEST]` | DORMANT | GREY |
| CareerMemoryEngine | no | no | — | yes | career_memory | yes | — | no | DORMANT | GREY |
| Agent tier (CareerAgentEngine/HumanApprovalGate/executor) | no | no | — | yes | — | — | — | no | DORMANT | GREY |
| AiContextEngine2 | no | no | — | yes | — | — | — | no | DORMANT | GREY |
| JobComparison | **no entry point** | yes | — | — | — | — | route only | no | ORPHAN UI | BLACK |
| Agent mission panel | n/a | **not rendered** | hardcoded | — | — | — | — | no | DEAD STATE | BLACK |
| ResumeAnalysisWorker / NotificationWorker | n/a | — | — | — | — | — | — | **never enqueued** | DEAD | BLACK |

---

## 8. Database topology

Room **v28**; full chain `MIGRATION_1_2 … MIGRATION_27_28` registered `[SRC DatabaseModule:33-59]`.

`[RT]` on-device: `user_version = 28`; `job_applications` **0 occurrences** in DB **and WAL**; `applications` present → **R1 consolidation holds at runtime**.

20 DAOs `[SRC core/database/dao]`: AiAnalytics, Aivance, Analytics, Assistant, Ats, Audit, CareerEventLog, **CareerMemory**, Company, CoverLetter, **Graph**, Interview, Job, Profile, Recruiter, Resume, Roadmap, Search, User, Workflow.

| Table | Writers | Readers | UI | Class |
|---|---|---|---|---|
| `applications` | `ApplicationWorkflowRepositoryImpl`, `BackupImporter` | Tracker, CareerStateEngine, graph, Assistant, Analytics, Workflow | Pipeline | **LIVE (canonical)** |
| `saved_jobs` | `JobDao` | `JobDao.getSavedJobIds` | SavedJobs | LIVE — **but Dashboard's Saved Jobs count ignores it** |
| `graph_nodes` / `graph_edges` | `CareerGraphEngine.persist` (entity slice) + replay (CAREER_EVENT slice) | `GraphDao`, `GetCareerGraphInsightsUseCase` | dashboard card | LIVE |
| `career_event_log` | `CareerEventDispatcher` (append-only) | `CareerEventLogRepository`; replay engine (no prod caller) | none | LIVE audit / DORMANT reader |
| `career_memory_entries` | `CareerMemoryRepository` | `CareerMemoryEngine` (no prod caller) | none | DORMANT |
| `provider_configurations` | **`SettingsRepositoryImpl` AND `ProviderRepositoryImpl`** | `AiAnalyticsDao`, `ProviderManager` | onboarding + settings | LIVE (dual writer) |
| `audit_logs`, `provider_configurations`, `roadmaps`, etc. | per-DAO | partial | partial | mixed |

**R1 baseline honoured:** no `job_applications`, no `TrackerDao`, no duplicate `JobTrackerRepository` in source `[SRC]` or DB `[RT]`.

---

## 9. CareerGraph topology + M05 boundary

```
CareerStateEngine.state (combine of 12 flows)
  → careerGraphEngine.buildGraph(profile,resumes,jobs,applications,interviews)
  → careerGraphEngine.persist(graph)        ← runs on EVERY emission  [SRC:95]
  → CareerGraphRepository → GraphDao → graph_nodes / graph_edges
  → GetCareerGraphInsightsUseCase (read-only) → Dashboard card
```

**Graph ownership matrix**

| Slice | Writer | Reader | Storage | Source of truth | Replay | UI | Status |
|---|---|---|---|---|---|---|---|
| Entity projection (all node types **except** `CAREER_EVENT`, plus edges) | `CareerGraphEngine.persist` | `GraphDao` / insights | `graph_nodes`/`graph_edges` | live state | no | dashboard card | LIVE |
| Event provenance (`CAREER_EVENT` nodes) | `CareerEventReplayEngine` | `GraphDao` | `graph_nodes` | `career_event_log` | **yes** | none | DORMANT (no prod caller) |

**M05 invariant:** single-writer-per-slice holds `[SRC CareerGraphEngine / CareerEventReplayEngine KDoc]`. Neither writer destroys the other's slice. Verified by `[TEST]` `CareerEventReplayRuntimeTest` (10/10 on device).

**Hot-path cost:** every emission of 12 combined flows triggers a full graph rebuild + transactional upsert. This is the only structural smell on the live path.

---

## 10. Event topology

`CareerEvent` → producers → `CareerEventDispatcher.dispatch` → (`CareerEventBus.emit` ∥ `CareerEventLogRepository.append`) → `career_event_log` (append-only, idempotent on event id) `[SRC CareerEventDispatcher:32-40]`.

**Production emissions — exactly three** `[SRC]`:

| Event | Producer |
|---|---|
| `ApplicationEvent.StageChanged` | `WorkflowEngine:60` |
| `ResumeEvent.AnalysisCompleted` | `ResumeRepositoryImpl:193` |
| `InterviewEvent.Completed` | `InterviewRepositoryImpl:185` |
| (`SystemEvent("init")`) | `CareerStateEngine:61` onStart |

Dispatcher defines **13 typed producers**; the other 10 (`onResumeCreated/Updated`, `onAtsScanStarted/ScoreChanged`, `onJobSaved/Viewed`, `onApplicationCreated`, `onInterviewStarted`, `onAgentAction*`) have **no production caller** `[SRC]`.

`CareerEventReplayEngine` is reachable only via `RebuildCareerEventProjectionUseCase`, which has **no production caller** — only `[TEST]` `CareerEventReplayRuntimeTest` + `RebuildCareerEventProjectionUseCaseTest` `[SRC]`.

Note: `CareerEventDispatcher` KDoc still says "no replay engine consumes the log yet" — accurate in production terms.

---

## 11. Memory / context / agent topology

| Component | Instantiated / DI-bound | Production caller | UI | Side effects | Tests | Class |
|---|---|---|---|---|---|---|
| `CareerMemoryEngine` | yes (`@Inject`) | **none** | no | via repo | `CareerMemoryEngineTest` | DORMANT |
| `CareerMemoryRepository` / `CareerMemoryDao` | yes | only by dormant engine | no | table | yes | DORMANT |
| `AiContextEngine2` | yes (`@Inject`) | **none** | no | none | `AiContextEngine2Test` | DORMANT |
| `ContextEngine` (legacy) | yes | live assistant path | via Assistant | none | yes | LIVE |
| `AssistantContextEngine` | yes | live assistant path | via Assistant | none | yes | LIVE |
| `CareerAgentEngine` | yes (`@Inject`) | **none** | no | **would execute actions** | `CareerAgentEngineTest` | DORMANT |
| `HumanApprovalGate` | yes (`@Inject`) | **none** | no | gate | `HumanApprovalGateTest` | DORMANT |
| `DefaultAgentActionExecutor` | bound in `AgentModule` | **none** | no | executor | yes | DORMANT |
| `CareerOperationTracer` | yes (`@Inject`) | none found | no | tracing | — | DORMANT |
| `AutonomousApplyUseCase` / `OutreachAgentUseCase` / `FollowUpAgentUseCase` | exist | **no production caller** | no | would write `applications` | yes | DORMANT |

**Safety boundary preserved:** no agent side-effecting path is reachable. Nothing here was wired.

---

## 12. Background topology

Enqueued at startup `[SRC AivanceApp.kt:142-302]`:

| # | Work | Worker | Cadence |
|---|---|---|---|
| 1 | `periodic_sync` | `SyncWorker` | 15 min |
| 2 | `periodic_job_sync` | `JobSyncWorker` | 2 h |
| 3 | `periodic_provider_refresh` | `ProviderRefreshWorker` | 6 h |
| 4 | `periodic_analytics_upload` | `AnalyticsUploadWorker` | 1 h |
| 5 | `periodic_cache_cleanup` | `CacheCleanupWorker` | 1 d |
| 6 | `periodic_db_cleanup` | `DatabaseCleanupWorker` | 7 d |
| 7 | `periodic_health_check` | `HealthCheckWorker` | 12 h |
| 8 | job alert | `JobAlertWorker` | 1 d |
| 9 | `periodic_follow_up` | `FollowUpWorker` | 1 d |
| 10 | `periodic_analytics_snapshot` | `AnalyticsSnapshotWorker` | 7 d |
| + | `security_migration` | `SecurityMigrationWorker` | one-time |

Dynamic: `SyncManager` → `SyncWorker` (`sync_retry`), `ModelDownloadScheduler` → `GemmaModelDownloadWorker`.

**Never enqueued (DEAD):** `ResumeAnalysisWorker`, `NotificationWorker` `[SRC]` — no enqueue site.

---

## 13. Runtime evidence

| Check | Result |
|---|---|
| Device | `emulator-5554`, API 34, x86_64, `sys.boot_completed=1` `[RT]` |
| APK | rebuilt + installed over existing v27 DB `[RT]` |
| Launch | MainActivity resumed, **no FATAL/ANR/SQLiteException** `[RT]` |
| Migration | DB `user_version` **27 → 28** on launch `[RT]` |
| R1 verify | `job_applications` **0 occurrences** in DB+WAL; `applications` present `[RT]` |
| Process death | force-stop → relaunch → **Dashboard** (root default); onboarding not re-shown `[RT]` |
| Persistence | career-score/counts/onboarding flag survive restart `[RT]` |
| Tabs | all 5 render with real empty states; no crash `[RT]` |
| Fabricated metric | **"18" Career Score** on screen at zero data `[RT]` |
| Zero-data false positive | **"Skill Match 100%"** + "0 of 0 target-job skills demonstrated" + 🎯 copy `[RT]` |
| Readiness split | Dashboard 75-baked-18 vs **Prep Studio "1%"** `[RT]` |
| Gate bypass | Dashboard reachable with zero providers `[RT]` (last turn) + both source bypasses `[SRC]` |
| Stale-dump correction | first process-death read showed Prep Studio; re-verified as a stale `ui.xml` → actual landing is Dashboard `[RT]` |

---

## 14. Persistence / process-death results

- `aivance-database` + `-shm` + `-wal` present and growing `[RT]`.
- Encrypted DataStore holds `onboardingCompleted` and auth flag `[SRC PreferencesManager]`.
- Cold start respects `onboardingCompleted` → skips onboarding `[RT]`.
- No state-loss or stale-UI defect observed in the traversal; the only metric staleness is the fabricated-value issue above.

---

## 15. Dead / dormant / duplicate infrastructure

**GREY — dormant infrastructure (keep, do not wire, do not delete):**
`CareerEventReplayEngine` + `RebuildCareerEventProjectionUseCase`, `CareerMemoryEngine` + repository/DAO, `AiContextEngine2`, `CareerAgentEngine`, `HumanApprovalGate`, `DefaultAgentActionExecutor`, `CareerOperationTracer`, agent use cases.

**BLACK — dead / orphaned:**
- `Destination.JobComparison` + `JobComparisonScreen` (no entry point)
- `agentMissions` / `activeTask` dead state + hardcoded data in `DashboardViewModel`
- `ResumeAnalysisWorker`, `NotificationWorker` (never enqueued)
- 10 of 13 `CareerEventDispatcher` producer methods

**DUPLICATE / competing ownership:**
- Interview Readiness: `AnalyticsRepositoryImpl.calculateReadiness` vs Prep Studio's own calc
- Provider config: `SettingsRepositoryImpl` vs `ProviderRepositoryImpl`
- Saved jobs count: `applications.currentStageId == "SAVED"` vs the `saved_jobs` table
- Career score: live hub vs periodic snapshot (documented fallback chain in `CareerStateEngine`)

---

## 16. Confirmed defects (evidence-classified)

| ID | Category | Location | Evidence | Current → Expected | Class |
|---|---|---|---|---|---|
| D-01 | G fabricated | `AnalyticsRepositoryImpl:174-177` | `[SRC]`+`[RT]` | Career Score 18 at zero data → 0/incomplete | A |
| D-02 | G fabricated | `CareerGraphEngine:336-340` | `[SRC]`+`[RT]` | Skill Match 100% at 0 target skills → 0% / no-data state | A |
| D-03 | C inconsistency | `AnalyticsRepositoryImpl` vs Prep Studio | `[SRC]`+`[RT]` | two readiness formulas → one owner | C |
| D-04 | C wrong owner | `CareerStateEngine:119` | `[SRC]` | Saved Jobs from application stages → `saved_jobs` | C |
| D-05 | C wrong source | `CareerStateEngine:123-128` | `[SRC]` | interview date = `application.dateApplied` → real interview date | C |
| D-06 | D contract gap | `OnboardingViewModel:294`, `AuthViewModel:115` | `[SRC]`+`[RT]` | Dashboard reachable with 0 providers → enforce gate | D |
| D-07 | G fabricated | `DashboardViewModel:75-80` | `[SRC]` | hardcoded agent missions/task; not rendered; test asserts them | G/A |
| D-08 | F dead | nav graph | `[SRC]` | `JobComparison` unreachable | F |
| D-09 | A defect | `CareerStateEngine:95` | `[SRC]` | full graph rebuild+persist on every emission | A |
| D-10 | F dead | `AivanceApp` scheduler | `[SRC]` | 2 workers never enqueued | F |
| D-11 | C duplicate | provider config writers | `[SRC]` | 2 writers to `provider_configurations` | C |
| D-12 | E dormant | V2 tier | `[SRC]` | 10/13 event producers unused; replay/memory/agents uncalled | E |

**Item re-verification (#1–#20 from the prior report): all 20 re-confirmed; one corrected** (#19 process-death landing was a stale-dump artifact — actual behavior is a clean Dashboard landing, not "restores last tab").

---

## 17. Missing product contracts

1. **Provider gate** — no invariant that Dashboard requires ≥1 validated provider.
2. **Metric integrity** — no invariant that a metric must not render a fabricated value at zero data.
3. **Single readiness owner** — no single calculation owner for Interview Readiness.
4. **Saved-jobs ownership** — no single owner for "saved jobs" (table vs application stages).
5. **No-data states** — Skill Match / readiness lack a defined "insufficient data" state.

---

## 18. Unknowns

- Whether the Welcome "Skip" landing on `Auth` is intentional `[INFER]`.
- Full census of `career_event_log` / `graph_nodes` rows (binary WAL scan is suggestive, not a definitive census).
- Whether `NotificationWorker`/`ResumeAnalysisWorker` are intended for a future enqueue path.
- Live provider network calls: not exercised (no credentials configured on device).
