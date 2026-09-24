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
- **Latest Migration**: `MIGRATION_26_27` (M04-A event-contract hardening — strictly additive: adds a `schemaVersion` column to `career_event_log`, default `1`, backfilling all pre-existing rows to contract v1; no other table or row is altered). The prior `MIGRATION_25_26` added the Career Knowledge OS foundation tables (`graph_nodes`, `graph_edges`, `career_event_log`, `career_memory_entries`). **The Room database remains the single source of truth. `career_event_log` is durable append-only *audit* persistence — NOT an authoritative state-reconstruction mechanism.** Every persisted event carries an explicit payload `schemaVersion` so replay decodes against a known contract rather than inferring the version from payload shape. **Event replay engine: IMPLEMENTED for the event-provenance projection only (M04-B).** `CareerEventReplayEngine.replayAll()` deterministically rebuilds the graph's `CAREER_EVENT` node slice from the log; entity-graph and memory rehydration remain DEFERRED because the flattened audit payloads omit the entity identities needed to reconstruct them without inventing data. No Room schema change was required for M04-B or M05 (both reuse v27). **Graph projection ownership (M05):** the graph is split into two disjoint slices — the *entity projection* (all node types except `CAREER_EVENT`, plus all edges), owned by the live `CareerStateEngine` via `GraphDao.replaceEntityProjection`, and the *event-provenance projection* (`CAREER_EVENT` nodes only), owned by replay via `GraphDao.replaceNodesOfType`. Neither slice can erase the other. Replay is activated only through the deliberate `RebuildCareerEventProjectionUseCase` maintenance trigger — never on the hot path.
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
- **2026-09-24 (M07 architecture)**: **M07 projection-ownership decision recorded in [ADR 0013](docs/adr/0013-m07-projection-ownership.md); entity/memory rehydration DEFERRED, not implemented.** The prior "M07 BLOCKED" state is now resolved into a deliberate architecture decision rather than an open blocker. Traced production reachability first: the entity graph is effectively **write-only** today — the only reader of graph-node *contents* is `AiContextEngine2.buildContext` (reads SKILL nodes), and `AiContextEngine2` has **no production caller** (infrastructure-only); `CareerStateEngine` reads only `nodes.size`/`edges.size` for telemetry; `loadGraph`/`analyzeSkillGaps`/`getApplicationContext` and `CareerMemoryEngine` have no production callers in `feature/`/`app/`. Also established that even **v2 payloads carry identity only, not full attributes** (e.g. `JobSaved` v2 = `{jobId, company, title}` — no location/description/remote/provider that a JOB node needs), and the log records no deletions — so replay cannot reconstruct a node *equivalent* to `buildGraph`'s. **Decision — Option C accepted:** the event log is historical *evidence*, Room stays authoritative, and replay's only graph output remains the `CAREER_EVENT` provenance slice (M05 ownership unchanged). **Option A rejected** (second writer to the entity slice / makes log canonical / stale overwrite / attribute-incomplete). **Option B rejected as premature** (a third "rehydrated entity" slice duplicates every entity with a permanent reconciliation burden and no consumer needs it today; it stays the candidate if a real consumer contract appears). Entity graph + memory rehydration are DEFERRED until (1) a product consumer needs reconstructed-historical state distinct from current state AND (2) payloads are attribute-complete or reconstruction is an explicit reconciliation overlay. **No production code change was justified** — only the ADR + docs were added. Full `testDebugUnitTest` and (from M06/M04-C) `:app:assembleDebug` green; the M06 `m06_test` emulator (Android 14 / API 34 / x86_64) remains attached and `CareerEventReplayRuntimeTest` → 10 tests / 0 failures still holds. Room DB remains authoritative; `career_event_log` remains append-only audit persistence. **M07: ARCHITECTURE ACCEPTED (rehydration deferred).**
- **2026-09-24 (M04-C)**: **Event-contract evolution to v2 with stable entity identity landed; M07 entity/memory rehydration reported BLOCKED (not weakened).** The M07 blocker was proven from source: the flattened, string-valued audit payloads persisted for the flagship events omit the entity identity that keys rehydration — `ResumeAnalysisCompleted` recorded only `atsScore`, `JobSaved` only company/title, `ApplicationStageChanged` only old/new stage, `InterviewCompleted` only score/weaknesses. M04-C evolves exactly those four payloads to **schema version 2**, flattening their stable identity into the persisted payload (`resumeId`+`versionId`, `jobId`, `applicationId`, `sessionId` respectively). `CareerEventContract.SUPPORTED_VERSIONS` now accepts **{1, 2}** for those four types (`ENTITY_IDENTITY_V2_TYPES`) and **{1}** for every other type; already-persisted **v1 rows still decode exactly as before** (and remain non-rehydratable because they lack identity), while an unsupported v3, unknown type, or malformed payload still fails loudly and distinguishably. **No Room schema change** — payload/schema version is deliberately independent of the Room database version, and the additive change lives entirely in the JSON payload of new rows (reuses v27). At the projection layer this immediately enriches the replay-owned `CAREER_EVENT` provenance node: a v2 event's identity now flows into the node's `payload.*` properties automatically, with no new writer and no change to the M05 ownership boundary. **M07 verdict — BLOCKED (by ownership, correctly):** actually creating/updating entity graph nodes (RESUME/JOB/APPLICATION/INTERVIEW_SESSION) or memory entries from replay would put the replay engine into the *entity* projection slice that M05 assigns exclusively to the live `CareerStateEngine` (fed by authoritative Room entities). Doing so would reintroduce two competing writers to the same slice and make the append-only log a second source of truth for entity state — both explicitly forbidden invariants. The honest result: **M04-C unblocks the payload contract; genuine entity rehydration is now gated on a deliberate ownership decision, not on missing data.** Evidence: `:core:common` + `:core:domain` unit suites, full `testDebugUnitTest`, and `:app:assembleDebug` green; new contract tests (v1 legacy decode, v2 round-trip carrying identity, both-versions-accepted, unsupported-v3 loud failure) and replay-engine tests (v2 identity flows into provenance node, mixed v1/v2 log, no entity node created); `CareerEventReplayRuntimeTest` re-run on `m06_test` (Android 14 / API 34 / x86_64) → **10 tests, 0 failures**. Room DB remains authoritative; `career_event_log` remains append-only audit persistence. **M04-C: PASS. M07: BLOCKED — entity/memory rehydration requires a projection-ownership decision (would otherwise violate the M05 single-writer invariant or make the log canonical).**
- **2026-09-24 (M06)**: **Durable replay proven on a real Android Room runtime.** Provisioned a headless emulator (AVD `m06_test`, Android 14 / API 34 / x86_64, `emulator-5554`) and executed the M04/M05 replay stack end-to-end against a real Room v27 database — no fakes. Fixed one **test-only** defect uncovered by the runtime (classification B, not a production defect): `MigrationTest.migrate25To26` seeded `user_profiles` without its v25 NOT NULL columns (`experienceYears`, `preferredIndustries`, `visaRequired`, `createdDate`), which failed the INSERT *before* the migration ran; the migration itself was correct. Added a new instrumented suite `CareerEventReplayRuntimeTest` in `:core:data` (the module where the real `CareerEventReplayEngine` + `CareerEventLogRepositoryImpl` + `CareerGraphRepositoryImpl` + Room converge) covering the M06.4 scenarios A–I: event-log persistence survives DB close/reopen; replay creates stable `event_<eventId>` provenance nodes; replay is idempotent; replay preserves every entity node type + edges; **the live entity re-projection preserves the replay-owned `CAREER_EVENT` slice and vice versa (the critical M05 co-writer regression, now runtime-proven)**; deterministic `(timestamp, eventId)` ordering incl. tie-break; and loud failure with zero projection on unsupported version / malformed payload / unknown event type / empty log. **Runtime evidence:** `:core:database:connectedDebugAndroidTest` → 36 tests, 0 failures (after the seed fix); `:core:data:connectedDebugAndroidTest` (`CareerEventReplayRuntimeTest`) → 10 tests, 0 failures on `m06_test`; full `testDebugUnitTest` and `:app:assembleDebug` green. Room DB remains authoritative; `career_event_log` remains append-only audit persistence; no schema change (reuses v27). **V2-M06: PASS.**
- **2026-09-24 (M05)**: **Graph projection ownership fixed + replay activated via a deliberate trigger.** Closed the M04-B co-writer hazard where the live `CareerStateEngine` graph persist (`GraphDao.replaceGraph` → clear-all-nodes) could erase the replay-owned `CAREER_EVENT` slice. The graph now has two disjoint, independently-owned slices: the **entity projection** (every node type except `CAREER_EVENT` + all edges) owned by the live engine via the new **`GraphDao.replaceEntityProjection`** (clears all edges and all non-`CAREER_EVENT` nodes, then upserts — transactional), and the **event-provenance projection** (`CAREER_EVENT` nodes only) owned by replay via `GraphDao.replaceNodesOfType`. `CareerGraphRepositoryImpl.persist` now targets the entity path, so a live re-projection can no longer erase a replay-rebuilt provenance slice and vice versa. **Replay activation:** the new domain-level **`RebuildCareerEventProjectionUseCase`** (`NoInputUseCase<CareerReplayResult>`) is the single production trigger for `replayAll()`; it is deliberately NOT wired into `CareerStateEngine.state`, repository reads, startup, or per-event dispatch — replay stays a conscious maintenance/repair operation. No UI was invented. No Room schema change (reuses v27). Evidence: `:core:common`/`:core:domain`/`:core:data`/`:core:database` compile + unit suites, full `testDebugUnitTest`, and `:app:assembleDebug` all green; new co-writer isolation + idempotency JVM tests (`CareerGraphRepositoryImplTest`, `RebuildCareerEventProjectionUseCaseTest`) and an instrumented `replaceEntityProjection` isolation test added — instrumented DAO tests **compiled but NOT executed (no device/emulator; `adb devices` empty)**.
- **2026-09-24 (M04-B)**: **Durable event replay landed (projection-only).** `CareerEventReplayEngine.replayAll()` reads `career_event_log`, decodes every row against the M04-A contract, and deterministically rebuilds the graph's `CAREER_EVENT` provenance slice. Guarantees: **deterministic ordering** (`(timestamp, eventId)` applied in-engine, not trusting storage order), **idempotency** (stable `event_<eventId>` node ids; replaying N times converges to the same rows), **loud failure** (an unknown type / unsupported version / malformed payload aborts the whole pass with an explicit `CareerReplayFailure` and writes nothing), and **transactional / no-partial projection** (the slice is rewritten in one transaction via the new `GraphDao.replaceNodesOfType` + `CareerGraphRepository.replaceEventProjection`, leaving the relational-entity projection untouched). Replay is a pure projection: **no command re-execution, no WorkflowEngine/repository mutation of business entities, no AI/network/outreach side effects, no re-emission onto the live bus.** Rebuild mode: **FULL REBUILD** (no incremental checkpoint claimed). **Coverage:** event-provenance graph layer only. **Deferred:** entity-graph nodes (owned by `CareerGraphEngine.buildGraph` fed by authoritative Room entities) and memory entries (owned by `CareerMemoryEngine`) — the audit payloads lack the entity identities to rebuild them without invention. Room DB stays authoritative; **no schema change** (replay reuses v27). Evidence: `:core:domain` + `:core:data` unit suites, full `testDebugUnitTest`, and `:app:assembleDebug` green; 10 new replay-engine tests + graph-slice repo/DAO tests added (instrumented DAO/migration tests require a device — compiled, not executed here).
- **2026-09-24 (M04-A)**: **Event-contract hardening landed.** Every persisted `CareerEvent` now carries an explicit payload/schema version (`CareerEvent.schemaVersion`, current = 1), a `CareerEventContract` registry maps each emitted event type to the payload versions it can decode, and a shared `CareerEventCodec` encodes on the write path and decodes on the read path. Room upgraded v26→v27 via the strictly-additive `MIGRATION_26_27` (adds `career_event_log.schemaVersion`, default 1; `27.json` exported). `CareerEventLogRepository.decodeAll()` surfaces each row as an explicit `CareerEventDecodeResult` — `Decoded` / `UnknownType` / `UnsupportedVersion` / `Malformed` — with **no silent fallback or reinterpretation**. This is contract hardening only: **replay is still NOT implemented**, and the Room DB stays authoritative. Evidence: `:core:common`/`:core:domain`/`:core:data`/`:core:database` unit suites + `assembleDebug` green; new contract/codec tests, `v26→v27` migration test, and `schemaVersion` DAO round-trip added (instrumented migration/DAO tests require a device — compiled, not executed here).
- **2026-09-24 (checkpoint)**: V2 foundation **accepted and tagged** as `v2-foundation-baseline` (commit `fc6b5b9`). This is the stable foundation checkpoint; further work branches from it rather than mutating the foundation. **Next milestone — M04: Durable Event Replay & State Rehydration** (make `career_event_log` replayable to deterministically rehydrate the graph + memory projections). M04-A (event-contract hardening) is complete; M04-B (replay/rehydration) is next. Explicitly *not* making the event log the source of truth: the Room DB stays authoritative; the log is durable append-only audit persistence. Invariants to establish first: idempotency, deterministic ordering, payload versioning, checkpoint/rebuild semantics, loud failure recovery, transactional projection, DB-stays-authoritative. See `ROADMAP.md` § M04.
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
