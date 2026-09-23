# AiVance Project State

> **Status: PRODUCTION READY** — v1.0.0 completed all 14 phases. Post-launch hardening through v1.0.2 active. Repository frozen for contracts; hotfixes + backward-compat additions only.

## Current Architecture
- **Paradigm**: Clean Architecture, SOLID Principles, Offline-First.
- **Pattern**: MVVM with Repository pattern.
- **Dependency Injection**: Hilt.
- **UI**: Jetpack Compose with Material Design 3.
- **Concurrency**: Kotlin Coroutines & Flow.
- **Data Persistence**: Room (v26) & DataStore.
- **Background Tasks**: WorkManager.
- **Provider System**: Plug-and-play Provider SDK architecture for AI, Job, and Enrichment services.
- **Security**: Centralized on-device encryption (AES-GCM via Google Tink) and Keystore-backed secrets management.

## Current Modules
### Core
- `:core:sdk`: Base infrastructure for providers, status, and lifecycle management.
- `:core:common`: Domain models, security wrappers (`EncryptedString`), and results.
- `:core:database`: Room database implementation (v25), encrypted type converters.
- `:core:data`: Repository implementations and local/remote bridges.
- `:core:domain`: Business logic, UseCases, and capability orchestration.
- `:core:ai-providers`: Concrete implementations for Gemini, Claude, etc.
- `:core:job-providers`: Concrete implementations for LinkedIn, Indeed, etc. + free global engines.
- `:core:enrichment-providers`: Hunter.io integration for recruiter discovery.
- `:core:designsystem`: Reusable Compose components, themes, and spacing.
- `:core:network`: Retrofit setup, security utilities.
- `:core:datastore`: Secure secret storage and preferences.
- `:core:util`: Utility classes, including `EncryptionService`.

### Features
- `:feature:dashboard`: Unified home view with career progress overview.
- `:feature:assistant`: Intelligent orchestration and conversational interface.
- `:feature:profile`: User settings, account management, and **Privacy Center**.
- `:feature:jobs`: Job discovery, aggregation, and caching.
- `:feature:resume`: Resume builder, AI parsing, and version management.
- `:feature:ats`: Semantic matching engine and match reports.
- `:feature:tracker`: Career pipeline management and application tracking.
- `:feature:interview`: Mock interviews and AI evaluation engine.
- `:feature:coverletter`: Sectional AI cover letter generation.
- `:feature:recruiter`: Recruiter CRM and AI outreach generation.
- `:feature:analytics`: Career Intelligence and Insights dashboard.

### App & Navigation
- `:app`: Application entry point, Hilt setup, WorkManager automation, release signing.
- `:navigation`: Central NavGraph (Type-safe), AppShell.

## Current Provider Support
- **AI Providers**: Google Gemini, Anthropic Claude, Groq, OpenRouter, OpenAI, Ollama.
- **Job Providers**: LinkedIn, Indeed, Greenhouse, Lever, RemoteOK, Remotive, Apify, **Arbeitnow** (free, EU/Germany), **Jobicy** (free, global remote), **Adzuna** (free tier, 16 countries), **USAJobs** (free, US federal).
- **Enrichment Providers**: Hunter.io (real domain search + email verification).

## Database & API
- **Room Version**: 26.
- **Latest Migration**: `MIGRATION_25_26` (Career Knowledge OS foundation — strictly additive: adds `graph_nodes`, `graph_edges`, `career_event_log`, `career_memory_entries` and their indices; no existing table or row is altered). Backs the persisted Career Graph, durable event log, and Career Memory. **The Room database remains the single source of truth. `career_event_log` is durable append-only *audit* persistence — NOT an authoritative state-reconstruction mechanism. Event replay engine: NOT YET IMPLEMENTED** (nothing consumes the log for replay yet; that is milestone M04).
- **Previous Migration**: `MIGRATION_24_25` (drops the legacy `resume_analyses` table — completes T-04, the AtsReport migration).
- **Previous Security Migration**: `MIGRATION_19_20` (Security Hardening — audit_logs table, removed `apiKey` column from `provider_configurations`).
- **Encryption**: AES-GCM (Tink) for PII (emails, resume text, outreach content).
- **API integrations**: Firebase AI SDK, Retrofit, OkHttp.

## Feature Completion Status
| Feature | Status | Completion % |
| :--- | :--- | :--- |
| Provider Platform | Completed | 100% |
| Intelligent Onboarding | Completed | 100% |
| Resume Engine | Completed | 100% |
| ATS Engine | Completed | 100% |
| Job Search (Unified) | Completed | 100% |
| Recruiter Platform | Completed | 100% |
| Cover Letter Engine | Completed | 100% |
| Interview Engine | Completed | 100% |
| Application Workflow | Completed | 100% |
| Analytics Platform | Completed | 100% |
| AI Career Assistant | Completed | 100% |
| Security & Privacy | Completed | 100% |

## Post-Launch Additions (2026-08-06 to 2026-08-10)
- **v1.0.1 E2E audit**: 10 functional bugs fixed (Google Sign-In, Resume Engine, Jobs, Prep Studio, Assistant fallback).
- **On-device Gemma**: `DeviceCapabilityProvider` gate, confirmation dialog with exact size, compact model fallback, `GemmaModelDownloadWorker` (resumable, Range-resume, WorkManager).
- **Offline AI fallback**: `GetAssistantResponseUseCase` → cloud → on-device Gemma → Copilot.
- **Claude provider**: added to `ProviderRefreshWorker`, `GetAvailableModelsUseCase`, `AiSettingsViewModel`.
- **Full i18n + Hindi**: all 12 feature modules extracted to string resources; `values-hi/` complete.
- **Feature interlinking**: Saved Jobs → Resume Engine / Tracker / Assistant with `AssistantJobContext`.
- **Analytics self-heal**: `AnalyticsRepositoryImpl.getSnapshots()` Mutex-guarded baseline guarantee.
- **Security audit**: 20/20 `security_scan.py` checks pass; weekly pin-drift CI workflow live.
- **T-03 provider factory path**: typed `ProviderFactory.createProvider(ProviderConfiguration)` SDK path; `adzuna`/`usajobs` factory bindings registered; Provider Management renders metadata-driven credential fields with secret/settings routing.
- **AI job-fit scoring (R-04)**: `ScoreJobFitUseCase` (batched, cached, fence-tolerant LLM scoring vs the user profile) merged with the rule-based `JobFitScorer` fallback; discovery cards show AI-upgraded fit badges and a "Best match" sort chip.
- **JSON Resume interop (R-03)**: `JsonResumeConverter` moved into `core:domain`; `ExportResumeUseCase` JSON export now emits the standard JSON Resume schema (previously truncated ad-hoc JSON) and round-trips back through the importer; import/export UI in the Resume Engine; round-trip unit tests.
- **Remote-company catalog (R-02)**: bundled snapshot of 882 remote-friendly companies (remoteintech/remote-jobs, ISC) served as a `core:data` asset; discovery filters by remote policy + tech stack (`CompanyCatalog.accepts`); company detail enriched with policy/size/region/tech/careers; `refresh_company_catalog.py` regenerates the snapshot.
- **STAR prep packs (R-05)**: `GenerateStarPackUseCase` (streaming AI via `AiRepository`, template fallback in `core:domain`) + `persistPackQuestions` so pack answers record against real session rows; Prep Studio Practice tab generates and practices role-specific STAR packs.
- **Remote-work resources hub (R-06)**: `RemoteResourcesScreen` reachable via `Destination.Resources` from About + Profile System tab; categorized links (boards, curated lists, prep, remote companies) with localized chrome.
- **Apply-assist rules (R-07)**: job-filter include/exclude keyword chips (whitelist/blacklist) + Tracker daily application quota (count vs. configurable DataStore-backed cap) — UX patterns reimplemented from scratch.
- **ViewModel test-strength pass (T-05)**: tautological initial-state assertions replaced with behavior checks (ATS resume loading, Home state content, Load/import transitions) and +33 event-verification tests added across 7 features (interview answers, ATS report actions, cover-letter edit/copy/export/regenerate, jobs clear/refresh, saved-jobs details/refresh/failure, resume OCR + JSON imports, notifications refresh/no-ops).
- **LLM training study artifact**: `docs/LLM_TRAINING_NOTES.md` — distilled techniques from the MIT-licensed `train-llm-from-scratch` repo (SFT loss masks, Bradley-Terry RM, DPO/ORPO/KTO, PPO/GAE, GRPO + k3 KL, RLVR rewards), with a section mapping the SFT/RLVR patterns onto a future on-device Gemma fine-tune for the Interview engine.
- **STAR coaching prompts + rubric gate (Option C)**: `docs/ONDEVICE_GEMMA_SFT_DESIGN.md` — on-device fine-tune design; **Gate G1 executed (2026-08-11)**: the current `.task` artifacts (LiteRT-Torch-converted) cannot carry MediaPipe LoRA, so the immediate deliverable is prompt-based coaching — `STARCoachingPrompts` (shared STAR guidance on every interview AI path) + `STARAnswerScorer` (deterministic STAR rubric; fills `starMethodScore` when the AI omits it). SFT/LoRA blueprint deferred to the LiteRT-LM migration (Option B).

## Phase 12 Completion
- **Design System**: Tokenized color/type/spacing/shape/elevation/motion with Light/Dark/AMOLED/Dynamic themes.
- **Component Library**: `:core:designsystem` catalog — see `COMPONENT_LIBRARY.md`.
- **Redesigned**: Dashboard (Command Center), Assistant (OS-style streaming), Analytics (interactive charts), Tracker (Kanban + drag-and-drop), Profile (sectioned hub), Jobs, Resume, Interview (mock data removed), Recruiter.
- **Contracts Frozen**: Design system, components, navigation, theme, motion — see `PHASE_12_REPORT.artifact.md`.

## Phase 13 Completion (Quality Engineering & Release Candidate)
- **Stale-test repair**: 20+ stale test files repaired across 9 modules (`core:domain`, `core:data`, `app`, tracker, profile, resume, jobs) against current contracts (deleted use cases removed, direct `CoreResult` stubs, Main-scheduler-safe tests).
- **App-module fixes**: WorkManager companion mocking (`mockkObject`), ConnectivityMonitor `getSystemService` stubbing, `Result.success()` equality assertions, `emptyFlow` determinism.
- **Verification**: full project `testDebugUnitTest` green; `assembleDebug` green.

## Phase 14 Completion (Production Launch & Operations)
- **Release build**: signing config (env-var secrets + `keystore.jks`), R8 minify + shrink, ProGuard mapping, native symbols, v1.0.0.
- **CI/CD**: 10-job pipeline (quick-check, quality, unit matrix, emulator tests, coverage, security scan, release build, benchmarks, Play upload, notify).
- **Monitoring**: `CrashReporter`, KPI targets, privacy-safe telemetry.
- **Docs finalized**: `CHANGELOG.md`, `ROADMAP.md`, `LICENSE`, `DATABASE_SCHEMA.md`, `SECURITY_GUIDE.md`, `TEST_PLAN.md`, `OBSERVABILITY_GUIDE.md`, `KNOWN_ISSUES.md`, `DEPLOYMENT_GUIDE.md`, `RELEASE_GUIDE.md`, `OPERATIONS_GUIDE.md`, plus the four final reports.
- **Deliverables**: `PRODUCTION_READINESS_REPORT.md`, `TECHNICAL_DEBT_REPORT.md`, `LAUNCH_CHECKLIST.md`, `PROJECT_COMPLETION_REPORT.md`.

## Known Issues
See `KNOWN_ISSUES.md` for the full catalog. All 🔴 High and 🟡 Medium severity issues are **resolved**.
Open items: P0-02 (MITM pen-test — requires device). P0-01 ✅ RESOLVED (2026-08-11 — `:core:database:connectedDebugAndroidTest` executed on the `aivance` AVD: 37 tests, 0 failures, migration chain 5→25 verified on-device). See `DEVICE_VALIDATION.md` for P0-02 execution instructions.

## Last Coordinated
- **2026-09-24 (checkpoint)**: V2 foundation **accepted and tagged** as `v2-foundation-baseline` (commit `fc6b5b9`). This is the stable foundation checkpoint; further work branches from it rather than mutating the foundation. **Next milestone — M04: Durable Event Replay & State Rehydration** (make `career_event_log` replayable to deterministically rehydrate the graph + memory projections). Explicitly *not* making the event log the source of truth: the Room DB stays authoritative; the log is durable append-only audit persistence. Invariants to establish first: idempotency, deterministic ordering, payload versioning, checkpoint/rebuild semantics, loud failure recovery, transactional projection, DB-stays-authoritative. See `ROADMAP.md` § M04.
- **2026-09-24 (landing)**: V2 "Career Knowledge OS" foundation landed and made durable. Room upgraded v25→v26 with the strictly-additive `MIGRATION_25_26`. `CareerGraphEngine` now persists/hydrates via `graph_nodes`/`graph_edges` (transactional replace, deterministic upsert IDs), `CareerMemoryEngine` hydrates + writes through `career_memory_entries`, and `CareerEventDispatcher` appends every event to the durable `career_event_log` (idempotent on `eventId`; logging only, replay not implemented). `CareerStateEngine` now projects saved jobs + interview sessions into the graph. Evidence: `:core:database`/`:core:domain`/`:core:data` unit suites + `assembleDebug` green; new migration + DAO/repository round-trip tests added.
- **2026-08-11**: P0-01 closed — instrumented DB suite executed on the `aivance` emulator (37 tests, 0 failures, migration chain 5→25 on-device). T-05 test-strength pass also landed.
- **2026-08-10**: Full walkthrough + TODO coordination pass. All stale debt entries updated. `DEVICE_VALIDATION.md` created.

## Release Readiness
- **Stability**: `assembleDebug` and full `testDebugUnitTest` green across all modules.
- **Release**: Signing + AAB/APK pipeline verified; CI `bundleRelease` job.
- **Security**: All API keys in encrypted DataStore; PII encrypted at rest; audit logs; Privacy Center.
- **Privacy**: GDPR-compliant Data Export and Deletion active.
- **Navigation**: Full type-safe backstack with 6 root destinations.
- **UI**: Unified design system; no mock data or dead controls.
- **Play readiness**: Data safety posture documented; staged rollout (10%) configured; mapping upload wired.
