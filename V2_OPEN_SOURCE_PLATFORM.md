# AiVance V2 — Open-Source Platformization & Licensing Strategy

**Document Type:** Open-Source Platform Architecture & Licensing Governance  
**Target Repository:** `IamAzmathullaShaikh/Aivance`  
**Platform Lead:** Open-Source Platform Architect  
**Status:** Approved Platform Roadmap  

---

## 1. Licensing Audit & Determination

### 1.1 Current Repository Status: PROPRIETARY
An audit of the repository's root files reveals an explicit legal contradiction:
* [`LICENSE`](file:///c:/Users/BangerSoul/Desktop/Projects/Aivance/LICENSE) explicitly states:
  > *"AiVance – Proprietary License ... You may not copy, modify, distribute, sublicense, or otherwise make available the source code or compiled binaries ... Private Repository ... Access does not constitute a license to use AiVance in your own products or services."*
* [`CONTRIBUTING.md`](file:///c:/Users/BangerSoul/Desktop/Projects/Aivance/CONTRIBUTING.md), conversely, invites open-source contributors with standard PR and issue templates.

**Definitive Legal Determination**: AiVance is currently **Source Available / Proprietary**, NOT Open Source. It does not possess an OSI-approved license.

### 1.2 Licensing Migration Recommendation: OPEN CORE (or Pure Apache-2.0)
To fulfill the vision of becoming the reference open-source Career OS while protecting commercial assets:
* **Recommended Strategy: Open Core Model**:
  1. **Permissive Open Core (`Apache-2.0`)**:
     - `career-schema/` (Open standards for all platforms)
     - `provider-sdk/` (Enables third-party plugin authors to build providers)
     - `agent-sdk/` (Controlled agent execution contracts)
     - `evaluation/` (Public benchmark datasets and harnesses)
     - Multiplatform core domain logic (`core:common`, `core:domain`)
  2. **Commercial / Pro Edition**:
     - Proprietary cloud sync relay, enterprise coaching seat federation, and Google Play commercial distribution binaries.
* **Alternative Strategy: Pure OSI Open Source (`Apache-2.0` across entire repo)**:
  - Grants complete freedom to developers, maximizes GitHub stars, forks, and external contributor velocity.
* **Governance Invariant**: **DO NOT silently alter `LICENSE`**. The project owner must formally approve the transition before committing the Apache-2.0 license file.

---

## 2. Platform Extension Points

AiVance is architected around 5 decoupled extension points allowing third parties to contribute without modifying core systems:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        AIVANCE EXTENSION POINTS                         │
├───────────────────────┬─────────────────────────┬───────────────────────┤
│ Extension Point       │ Target Interface        │ Packaging             │
├───────────────────────┼─────────────────────────┼───────────────────────┤
│ 1. AI Provider        │ `AIProvider`            │ Standalone Maven Jar  │
│ 2. Job Search Feed    │ `JobProvider`           │ Standalone Maven Jar  │
│ 3. Career Skill Tool  │ `AgentStepExecutor`     │ Domain Plugin SPI     │
│ 4. Event Subscriber   │ `CareerEventListener`   │ Event Bus Consumer    │
│ 5. Schema Definition  │ JSON Schema Draft 2020  │ `career-schema/*.json`│
└───────────────────────┴─────────────────────────┴───────────────────────┘
```

---

## 3. Contributor Guides: "Add Your First..."

### 3.1 "Add Your First Job Provider" (in < 30 Minutes)
1. Subclass `JobProvider` in a new package:
   ```kotlin
   class WeWorkRemotelyProvider @Inject constructor(
       metadata: ProviderMetadata,
       capabilities: Set<ProviderCapability>
   ) : JobProvider(metadata, capabilities) {
       override suspend fun searchJobs(filter: JobSearchFilter, sortOrder: JobSortOrder, page: Int): Result<List<JobListing>> {
           // Implement feed query
       }
       override suspend fun getJobDetails(jobId: String): Result<JobListing> { ... }
   }
   ```
2. Bind into Hilt multi-binding via `@IntoSet`.
3. Add mock integration test inheriting `BaseJobProviderTest`.

### 3.2 "Add Your First Career Event"
1. Define typed event in `CareerEvent.kt`:
   ```kotlin
   data class PortfolioLinked(val portfolioUrl: String, ...) : SkillEvent
   ```
2. Emit through `CareerEventDispatcher`:
   ```kotlin
   eventDispatcher.dispatch(PortfolioLinked("https://github.com/alice"))
   ```
3. Subscribe reactively in any module using `eventBus.eventsOfType<PortfolioLinked>()`.

### 3.3 "Add Your First Agent Action"
1. Define step type in `AgentStepType` and action classification (`READ_ONLY`, `LOCAL_MUTATION`, `EXTERNAL_SIDE_EFFECT`, `DESTRUCTIVE`).
2. Implement execution handler in `AgentExecutor`.
3. If classified as `EXTERNAL_SIDE_EFFECT` or `DESTRUCTIVE`, the `HumanApprovalGate` automatically intercepts execution and prompts the user.
