# AiVance V2 Foundation & Architectural Integration Audit

**Document Type:** Master System Audit & Architectural Baseline  
**Target Repository:** `IamAzmathullaShaikh/Aivance`  
**Package Root:** `com.bangersoul.aivance`  
**Auditor Roles:** Principal Architect, Staff Android Engineer, AI Systems Engineer, Security Engineer, QA Lead, Open-Source Platform Engineer  
**Audit Date:** September 2026  
**Status:** PHASE 0 COMPLETE — BASELINE FROZEN & AUDITED  

---

## 1. Executive Summary & System Mapping

This audit establishes the definitive baseline of the AiVance codebase across its frozen **v1.0.0** production implementation and the newly introduced **V2 foundational components**. 

AiVance currently comprises 17 Gradle modules, 45+ Room SQLite database entities across 25 schema migrations, 6 AI providers, 15 job scraping/feed providers, 1 enrichment provider, and 11 feature presentation modules.

While the previous engineering phase introduced valuable architectural artifacts (the `career-schema/` suite, `evaluation/` testbed, `core:common:events` bus, and `core:domain:agent` runtime), our deep audit reveals **significant isolation, conceptual duplication, missing event wiring, and schema-divergence risks** that prevent these foundations from functioning as an integrated, production-grade Career Knowledge OS.

---

## 2. Architecture Comparison: V1 vs. Target V2

```
+──────────────────────────────────────────────────────────────────────────────────────────────────────────+
|                                    V1 ARCHITECTURE (FROZEN BASELINE)                                     |
+──────────────────────────────────────────────────────────────────────────────────────────────────────────+
  UI Screens (:feature:*)
       │
       ▼
  ViewModels (Combine 5 Repositories via StateFlow)
       │
       ▼
  Repositories (ResumeRepository, JobRepository, WorkflowRepository, etc.)
       │
       ├─────────────────────────────────┬─────────────────────────────────┐
       ▼                                 ▼                                 ▼
  Room SQLite (45+ isolated tables)   DataStore Preferences           Remote Providers (Apify, Gemini, etc.)
  - Flat relational schema            - Encrypted Tink keystore       - Unstandardized response mapping
  - Heavy 5-way polling in engine                                     - Ad-hoc prompt strings

+──────────────────────────────────────────────────────────────────────────────────────────────────────────+
|                                    V2 ARCHITECTURE (CANONICAL TARGET)                                    |
+──────────────────────────────────────────────────────────────────────────────────────────────────────────+
  Multi-Surface Clients (Android App, Browser Extension, Web Client, CLI)
       │
       ▼
  Controlled Agent Runtime (:core:domain:agent)
  (Planner ──► Reasoner ──► Human Approval Gate ──► Executor ──► Verifier)
       │
       ▼
  Canonical Career Graph (:core:careergraph) & Temporal Memory (:core:memory)
  (Unified Node/Edge Ontology: User, Skill, Resume, Job, Company, Recruiter, Application, Interview)
       │
       ▲
  Reactive Career Event Bus (:core:common:events)
  (Decoupled Event Streaming: ResumeEvent, AtsEvent, JobEvent, ApplicationEvent, InterviewEvent)
       │
       ▼
  Provider SDK 2.0 & AI Evaluation Framework
  (Capability-based Routing: Streaming, Structured Output, Local Gemma Inference, Regression Testbed)
```

---

## 3. Subsystem Inventory & Ownership

| Subsystem | Module | Key Classes & Entry Points | Primary Responsibility |
| :--- | :--- | :--- | :--- |
| **Application Root** | `:app` | `AivanceApp`, `MainActivity`, `FollowUpWorker`, `JobSyncWorker` | App lifecycle, Hilt injection, WorkManager schedulers |
| **Navigation** | `:navigation` | `AivanceNavGraph`, `NavigationSuiteScaffold`, `Destination` | Multi-screen routing, bottom navigation, adaptive layout |
| **Core Models** | `:core:common` | `DomainModels.kt`, `WorkflowModels.kt`, `CareerState.kt` | Canonical domain models, enums, result types, dispatchers |
| **Event Bus (V2)** | `:core:common` | `CareerEvent.kt`, `CareerEventBus.kt`, `CareerEventListener.kt` | Asynchronous decoupled reactive domain event bus |
| **Relational DB** | `:core:database` | `AivanceDatabase`, 18 DAOs, 45+ Entity models | Room SQLite persistence, 25 schema migrations, encryption |
| **Key-Value Store**| `:core:datastore`| `UserPreferences`, `EncryptedUserPreferencesSerializer` | Google Tink hardware-backed encrypted credentials & settings |
| **Network Engine** | `:core:network` | Retrofit clients, OkHttp interceptors, TLS pinners | Secure HTTP communication, certificate pinning, circuit breaking |
| **Design System** | `:core:designsystem`| `AivanceTheme`, `AivanceButton`, `AivanceCard` | Material 3 tokenized UI components & theme |
| **Utilities** | `:core:util` | `PdfTextExtractor` (PDFBox-Android), `FileUtils` | Local PDF parsing, safe file stream handling |
| **Provider SDK** | `:core:sdk` | `AIProvider`, `JobProvider`, `EnrichmentProvider`, `ProviderManager` | Pluggable vendor interfaces, health tracking, metadata |
| **AI Providers** | `:core:ai-providers`| `GeminiAIProvider`, `ClaudeProvider`, `OpenAIProvider`, `GroqProvider`, `GemmaOnDeviceProvider` | Remote and on-device LLM inference engines |
| **Job Providers** | `:core:job-providers`| `ApifyJobProvider`, `GreenhouseProvider`, 13 others | Job scraping and structured API search feeds |
| **Enrichment** | `:core:enrichment-providers`| `HunterEnrichmentProvider` | Recruiter contact discovery & email verification |
| **Domain Engines**| `:core:domain` | `CareerStateEngine`, `CareerIntelligenceEngine`, `ContextEngine`, `PromptOrchestrator` | Business logic, state derivation, AI prompt synthesis |
| **Agent RT (V2)** | `:core:domain` | `CareerAgentEngine`, `HumanApprovalGate`, `AgentModels.kt` | Goal decomposition, plan execution, human approval gates |
| **Feature UIs** | `:feature:*` (11) | `ResumeViewModel`, `AtsViewModel`, `JobsViewModel`, `AssistantViewModel`, etc. | Screen-specific state reduction and Compose UI layouts |
| **Evaluation (V2)**| `/evaluation` | `eval_runner.py`, `metrics.py`, synthetic datasets | CI AI benchmark testbed, hallucination/ATS regression tests |
| **Schema Std (V2)**| `/career-schema` | 11 Draft 2020-12 JSON Schemas | Language-agnostic career knowledge standard |

---

## 4. Duplicated Concepts Audit

Our audit identified 5 severe areas of duplicate domain modeling between V1 and V2:

### 4.1 Goal & Roadmap Duplication
* **V1 Entity**: `GoalEntity` in `core:database` (`career_goals` table: `id: Long`, `title`, `description`, `targetValue`, `currentValue`, `unit`, `deadline`, `isCompleted`, `type`).
* **V1 Domain**: `CareerRoadmap` & `RoadmapStep` in `core:common:model:DomainModels.kt`.
* **V2 Agent**: `CareerGoal` in `core:domain:agent:AgentModels.kt` (`id: String`, `targetRole`, `targetCompanyTypes`, `timeframeDays`, `minSalary`, `constraints`, `status`).
* **V2 Schema**: `career-graph.schema.json` defines `GOAL` node type.
* **Finding**: Three distinct, disconnected representations of user career goals exist simultaneously without conversion mappers.

### 4.2 Application Tracking Model Duplication
* **V1 Entity 1**: `JobApplicationEntity` (`job_applications` table) used by `TrackerDao` and `JobTrackerRepository`.
* **V1 Entity 2**: `ApplicationEntity` (`applications` table) used by `WorkflowDao` and `ApplicationWorkflowRepository`.
* **V1 Model 1**: `JobApplication` in `DomainModels.kt` (flat model with simple `ApplicationStatus`).
* **V1 Model 2**: `Application` in `WorkflowModels.kt` (rich model with stages, timeline events, and tasks).
* **V2 Schema**: `application.schema.json` defines `AiVanceJobApplication`.
* **Finding**: Two separate tables and repositories track job applications in the same application. `feature:tracker` uses `JobApplicationEntity`, while `core:domain:engine:CareerStateEngine` consumes `ApplicationWorkflowRepository` (`ApplicationEntity`).

### 4.3 Copilot Context Engine Duplication
* **Engine A**: `com.bangersoul.aivance.core.domain.engine.ContextEngine` (49 lines, simple string concatenation of `CareerState`).
* **Engine B**: `com.bangersoul.aivance.core.domain.assistant.AssistantContextEngine` (49 lines, queries 5 repositories directly for context).
* **Finding**: Two conflicting context builders exist with neither implementing token limits, privacy redaction, tiered priority (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`), or Career Memory integration.

### 4.4 Action Proposal & Automation Rules Duplication
* **V1 Persistence**: `AutomationRuleEntity` in `core:database` (`automation_rules` table: `triggerType`, `triggerValue`, `actionType`, `actionParamsJson`, `isEnabled`).
* **V1 Persistence**: `WorkflowExecutionEntity` (`workflow_executions` table).
* **V2 Domain**: `ActionProposal` / `AgentStep` in `core:domain:agent:AgentModels.kt`.
* **Finding**: V1 has a database schema for automation rules that is completely disconnected from the V2 `CareerAgentEngine` and `HumanApprovalGate`.

### 4.5 Career Recommendation Duplication
* **V1 Entity**: `RecommendationEntity` in `core:database` (`recommendations` table: `title`, `description`, `priority`, `category`, `evidenceJson`).
* **V1 Domain**: `CareerRecommendation` in `core:common:model:DomainModels.kt`.
* **V2 Schema**: `career-memory.schema.json` and `career-graph.schema.json`.
* **Finding**: Recommendations lack structured evidence grounding (`Signal`, `Weight`, `EvidenceRef`, `Confidence`, `ExpectedImpact`).

---

## 5. Architectural Conflicts & Critical Deficiencies

### 5.1 The Event Bus is an Isolated Island
* **Deficiency**: While `CareerEventBus.kt` was created in `core:common:events`, **not a single repository, use case, or ViewModel emits to or subscribes from it**.
* **Impact**: Domain events exist purely in unit tests. Any state mutation in `ResumeRepository`, `AtsRepository`, `JobRepository`, or `ApplicationWorkflowRepository` remains silent.

### 5.2 Missing Event Correlation & Audit Envelope
* **Deficiency**: `CareerEvent.kt` currently defines only `eventId`, `timestamp`, and `correlationId`.
* **Required Standard**: To reconstruct originating workflows for auditability, every event must also enforce:
  * `sourceModule: String` (e.g., `"feature:resume"`, `"core:agent"`)
  * `eventType: String` (canonical event topic)
  * `causationId: String?` (the triggering event/command ID)
  * `payload: Map<String, Any?>` (structured telemetry attributes)

### 5.3 Action Risk Level Mismatch with Enterprise Governance
* **Deficiency**: `AgentModels.kt` implemented a generic `ActionRiskLevel` enum (`LOW`, `MEDIUM`, `HIGH`).
* **Required Standard**: Phase 6 mandates explicit action capability classes with deterministic execution policies:
  * `READ_ONLY` ──► Automatically permitted
  * `LOCAL_MUTATION` ──► Optionally automatic (user-configurable toggle)
  * `EXTERNAL_SIDE_EFFECT` ──► **ALWAYS** requires explicit human approval
  * `DESTRUCTIVE` ──► **ALWAYS** requires explicit human approval + confirmation prompt

### 5.4 Career State Engine Bottleneck
* **Deficiency**: `CareerStateEngine.kt` uses a nested `combine(...)` across 5 flows (`UserRepository`, `ResumeRepository`, `ApplicationWorkflowRepository`, `AnalyticsRepository`, `ProviderManager`).
* **Impact**: Any individual entity update forces full recomputation of the entire career state, leading to unnecessary CPU cycles and Compose recomposition cascades.

### 5.5 Absence of Native Career Graph & Memory Subsystems in Kotlin
* **Deficiency**: While `career-schema/` provides JSON Schemas, there are no Kotlin domain interfaces or Room SQLite schema tables for graph traversal (`CareerGraphNode`, `CareerGraphEdge`, `CareerMemoryEntry`).
* **Impact**: The application still relies on flat, unindexed SQLite joins without graph query capabilities (such as finding skill gaps relative to target job requirements).

---

## 6. Dependency Graph & Layering Invariants

```
                      ┌───────────────┐
                      │     :app      │ (Application Root & Workers)
                      └───────┬───────┘
                              │
                              ▼
                      ┌───────────────┐
                      │  :navigation  │ (Route Contracts & NavGraph)
                      └───────┬───────┘
                              │
          ┌───────────────────┴───────────────────┐
          ▼                                       ▼
  ┌───────────────┐                       ┌───────────────┐
  │  :feature:*   │ (11 Feature Modules)  │  :feature:*   │
  └───────┬───────┘                       └───────┬───────┘
          │                                       │
          └───────────────────┬───────────────────┘
                              │
                              ▼
                      ┌───────────────┐
                      │ :core:domain  │ (UseCases, Engines, Agent RT, Repositories)
                      └───────┬───────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
  ┌───────────────┐   ┌───────────────┐   ┌───────────────┐
  │  :core:data   │   │  :core:sdk    │   │:core:design   │
  └───────┬───────┘   └───────┬───────┘   └───────────────┘
          │                   │
          ▼                   ▼
  ┌───────────────┐   ┌───────────────┐
  │:core:database │   │ :core:network │
  └───────┬───────┘   └───────┬───────┘
          │                   │
          └───────────────────┼───────────────────┐
                              ▼                   ▼
                      ┌───────────────┐   ┌───────────────┐
                      │:core:datastore│   │  :core:util   │
                      └───────┬───────┘   └───────┬───────┘
                              │                   │
                              └─────────┬─────────┘
                                        ▼
                                ┌───────────────┐
                                │ :core:common  │ (Foundation Models & Event Bus)
                                └───────────────┘
```

### Layering Rules:
1. **Zero Feature-to-Feature Coupling**: Features must never depend on each other. Cross-module actions flow through `CareerEventBus` or navigation routes.
2. **Zero Inverted Dependencies**: `:core:common` and `:core:domain` must never import Android UI (`androidx.compose.*`), SQLite DAOs, or Retrofit clients.
3. **No Direct UI-to-Repository Access**: Compose UI accesses ViewModels; ViewModels execute UseCases or observe `CareerStateEngine`.

---

## 7. Migration Risks & Safety Requirements

1. **Dual Application Tables (`job_applications` vs `applications`)**:
   - *Risk*: Data inconsistency where applications created in the job search workflow are invisible in the tracker view.
   - *Mitigation*: Unify on `applications` table backed by `ApplicationWorkflowRepository` as the single canonical source of truth; implement a zero-downtime Room migration (v25 ──► v26) migrating legacy records from `job_applications` into `applications`.
2. **Event Flooding & Out-of-Order Delivery**:
   - *Risk*: Rapid successive DB updates flooding the `CareerEventBus` and exhausting memory.
   - *Mitigation*: `BufferOverflow.DROP_OLDEST` is configured with a 64-event buffer; critical persistence events must be written to SQLite before in-memory emission.
3. **Non-Destructive User Data Guarantee**:
   - *Policy*: Schema changes must **never** execute destructive `fallbackToDestructiveMigration()`. Every Room migration must provide explicit SQL statements with before-and-after schema JSON verification.

---

## 8. Canonical Source of Truth Recommendations

| Concept | Deprecated / Parallel Concept | **Canonical Source of Truth (V2 Standard)** |
| :--- | :--- | :--- |
| **Career Goal** | `GoalEntity`, `CareerRoadmap` | **`CareerGoal` (`core:domain:agent`)** mapped to `career-schema/profile.schema.json` |
| **Application** | `JobApplicationEntity` (`TrackerDao`) | **`ApplicationEntity` (`WorkflowDao`)** mapped to `career-schema/application.schema.json` |
| **Event Bus** | Repository Polling (`combine`) | **`CareerEventBus` (`core:common:events`)** with mandatory audit envelope |
| **Copilot Context** | `AssistantContextEngine`, `ContextEngine` | **`AiContextEngine2` (`core:domain:context`)** with prioritized token tiers |
| **Agent Action** | `AutomationRuleEntity` | **`ActionProposal` (`core:domain:agent`)** with strict 4-class action safety |
| **Career Memory** | Unindexed `InterviewEvaluationEntity` | **`CareerMemoryEngine` (`core:domain:memory`)** with explicit evidence citations |
| **Skill Model** | Comma-separated strings in `UserProfile` | **`SkillNode` (`core:domain:careergraph`)** with proficiency & evidence IDs |
| **Provider Contracts**| Fragmented provider interfaces | **Capability-Driven Provider Abstraction 2.0** (`core:sdk`) |

---

## 9. Conclusion of Phase 0 Audit

The repository possesses a robust technical baseline, but requires focused consolidation:
* Unify duplicate models around canonical domain entities.
* Wire the `CareerEventBus` directly into repositories to eliminate polling.
* Upgrade the Context Engine to a token-budget-aware, prioritized engine.
* Enforce strict action classification in the Agent Runtime (`READ_ONLY`, `LOCAL_MUTATION`, `EXTERNAL_SIDE_EFFECT`, `DESTRUCTIVE`).
* Establish the native Kotlin Career Graph and Career Memory engines.

**Status**: Phase 0 Complete. Proceed to execute Phase 1 through Phase 20 based on approved architectural standards.
