# AiVance V2 — Master Architectural Specification

**Document Type:** Master System Design Specification & Architectural Standard  
**Package Root:** `com.bangersoul.aivance`  
**Standard:** AiVance Career OS v2.0  
**Status:** Approved Master Specification  

---

## 1. Architectural Vision: The Career Knowledge OS

AiVance is transitioning from an isolated mobile tool into an extensible, provider-neutral, privacy-preserving **Career Operating System**.

The foundational shift is from passive screens to a reactive, evidence-grounded loop:

$$\text{Data Change} \longrightarrow \text{Career Event Bus} \longrightarrow \text{Career Graph Engine} \longrightarrow \text{Longitudinal Memory} \longrightarrow \text{Context 2.0} \longrightarrow \text{Controlled Agent}$$

```
+───────────────────────────────────────────────────────────────────────────────────────────────────+
|                                    AIVANCE V2 SYSTEM TOPOGRAPHY                                   |
+───────────────────────────────────────────────────────────────────────────────────────────────────+
                      ┌───────────────────────────────────────────────────────┐
                      │                 MULTI-SURFACE CLIENTS                 │
                      │  Android App  │  Browser Extension  │  Web  │  CLI   │
                      └──────────────────────────┬────────────────────────────┘
                                                 │
                                                 ▼
                      ┌───────────────────────────────────────────────────────┐
                      │              CONTROLLED AGENT RUNTIME                 │
                      │  (Planner ──► Reasoner ──► Gate ──► Executor)         │
                      └──────────────────────────┬────────────────────────────┘
                                                 │
                                                 ▼
                      ┌───────────────────────────────────────────────────────┐
                      │             AI CONTEXT ENGINE 2.0 (TIERED)            │
                      │  [CRITICAL: Job, Resume] ──► [HIGH: ATS, Goal] ──►    │
                      │  [MEDIUM: Skills, Profile] ──► [LOW: Events]          │
                      └──────────────────────────┬────────────────────────────┘
                                                 │
                                                 ▼
                      ┌───────────────────────────────────────────────────────┐
                      │               CANONICAL CAREER GRAPH                  │
                      │  (15 Canonical Node Types, 14 Typed Edge Relations)   │
                      └──────────────┬─────────────────────────┬──────────────┘
                                     │                         │
                                     ▼                         ▼
                      ┌───────────────────────────┐ ┌─────────────────────────┐
                      │    LONGITUDINAL MEMORY    │ │    CAREER EVENT BUS     │
                      │  (Facts, Weaknesses,      │ │  (Decoupled Reactive    │
                      │   Evidences, Inferences)  │ │   Domain Stream)        │
                      └───────────────────────────┘ └─────────────────────────┘
                                     │                         │
                                     ▼                         ▼
                      ┌───────────────────────────────────────────────────────┐
                      │          LOCAL PERSISTENCE (OFFLINE-FIRST SSOT)       │
                      │  Room SQLite (v26 Schema) + Encrypted Tink DataStore  │
                      └───────────────────────────────────────────────────────┘
```

---

## 2. Canonical Career Graph Architecture (`:core:common:graph`, `:core:domain:careergraph`)

### 2.1 Canonical Node Types (`CareerNodeType`)
1. `PROFILE`: User identity, target role, work preferences, and contact details.
2. `GOAL`: Long-term career targets, compensation constraints, and milestone timeframes.
3. `SKILL`: Competency items tagged with taxonomy category and proficiency level.
4. `EXPERIENCE`: Professional employment history items.
5. `RESUME`: Master resume document entity.
6. `RESUME_VERSION`: Specific resume variant tailored for roles.
7. `JOB`: Discovered or saved job opportunity.
8. `COMPANY`: Prospective employer organization.
9. `RECRUITER`: Hiring point-of-contact with verified communication channels.
10. `APPLICATION`: Pipeline tracking record.
11. `INTERVIEW`: Scheduled or completed evaluation round.
12. `INTERVIEW_SESSION`: Multi-turn mock simulation record.
13. `COVER_LETTER`: Tailored outreach document.
14. `CAREER_EVENT`: Immutable event log record.
15. `CAREER_MEMORY`: Longitudinal weakness, strength, or evidence anchor.

### 2.2 Canonical Edge Relationships (`CareerEdgeType`)
* `HAS_SKILL`: `(Profile) ──► (Skill)`
* `HAS_GOAL`: `(Profile) ──► (Goal)`
* `HAS_RESUME`: `(Profile) ──► (Resume)`
* `HAS_VERSION`: `(Resume) ──► (ResumeVersion)`
* `CONTAINS_SKILL`: `(ResumeVersion) ──► (Skill)`
* `REQUIRES_SKILL`: `(Job) ──► (Skill)`
* `BELONGS_TO`: `(Job) ──► (Company)`
* `CONTACTED_BY`: `(Job) ──► (Recruiter)`
* `APPLIED_TO`: `(Profile) ──► (Application)`
* `FOR_JOB`: `(Application) ──► (Job)`
* `USES_RESUME`: `(Application) ──► (ResumeVersion)`
* `GENERATED_COVER_LETTER`: `(Application) ──► (CoverLetter)`
* `HAS_INTERVIEW`: `(Application) ──► (Interview)`
* `EVALUATED_BY`: `(Interview) ──► (InterviewSession)`
* `IDENTIFIED_WEAKNESS`: `(InterviewSession) ──► (CareerMemory)`
* `SUPPORTED_BY`: `(Skill) ──► (CareerMemory)`

---

## 3. Reactive Career Event Bus (`:core:common:events`)

Decoupled reactive communication replaces monolithic polling. Every event satisfies the mandatory audit envelope:
* `eventId: String` (UUID)
* `timestamp: Long` (Epoch ms)
* `correlationId: String?` (Workflow grouping)
* `causationId: String?` (Triggering command/event)
* `sourceModule: String` (Originating module)
* `eventType: String` (Canonical event discriminator)
* `payload: Map<String, Any?>` (Structured telemetry)

Supported event families:
* `ResumeEvent`: `Created`, `Updated`, `VersionCreated`, `AnalysisCompleted`, `SectionModified`, `Deleted`.
* `AtsEvent`: `ScanStarted`, `ScoreChanged`, `OptimizationCompleted`.
* `JobEvent`: `Discovered`, `Saved`, `Viewed`, `MatchCalculated`, `Applied`, `Archived`.
* `ApplicationEvent`: `Created`, `StageChanged`, `TaskCreated`, `TaskCompleted`.
* `InterviewEvent`: `Scheduled`, `Started`, `TurnEvaluated`, `Completed`, `Evaluated`.
* `SkillEvent`: `Detected`, `Updated`.
* `GoalEvent`: `Created`, `Changed`, `ProgressUpdated`, `Achieved`.
* `CoverLetterEvent`: `Created`, `Updated`.
* `ProviderEvent`: `Connected`, `Disconnected`, `HealthChanged`.
* `CareerAnalyticsEvent`: `ScoreChanged`, `InsightGenerated`.
* `AgentEvent`: `GoalCreated`, `PlanCreated`, `ActionProposed`, `ActionApproved`, `ActionRejected`, `ActionExecuted`.
* `AutomationEvent`: `RuleTriggered`.

---

## 4. AI Context Engine 2.0 (`:core:domain:context`)

Assembles prompt context using strict, tiered token budgets with deterministic truncation:

| Tier | Priority Weight | Included Context Components |
| :--- | :---: | :--- |
| **CRITICAL** | 4 | Target active Job posting, Active Resume summary |
| **HIGH** | 3 | Latest ATS match result, Active Career Goal, Recent Interview outcome |
| **MEDIUM** | 2 | Candidate Profile, Verified Skills from Career Graph |
| **LOW** | 1 | Recent Career Event audit log entries |

**Privacy Redaction**: All incoming context strings are scrubbed of emails, phone numbers, and government ID numbers before assembly.

---

## 5. Controlled Agent Runtime & Safety Invariants (`:core:domain:agent`)

### 5.1 Action Classification Matrix
* `READ_ONLY`: Queries, profile inspections, draft generation. Automatically permitted.
* `LOCAL_MUTATION`: Bookmarks, local settings updates. Optionally automatic.
* `EXTERNAL_SIDE_EFFECT`: Outbound emails, application submissions. **ALWAYS requires explicit human approval**.
* `DESTRUCTIVE`: Deleting resumes, wiping history. **ALWAYS requires explicit human approval + confirmation prompt**.

### 5.2 Deterministic Approval Gate Pipeline
$$\text{Action Proposal} \longrightarrow \text{Safety Scanned} \longrightarrow \text{Human Approval Gate} \longrightarrow \text{Executor} \longrightarrow \text{Execution Receipt with Undo Token}$$

---

## 6. Offline-First Single Source of Truth

* Local Room SQLite database serves as the absolute SSOT.
* Network operations produce explicit queued states without blocking UI.
* 100% of Career Graph queries, ATS rules, and memory retrievals execute locally.
