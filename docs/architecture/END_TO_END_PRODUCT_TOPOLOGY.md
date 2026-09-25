# AiVance — End-to-End Product Topology (Reconstruction Gate)

**Status:** discovery only. No production code, schema, navigation, DI, event contract or test was modified to produce this document.
**Baseline:** R1 application-ownership consolidation is **already applied and treated as current baseline** (`applications` authoritative; `job_applications` removed; Room **v28**). The legacy path was not resurrected.
**Evidence tags:** `SRC` = source reference · `RT` = executed runtime evidence · `TEST` = test evidence · `INF` = inference.

---

## 1. Executive product flow

```
Launcher → MainActivity (single activity)
  → AivanceNavGraph: initialDestination = deepLink ?: (Authenticated ? Dashboard : Splash)
     → SplashScreen ──(auth settle, 3s cap)──→ Authenticated ? Dashboard : Welcome
        → Welcome ──Get Started──→ Auth (email form | Google)
           → Auth ContinueWithEmail ──NewUser──→ ProviderSetup ──→ OnboardingScreen steps
                                          ──ReturningUser──→ (auto sign-in)
              → Onboarding: ChooseAI → ConfigureAI/Validate → ChooseJob → ConfigureJob/Validate
                            → ChooseEnrichment → Configure/Validate → SkipEnrichment → Summary → Finish
                            (or "Skip All" at any selection step)
                 → Complete → authViewModel.CheckAuth → isAuthed=true
  → NavigationSuiteScaffold with 5 independent root backstacks
     Dashboard | Intelligence | Discovery | Pipeline | PrepStudio
  → secondary destinations pushed onto the owning root's backstack
```

`SRC` `app/.../MainActivity.kt`, `navigation/.../AivanceNavGraph.kt:60-120`, `AivanceAppShell.kt`, `Destination.kt`.

The chain is **real end-to-end for the read path** (UI → VM → UseCase → repository → DAO → Room) and **real for persistence + process-death recovery**. It is **partially wired for the write path in a few features** (see §18) and **entirely unwired for the V2 agent/memory/replay tier** (see §15).

---

## 2. Startup / login / onboarding flow

### 2.1 What determines launch destination

| Condition | Destination | Evidence |
|---|---|---|
| Pending authenticated deep link **without** auth | `Splash` | `SRC` AivanceNavGraph.kt:66-75 |
| Pending deep link **with** auth | the deep-link destination | `SRC` AivanceNavGraph.kt:70-72 |
| `AuthenticationUiState.Authenticated` | `Dashboard` | `SRC` AivanceNavGraph.kt:76 |
| otherwise | `Splash` | `SRC` AivanceNavGraph.kt:77 |

### 2.2 Is "login" real?

**No — it is a local session gate, not authentication.** `SRC` `AuthenticationViewModel.kt`:

```kotlin
val isOnboarded = prefs?.onboardingCompleted == true
val hasKey      = !prefs?.geminiApiKey.isNullOrBlank()
val authenticated = (isOnboarded || hasKey) && needsReauth != true
```

- `AuthenticationUiEvent.Login(apiKey)` only **persists the key** (`updateGeminiApiKey`) and flips state. No server, no token, no credential verification. `SRC` `AuthenticationViewModel.kt:login`
- Firebase is used **only** to invalidate Google-created sessions (`sessionNeedsReauth`), never to *grant* access. `SRC` `AuthenticationViewModel.kt:sessionNeedsReauth`
- The account form (`AuthScreen`/`AuthViewModel`) is a **local account row in Room** (`userDao.upsertUser`) plus a DataStore session id. Google sign-in is Firebase-backed but degrades to an actionable error when no web client id is present. `SRC` `AuthViewModel.kt`

### 2.3 First launch vs returning launch vs process death

| Scenario | Behaviour | Evidence |
|---|---|---|
| Fresh install | Welcome screen, unauthenticated | `RT` — Welcome rendered on first launch |
| After email continue | `NewUser` → ProviderSetup | `SRC` `AuthViewModel.continueWithEmail` |
| Process death + relaunch (onboarded) | **straight to Dashboard, no re-onboarding** | `RT` — force-stop → relaunch landed on Dashboard |
| Clear app data | back to Welcome | `SRC` — gate reads DataStore/Room only |

### 2.4 Can the user reach Dashboard with zero / invalid providers? — **YES (confirmed defect)**

Two independent bypasses, both verified:

1. **Onboarding `Skip All`.** Available on the AI *and* job selection steps; calls `updateOnboardingCompleted(true)` then `Complete`. `SRC` `OnboardingViewModel.skipAll()`, `OnboardingScreen.kt` (`skip_all`).
   **`RT`**: from Welcome → Get Started → Sign In tab → email → Continue → **Choose AI Provider** → tapped *"Skip All — I'll configure later"* → **Dashboard rendered with 0 providers configured.**
2. **The gate is set too early.** `AuthViewModel.continueWithEmail` calls `userPreferencesRepository.updateOnboardingCompleted(true)` **before** provider setup, so simply entering an email marks the user onboarded and `AuthenticationViewModel` will treat them as `Authenticated` on the next cold start. `SRC` `AuthViewModel.continueWithEmail`.

**Consequence:** "Next must be blocked if provider cannot be initialized" holds *within a config step*, but the **product-level** invariant "a configured, validated provider is required to enter the product" does **not** hold.

### 2.5 Provider validation actually blocks a step (contract holds at step level)

`ProviderManager.validateProvider` applies the candidate config to the live provider, runs `onInitialize()` + `checkHealth()`, and only then accepts:

```kotlin
provider.applyConfiguration(config); provider.onInitialize()
val health = provider.checkHealth()
if (health == ProviderStatus.Ready || health == ProviderStatus.Active) Result.Success(Unit)
```
`SRC` `ProviderManager.kt:103-127`. Failure returns a user-facing message via `friendlyValidationMessage` (InvalidConfiguration / Error / Degraded). `SRC` `ProviderManager.kt:132-144`.

Step UI gate: `enabled = !isValidating && (!isOnDevice || modelReady)`; a failed validation keeps the user on the step with an error and never advances. `SRC` `OnboardingScreen.kt:ProviderConfigStep`. On-device (Gemma) has no credentials — the downloaded model **is** the config and `Continue` stays disabled until `modelReady`. `SRC` `OnboardingViewModel.observeModelDownload/validateAiProvider`.

Determinism of selection is preserved: candidates are id-sorted and tiered (Active+credentials → Ready+credentials → Active+configured → Ready+configured → …). `SRC` `ProviderManager.getBestProviderFor`.

---

## 3. Provider configuration graph

```
UI (OnboardingScreen / ProviderManagementScreen)
 → ViewModel (OnboardingViewModel / ProviderManagement VM)
   → ProviderRegistry.getProvider(id)        [what exists]
   → ProviderManager.validateProvider(id,cfg)[apply → onInitialize → checkHealth]
   → ProviderRepository.saveProviderConfig   [persist]
        → secrets  → SecretsManager (Tink AEAD)   key: provider_<id>_<key>
        → settings → provider_configurations table
 → ProviderManager.reconfigure(id,cfg)        [apply to live singleton]
 → app restart: AivanceApp.hydrateSavedProviderConfigs() re-applies on every launch
 → runtime consumption: ProviderManager.getBestProviderFor / getOnDeviceProviderFor / getRoutedProvider
```
`SRC` `OnboardingViewModel.buildProviderConfig` (secrets split by `FieldType.PASSWORD || isSensitive`), `AivanceApp.kt:hydrateSavedProviderConfigs`, `ProviderManager.reconfigure`.

### 3.1 Provider inventory (registered via Hilt `@IntoSet`)

| Type | IDs | Evidence |
|---|---|---|
| AI (7) | `anthropic`, `gemini`, `gemma` (on-device), `groq`, `ollama`, `openai`, `openrouter` | `SRC` `AiProvidersModule.kt`; `RT` onboarding listed Anthropic Claude / Gemma (On-device) / Google Gemini / Ollama (Local) / OpenAI |
| Job (15) | `adzuna`, `arbeitnow`, `apify`, `bayt`, `glassdoor`, `greenhouse`, `indeed`, `jobicy`, `lever`, `linkedin`, `naukri`, `remoteok`, `remotive`, `usajobs`, `ziprecruiter` (+ `RestJobProvider`) | `SRC` `JobProvidersModule.kt`, `*Provider.kt` |
| Enrichment (1) | `hunter` | `SRC` `EnrichmentProvidersModule.kt` |

### 3.2 Provider matrix

| Provider (type) | UI | Config | Saved | Validated | Registered | Runtime caller | Failure UI |
|---|---|---|---|---|---|---|---|
| anthropic / gemini / groq / openai / openrouter (AI) | ✅ selection+form | ✅ | ✅ secrets+settings | ✅ via `validateProvider` | ✅ | ✅ `getBestProviderFor(AI)` | ✅ inline `error` text |
| gemma (AI, on-device) | ✅ download panel | ✅ (model file) | ✅ | ✅ gated on `modelReady` | ✅ | ✅ `getOnDeviceProviderFor` | ✅ storage + download notice |
| ollama (AI, keyless local) | ✅ | ✅ base URL | ✅ | ✅ health check | ✅ | ✅ tier `isConfigured` | ✅ |
| 15 job providers | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ through job search orchestrator | ✅ |
| hunter (enrichment) | ✅ (skippable) | ✅ | ✅ | ✅ | ✅ | ⚠️ only via enrichment paths; **no UI caller verified** | ✅ |

Runtime note: with no configured provider the app logged the **provider health worker** reporting healthy providers/models on a *previous* build in an earlier session; in this session no provider was configured (Skip All), and the app remained stable. `RT`

### 3.3 Explicitly tested provider cases

| Case | Result |
|---|---|
| Missing key | Step does not advance; `error` surfaced (`friendlyValidationMessage`) |
| Invalid key | `checkHealth()` ≠ Ready/Active → `Result.Failure` → error text |
| Provider unavailable / offline | `ECONNREFUSED` handled; no crash (job search degrades) |
| Duplicate configuration | `saveProviderConfig` is per-`providerId` → idempotent overwrite |
| Switch provider | per-step drafts cleared on provider change (`aiConfigDraft.clear()`) |
| Restart / process death | `hydrateSavedProviderConfigs()` re-applies secrets `SRC`; gate state persisted `RT` |
| Delete configuration | Provider Management path; not exercised at runtime this pass → **UNKNOWN** |
| Capability mismatch | `resolveCapabilityFallbackChain` + `getRoutedProvider` order remote/local; not runtime-exercised → **UNKNOWN** |

---

## 4. Main navigation graph

**Implementation:** Navigation 3 (`androidx.navigation3.runtime.rememberNavBackStack`), **not** `NavHost`/`composable<>` (those return zero matches). One `NavBackStack<Destination>` per root workspace, plus a separate auth backstack.

Root tabs (`Destination.rootDestinations`): **Dashboard, Intelligence, Discovery, Pipeline, PrepStudio** — each keeps an independent backstack ("zero progress loss during workspace context switching"). `SRC` `AivanceNavGraph.kt:96-110`.

Routing logic:
- authenticated destination while unauthenticated → push `Destination.Auth`
- root destination → switch `activeWorkspace`
- auth destination → push onto auth backstack
- otherwise → workflow-aware hub switch (Ats/ResumeDetail/ResumeEngine/Intelligence → Intelligence; CoverLetter/JobComparison/DiscoverBySkill/RecruiterDashboard → Discovery; PrepStudio/LearnSkill → PrepStudio; Pipeline/TrackApplication → Pipeline) then push
- `BackHandler`: pop last if stack > 1, else return to Dashboard from a non-Dashboard workspace
- unmatched → `InvalidRouteScreen`
`SRC` `AivanceNavGraph.kt:120-180`, `:200-215`, `:430-436`.

### 4.1 Reachable destinations

`Splash, Welcome, Auth, Onboarding, ProviderSetup, Dashboard, Intelligence, Discovery, Pipeline, PrepStudio, Assistant, Analytics, IdentityHub, ResumeEngine(jobDescription?), Ats(jobDescription?, reportId?), CoverLetter(jobId?), JobDetails(jobId), CompanyDetail(companyId), ResumeDetail(resumeId), JobComparison, RecruiterDashboard(jobId), SavedJobs, TrackApplication(jobId), DiscoverBySkill(skill), LearnSkill(skill), Appearance, ProviderManagement, Notifications, PrivacyCenter, About, Resources`

### 4.2 Unreachable / dead

| Route | Status | Evidence |
|---|---|---|
| `JobComparison` | **registered + rendered but no entry point**, and the screen receives `jobs = emptyList()` hardcoded | `SRC` AivanceNavGraph.kt:393-396; `grep Destination\.JobComparison` → 0 `onNavigate` call sites (only enum/icon/label/mapping/`when`-render) |
| `InvalidRouteScreen` | fallback only | `SRC` AivanceNavGraph.kt:434 |

### 4.3 Deep links

`aivance://` and `https://aivance.app/`: `jobs/{id}`, `chat`, `interview`, `resume`, `app|dashboard`, `settings|profile|identity`, `saved`, `notifications`. Authenticated targets are gated behind auth at startup; `consumePending()` is one-shot. `SRC` `DeepLinkHandler.kt`. Warm-start stores `lastDeepLinkDestination` but **nothing consumes it** — `SRC` (0 readers) → partially wired.

---

## 5. Dashboard graph

`DashboardViewModel` — the single state owner:
```
combine(CareerStateEngine.state, _insightsRefresh)
  → mapLatest { GetCareerGraphInsightsUseCase() }   // graph read, off the write path
  → DashboardUiState
```
`SRC` `DashboardViewModel.kt:44-98`.

| Card / metric | Source | Real? | Persisted | Runtime @ zero data |
|---|---|---|---|---|
| greeting `Hello, {name}` | `state.profile.name` | ✅ real | Room `user_profiles` | `"Hello, "` (empty name) `RT` |
| designation | `state.profile.targetRole` | ✅ | Room | empty |
| **Career Score** | `state.growth.careerScore` ← `intelHub.careerScore` ∈ `CareerScoreEngine.calculateCompositeScore` | ⚠️ **fabricated baseline** | derived | **18** `RT` |
| **ATS Score** | `intelHub.dimensionScores["ATS_READINESS"] ?: snapshot ?: 0` | ✅ real | derived | 0 `RT` |
| Active Apps | `state.pipeline.activeApplications` (status == ACTIVE) | ✅ | Room `applications` | 0 `RT` |
| **Saved Jobs** | `state.discovery.savedJobsCount` = `applications.count { currentStageId == "SAVED" }` | ⚠️ **wrong table** | derived | 0 `RT` |
| next interview | `pipeline.upcomingInterviews.first().dateTime` ← **`application.dateApplied`** | ⚠️ **mislabeled** | derived | — |
| AI Tip | `state.recommendations.first().title` → `aiRecommendation = "AI Tip: <title>"` | ✅ real, nullable | Room `recommendations` (written only by AI-backed `RecommendationEngine` via weekly `AnalyticsSnapshotWorker`) | **null at zero data → hero shows static "The next step in your career journey."** (verified `RT` 2026-09-25) |
| Next Best Action | `NavigationWorkflowEngine.getRecommendedDestination(state)` | ✅ | computed | rendered `RT` |
| Career Graph / Skill Match | `GetCareerGraphInsightsUseCase` → `analyzeSkillGaps` | ⚠️ **degenerate at zero data** | Room `graph_nodes/edges` | **`100%` + "You demonstrate every skill your target jobs ask for 🎯"** `RT` |
| ~~`agentMissions` (3 hardcoded)~~ | **REMOVED** (commit 6b98396) — was fabricated, never rendered | — | — | — |
| ~~`activeTask`~~ | **REMOVED** (commit 6b98396) — was fabricated, never rendered | — | — | — |

`RT` dashboard traversal captured exactly: `ATS Score 0`, `Career Score 18`, `Active Apps 0`, `Saved Jobs 0`, `Building`, `Skill Match 100%`, `0 of 0 target-job skills demonstrated`.

---

## 6. Feature-by-feature user journeys

### Resume (Intelligence Hub) — **GREEN**
`IntelligenceHubScreen` → `IntelligenceHubViewModel` → `ResumeRepository` (+ ATS) → `ResumeDao`/`AtsDao` → Room. Empty state proven at runtime: "Your Resumes — Tap + to import a PDF or DOCX and build your first resume."

### ATS — **GREEN**
`AtsScreen` → ATS VM → `AtsRepository` → `AtsDao` → `ats_reports`. Runtime empty state: "No ATS scans yet … Run an ATS scan from the Resume Engine to see your match history." Writes a `ResumeEvent.AnalysisCompleted` event through `ResumeRepositoryImpl` (`SRC`).

### Jobs (Discovery) — **GREEN / YELLOW offline**
`JobsScreen` → `JobsViewModel` → `JobRepository` → job providers → `JobDao`. Runtime: full filter surface + "Unified search across providers". Offline = graceful failure, no crash.

### Tracker (Pipeline) — **GREEN**
`TrackerScreen` → `TrackerViewModel` → **`ApplicationWorkflowRepository`** (the canonical `applications` table) → `WorkflowDao`. Runtime: Kanban Board, Saved/Preparing columns, quota, "You have 0 active applications. Your interview conversion is 0%."

### Interview / Prep Studio — **GREEN**
`PrepStudioScreen` → `InterviewViewModel` → `InterviewRepository` → `InterviewDao`. Runtime: Practice/Learn/History/Question Bank/Readiness. Dispatches `InterviewEvent.Completed` (`SRC`).

### Cover Letter — **GREEN** (`CoverLetterScreen`, `CoverLetterRepository`, `CoverLetterDao`, AI provider generation, persistence, versions/sections).

### Assistant — **GREEN (with duplication)**
`AssistantViewModel` injects `AssistantRepository`, `GetAssistantResponseUseCase`, `CareerStateEngine`, `ContextEngine`, `IntentEngine`, `PromptOrchestrator`, `ProviderManager`, `ProviderRegistry` `SRC`. Two surfaces: the `Assistant` destination and a global `AssistantOverlay` bottom sheet carrying an `AssistantJobContext`. Runtime: the overlay is reachable from Saved Jobs ("Ask Assistant"); the destination is reachable from the dashboard Quick Actions.

### Recruiter / CRM — **YELLOW** (`RecruiterDashboard`, `RecruiterViewModel`, `RecruiterDao`, `crm/*` repositories present; entry only from `JobDetails`/`CompanyDetail`). **No approval→send path is wired** (see §12).

### Career Graph — **GREEN (read) / GREY (replay)** — see §7.

### Career Memory — **GREY** (fully dormant) — see §10.

### Agents — **GREY** (fully dormant) — see §11.

---

## 7. CareerGraph topology — two disjoint slices

```
ENTITY SLICE  (live)
CareerStateEngine.combine{...}  ──▶ CareerGraphEngine.buildGraph(profile,resumes,jobs,applications,interviews)
                                ──▶ CareerGraphEngine.persist(graph)
                                ──▶ CareerGraphRepositoryImpl.persist
                                ──▶ GraphDao.replaceEntityProjection  [@Transaction]
                                       clearEdges() + clearNodesExceptType("CAREER_EVENT") + upsert
READER: GetCareerGraphInsightsUseCase (dashboard) ──▶ analyzeSkillGaps(graph)

CAREER_EVENT SLICE  (no production writer)
RebuildCareerEventProjectionUseCase ──▶ CareerEventReplayEngine ──▶ CareerGraphRepositoryImpl.replaceEventProjection
                                     ──▶ GraphDao.replaceNodesOfType("CAREER_EVENT")  [@Transaction]
```

**M05 invariant verified (`SRC` `GraphDao.kt`):** `replaceEntityProjection` clears all edges + every node type **except** `CAREER_EVENT`; `replaceNodesOfType` clears exactly one type. The slices cannot erase each other.

**M05 invariant verified at runtime (`RT`):** on-device DB (v28, main + WAL scanned) contains the profile node identity `user_default` (entity slice **is** written) and **zero** `CAREER_EVENT` occurrences (replay slice is empty because nothing triggers it).

**Note:** `GraphDao.replaceGraph` (destructive whole-graph rewrite) has **no production caller** — only KSP-generated code and DAO tests reference it. `SRC` (0 call sites outside `GraphDao`).

### 7.1 Graph ownership matrix

| Graph / slice | Writer | Reader | Storage | Source of truth | Replay | UI | Status |
|---|---|---|---|---|---|---|---|
| Navigation graph | `AivanceNavGraph` | Compose | in-memory `NavBackStack` | navigation code | n/a | ✅ | GREEN |
| DI graph | Hilt modules | Hilt | build-time | modules | n/a | n/a | GREEN |
| Repository/data graph | `core:data` impls | ViewModels/UseCases | Room/network | Room | n/a | ✅ | GREEN |
| CareerGraph — **entity** | `CareerStateEngine` → `CareerGraphEngine` | `GetCareerGraphInsightsUseCase` | `graph_nodes`/`graph_edges` | `CareerStateEngine` | no | ✅ dashboard | GREEN (hot-path smell) |
| CareerGraph — **CAREER_EVENT** | `RebuildCareerEventProjectionUseCase` (**no caller**) | replay tests only | `graph_nodes` (type-filtered) | `career_event_log` | ✅ deliberate | ❌ | GREY |
| Event provenance | `CareerEventDispatcher` | `career_event_log` (audit) + `CareerEventBus` | Room | Room (log is audit-only) | ✅ | ❌ | GREEN audit / GREY consumer |
| Memory relationship graph | `CareerMemoryEngine` (**no caller**) | — | `career_memory_entries` | — | — | ❌ | GREY |
| Agent execution graph | `CareerAgentEngine` (**no caller**) | — | none | — | — | ❌ | GREY |
| UI state graph | per-screen ViewModel `StateFlow` | Compose | memory | ViewModel | n/a | ✅ | GREEN |
| Workflow/lifecycle graph | `NavigationWorkflowEngine` | Dashboard VM | computed | recommendations | n/a | ✅ | GREEN |

---

## 8. Database topology

**Room v28.** 48 entities, 20 DAOs, 28-step migration chain (`MIGRATION_1_2` … `MIGRATION_27_28` all registered in `DatabaseModule`). `SRC` `AivanceDatabase.kt:62`, `DatabaseModule.kt:33-59`, `schemas/.../28.json`.

Tables (28.json): `aivance_entities, companies, jobs, resumes, resume_versions, resume_sections, cover_letters, cover_letter_versions, cover_letter_sections, roadmaps, roadmap_steps, user_profiles, interview_sessions, interview_messages, interview_questions, interview_evaluations, ai_conversations, ai_messages, provider_configurations, analytics_events, saved_searches, job_descriptions, ats_reports, saved_jobs, viewed_jobs, search_history, recruiters, recruiter_contacts, outreach_drafts, communication_history, applications, application_stages, application_timeline, application_tasks, automation_rules, analytics_snapshots, recommendations, career_goals, assistant_conversations, assistant_messages, workflow_executions, audit_logs, users, graph_nodes, graph_edges, career_event_log, career_memory_entries`.

### 8.1 R1 baseline verified

| Claim | Evidence |
|---|---|
| `applications` is the only application table | `28.json` contains `applications`, **not** `job_applications`; `applications` has `salaryRange` |
| `job_applications` is gone on a real device | `RT` — main+WAL binary scan: **0** occurrences of `job_applications` |
| Migration executes | `RT` — device DB `user_version` went **27 → 28** on launch, no `SQLiteException`, no crash |
| Legacy layer deleted | `SRC` — `TrackerDao`, `JobApplicationEntity`, `JobApplicationWithDetails`, **both** `JobTrackerRepository` interfaces/impls, `TrackerModule` deleted; `FollowUpWorker`/`BackupExporter`/`BackupImporter` repointed |

### 8.2 Orphan / dormant tables (no production reader **and** no production writer)

| Table | DAO | Evidence |
|---|---|---|
| `aivance_entities` | `AivanceDao` | 0 production consumers of the DAO (`SRC`); only `AivanceFeatureDaoTest` |
| `audit_logs` | `AuditDao` | 0 production consumers (`SRC`); only `MigrationTest`. Nothing in the app writes an audit row — including the agent tier. |
| `automation_rules` | `WorkflowDao.getEnabledAutomationRules` | accessor **never invoked** (`SRC`) |
| `workflow_executions` | `AssistantDao.getWorkflows…` | accessor **never invoked** (`SRC`) |

### 8.3 Duplicate / competing persistence (confirmed)

| Concern | A | B | Both live? |
|---|---|---|---|
| Conversations | `ai_conversations` + `ai_messages` (`AiRepository` → `AiLocalDataSource` → `AiAnalyticsDao`) | `assistant_conversations` + `assistant_messages` (`AssistantRepository` → `AssistantDao`) | **YES** — A used by AI use cases, interview knowledge, resume improve, `PrivacyViewModel`; B used by `AssistantViewModel` |
| Repository interfaces with identical names | historically two `JobTrackerRepository` (resolved by R1) | — | resolved |

### 8.4 WAL / persistence

On-device: `aivance-database`, `-shm`, `-wal` all present after launch → WAL mode active. `RT`

---

## 9. Event topology

`SRC` `CareerEvent.kt` defines ~50+ types across `ResumeEvent, AtsEvent, JobEvent, ApplicationEvent, InterviewEvent, AgentEvent, ProviderEvent, AnalyticsEvent, CareerGoalEvent, …` with a versioned contract (`CareerEventContract.kt`, payload schema v1/v2 and stable entity identity per M04-A/C).

**Only three producers actually dispatch in production:**

| Producer | Event | Evidence |
|---|---|---|
| `ResumeRepositoryImpl:193` | `onResumeAnalysisCompleted` | `SRC` |
| `InterviewRepositoryImpl:185` | `onInterviewCompleted` | `SRC` |
| `WorkflowEngine:60` | `onApplicationStageChanged` | `SRC` |

Everything else (job saved/viewed, application created, ATS scan started/score changed, all `AgentEvent`s, resume created/updated, interview started) is **defined but never emitted**. `SRC` (0 `dispatch` call sites).

`CareerEventDispatcher.dispatch` does two fire-and-forget writes on its own scope: `eventBus.emit` and `eventLogRepository.append` (idempotent on event id, failure-isolated). `SRC` `CareerEventDispatcher.kt`.

Doc drift: the dispatcher KDoc still says *"no replay engine consumes the log yet"* while `CareerEventReplayEngine` (M04-B) exists — the log is **audit-only and non-canonical**; Room remains authoritative. `SRC`.

Runtime: `career_event_log` table exists on device. Slice contents not enumerated → **UNKNOWN** (not claimed).

---

## 10. Memory / context topology

| Component | Production caller | UI caller | Persistence | Status |
|---|---|---|---|---|
| `ContextEngine` | ✅ `AssistantViewModel` | via Assistant | computes from `CareerStateEngine.state` | GREEN |
| `PromptOrchestrator` / `IntentEngine` | ✅ `AssistantViewModel` | via Assistant | — | GREEN |
| `AssistantContextEngine` | ✅ `GetAssistantResponseUseCase` | via Assistant | reads Room | GREEN |
| `AiContextEngine2` | ❌ **0 consumers** (`@Inject constructor()` only) | ❌ | none | **GREY — dead code** |
| `CareerMemoryEngine` + `CareerMemoryRepository(Impl)` + `CareerMemoryDao` + `career_memory_entries` | ❌ **0 production callers/readers** | ❌ | table exists | **GREY** |

**Three context engines coexist** for one Assistant surface — a genuine duplication (§16).

---

## 11. Agent topology

```
CareerAgentEngine ──▶ HumanApprovalGate ──▶ AgentActionExecutor (DefaultAgentActionExecutor)
        │                                        
        └──▶ emits AgentEvent.ActionProposed / Approved / Executed
AgentModule binds AgentActionExecutor
```
`SRC` `CareerAgentEngine.kt`, `HumanApprovalGate.kt`, `AgentExecutor.kt`, `AgentModule.kt`.

| Component | Production caller | Status |
|---|---|---|
| `CareerAgentEngine` | ❌ only self + `CareerAgentEngineTest` | GREY |
| `HumanApprovalGate` | ❌ only `CareerAgentEngine` (itself uncalled) + test | GREY |
| `AgentActionExecutor` / `DefaultAgentActionExecutor` | ❌ only the DI binding | GREY |
| `AutonomousApplyUseCase` | ❌ **0 production call sites** | GREY (dormant) |
| `OutreachAgentUseCase` | ❌ 0 | GREY |
| `FollowUpAgentUseCase` | ❌ 0 (`FollowUpWorker` does **not** call it) | GREY |

**Invariant state:** no agent side-effecting path is reachable, therefore nothing currently bypasses `HumanApprovalGate`. This is *safe by omission*, not by enforcement — the gate has **zero production coverage** (`SRC`/`TEST`).

---

## 12. Runtime VM evidence

| Item | Value | Evidence |
|---|---|---|
| Device | `emulator-5554`, `sdk_phone64_x86_64` | `RT` |
| Android | **14** (API **34**) | `RT` |
| ABI | `x86_64` | `RT` |
| Package | `com.bangersoul.aivance.debug` v1.0.0-debug | `RT` |
| APK under test | **locally rebuilt** `app-x86_64-debug.apk` (`:app:assembleDebug` → `BUILD SUCCESSFUL in 3m 15s`) | `RT` |
| Launch | `MainActivity` resumed, **no FATAL / AndroidRuntime exception / ANR** | `RT` |
| Pre-existing DB | `user_version = 27` before install | `RT` |
| Post-migration DB | `user_version = 28`, no `SQLiteException` | `RT` |
| Startup workers | `AivanceApp: All 10 periodic workers scheduled + security migration queued`; `SecurityMigrationWorker` → `scan complete — migrated 0 secret(s) from 0 provider config(s)` | `RT` |
| Traversed path | Welcome → Get Started → Auth(Sign In/Create Account) → email Continue → **Choose AI Provider** → Skip All → **Dashboard** | `RT` |
| Tabs rendered | Dashboard, Intelligence, Discovery, Pipeline, Prep Studio — all with real empty states | `RT` |
| Dashboard values @ zero data | ATS 0, Career Score 18, Active Apps 0, Saved Jobs 0, Skill Match 100% | `RT` |
| Prep Studio values @ zero data | Interview Readiness 1%, 0.2 hrs, 1 | `RT` |
| Tooling note | `crave.exe` **not on PATH** despite `crave.conf`; the Gradle build was run locally | `RT` |

Not exercised this pass (→ **UNKNOWN**, not claimed PASS): record CRUD, deep links on device, invalid-provider UI path, provider deletion, enrichment runtime path, Assistant round-trip, ARM ABIs.

---

## 13. Process-death evidence

`adb shell am force-stop com.bangersoul.aivance.debug` → `monkey … LAUNCHER` → app resumed **directly on Dashboard** (no Welcome, no re-onboarding). Persistence of `onboardingCompleted` + session across process death: **PASS**. `RT`

Backstack was **not** restored across process death (`rememberNavBackStack` is `remember`, not `rememberSaveable`) — previous screen position is lost; the app returns to the root workspace. `SRC` `AivanceNavGraph.kt:96` (INF on the save behaviour, consistent with observed Dashboard landing).

---

## 14. Feature status matrix

| Feature | UI | Navigation | VM | UseCase | Repository | DAO/API | Persistence | Runtime | Outcome | Class |
|---|---|---|---|---|---|---|---|---|---|---|
| Splash / startup gate | ✅ | ✅ | `AuthenticationViewModel` | — | DataStore/Room | `UserDao` | ✅ | ✅ | real, local-only | **GREEN** |
| Welcome | ✅ | ✅ | (stateless) | — | — | — | — | ✅ | real | **GREEN** |
| Auth (email) | ✅ | ✅ | `AuthViewModel` | `TrackEventUseCase` | — | `UserDao` | ✅ | ✅ | real local session | **GREEN** |
| Auth (Google) | ✅ | ✅ | `AuthViewModel` | — | Firebase | — | ✅ | ⚠️ needs web client id | actionable error | **YELLOW** |
| Onboarding AI/Job/Enrichment | ✅ | ✅ | `OnboardingViewModel` | — | `ProviderRepository` | `provider_configurations` + SecretsManager | ✅ | ✅ | real, **bypassable** | **YELLOW** |
| Provider validation | ✅ | ✅ | ✅ | — | — | `ProviderManager` | ✅ | ✅ step-level | real, deterministic | **GREEN** |
| Provider management | ✅ | ✅ | ✅ | — | `ProviderRepository` | ✅ | ✅ | not exercised | — | **YELLOW** |
| Dashboard | ✅ | ✅ | `DashboardViewModel` | `GetCareerGraphInsights`, `RecordSkillGapEngagement`, `TrackEvent` | `CareerStateEngine` | Room | derived | ✅ | 2 fabricated metrics | **YELLOW** |
| Intelligence Hub / Resume | ✅ | ✅ | `IntelligenceHubViewModel` | resume UCs | `ResumeRepository` | `ResumeDao` | ✅ | ✅ empty state | real | **GREEN** |
| Resume Engine | ✅ | ✅ | `ResumeEngineViewModel` | ✅ | `ResumeRepository` | ✅ | ✅ | not exercised | — | **GREEN** |
| Resume Detail | ✅ | ✅ (no caller found from Hub list) | `ResumeDetailViewModel` | — | `ResumeRepository` | `ResumeDao` | ✅ | not exercised | route reachable only via deep link/`Destination` push | **YELLOW** |
| ATS | ✅ | ✅ | ATS VM | ✅ | `AtsRepository` | `AtsDao` | ✅ | ✅ empty state | real | **GREEN** |
| Cover Letter | ✅ | ✅ | ✅ | ✅ | `CoverLetterRepository` | `CoverLetterDao` | ✅ | not exercised | — | **GREEN** |
| Jobs / Discovery | ✅ | ✅ | `JobsViewModel` | ✅ | `JobRepository` | providers + `JobDao` | ✅ | ✅ empty state | real; offline degrades | **GREEN** |
| Job Details | ✅ | ✅ | `JobDetailsViewModel` | ✅ | `JobRepository`/`ApplicationWorkflowRepository` | ✅ | ✅ | not exercised | — | **GREEN** |
| Company Detail | ✅ | ✅ | `CompanyDetailViewModel` | ✅ | `CompanyCatalogRepository` | `CompanyDao` | ✅ | not exercised | — | **GREEN** |
| Saved Jobs | ✅ | ✅ | ✅ | ✅ | `JobRepository` | `saved_jobs` | ✅ | not exercised | — | **GREEN** |
| Pipeline / Tracker | ✅ | ✅ | `TrackerViewModel` | ✅ | **`ApplicationWorkflowRepository`** | `WorkflowDao`/`applications` | ✅ | ✅ empty state | real (post-R1) | **GREEN** |
| Application save flow | ✅ | ✅ | (JobDetails) | ✅ | `ApplicationWorkflowRepository` | `applications` | ✅ | not exercised | — | **GREEN** |
| Prep Studio / Interview | ✅ | ✅ | `InterviewViewModel` | ✅ | `InterviewRepository` | `InterviewDao` | ✅ | ✅ empty state | readiness disagrees with analytics | **YELLOW** |
| Assistant | ✅ | ✅ | `AssistantViewModel` | `GetAssistantResponseUseCase` | `AssistantRepository` **and** `AiRepository` | `assistant_*` **and** `ai_*` | ✅ | ⚠️ needs provider | duplicated stacks | **YELLOW** |
| Recruiter discovery / CRM | ✅ | ✅ | `RecruiterViewModel` | ✅ | `crm/*` | `RecruiterDao` | ✅ | not exercised | no approval→send path | **YELLOW** |
| Analytics | ✅ | ✅ | `AnalyticsViewModel` | ✅ | `AnalyticsRepository` | `AiAnalyticsDao`/`AnalyticsDao` | ✅ | not exercised | — | **GREEN** |
| Identity Hub / Settings | ✅ | ✅ | ✅ | ✅ | `SettingsRepository`/`ApplicationPreferencesRepository` | Room/DataStore | ✅ | not exercised | — | **GREEN** |
| Career Graph (entity read) | ✅ | ✅ | `DashboardViewModel` | `GetCareerGraphInsightsUseCase` | `CareerGraphRepository` | `GraphDao` | ✅ `graph_nodes/edges` | ✅ 100% @ zero data | degenerate empty state | **ORANGE** |
| Event log (audit) | ❌ | ❌ | — | — | `CareerEventLogRepository` | `CareerEventLogDao` | ✅ | 3 producers only | audit-only | **ORANGE** |
| Event replay | ❌ | ❌ | — | `RebuildCareerEventProjectionUseCase` | `CareerEventReplayEngine` | `GraphDao` | ✅ (tests) | **no production trigger** | unreachable | **GREY** |
| Career Memory | ❌ | ❌ | — | — | `CareerMemoryRepository` | `CareerMemoryDao` | ✅ table only | none | dormant | **GREY** |
| Agent tier | ❌ | ❌ | — | 3 agent UCs | — | — | ❌ | none | dormant (safe) | **GREY** |
| `AiContextEngine2` | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | none | dead code | **BLACK** |
| JobComparison | ✅ (screen) | ❌ **no entry** | ❌ | ❌ | ❌ | ❌ | ❌ | **unreachable**; `jobs=emptyList()` | dead route | **BLACK** |
| `aivance_entities` | ❌ | ❌ | ❌ | ❌ | ❌ | `AivanceDao` | ✅ table only | none | orphan | **BLACK** |
| `audit_logs` | ❌ | ❌ | ❌ | ❌ | ❌ | `AuditDao` | ✅ table only | none | orphan | **BLACK** |
| `automation_rules`, `workflow_executions` | ❌ | ❌ | ❌ | ❌ | ❌ | accessors unused | ✅ tables only | none | orphan | **BLACK** |
| `GraphDao.replaceGraph` | ❌ | ❌ | ❌ | ❌ | — | unused in prod | — | none | dead method | **BLACK** |
| `Destination.Resources` | ✅ | ✅ | ✅ (`RemoteResourcesScreen`) | — | — | — | — | reachable from Identity Hub/About | — | **GREEN** |

---

## 15. Navigation matrix

| From | Action | To | Route | VM | Data | Runtime Result | Back |
|---|---|---|---|---|---|---|---|
| Splash | auth settled | Dashboard / Welcome | `Destination.Dashboard`/`Welcome` | `AuthenticationViewModel` | DataStore | ✅ | — |
| Welcome | Get Started | Auth | `Destination.Auth` | `AuthViewModel` | — | ✅ `RT` | ✅ |
| Welcome | **Skip** | → `Dashboard` is authenticated → pushes `Auth` | `Destination.Auth` | — | — | ⚠️ lands on sign-in, not dashboard | ✅ |
| Auth | Create Account + Continue (email) | ProviderSetup | `Destination.ProviderSetup` | `AuthViewModel` | `UserDao` + DataStore | ✅ `RT` (Choose AI Provider) | ✅ |
| Auth | Sign In + Continue (email, existing) | Dashboard via `CheckAuth` | — | `AuthenticationViewModel` | DataStore | not exercised | — |
| Auth | Continue with Google | ProviderSetup / Dashboard | — | Firebase | — | not exercised (needs web client id) | — |
| Onboarding | Skip All | Complete → CheckAuth → Dashboard | — | `OnboardingViewModel` | DataStore | ✅ `RT` (**zero providers**) | — |
| Onboarding | Select AI → Configure → Validate | Choose Job | — | `OnboardingViewModel`/`ProviderManager` | `provider_configurations` | not exercised (needs key) | ✅ |
| Onboarding | Finish | Dashboard | — | — | — | not exercised | — |
| Dashboard | tab Intelligence | Intelligence | ✅ | `IntelligenceHubViewModel` | `ResumeRepository` | ✅ `RT` | own stack |
| Dashboard | tab Discovery | Discovery | ✅ | `JobsViewModel` | providers | ✅ `RT` | own stack |
| Dashboard | tab Pipeline | Pipeline | ✅ | `TrackerViewModel` | `applications` | ✅ `RT` | own stack |
| Dashboard | tab Prep Studio | PrepStudio | ✅ | `InterviewViewModel` | `InterviewRepository` | ✅ `RT` | own stack |
| Dashboard | Upload Resume card | Intelligence | ✅ | — | — | ✅ (card present) | ✅ |
| Dashboard | Analytics | Analytics | ✅ | `AnalyticsViewModel` | `AnalyticsRepository` | not exercised | ✅ |
| Dashboard | Identity Hub | IdentityHub | ✅ | ✅ | Room/DataStore | not exercised | ✅ |
| Dashboard | Assistant | Assistant | ✅ | `AssistantViewModel` | `AssistantRepository` | not exercised | ✅ |
| Dashboard | skill chip → Discover/Learn | DiscoverBySkill / LearnSkill | ✅ | ✅ + `RecordSkillGapEngagementUseCase` | `skill_gap` progress | not exercised | ✅ |
| Discovery | job | JobDetails | ✅ | `JobDetailsViewModel` | `JobRepository` | not exercised | ✅ |
| JobDetails | recruiters | RecruiterDashboard | ✅ | `RecruiterViewModel` | `crm/*` | not exercised | ✅ |
| JobDetails | cover letter | CoverLetter | ✅ | ✅ | `CoverLetterRepository` | not exercised | ✅ |
| JobDetails | company | CompanyDetail | ✅ | `CompanyDetailViewModel` | `CompanyCatalogRepository` | not exercised | ✅ |
| SavedJobs | assistant for job | Assistant overlay | `AppShellState.toggleAssistant` | `AssistantViewModel` | `AssistantJobContext` | not exercised | dismiss |
| SavedJobs | create resume / track | ResumeEngine / TrackApplication | ✅ | ✅ | ✅ | not exercised | ✅ |
| **anywhere** | **compare jobs** | **JobComparison** | **NO ENTRY POINT** | — | — | **unreachable** | — |
| any | unmatched destination | InvalidRouteScreen | ✅ | — | — | fallback | ✅ |

---

## 16. Simplification / duplicate-infrastructure matrix

| Existing | Evidence | Why redundant | Safe action | Dependency | Risk |
|---|---|---|---|---|---|
| `AiContextEngine2` | 0 consumers (`SRC`) | third context engine for the same Assistant surface | Remove, or fold its PII-redaction + token budget into `ContextEngine` | none | Low (dead) |
| `CareerMemoryEngine` + repo/dao/table | 0 callers/readers | built with no consumer | Keep deferred **or** remove (product call) | M07 decision | Low |
| Agent tier (`CareerAgentEngine`, `HumanApprovalGate`, `AgentActionExecutor`, 3 agent UCs) | 0 external refs | built with no consumer | Keep deferred behind approval-gated milestone | roadmap | Low now, high if wired unsafely |
| `RebuildCareerEventProjectionUseCase` | tests only | no trigger exists | Keep; add an explicit maintenance trigger later | none | Low |
| `GraphDao.replaceGraph` | no prod caller | superseded by `replaceEntityProjection` | Remove or keep for reset tooling | DAO tests | Low |
| `aivance_entities` + `AivanceDao` | 0 consumers | orphan table+DAO from scaffolding | Remove (with migration) | schema | Low |
| `audit_logs` + `AuditDao` | 0 consumers | nothing writes audit rows | **Wire** (agent/approval audit) or remove | agent tier | Low |
| `automation_rules`, `workflow_executions` | accessors unused | orphan tables | Remove (with migration) | schema | Low |
| `ai_conversations`/`ai_messages` vs `assistant_conversations`/`assistant_messages` | both live | two conversation sources of truth | Consolidate onto one | Assistant + AI UCs + Privacy | **Medium** |
| `AnalyticsDao` vs `AiAnalyticsDao` | both used | two analytics DAOs | Evaluate for merge | many readers | Medium |
| `Destination.JobComparison` + `JobComparisonScreen` | no entry; `jobs=emptyList()` | dead route + dead param | Wire a selection UI or remove | Jobs multi-select | Low |
| `DeepLinkHandler.lastDeepLinkDestination` | 0 readers | warm-start path never consumed | Wire or remove | nav | Low |
| 10 periodic workers | enqueued at startup | some have no consumer (e.g. follow-up → no agent) | Audit each worker's effect | WorkManager | Low |

---

## 17. Competing sources of truth

| Domain | Competing sources | Evidence | Consequence |
|---|---|---|---|
| Interview Readiness | `AnalyticsRepositoryImpl.calculateReadiness` (**defaults to 75 with zero sessions**) vs `InterviewViewModel.calculateReadiness` | `SRC` both; `RT` Dashboard 18 vs Prep Studio 1% | Same concept shows two different numbers |
| Career/overall score | `CareerScoreEngine` composite **with hardcoded 75 readiness** | `SRC`; `RT` 18 at zero data | Fabricated non-zero score |
| Conversations | `ai_*` vs `assistant_*` | `SRC` | Split history; two DAOs |
| Assistant context | `ContextEngine` / `AssistantContextEngine` / `AiContextEngine2` | `SRC` | Three assemblers, one surface |
| Applications | **resolved by R1** — `applications` only | `SRC` + `RT` (0 `job_applications`) | — |
| Saved jobs | `saved_jobs` table (used by graph) vs `applications.stageId == "SAVED"` (used by dashboard metric) | `SRC` `CareerStateEngine` | Dashboard metric reads the wrong table |
| Events | `career_event_log` (audit) vs Room (authoritative) | `SRC` | Correct by design — must not become canonical |

---

## 18. Broken / incomplete flows

| ID | Flow | Problem | Evidence | Class |
|---|---|---|---|---|
| B1 | Provider gate | Not binding — `Skip All` and `updateOnboardingCompleted(true)` at email-continue both permit Dashboard with zero providers | `SRC` + `RT` | **C (architecture/contract)** |
| B2 | Dashboard Career Score | Non-zero fabricated baseline (hardcoded readiness 75 → 18) | `SRC` + `RT` (18) | **C** |
| B3 | Dashboard Skill Match | Shows **100%** + "you demonstrate every skill" with zero target jobs (`matchRatio = 1.0f` when `totalTarget == 0`) | `SRC` `CareerGraphEngine.analyzeSkillGaps` + `RT` | **C** |
| B4 | Dashboard Saved Jobs | Metric derived from `applications.stageId == "SAVED"`, not `saved_jobs` | `SRC` | **C** |
| B5 | Dashboard next interview | `dateTime` populated from `application.dateApplied` | `SRC` | **C** |
| B6 | Dashboard agent missions/task | Hardcoded fabricated constants **and** never rendered | `SRC` (0 refs in `DashboardScreen`) + `RT` (absent) | **D (dead + fabricated)** |
| B7 | `JobComparison` | Reachable only by code that does not exist; receives `jobs = emptyList()` | `SRC` | **D** |
| B8 | Interview readiness | Two formulas disagree | `SRC` + `RT` | **C** |
| B9 | Replay | `RebuildCareerEventProjectionUseCase` has no production trigger → `CAREER_EVENT` slice is permanently empty | `SRC` + `RT` (0 occurrences) | **E (deferred)** |
| B10 | Agent approval | `HumanApprovalGate` has zero production coverage; agent side effects unreachable | `SRC` | **E (safe by omission)** |
| B11 | Audit trail | Nothing anywhere writes `audit_logs` | `SRC` | **E** |
| B12 | Deep-link warm start | `lastDeepLinkDestination` never consumed | `SRC` | **D** |
| B13 | Backstack restore | Root backstack not persisted across process death | `SRC` (INF) | **Y (minor UX)** |
| B14 | `CareerStateEngine` hot path | Full graph rebuild + transactional rewrite on **every** state emission (12 combined flows) | `SRC` `CareerStateEngine.kt:96-104` | **C (performance/ownership)** |
| B15 | Welcome "Skip" | Routed to `Auth`, not Dashboard (authenticated-destination rule) | `SRC` | **Y** |

## 19. Missing product contracts

1. **No enforced "provider configured ⇒ product access" invariant** (B1).
2. **No defined empty-state semantics for derived metrics** (B2, B3) — zero data must not read as 18 or 100%.
3. **No single owner for "interview readiness"** (B8).
4. **No replay activation contract** (maintenance trigger undefined) (B9).
5. **No approval/audit contract for agent actions** (B10, B11).
6. **No conversation-history contract** (§17).
7. **No multi-select → compare contract** for `JobComparison` (B7).

## 20. Minimal stable architecture

### KEEP
`MainActivity` + `AivanceNavGraph` (Nav3, 5 root backstacks) · `AuthenticationViewModel` gate · `Auth`/`Onboarding`/`ProviderManager` validation stack · `CareerStateEngine` as the single state owner · **entity** graph slice `CareerStateEngine → CareerGraphEngine → CareerGraphRepository → GraphDao.replaceEntityProjection` · `GetCareerGraphInsightsUseCase` · `career_event_log` (audit) + `CareerEventDispatcher` · `ProviderManager` / `ProviderRegistry` / `SecretsManager` (Tink) · all GREEN features · 10 periodic workers · `ProviderManager.getBestProviderFor` determinism · **`applications` as the single application table (R1, v28)**.

### REPAIR
Dashboard metric integrity (B2, B3, B4, B5) · single readiness formula (B8) · provider gate binding (B1) · move graph persistence off the state hot path (B14).

### CONSOLIDATE
Three context engines → one (keep `ContextEngine`, absorb `AiContextEngine2`'s redaction/budget, retire the third) · two conversation stacks → one · two analytics DAOs → evaluate merge · JobComparison: wire or remove (B7).

### DEFER
Event replay activation + maintenance trigger (B9) · Career Memory (M07, after ownership decisions) · Agent tier **behind an enforced `HumanApprovalGate` + audit** (B10, B11) · deep-link warm start (B12).

### REMOVE (only after a migration + test)
`AiContextEngine2` · `aivance_entities` + `AivanceDao` · `automation_rules` + `workflow_executions` accessors · `GraphDao.replaceGraph` · `DeepLinkHandler.lastDeepLinkDestination` · `DashboardUiState.agentMissions`/`activeTask` (B6) · `JobComparisonScreen` **if** not wired.

## 21. Proposed reconstruction order

| Gate | Scope | Depends on | Reason |
|---|---|---|---|
| **R2** | Move entity-graph persistence off the `CareerStateEngine` hot path (debounced/explicit projection) | none | B14 is the only structural perf/ownership smell on the live path |
| **R3** | Dashboard metric integrity: kill fabricated baselines (readiness default), fix `Skill Match` zero-data, `Saved Jobs` source, `upcomingInterviews` label; delete `agentMissions`/`activeTask` | R2 (same owner) | User-visible correctness; runtime-confirmed wrong values |
| **R4** | Provider-gate binding: stop setting `onboardingCompleted` at email-continue; make the provider invariant explicit | none | B1 — the product contract |
| **R5** | Context/conversation consolidation (one engine, one history) | Assistant behaviour freeze | removes 2 competing sources |
| **R6** | Training wheels off dormant infra: explicit decision + docs for memory/agent/replay; wire `audit_logs` if agents are committed | R5 | keeps GREY honest |
| **R7** | JobComparison: wire (multi-select) or remove | R3 | B7 |
| (later) | Replay activation trigger, M07 memory rehydration, agent wiring **only** through `HumanApprovalGate` | all above | Do not rehydrate onto unresolved ownership |

## 22. Explicit items NOT to touch yet

- `career_event_log` semantics (audit-only, never canonical) — **do not** promote to a source of truth.
- M05 slice ownership (`replaceEntityProjection` vs `replaceNodesOfType`) — **verified intact; do not touch**.
- `ProviderManager` selection determinism and `friendlyValidationMessage` mapping.
- `SecretsManager`/Tink encryption, `SecurityMigrationWorker`, `provider_configurations` secret removal.
- `applications` / `job_applications` history — **R1 is the baseline; do not resurrect `job_applications`**.
- `HumanApprovalGate` bypass — no agent path may be wired without it.
- The accepted fixes A–F and the M04–M06 replay/graph contracts.

---

## Appendix A — File inventory touched by the audit (read-only)

`app/{MainActivity,AivanceApp}.kt`, `navigation/{AivanceNavGraph,AivanceAppShell,Destination,DeepLinkHandler}.kt`,
`feature/profile/{OnboardingViewModel,OnboardingScreen,AuthViewModel,AuthScreen,AuthenticationViewModel,WelcomeScreen,SplashScreen}.kt`,
`feature/dashboard/{DashboardViewModel,DashboardScreen}.kt`, `feature/assistant/AssistantViewModel.kt`, `feature/tracker/TrackerViewModel.kt`,
`core/domain/**/{CareerStateEngine,CareerGraphEngine,CareerEventDispatcher,CareerEventReplayEngine,CareerMemoryEngine,AiContextEngine2,ContextEngine,AssistantContextEngine,CareerAgentEngine,HumanApprovalGate,AgentModule,CareerIntelligenceEngine,CareerScoreEngine}.kt`,
`core/data/repository/{CareerGraphRepositoryImpl,AnalyticsRepositoryImpl}.kt`, `core/sdk/infrastructure/ProviderManager.kt`,
`core/database/{AivanceDatabase.kt,dao/*,model/*,schemas/.../28.json}`, `core/util/{BackupExporter,BackupImporter}.kt`.

## Appendix B — Commands used for runtime evidence

```
git rev-parse --short HEAD ; git status --porcelain ; git tag
adb devices -l ; adb shell getprop ro.build.version.sdk|ro.product.cpu.abi
./gradlew :app:assembleDebug            # BUILD SUCCESSFUL in 3m 15s
adb install -r app/build/outputs/apk/debug/app-x86_64-debug.apk
adb exec-out run-as <pkg> cat databases/aivance-database > db.bin   # user_version 27 → 28
adb shell monkey -p <pkg> -c android.intent.category.LAUNCHER 1
adb shell uiautomator dump /sdcard/ui.xml ; adb shell input tap <x> <y>
adb shell am force-stop <pkg>           # process death → relaunch → Dashboard
```
