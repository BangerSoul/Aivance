# AiVance Master Architectural Roadmap (2026–2028)
## The Open-Source Career Operating System

> **Vision**: AiVance should know the user's career, reason about it, and help execute the career strategy—not merely provide career tools.
> Transform AiVance from an Android application into an extensible, reference-quality, privacy-first **Career Knowledge OS Platform**.

---

## Strategic Overview: The 1000× Evolution

AiVance shipped **v1.0.0** on 2026-07-31 as a production-grade Android application featuring 17 Gradle modules, 45+ Room SQLite entities across 25 schema migrations, 6 AI providers, 15 job providers, and 1 enrichment provider. 

The path to making AiVance 1000× more impactful is **not adding 20 more isolated screens**. The path is establishing an open, extensible, provider-neutral, and privacy-preserving platform:

```
                                 AiVance
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │ CAREER KNOWLEDGE OS  │
                         └──────────┬───────────┘
                                    │
         ┌──────────────────────────┼──────────────────────────┐
         ▼                          ▼                          ▼
    Career Graph               Intelligence                Execution
  Profile / Skills             AI / Copilot               Applications
  Resume / History             Predictions                  Outreach
  Experience                    Matching                   Interviews
  Goals                        Forecasting                 Follow-ups
         │                          │                          │
         └──────────────────────────┼──────────────────────────┘
                                    ▼
                         ┌──────────────────────┐
                         │    CAREER MEMORY     │
                         └──────────┬───────────┘
                                    ▼
                           Continuous Learning
```

### Core Tenets of the Career OS
1. **One Career Graph & One Career State**: Unify isolated tables into an interconnected knowledge graph with typed edges and schema standards.
2. **Controlled Agent Runtime**: Replace passive conversational bots with goal-oriented AI planners bounded by **Human Approval Gates** (`PROPOSE -> EXPLAIN -> APPROVE -> EXECUTE -> VERIFY`).
3. **Temporal Career Memory & Evidence-Based AI**: Every recommendation is backed by auditable evidence (`Signal`, `Weight`, `Evidence`, `Confidence`, `Expected Impact`) and longitudinal interview/ATS outcome history.
4. **Decoupled Provider SDK & Plugin Ecosystem**: Make provider contracts (`AiProvider`, `JobProvider`, `EnrichmentProvider`, `ResumeParserProvider`) a standalone multiplatform SDK published separately.
5. **Local-First & Zero Vendor Lock-In**: User data is 100% locally owned in SQLite with optional zero-knowledge sync and one-click data export.
6. **AI Evaluation & Quality Rigor**: Automated regression testbed (`/evaluation`) testing grounding, hallucinations, latency, cost, and ATS precision on every prompt change.
7. **Multi-Surface Client Ecosystem**: Android App, Manifest V3 Browser Extension, Web Dashboard, and Developer CLI sharing domain logic and schemas.

---

## The 5 Foundational Projects (Immediate Priority)

Before implementing ad-hoc features, repository velocity aligns around these five architectural primitives:

```
01 — Career Graph Engine (`:core:careergraph`)
02 — Reactive Career Event Bus (`:core:events`)
03 — Provider SDK 2.0 (`:aivance-sdk`)
04 — Controlled Agent Runtime (`:core:agent`)
05 — Career AI Evaluation Framework (`/evaluation`)
```

### 01 — Career Graph Engine (`:core:careergraph`)
* **Objective**: Interconnect `User`, `Resume`, `Job`, `Company`, `Recruiter`, `Application`, `Interview`, `Skills`, and `Goals` into a queryable graph.
* **Nodes & Edges**:
  * Nodes: `UserNode`, `SkillNode`, `ResumeNode`, `JobNode`, `CompanyNode`, `RecruiterNode`, `ApplicationNode`, `InterviewNode`, `GoalNode`.
  * Edges: `HAS_SKILL(level, evidenceId)`, `TARGETS_ROLE(priority)`, `APPLIED_TO(date, stage)`, `REQUIRES_SKILL(importance)`, `EVALUATED_IN(score)`.
* **Graph Traversal Engine**: Enables relational reasoning (e.g., *"Find skills demanded by target jobs that lack strong resume evidence"*).
* **Storage**: Indexed SQLite relation tables (`graph_nodes`, `graph_edges`) with recursive CTE traversal and in-memory cache.

### 02 — Reactive Career Event Bus (`:core:events`)
* **Objective**: Replace monolithic 5-repository polling in `CareerStateEngine.kt` with a decoupled, reactive event bus.
* **Events**: `ResumeUpdated`, `AtsScoreChanged`, `JobSaved`, `ApplicationCreated`, `ApplicationStageChanged`, `InterviewCompleted`, `SkillDetected`, `GoalProgressUpdated`.
* **Mechanism**: Kotlin `SharedFlow<CareerEvent>` backed by a SQLite persistent replay log for durability across process lifecycles.

### 03 — Standalone Provider SDK 2.0 (`:aivance-sdk`)
* **Objective**: Extract provider interfaces from Android dependencies into a standalone, pure Kotlin Multiplatform library publishable to Maven Central.
* **Stable Contracts**: `AiProvider`, `JobProvider`, `EnrichmentProvider`, `ResumeParserProvider`, `InterviewCoachProvider`.
* **Extensibility**: External contributors can author and publish third-party provider plugins without cloning or modifying core application code.

### 04 — Controlled Agent Runtime (`:core:agent`)
* **Objective**: Autonomous career goal orchestration with deterministic safety guarantees.
* **Pipeline**:
  $$\text{User Goal} \longrightarrow \text{Planner} \longrightarrow \text{Reasoner} \longrightarrow \mathbf{\text{Human Approval Gate}} \longrightarrow \text{Executor} \longrightarrow \text{Verifier}$$
* **Safety Contract**: Destructive or outbound actions (altering stored resumes, dispatching emails, submitting applications) require explicit user approval.

### 05 — Career AI Evaluation Framework (`/evaluation`)
* **Objective**: Golden benchmark datasets and CI regression testing for all AI prompts and models.
* **Test Suites**: Resume extraction accuracy, ATS scoring determinism, cover letter grounding, interview evaluation fidelity, and adversarial hallucination defense.
* **Evaluation Matrix**: Benchmarks Gemini 1.5, Claude 3.5, OpenAI GPT-4o, Groq Llama-3.3, and local on-device Gemma 2.

---

## 18-Phase Detailed Platform Roadmap (2026–2028)

| Phase | Milestone Name | Primary Modules | Target Output | Status |
| :--- | :--- | :--- | :--- | :--- |
| **v1.0** | Production Android Launch | `:app`, `:feature:*`, `:core:*` | Production Android release with 6 AI + 15 Job providers | ✅ Shipped |
| **Phase 0** | Repo Stabilization & Open Source Ergonomics | `.github/`, root, `docs/` | OSI licensing resolution, issue templates, <10m contributor setup | 🔜 In Progress |
| **Phase 1** | Career Graph & Open Schema Standard | `career-schema/`, `:core:database` | Open JSON Schemas, Room graph schema migration (v26) | 🚧 In Progress |
| **Phase 2** | Event-Driven Career State Engine | `:core:events`, `:core:domain` | `CareerEventBus`, refactored reactive `CareerStateEngine` | 🚧 In Progress |
| **Phase 3** | Provider SDK 2.0 & Standalone Decoupling | `sdk/`, `:core:ai-providers`, `:core:job-providers` | Multiplatform `aivance-sdk` published to Maven Central | 🧭 Planned |
| **Phase 4** | Controlled Agentic AI Runtime | `:core:agent`, `:core:designsystem` | Goal planner, reasoner, and Human Approval Gate UI | 🧭 Planned |
| **Phase 5** | Temporal Career Memory & Evidence Auditing | `:core:memory`, `:feature:assistant` | Longitudinal pattern tracker, RACM evidence queries | 🧭 Planned |
| **Phase 6** | AI Evaluation Framework & Golden Datasets | `/evaluation`, `.github/workflows` | CI automated grounding, hallucination, and ATS testbed | 🧭 Planned |
| **Phase 7** | Edge / Cloud Hybrid AI Runtime | `:core:ai-providers`, `:core:domain` | MediaPipe on-device Gemma 2 + privacy-aware cloud routing | 🧭 Planned |
| **Phase 8** | Cross-Platform Browser Extension | `clients/browser` | Manifest V3 extension for LinkedIn, Indeed, Greenhouse | 🧭 Planned |
| **Phase 9** | Multi-Surface Web Dashboard | `clients/web` | Full-screen desktop web client sharing core contracts | 🧭 Planned |
| **Phase 10** | Developer Terminal CLI | `clients/cli` | `aivance` command line for automation and headless workflows | 🧭 Planned |
| **Phase 11** | Developer SDK & Community Plugin System | `sdk/templates`, `docs/providers` | Plugin development kit (PDK) and test fixture libraries | 🧭 Planned |
| **Phase 12** | Community Plugin Marketplace & Registry | `registry/`, `:feature:profile` | Decentralized verified plugin registry and in-app discovery | 🧭 Planned |
| **Phase 13** | Public Career AI Benchmark & Leaderboard | `evaluation/public` | Open benchmark datasets and model performance leaderboard | 🧭 Planned |
| **Phase 14** | Global Open-Source Community Ecosystem | Community / Governance | Technical Steering Committee, bi-monthly hackathons, i18n | 🧭 Planned |
| **Phase 15** | Zero-Knowledge Encrypted Sync | `:core:data`, `:core:network` | Client-side E2EE cloud backup with zero-knowledge relay | 💡 Exploratory |
| **Phase 16** | Open Core & Team / Coaching Federation | Enterprise / Federation | Shared mentor/coaching views, university self-hosting | 💡 Exploratory |
| **Phase 17** | AiVance 2.0 Unified Career OS Release | All modules & clients | Unified launch across Android, Web, Extension, and CLI | 🧭 Planned |

---

## Phase Breakdown & Deliverables

### Phase 0: Repository Stabilization & Open Source Ergonomics
* **Licensing Resolution**: Transition from proprietary terms to **Apache-2.0** (or Open-Core) to align with `CONTRIBUTING.md`.
* **Governance**: Setup `.github/ISSUE_TEMPLATE/`, `PULL_REQUEST_TEMPLATE.md`, `CODEOWNERS`, and `docs/ADR/` (Architecture Decision Records).
* **Contributor Productivity (< 10 min)**: Out-of-the-box debug builds (`./gradlew assembleDebug`) utilizing mock providers without requiring external API keys.
* **Starter Backlog**: Create 15+ tagged `good-first-issue` tasks across UI, providers, and test fixtures.

### Phase 1: Career Graph & Open Schema Standard (`career-schema/`)
* **Language-Agnostic JSON Schemas** (Draft 2020-12):
  * `profile.schema.json`, `skill.schema.json`, `resume.schema.json`, `job.schema.json`
  * `company.schema.json`, `recruiter.schema.json`, `application.schema.json`, `interview.schema.json`
  * `career-event.schema.json`, `career-memory.schema.json`, `career-graph.schema.json`
* **Room SQLite Graph Layer**: Migration v26 introducing `graph_nodes` and `graph_edges` tables with recursive traversal queries. ✅ **Landed** — strictly-additive `MIGRATION_25_26`; `CareerGraphEngine` persists/hydrates via `CareerGraphRepository` with transactional replace and deterministic upsert IDs.
* **Domain Engine**: `CareerGraphRepository` with `GetSkillGapGraphUseCase` and `GetCareerNetworkUseCase`. *(traversal use cases still planned)*

### Phase 2: Event-Driven Career State Engine (`:core:events`)
* **Event Bus**: In-memory `SharedFlow` coupled with SQLite event logging. ✅ **Durable log landed** (`career_event_log`, idempotent on `eventId`; each row carries an explicit payload `schemaVersion` — M04-A). ✅ **Replay engine landed (projection-only, M04-B)** — `CareerEventReplayEngine` deterministically rebuilds the graph's `CAREER_EVENT` provenance slice from the log; entity-graph + memory rehydration remain deferred. The log remains append-only audit persistence and the Room DB stays authoritative.
* **State Decoupling**: Refactor `CareerStateEngine.kt` to subscribe reactively to events rather than polling 5 repositories. ✅ Reactive event subscription + graph projection (saved jobs + interview sessions) landed.
* **Emission Pipeline**: Instrument `:feature:resume`, `:feature:ats`, `:feature:jobs`, `:feature:tracker`, and `:feature:interview`. *(resume + interview + workflow emit today; remaining producers planned)*

### M04 — Durable Event Replay & State Rehydration (next foundation milestone)

> **Baseline:** tag `v2-foundation-baseline` (commit `fc6b5b9`) is the stable V2 foundation checkpoint. M04 begins from this tag rather than continuing to mutate the foundation.

* **Objective**: Make the durable `career_event_log` **replayable**, and use replay to deterministically rehydrate the Career Knowledge OS projections (graph + memory).
* **Non-objective (for now)**: The event log does **not** become the system's source of truth. `career_event_log` is durable append-only audit persistence; the Room database remains authoritative until event sourcing is deliberately adopted as the canonical persistence model.
* **Invariants to establish before implementing**:
  1. **Idempotency** — replaying the same event cannot duplicate graph nodes, edges, or memory entries (keyed on `eventId` + deterministic projection IDs).
  2. **Deterministic ordering** — replay applies events in a stable, well-defined order (e.g. `(timestamp, eventId)` or a monotonic sequence).
  3. **Payload versioning** — persisted event payloads carry an explicit schema/version, with a forward-compatible decode strategy.
  4. **Checkpoint/rebuild semantics** — explicitly define whether replay reconstructs from empty state or incrementally repairs existing state.
  5. **Failure recovery** — a single malformed event must fail loudly, never silently produce a falsely complete reconstruction.
  6. **Transactional projection** — graph/memory projections update atomically; no partially-applied replay.
  7. **DB stays authoritative** — replay is a rehydration/repair path, not a replacement for the canonical Room state.
* **Progression**: M01 event production → M02 multiple real producers → M03 career-intelligence projection → **foundation landing (baseline tag)** → **M04 durable replay/rehydration** → M05 knowledge/context consumers → later: controlled agent execution + `HumanApprovalGate`.

#### M04-A — Event Contract Hardening ✅ (landed 2026-09-24)

Prerequisite for replay: the persisted event contract now carries an explicit payload version so a future replay engine never infers version from payload shape.

* **`CareerEvent.schemaVersion`** — every event carries an explicit payload/schema version (current = `1`), distinct from the Room DB version and the event-type discriminator.
* **`CareerEventContract`** — registry mapping each emitted event type to the payload versions it can decode (`New contract → v1`; a breaking payload change adds a new version).
* **`CareerEventCodec`** — one codec shared by the write and read paths, so payloads encode/decode identically.
* **`CareerEventDecodeResult`** — decoding yields an explicit `Decoded` / `UnknownType` / `UnsupportedVersion` / `Malformed`; **no silent fallback or reinterpretation**. `CareerEventLogRepository.decodeAll()` exposes this read path (no projection).
* **Room v26 → v27** — strictly-additive `MIGRATION_26_27` adds `career_event_log.schemaVersion` (default `1`, backfilling legacy rows); `27.json` exported.
* **Still out of scope**: replay, rehydration, and making the log the source of truth — those are M04-B.

#### M04-B — Durable Replay & Rehydration ✅ (landed 2026-09-24, projection-only)

`CareerEventReplayEngine.replayAll()` reads `career_event_log`, decodes each row against the M04-A contract, and deterministically rebuilds the graph's event-provenance layer. It honors the seven invariants:

* **Idempotency** — each event maps to a stable `event_<eventId>` node id, so replaying N times converges to the same rows.
* **Deterministic ordering** — events are projected in `(timestamp, eventId)` order, applied in the engine rather than trusting storage order.
* **Payload versioning** — decoding is governed by `CareerEventContract`/`CareerEventCodec`; version is never inferred from payload shape.
* **Checkpoint/rebuild** — **FULL REBUILD** each pass; no incremental checkpoint is implemented or claimed.
* **Loud failure** — an unknown type / unsupported version / malformed payload aborts the whole pass with an explicit `CareerReplayFailure` and writes nothing; nothing is silently skipped or coerced.
* **Transactional projection** — the slice is rewritten in one transaction (`GraphDao.replaceNodesOfType` → `CareerGraphRepository.replaceEventProjection`); a failed decode or write leaves no partial projection, and the relational-entity slice is untouched.
* **DB stays authoritative** — replay is a pure projection/repair path: no command re-execution, no business-entity mutation, no AI/network/outreach side effects, no re-emission onto the live bus.

**Replay mode: FULL REBUILD. API: `replayAll()`** (no `replayFrom`/checkpoint yet). **No Room schema change** (reuses v27).

**Coverage (honest):** the event-provenance graph layer only. **Deferred:** entity-graph nodes (owned by `CareerGraphEngine.buildGraph`, fed by authoritative Room entities) and structured memory entries (owned by `CareerMemoryEngine`) — the flattened audit payloads omit the entity identities required to rebuild those without inventing historical data. Promoting them to replay targets requires first enriching persisted payloads with stable entity identifiers (a future contract evolution, i.e. a new payload version).

#### M05 — Graph Projection Ownership & Replay Activation ✅ (landed 2026-09-24)

Closes the M04-B co-writer hazard: the live graph persist used `GraphDao.replaceGraph` (clear ALL nodes + edges), which could erase the replay-owned `CAREER_EVENT` slice on the next state emission.

* **Disjoint projection ownership** — the graph is now split into two independently-owned slices that never overlap:
  * **Entity projection** — every `CareerNodeType` except `CAREER_EVENT`, plus all edges. Owned by the live `CareerStateEngine` via the new transactional **`GraphDao.replaceEntityProjection`** (clears all edges + all non-`CAREER_EVENT` nodes, then upserts). `CareerGraphRepositoryImpl.persist` now targets this path.
  * **Event-provenance projection** — `CAREER_EVENT` nodes only. Owned by `CareerEventReplayEngine` via `GraphDao.replaceNodesOfType`.
* **Co-writer safety proven** — JVM + instrumented tests assert that a live entity persist after replay preserves the `CAREER_EVENT` slice, and replay after a live persist preserves the entity slice.
* **Deliberate replay trigger** — **`RebuildCareerEventProjectionUseCase`** (`NoInputUseCase<CareerReplayResult>`) is the single production entry point for `replayAll()`. It is intentionally NOT wired into `CareerStateEngine.state`, repository reads, startup, or per-event dispatch — replay is a conscious maintenance/repair operation. No UI was invented.
* **No schema change** (reuses v27). DB stays authoritative; the event log stays append-only audit persistence.
* **Device status:** instrumented DAO isolation test compiled but NOT executed (no emulator/device available).

#### M06 — Durable Replay Runtime Acceptance ✅ (landed 2026-09-24)

Converts M04/M05 from "replay code that compiles + passes JVM tests with fakes" to **runtime-proven durable replay** against a real Android Room v27 database.

* **Real Android runtime** — provisioned a headless emulator (AVD `m06_test`, Android 14 / API 34 / x86_64, `emulator-5554`, `boot_completed=1`) and executed the instrumentation on it. Compiled-but-unexecuted androidTests are explicitly NOT counted as PASS.
* **End-to-end runtime suite** — new `CareerEventReplayRuntimeTest` (`:core:data`) drives the actual production stack (`CareerEventLogRepositoryImpl` → real DAO → `career_event_log`; `RebuildCareerEventProjectionUseCase` → `CareerEventReplayEngine` → `CareerGraphRepositoryImpl` → `GraphDao`) covering M06.4 A–I: log persistence survives DB close/reopen, replay creates stable idempotent `event_<eventId>` provenance nodes, entity/event slices never erase each other (the M05 co-writer regression — now runtime-proven), deterministic `(timestamp, eventId)` ordering incl. tie-break, and loud failure with zero projection on unsupported version / malformed payload / unknown type / empty log.
* **One test-only fix** — `MigrationTest.migrate25To26`'s seed omitted v25 `user_profiles` NOT NULL columns, failing the INSERT before the migration; corrected the seed. The migration itself was already correct (classification B, not a production defect).
* **Runtime evidence** — `:core:database:connectedDebugAndroidTest` → 36 tests, 0 failures; `:core:data:connectedDebugAndroidTest` (`CareerEventReplayRuntimeTest`) → 10 tests, 0 failures; full `testDebugUnitTest` + `:app:assembleDebug` green. No schema change (reuses v27). DB stays authoritative; the event log stays append-only audit persistence.

### Phase 3: Provider SDK 2.0 & Standalone Decoupling
* **Pure Kotlin SDK**: Extract `core:sdk` into standalone multiplatform library `aivance-sdk`.
* **Abstracted Secrets**: Abstract Android Keystore behind `SecretStore` interface (supporting keychain, env vars, or encrypted preferences).
* **Provider Updates**: Migrate 6 AI providers and 15 Job providers to SDK 2.0.
* **Publication**: Publish to Maven Central under `com.bangersoul.aivance:provider-sdk`.

### Phase 4: Controlled Agentic AI Runtime
* **Agent State Machine**: Goal formulation, task decomposition, and execution plan generation.
* **Human Approval Gates**: Interactive `ApprovalDialog` and `ActionReviewCard` with change diffs and evidence justification.
* **Auditability**: Every agent execution recorded in `WorkflowExecutionEntity` and `AuditLogEntity`.

### Phase 5: Temporal Career Memory & Longitudinal Learning
* **Longitudinal Persistence**: Record temporal interview metrics, resume versions, and application conversion rates.
* **Pattern Recognition**: Identify recurring interview weak points (e.g. system design, behavioral pacing) across time.
* **Auditable Explainability**: Every recommendation includes `Signal`, `Weight`, `Evidence`, `Confidence`, and `Expected Impact`.

### Phase 6: Career AI Evaluation Framework & Golden Benchmarks
* **Evaluation Testbed**: Automated Python/Kotlin harness in `/evaluation`.
* **Golden Datasets**: Synthetic, verified test sets for resume parsing, ATS scoring, and interview evaluation.
* **CI Quality Gate**: GitHub Action `.github/workflows/ai-evaluation.yml` blocking regressions on PRs.

### Phase 7: Edge / Cloud Hybrid AI Runtime
* **On-Device Inference**: Quantized Gemma 2B/7B via MediaPipe GenAI.
* **AiRuntimeRouter**: Dynamic routing—local execution for sensitive PII parsing, cloud execution for complex reasoning.
* **Offline Resilience**: Seamless fallback to on-device models when disconnected or rate-limited.

### Phase 8: Cross-Platform Browser Extension (`clients/browser`)
* **Manifest V3 Extension**: Injects live job analysis overlay into LinkedIn, Indeed, Greenhouse, and Lever.
* **Features**: Live match score, missing skills list, one-click save to pipeline, and tailored resume generation.
* **Bridge**: Syncs with local desktop or mobile app via secure WebSocket/API bridge.

### Phase 9: Multi-Surface Web Dashboard (`clients/web`)
* **Desktop Client**: Responsive Next.js / Kotlin Wasm application sharing design tokens and contracts.
* **Workspaces**: Multi-profile management (e.g., "Android Lead" vs. "Engineering Manager").
* **Rich Visual Builder**: Real-time PDF preview and side-by-side job tailoring.

### Phase 10: Developer Terminal CLI (`clients/cli`)
* **Terminal Tools**:
  ```bash
  aivance resume analyze --file resume.pdf --job "https://..."
  aivance jobs search --role "Android Engineer" --remote
  aivance interview practice --role "Staff Android Engineer"
  aivance export --format json --output backup.json
  ```

### Phases 11–17: Ecosystem, Marketplace & V2 Platform
* **Phase 11**: Plugin Development Kit (PDK) and developer documentation.
* **Phase 12**: Verified community plugin registry and in-app marketplace.
* **Phase 13**: Public Career AI Benchmark dataset and model leaderboard.
* **Phase 14**: Technical Steering Committee (TSC) and multi-maintainer governance.
* **Phase 15**: Client-side Zero-Knowledge End-to-End Encrypted (E2EE) sync.
* **Phase 16**: Enterprise / Coaching federation for bootcamps and universities.
* **Phase 17**: **AiVance 2.0 Unified Platform Launch**.

---

## Codebase Architecture Mapping

```
+─────────────────────────────────────────────────────────────────────────+
|                        AIVANCE MONOREPO STRUCTURE                       |
+─────────────────────────────────────────────────────────────────────────+
├── career-schema/             # Open JSON Schema standards (Language-agnostic)
├── evaluation/                # AI benchmark datasets & regression testbed
├── sdk/                       # Standalone Multiplatform Provider SDK (KMP)
├── core/
│   ├── common/                # Core result types, dispatchers, constants
│   ├── events/                # Reactive CareerEventBus & event log
│   ├── careergraph/           # Career Graph nodes, edges, query engine
│   ├── memory/                # Temporal Career Memory & evidence logging
│   ├── agent/                 # Controlled Agent Runtime & approval gates
│   ├── database/              # Room SQLite database (Migrations 1-26+)
│   ├── datastore/             # Tink hardware-backed encrypted preferences
│   ├── network/               # Retrofit, OkHttp, TLS pinning, interceptors
│   ├── designsystem/          # Material 3 design tokens & UI components
│   └── util/                  # PDFBox parser, file utilities
├── providers/
│   ├── ai/                    # Gemini, Claude, OpenAI, Groq, Ollama, Gemma
│   ├── jobs/                  # 15+ Job scraping & API providers
│   └── enrichment/            # Hunter.io & recruiter enrichment
├── android/
│   ├── app/                   # Android entry point, Hilt setup, WorkManager
│   ├── navigation/            # NavigationSuiteScaffold & navigation graph
│   └── feature/               # 11 Android feature modules (Compose UI)
├── clients/
│   ├── browser/               # Chrome / Firefox extension (Manifest V3)
│   ├── web/                   # Web application (Next.js / Kotlin Wasm)
│   └── cli/                   # Command-line interface tool
└── docs/                      # Master specs, RFCs, ADRs, contributor guides
```

---

## Non-Negotiable Guarantees

* **Privacy First**: Zero plaintext user career data uploaded to remote servers without explicit E2EE keys.
* **Zero Vendor Lock-In**: Full career data exportable at any time in standardized JSON/Graph formats.
* **Explainable AI**: No opaque recommendations; every suggestion surfaces its underlying evidence and confidence.
* **Controlled Automation**: No unconfirmed destructive or outbound actions executed by AI agents.
* **Deterministic Code Quality**: Zero feature-to-feature dependencies, strict unidirectional data flow, and 100% CI pass rates.
