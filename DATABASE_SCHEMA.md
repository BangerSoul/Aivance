# AiVance Database Schema

## Overview

- **Engine**: AndroidX Room (SQLite).
- **Current version**: **25**.
- **Schema location**: `core/database/schemas/com.bangersoul.aivance.core.database.AivanceDatabase/` (exported JSON per version).
- **Encryption at rest**: PII columns are stored as `EncryptedString` (AES-GCM via Google Tink, keys held in Android Keystore) and transparently converted by `EncryptedTypeConverters` (`ProvidedTypeConverter`).
- **Secrets**: API keys are **not** in this database. They live in an encrypted DataStore (`:core:datastore` → `SecretsManager`).

## Migration Chain

| From | To | Notes |
| :--- | :--- | :--- |
| 1 … 9 | … | Historical development schema versions. |
| 9 → 10 | 10 | Unified `provider_configurations` (type, selectedModel, actorId, etc.). |
| 10 → 11 | 11 | Normalized Resume → Version → Section schema. |
| 11 → 12 | 12 | ATS report + job description entities. |
| 12 → 13 | 13 | Job discovery cache and metadata. |
| 13 → 14 | 14 | Recruiter CRM tables (companies, recruiters, communications). |
| 14 → 15 | 15 | Cover letter versions and sections. |
| 15 → 16 | 16 | Interview session orchestration entities. |
| 16 → 17 | 17 | Application Workflow (aggregate root `Application`). |
| 17 → 18 | 18 | Analytics snapshots and career score. |
| 18 → 19 | 19 | Assistant conversations and messages. |
| 19 → 20 | 20 | Security hardening: PII → `EncryptedString`, audit logs; `apiKey` removed from `provider_configurations`. |
| 20 → 21 | 21 | `users` table (Google Sign-In profile storage). |
| 21 → 22 | 22 | `interview_questions` rebuilt with NOT NULL `isFavorite`. |
| 22 → 23 | 23 | `user_profiles` rebuilt with 9 career-preference columns. |
| 23 → 24 | 24 | Schema-alignment no-op (identical schema, version bump only). |
| 24 → 25 | 25 | Legacy `resume_analyses` dropped — superseded by `ats_reports` (T-04). |

> Migrations are defined in `AivanceDatabase` companion. The destructive plaintext cleanup performed by `SecurityMigrationWorker` (DB v21-era rows) runs as a worker, not a migration.

## Entity Inventory (v25)

### Provider & Configuration
- `ProviderConfigurationEntity` — provider type, selected model, actor ID, enabled flags. **(no `apiKey` — moved to encrypted DataStore)**

### Resume (version-centric)
- `ResumeEntity` → `ResumeVersionEntity` (1:N) → `ResumeSectionEntity` (1:N).

### ATS
- `AtsReportEntity` — overall score, match percentage, matched/missing keywords, section scores, optimization tips (JSON), generated date; FK to `resume_versions` + `job_descriptions` (CASCADE).
- `JobDescriptionEntity` — normalized JD storage (required FK parent for `ats_reports.jobDescriptionId`).

### Jobs
- `JobListingEntity` — normalized, provider-tagged listing cache.
- `JobDao` includes `deleteAllJobs()` used by `RoomJobCache.clear()`.

### Career CRM (Recruiter)
- `CompanyEntity`, `RecruiterEntity` (PII emails encrypted), `CommunicationHistoryEntity` (outreach content encrypted).

### Cover Letter
- `CoverLetterEntity` → `CoverLetterSectionEntity` (1:N), versioned.

### Interview
- `InterviewSessionEntity` → `InterviewQuestionEntity` → `InterviewAnswerEntity` + `InterviewEvaluationEntity` (per-answer AI deep-dive).

### Workflow / Pipeline
- `ApplicationEntity` (aggregate root; links job, resume version, ATS report, cover letter version).
- `ApplicationStageEntity`, `TimelineEventEntity`, `ApplicationTaskEntity`.

### Analytics
- `AnalyticsSnapshotEntity` — KPI snapshots.
- `CareerScoreEntity` — four-dimension explainable score.
- `AuditLogEntity` — system action log (Privacy Center).

### Assistant
- `AssistantConversationEntity` → `AssistantMessageEntity` (1:N).

## Conventions

- Every table carries `id` (autoincrement) and `lastModified` where relevant.
- PII columns (`email`, `rawText`, outreach content, transcript text) use `EncryptedString` — ciphertext at rest, plaintext only in memory.
- Foreign keys are enforced where relationships are mandatory; cascades are explicit.
- Schema JSON exports are committed for the current lineage (v10–25) to support migration testing and Room's `exportSchema`; earlier versions (1–9) are historical.

## Backup & Migration Safety

- **Upgrades**: Room runs the migration chain automatically; no data loss for any v1 → v25 upgrade (the only table dropped in the modern chain is the legacy `resume_analyses` at 24 → 25, intentionally discarded).
- **Clean install**: fresh schema v25 is created directly.
- **Keystore note**: encrypted data is bound to the device Keystore key. Backing up the database alone is insufficient — restore must occur on a device with the same Keystore key or via an encrypted export/import flow.
