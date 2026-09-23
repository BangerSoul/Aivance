# AiVance V2 — Database & Architectural Migration Plan

**Document Type:** Permanent Migration Specification & Rollback Procedure  
**Target Repository:** `IamAzmathullaShaikh/Aivance`  
**Current Baseline:** Room Schema Version 25  
**Target Version:** Room Schema Version 26  
**Status:** Canonical Migration Guide  

---

## 1. Zero Data Loss Policy & Migration Invariants

1. **Non-Destructive Guarantee**: User career data (resumes, application history, interview feedback, notes) must **NEVER** be dropped, overwritten, or cleared using `fallbackToDestructiveMigration()`.
2. **Deterministic Pre-Migration Snapshot**: A binary SQLite snapshot and JSON export of user tables must be created prior to executing schema alterations.
3. **Rollback Reversibility**: Every schema migration must declare an inverse SQL procedure and verification validation check.

---

## 2. Entity Mapping & Consolidation Matrix

| Legacy V1 Concept | Table / Storage | Canonical V2 Target | Migration Strategy | Rollback Strategy |
| :--- | :--- | :--- | :--- | :--- |
| `JobApplicationEntity` | `job_applications` | `ApplicationEntity` (`applications`) | Copy records to `applications`; preserve legacy IDs; maintain backward read-view | Re-point TrackerDao to `job_applications` table |
| `GoalEntity` | `career_goals` | `CareerGoal` (`core:domain:agent`) | Retain table; add canonical JSON metadata column for constraints & target companies | Drop metadata column |
| `AutomationRuleEntity`| `automation_rules` | `ActionProposal` / Agent Policies | Read active rules into agent runtime; retain table for legacy trigger queries | None required |
| `UserProfileEntity` | `user_profiles` | `CareerGraphNode` (`PROFILE`) | Project profile and skills into Graph nodes via `CareerGraphEngine` | Fall back to flat profile query |
| Unindexed Weaknesses | `interview_evaluations`| `CareerMemoryEntry` (`INTERVIEW_WEAKNESS`) | Extract `improvements` list into `career_memory_entries` table | Query raw JSON in `interview_evaluations` |

---

## 3. Schema Migration v25 ──► v26 Specification

### 3.1 Migration SQL Execution
```sql
-- Migration 25 -> 26

-- 1. Create canonical Career Graph Node table
CREATE TABLE IF NOT EXISTS `graph_nodes` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `type` TEXT NOT NULL,
    `label` TEXT NOT NULL,
    `properties_json` TEXT NOT NULL,
    `created_at` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS `idx_graph_nodes_type` ON `graph_nodes` (`type`);

-- 2. Create canonical Career Graph Edge table
CREATE TABLE IF NOT EXISTS `graph_edges` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `source_id` TEXT NOT NULL,
    `target_id` TEXT NOT NULL,
    `relation_type` TEXT NOT NULL,
    `weight` REAL NOT NULL DEFAULT 1.0,
    `properties_json` TEXT NOT NULL,
    `created_at` INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS `idx_graph_edges_source` ON `graph_edges` (`source_id`, `relation_type`);
CREATE INDEX IF NOT EXISTS `idx_graph_edges_target` ON `graph_edges` (`target_id`, `relation_type`);

-- 3. Create persistent Career Event Log table
CREATE TABLE IF NOT EXISTS `career_event_log` (
    `event_id` TEXT NOT NULL PRIMARY KEY,
    `timestamp` INTEGER NOT NULL,
    `correlation_id` TEXT,
    `causation_id` TEXT,
    `source_module` TEXT NOT NULL,
    `event_type` TEXT NOT NULL,
    `payload_json` TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS `idx_career_event_log_type_time` ON `career_event_log` (`event_type`, `timestamp`);

-- 4. Create Career Memory table
CREATE TABLE IF NOT EXISTS `career_memory_entries` (
    `memory_id` TEXT NOT NULL PRIMARY KEY,
    `type` TEXT NOT NULL,
    `content` TEXT NOT NULL,
    `created_at` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL,
    `confidence` REAL NOT NULL,
    `source_event_ids_json` TEXT NOT NULL,
    `evidence_refs_json` TEXT NOT NULL,
    `is_user_confirmed` INTEGER NOT NULL DEFAULT 0,
    `expiration_timestamp` INTEGER
);
CREATE INDEX IF NOT EXISTS `idx_career_memory_type` ON `career_memory_entries` (`type`, `is_user_confirmed`);

-- 5. Data Migration: Copy legacy job_applications to applications if not already present
INSERT OR IGNORE INTO `applications` (id, jobId, resumeVersionId, atsReportId, coverLetterVersionId, currentStageId, status, dateApplied, lastModified, notes)
SELECT id, 0, NULL, NULL, NULL, status, 'ACTIVE', dateApplied, lastModified, notes
FROM `job_applications`;
```

---

## 4. Migration Validation Procedure

1. **Count Verification**:
   ```sql
   SELECT COUNT(*) FROM job_applications;
   SELECT COUNT(*) FROM applications;
   -- Ensure applications count >= legacy job_applications count
   ```
2. **Schema Export Comparison**:
   Run `./gradlew testDebugUnitTest` with Room schema export enabled (`exportSchema = true`) and compare `schemas/26.json` with baseline `25.json`.
3. **Integrity Check**:
   Run `PRAGMA integrity_check;` and `PRAGMA foreign_key_check;`.

---

## 5. Rollback Strategy

In the event of a critical migration failure on client devices:
1. Revert `version = 25` in `AivanceDatabase.kt`.
2. Provide downward migration `MIGRATION_26_25` that drops newly created tables (`graph_nodes`, `graph_edges`, `career_event_log`, `career_memory_entries`) while leaving all core tables completely intact.
