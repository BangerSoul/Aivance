# AiVance — Technical Debt Report

**Version**: 1.0.0 · **Last updated**: 2026-08-11

Debt is categorized by the horizon in which it must be addressed. Each item has an owner and mitigation plan.

---

## 🔴 Immediate (blocking nothing, but fix before v1.1)

### ~~T-01 — SecurityMigrationWorker is a skeleton~~ ✅ RESOLVED (2026-08-10)
- **Area**: `:app` → `SecurityMigrationWorker`.
- **Resolved**: A code audit (2026-08-10) confirmed the worker is **fully implemented**: scans every `provider_configurations` row for plaintext secret values (keys matching `apiKey`, `token`, `secret`, etc.), migrates them to `SecretsManager` encrypted storage, strips them from the DB settings map, and is idempotent via `ExistingWorkPolicy.KEEP`. The debt register entry was stale.

### ~~T-02 — Recruiter persistence mock logic~~ ✅ RESOLVED (2026-08-10)
- **Area**: `RecruiterIntelligenceRepository` (`core:data`).
- **Resolved**: A code audit (2026-08-10) confirmed `RecruiterIntelligenceRepositoryImpl` is fully backed by `RecruiterDao` (Room): `getRecruitersForCompany()` is a live Room `Flow`, `findRecruiters()` calls Hunter.io then auto-saves via `saveRecruiter()`, and `saveRecruiter()` inserts via `recruiterDao.insertRecruiter()` + `recruiterDao.insertContact()`. This matches the KNOWN_ISSUES M-01 resolution recorded earlier — the debt register entry was stale.

## 🟡 Short-term (v1.1–v1.2)

### ~~T-03 — Runtime config path for keyed job providers~~ ✅ RESOLVED (2026-08-11)
- **Resolved**: The formal ProviderFactory-style config path is now enforced at the SDK level and keyed job providers are onboarded through Provider Management:
  - **SDK**: `ProviderFactory.createProvider(ProviderConfiguration)` typed overload + the shared factory config-map contract (`ProviderConfiguration.toFactoryMap()` / `credential()`) — documented in the `ProviderFactory` KDoc (settings = plaintext preferences, secrets = encrypted credentials; every provider module must register a `@IntoMap @StringKey` factory binding).
  - **Job providers**: `adzuna` + `usajobs` factories registered in `JobProvidersModule` (previously missing, so `createProvider` threw for job providers), constructing configured instances from persisted settings/secrets.
  - **Provider Management**: the hardcoded single API-key field + `adzuna` `"appId:appKey"` special-case replaced by metadata-driven credential forms — every `ConfigField` renders as its own input (Adzuna = App ID + API Key, USAJobs = API Key) and is routed by sensitivity (PASSWORD/sensitive → encrypted secrets, else plaintext settings), mirroring the onboarding flow.
- **Evidence**: 6 new `ProviderFactoryTest`, 5 new `JobProvidersModuleTest` factory cases, 1 new `ProviderManagementViewModelTest` secret-routing case — all green; full `testDebugUnitTest` + `assembleDebug` green.

### ~~T-03b — Keystore-bound recovery UX~~ ✅ RESOLVED (2026-08-06, P1-04)
- **Resolved**: `BackupImporter` detects passphrase mismatch on cross-device restore and `PrivacyCenterScreen` presents an interactive passphrase prompt dialog. Documented in `KNOWN_ISSUES.md` P1-04.

### ~~T-04 — Deprecated core models (`AtsResult`, `ResumeAnalysis`)~~ ✅ RESOLVED (2026-08-11)
- **Resolved**: The AtsReport migration is complete. `AtsResult`, `ResumeAnalysis`, their DTOs/requests, `ResumeAnalysisEntity` and the legacy `resume_analyses` DAO methods were deleted. `ResumeRepository.analyzeResume`, `CalculateATSScoreUseCase` and `AnalyseResumeUseCase` now return a persisted `AtsReport` (job description saved first so the enforced FK holds; score clamping kept at the domain boundary; legacy `formattingScore` surfaces as a "Formatting" `OptimizationTip`). Database **v25** (`MIGRATION_24_25`) drops the legacy `resume_analyses` table.
- **Evidence**: full `testDebugUnitTest` + `assembleDebug` green across all modules; `migration_validate.py` and `db_certify.py` replayed to v25 (0 issues, 20 upgrade paths OK); `test_sql_check.py` validates the new migration-test SQL; new `migrate24To25`/`migrate10To25` instrumented migration tests.

### ~~T-05 — Coverage gaps in tests~~ ✅ RESOLVED (2026-08-11)
- **Resolved**: A test-strength pass eliminated the tautological initial-state assertions and closed the event-verification gaps across the ViewModel suite (+33 tests):
  - **De-tautologized**: `AtsViewModelTest` init now asserts `loadResumes()` actually populates the `resumes` flow (plus the resume-load failure → Error path); `HomeViewModelTest` asserts the Success state's content (full quick-action set + greeting); bare `initial state is X` assertions replaced with behavior checks (ATS init, cover-letter Load → Success, OCR/JSON imports).
  - **Event verification added**: `InterviewViewModel.SubmitAnswer` success/failure/no-op (answers persist with the correct `MessageSender`); ATS `GenerateCoverLetter` + `ExportReport` effects with report content; cover-letter edit mode (`ToggleEdit`/`UpdateSection`/`SaveEdits` with draft capture and rollback, failure snackbar), `CopyAll`, PDF `Export`, `RegenerateSection` failure, and both `GenerateForJob` paths (no-resume snackbar, with-resume stream); `JobsViewModel.ClearFilters` (non-query dimensions reset, query kept) + `Refresh` (re-runs current search); `SavedJobsViewModel.ViewDetails`/`Refresh`/remove-failure; `ResumeEngineViewModel` OCR import (valid + blank), JSON Resume import (valid + malformed), `UpdateSectionContent`, `DiscardSuggestion` (suggestion removed without touching the version); `NotificationsViewModel.Refresh` tracking + safe no-op `MarkAsRead`/`Delete` on the empty list.
- **Evidence**: full `testDebugUnitTest` + `assembleDebug` green across all modules (1085 tasks).

### T-06 — Automated accessibility UI tests
- **Debt**: Accessibility validated by guide + manual QA; no automated a11y tests in CI.
- **Mitigation**: Add Compose a11y UI tests for critical flows in v1.1.

## 🟢 Long-term (v2.0+)

### T-07 — Logging abstraction
- **Debt**: Telemetry was decoupled from Timber (ADR 003) with raw `android.util.Log`; a robust abstraction is still pending.
- **Mitigation**: Introduce a release-grade logging facade in v2.0.

### T-08 — Sync/backup restore
- **Debt**: Encrypted data is device-bound; no encrypted export/import or server sync.
- **Mitigation**: Secure blob sync (zero-knowledge server) with encrypted export/import in v1.1/v2.0.

### T-09 — Legacy `SyncManager` endpoints
- **Debt**: `SyncManager` operations were local placeholders; remote sync endpoints do not exist.
- **Mitigation**: Replace with the v2.0 sync service; remove placeholder code.

### T-10 — Duplicated deprecated DTO paths
- **Debt**: Multiple DTO/mapper layers for jobs (feature-local `JobListing` vs core) exist.
- **Mitigation**: Consolidate on core models in a future refactor.

---

## Debt Summary

| Horizon | Items | Resolved | Open | Release impact |
| :--- | :--- | :--- | :--- | :--- |
| Immediate | 2 | 2 | 0 | None |
| Short-term | 5 | 4 (T-03, T-03b, T-04, T-05) | 1 (T-06) | None |
| Long-term | 4 | 0 | 4 | None — scheduled v2.0+. |

## Principles Going Forward

1. **No new debt in release builds**: every PR must compile, pass tests, and lint in CI.
2. **Pay down with feature work**: T-01–T-04 are prioritized for v1.1.
3. **Frozen contracts**: none of the above requires breaking a v1.0.0 contract.
