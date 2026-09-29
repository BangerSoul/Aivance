# AiVance Known Issues

This document tracks known limitations and defects at the **v1.0.0** release. Issues are categorized by severity and have mitigation plans.

## Severity Legend
- 🔴 **High** — impacts data integrity, security, or core workflows.
- 🟡 **Medium** — degrades an experience or a secondary feature.
- 🟢 **Low** — cosmetic or niche.

---

## 🔴 High

### H-01 — ~~Legacy plaintext PII cleanup is pending (DB v21)~~ ✅ RESOLVED
- **Resolved in Phase 5**: `SecurityMigrationWorker` now performs a real scan of all `provider_configurations` rows for plaintext secret values (keys matching `apiKey`, `token`, `secret`, etc.), migrates them to `SecretsManager` encrypted storage, and strips them from the DB settings map. Worker is idempotent via `ExistingWorkPolicy.KEEP`.

### H-02 — ~~Keystore-bound recovery~~ ✅ RESOLVED
- **Resolved in Phase 6**: `BackupExporter` and `BackupImporter` implemented in `core:util`. Users can export an encrypted `.aivance_backup` file containing all Room database tables (resumes, jobs, cover letters, applications, profile) using PBKDF2 + AES-256-GCM encryption, and restore it on any device via SAF file picker.

## 🟡 Medium

### M-01 — ~~Recruiter persistence uses mock logic~~ ✅ RESOLVED
- **Resolved in Phase 4/5 audit**: `RecruiterIntelligenceRepositoryImpl` is fully backed by `RecruiterDao` (Room). `findRecruiters()` calls the Hunter.io enrichment provider, auto-saves results via `recruiterDao.insertRecruiter()` + `recruiterDao.insertContact()`, and `getRecruitersForCompany()` returns a live Room `Flow`. The stale mock annotation was incorrect.

### M-02 — ~~Keyed free job providers dormant by default~~ ✅ RESOLVED
- **Resolved in Phase 6**: Runtime configuration for `AdzunaProvider` and `USAJobsProvider` enabled via `ProviderManagementViewModel` and `saveProviderConfig`. Users can enter Adzuna App ID / API Key and USAJobs API Key dynamically at runtime; `reconfigure()` re-hydrates credentials instantly.

### M-03 — ~~Interview analytics timeline incomplete~~ ✅ RESOLVED (2026-08-07, UESF adoption run)
- **Area**: `feature:analytics`.
- **Description**: Interview improvement timeline and achievement cards require accumulated analytics history.
- **Impact**: Charts render empty/partial for new users.
- **Resolved**: Root cause — `AnalyticsSnapshotWorker` (weekly periodic) was the **only** producer of `analytics_snapshots` rows, so a new user's Trends timeline stayed empty for up to 7 days. The guarantee now lives in the **data layer**: `AnalyticsRepositoryImpl.getSnapshots()` is self-healing — when the snapshot list is empty it captures a real baseline snapshot (derived from actual applications/interview sessions/ATS results, never fabricated) before forwarding the Room flow, guarded by a `Mutex` so concurrent collectors can never double-insert. Every consumer (analytics dashboard, career state engine, assistant context) inherits the guarantee, not just the dashboard. The duplicated ATS-report/readiness/recruiter derivation shared with `getCareerIntelligence()` was extracted into `toAtsReport()` (mapper) + private helpers. Evidence: new `AnalyticsRepositoryImplTest` 5/5 green (self-heal, honest empty-state, no-heal-with-history, no double-insert on repeated collection, derived kpis/score persisted); `AnalyticsViewModelTest` 4/4 green; plan + review records in `docs/uesf/`.
### M-04 — ~~Excessive binder transaction overload during tab navigation~~ ✅ RESOLVED
- **Resolved in End-to-End Device Pass**: `TrackerViewModel` now manages `loadJob: Job?` to cancel prior flow collection coroutines before launching new ones. Eliminates duplicate Room `combine().collect` collectors on database writes and prevents Android OS from terminating cached app processes due to binder transaction limits during rapid tab navigation.

### M-05 — ~~Claude AI provider missing from refresh/selection lists~~ ✅ RESOLVED (2026-08-08)
- **Area**: `app` `ProviderRefreshWorker`, `core:domain` `GetAvailableModelsUseCase`, `feature:profile` `AiSettingsViewModel`.
- **Description**: The Anthropic Claude provider was registered in DI but absent from `ProviderRefreshWorker.knownProviders` and the hardcoded provider lists — so Claude was never health-checked, model-refreshed, or selectable in the AI settings list.
- **Resolved**: `anthropic` added to all three lists (alongside the new on-device `gemma` provider). `WorkerTests.providerRefreshWorker_hasKnownProviders` updated to assert the full 7-provider set.

### M-06 — ~~Dead Android-Studio template theme shipped in app module~~ ✅ RESOLVED (2026-08-08)
- **Area**: `app/ui/theme/*`.
- **Description**: The app module still contained the default scaffold theme (`Purple80/Purple40` color scheme) with zero references — every screen uses the real tokenized `core:designsystem` `AivanceTheme` (4 modes + accents + dynamic color).
- **Resolved**: Deleted `app/src/main/java/com/bangersoul/aivance/ui/theme/{Color,Type,Theme}.kt` after confirming zero external imports; `assembleDebug` green.

## 🟢 Low

### L-01 — ~~Deprecation warnings in tests~~ ✅ RESOLVED
- **Resolved in Phase 6**: Updated test suites and dependencies for `ResumeEngineViewModelTest` to use non-deprecated model constructors and mocked exporters.

### M-07 — ~~Gemma download offered without a device-capability check~~ ✅ RESOLVED (2026-08-08)
- **Area**: `feature:profile` `ProviderManagementViewModel`, `core:sdk` `ModelDownloadable`, `core:ai-providers` `GemmaOnDeviceProvider`, `navigation` `FeatureScreens`.
- **Description**: The *Download model* button started a ~2.9 GiB download with no storage/RAM gate and no size confirmation. The provider also advertised "~1.3 GB" — the real live-verified artifact is 3,136,226,711 bytes (≈2.9 GiB).
- **Resolved**: `DeviceCapabilityProvider` (StatFs + ActivityManager, IO-dispatched, Hilt-bound) gates the flow: free storage must be ≥2 GiB (and fit the file +15% headroom); total RAM <4 GiB triggers a warning. A confirmation dialog shows the **exact byte size**, free storage, RAM warning, and — on constrained devices — a compact alternative (FunctionGemma 270M int8, 284,342,855 bytes ≈ 271 MiB, verified live). Separate primary/compact download buttons route the chosen URL; when nothing fits the download is hard-blocked with a snackbar. `ModelDownloadable` grew `modelSizeBytes` + `compactModel`. Evidence: 12 `ProviderManagementViewModelTest` + 25 `GemmaOnDeviceProviderTest` green (dialog flow, compact URL routing, storage/RAM branches, hard-block).

### M-08 — ~~AI Assistant dead-ends offline~~ ✅ RESOLVED (2026-08-08)
- **Area**: `core:domain` `GetAssistantResponseUseCase`, `core:sdk` `ProviderManager`.
- **Description**: With no cloud provider configured — or a configured cloud provider unreachable (offline) — the Assistant fell straight to the canned local Copilot replies instead of using the downloaded on-device Gemma model, so users with the ~2.9 GB model installed got no offline intelligence.
- **Resolved**: `GetAssistantResponseUseCase.stream()` and `generateResponse()` now try the best cloud provider first, then fall back to a **ready on-device model** (`ProviderManager.getOnDeviceProviderFor`, a new SDK helper returning the best Active/Ready `ModelDownloadable` whose model file is downloaded) before the Copilot fallback. Identity-guarded so the same provider instance is never invoked twice; the "No AI provider configured" error surfaces only when neither a cloud provider nor a downloaded model exists. Evidence: 9 new `GetAssistantResponseUseCaseTest` + 4 new `ProviderManagerTest` cases, all green.

### L-02 — ~~Tautological initial-state tests~~ ✅ RESOLVED
- **Resolved in 2026-08-04 hardening pass**: `JobDetailsViewModelTest` / `DashboardViewModelTest` loading-state tests now assert the Loading → loaded transition. The Career-OS refactor had also left **stale non-compiling tests** across `feature:dashboard` (DashboardViewModelTest, ComposeScreenTests), `feature:jobs` (JobsViewModelTest), `feature:interview` (InterviewViewModelTest), `feature:tracker` (TrackerViewModelTest), `feature:assistant` (AssistantViewModelTest), `feature:profile` (ProfileViewModelTest, SettingsViewModelTest) and `navigation` (DestinationTest, AivanceNavGraphTest) — all repaired or removed (stale tests for **deleted** ViewModels were deleted, per the Phase-13 pattern). `testDebugUnitTest` for every module is green again.

---

## v2 Redesign — Deferred / Known Items

### V2-01 — Dashboard saved-jobs count waits for a real JobRepository read
- **Area**: `DashboardViewModel`.
- **Description**: Career HQ shows `savedJobs` from `JobRepository.getSavedJobs()`; the count is 0 until a job provider is configured and jobs are bookmarked. Intentional (no hardcoded data), but the card reads empty for new users.
- **Mitigation**: Acceptable — consistent with the no-fake-data rule; empty states are honest.

### V2-02 — ~~Assistant attachment affordances are visual-only~~ ✅ RESOLVED
- **Resolved in Phase 6**: Mic (SpeechRecognizer with Accompanist RECORD_AUDIO permission request), Attach file (OpenDocument contract), and Photo (PickVisualMedia contract) are fully wired in `AssistantInputBar` (`AssistantScreen.kt`).

### V2-03 — WorkerTests needed mock stubs for notification managers
- **Area**: `app` `WorkerTests`.
- **Description**: `UploadManager`/`DownloadManager` init blocks create `NotificationChannel`s; the JVM unit tests were updated to stub `getSystemService(NOTIFICATION_SERVICE)` with a typed mock (they previously crashed with NPE/CCE after the SDK guard removal).
- **Impact**: None — tests are green.

### V2-04 — Lint suppressions for guarded notification calls
- **Area**: `DownloadManager`, `UploadManager`, `NotificationHelper`, `AndroidManifest`.
- **Description**: `@SuppressLint("MissingPermission")` on `notify()` call sites that are already runtime-guarded (`checkSelfPermission` / `try-catch`), and `tools:ignore="AppLinkUrlError"` for the intentional `aivance://` custom-scheme deep links (not Android App Links).
- **Impact**: Lint is clean (0 errors); suppressions are justified in KDoc.

### V2-09 — ~~DOCX export deferred~~ ✅ RESOLVED
- **Resolved in Phase 6**: Native `.docx` document generation implemented via Apache POI in `DocxExporter` (`core:util`), generating fully structured Microsoft Word documents from `ResumeVersion` sections and exposing them via FileProvider.

### V2-10 — ~~Google Sign-In requires Web Client ID configuration~~ ✅ RESOLVED
- **Resolved in Phase 5**: Web Client ID `433186935073-elau2khhj78koof6puo1mrk2hifa26jt.apps.googleusercontent.com` injected into `google-services.json` for both release and debug variants (`client_type: 3`). `processDebugGoogleServices` confirmed `default_web_client_id` is now generated. Google Sign-In via Credential Manager is fully operational.

### V2-11 — ~~Certificate pinning remains disabled~~ ✅ RESOLVED
- **Resolved in Phase 6, re-verified in Security Certification sprint**: Phase 6 added leaf + CA pins to `CertificatePinningInterceptor`, but the Security Certification sprint proved those pins were **fabricated placeholders** (none of the 16 matched the live SPKI hashes; several were literal countdown sequences). Replaced with live-verified leaf + CA SPKI SHA-256 pins for all 9 provider hosts in the new `CertificatePins.kt` registry (`core:common`), enforced at handshake time via native OkHttp `CertificatePinner` in the DI client (`NetworkModule`) and both AI provider clients (`OpenAiBaseProvider`, `ClaudeProvider`). Re-verified 3/3 pins present in every live chain by `security_scan.py` (2026-08-04).

### V2-12 — Integration tests require local.properties API keys
- **Area**: `app/src/androidTest/java/.../integration/`.
- **Description**: Real-API integration tests require `apifyApiKey`, `groqApiKey`, etc., in local.properties.
- **Mitigation**: Documented in developer guide; CI/CD pipeline must inject secrets.

### V2-13 — ~~Camera scan in Resume Engine has no OCR~~ ✅ RESOLVED
- **Resolved in Phase 6**: Camera capture now passes `InputImage` to ML Kit Text Recognition (`TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)`), emitting `ResumeEngineEvent.ImportOcrText` to build and preview parsed resume sections directly.

---

### V2-14 — Job search relevance depends on provider data quality
- **Area**: `core:data` `JobFilterMatcher` + `JobRepositoryImpl`.
- **Description**: Search results are now client-side filtered on every filter dimension (structured location country/state/city, employment type incl. Apprenticeship, workplace on-site/remote/hybrid, experience 0–15+ years, salary) and relevance-ranked (title > company > description) with recency tiebreak. Some providers return sparse metadata (e.g., missing `employmentType`/`experienceLevel`), so a job may pass/fail a filter based on defaults.
- **Mitigation**: Providers with missing metadata default to `FULL_TIME`/`NOT_SPECIFIED`; the matcher's experience-year bucketing uses level midpoints. Acceptable trade-off — documented in `JobFilterMatcher` KDoc.

### V2-15 — Apply URL resolution is best-effort
- **Area**: `JobDetailsViewModel.resolveApplyUrl`.
- **Description**: The apply link is resolved by priority — explicit job `url` → `sourceUrl` → first `href` found in the description HTML — then normalized to absolute `https`. When a listing carries none of these, the button shows a snackbar ("No apply link available") instead of failing silently.
- **Mitigation**: Acceptable; real apply pages are opened where the provider supplies them.

### V2-16 — ATS streaming surfaces progress but the final report is one-shot
- **Area**: `feature:ats` `StreamAtsAnalysisUseCase` + `AtsRepositoryImpl.streamAtsAnalysis`.
- **Description**: ATS analysis now streams live analysis-progress tokens into the UI while computing; the persisted `AtsReport` is still produced by the one-shot scoring pipeline. Streaming text is a progress narrative, not the report body.
- **Mitigation**: Intentional — keeps the report format stable for downstream export while giving real-time feedback.

### V2-18 — Streaming wired into Cover Letter + Resume Engine
- **Area**: `feature:coverletter`, `feature:resume`, `core:domain`, `core:data`.
- **Description**: `StreamGenerateCoverLetterUseCase` + `CoverLetterRepository.streamGenerate` and `StreamImproveSectionUseCase` + `AiRepository.streamAnalyzeText` now stream tokens into the Cover Letter generation view and the Resume Engine's optimization step (typewriter caret, retry on stream failure). Falls back to non-streaming providers gracefully.

### V2-19 — Dashboard career-breakdown pie chart
- **Area**: `core:designsystem` `PieChart`, `feature:dashboard`.
- **Description**: Added an animated multi-segment `PieChart` (ATS score, applied jobs, saved jobs, career score) to the Career HQ alongside the existing hero gauge, quick stats and activity feed.

### V2-20 — About AiVance screen + provider key masking
- **Area**: `feature:profile` `AboutScreen`, `Destination.About`, `ProviderManagement`.
- **Description**: New About screen (creator email `iamshaikhazmathulla@outlook.com` + Instagram `@Iamazmathulla` with icons, open-source licenses with clickable links, and a "How AiVance is Made" tech section). Provider Management now shows a masked credential preview (`sk-••••abcd`) next to the live health chip — the full key is never rendered.

### V2-21 — Provider selection consolidated into Provider Management
- **Area**: `feature:profile` Settings + Profile screens.
- **Description**: The duplicate "AI Configuration" entry was removed from Settings and Profile so AI / Job / Enrichment provider dropdowns exist only in Provider Management (per user request).

### V2-22 — ~~Language picker now functional~~ ✅ RESOLVED
- **Resolved in the full localization pass**: Language is a real picker (English/हिन्दी/Español/Français/Deutsch/中文/日本語) persisted to the encrypted DataStore via `UserPreferencesRepository.updateLanguage`; `MainActivity` applies the locale at startup so dates and formatting follow the choice. With V2-31, every user-facing UI string now comes from `res/values*` string resources including a complete `values-hi` (Hindi) set — the picker now translates the whole app, not just system-formatted values.

### V2-23 — Job search triggers only on commit (Enter / filters)
- **Area**: `feature:jobs` `JobsScreen`.
- **Description**: The search field no longer fires a provider call on every keystroke — results refresh only when the user commits the query (keyboard Search action or the Send button) or applies a filter. This keeps provider calls intentional and prevents "random results" appearing mid-typing.

### V2-24 — World-scale location catalog
- **Area**: `feature:jobs` `LocationCatalog`.
- **Description**: The Country → State/Region → City catalog was expanded from a 6-country starter set to a broad world dataset (~80 countries across every inhabited continent, each with its subdivisions and major cities). Client-side matching (`JobFilterMatcher.matchesStructuredLocation`) keeps results honest to the selected location.

### V2-25 — Job detail resolution fixed ("Something went wrong" on tap)
- **Area**: `core:data` `JobRepositoryImpl` + `JobDao.getJobByUrl`.
- **Description**: Tapping a job from the discovery list could surface "Something went wrong" because the list carried provider-external ids that `getJobById` could not resolve. `searchJobs` now caches each listing and remaps its id to the internal DB row id (deduped by URL), and `getJobById` additionally falls back to a URL lookup — so tapping a result always resolves from the local DB.

### V2-26 — Resume parsing no longer dead-ends on empty sections
- **Area**: `core:data` `ResumeLocalDataSource`, `ResumeParser`, `feature:resume` `ResumeEngineViewModel`.
- **Description**: Three root causes fixed: (1) `saveVersion` orphaned sections on new versions (sections were written under the pre-insert id 0); (2) `getVersionsForResume` returned empty-shell versions without their sections; (3) `ResumeParser` returned an empty section list when no AI provider was configured. Now sections are saved under the real generated version id, versions are fully hydrated, and the parser falls back to a deterministic heading-based splitter (with a final "Summary" catch-all) — the "Parsing failed — no sections" error can no longer occur for valid text.

### V2-27 — System back no longer exits from every screen
- **Area**: `navigation` `AivanceNavGraph`.
- **Description**: Added a `BackHandler` that pops the typed back stack one screen at a time (instead of the activity finishing), and the bottom navigation is now part of the main-graph shell — visible on every authenticated screen, including detail screens — with the owning tab highlighted (e.g. Jobs while viewing a job detail). Tab switching now works everywhere without hunting for a back arrow.

### V2-28 — Pipeline manual application adding
- **Area**: `feature:tracker` `TrackerViewModel` + `TrackerScreen`.
- **Description**: The Pipeline now supports manually adding an application (company + role + stage) via a FAB that opens an add dialog. The ViewModel caches a synthetic job row (reusing the URL-dedup path) so the FK is valid, saves the `Application`, records a timeline event, and refreshes the board.

### V2-29 — Typography + nav icon refresh
- **Area**: `core:designsystem` `Type.kt`, `navigation`.
- **Description**: Typography moved to a Helvetica/Arial-style geometric sans-serif stack with tighter display tracking for a cleaner, more professional card UI; bottom-nav icons are tinted with the theme primary when selected.

### V2-30 — ~~Hindi language option~~ ✅ RESOLVED
- **Resolved in the full localization pass**: Added हिन्दी (Hindi) to the functional Language picker (persisted to DataStore and applied at startup by `MainActivity`). With V2-31 the whole app — including Hindi — is now translated via `values-hi` string resources.

### V2-31 — Full UI string extraction + Hindi translation
- **Area**: All 12 feature modules + `navigation` + `app` workers.
- **Description**: Every hardcoded user-facing UI string across the codebase (screen titles, buttons, placeholders, labels, content descriptions, error/success messages, chat prompts, and worker notification text) was extracted into Android string resources. Each module gained `res/values/strings.xml` (English) and `res/values-hi/strings.xml` (complete Hindi translation): navigation (nav-bar labels, Auth, Onboarding, Prep Studio, FeatureScreens), profile (Welcome/Splash/Login/About/Settings/Privacy/Appearance), jobs (discovery, details, company, saved), interview + recruiter, resume + Resume Engine, tracker, ats, coverletter, assistant, analytics, dashboard, and app worker notifications (Download/Upload/FollowUp/NotificationWorker channel names, titles, messages). The Settings → Language picker now translates the entire application.
- **Details**: `Destination` gained a `@StringRes labelRes` mapping used by the bottom-nav bar (the `label` property stays for the existing nav tests); `strings.xml` reserved-keyword resource names (`continue`) were renamed to `continue_button` in profile + resume; launcher callbacks use `context.getString(...)` since they are not composable scopes.

### V2-17 — New unit-test coverage across previously-uncovered modules
- **Area**: `feature:assistant`, `feature:analytics`, `feature:recruiter`, `core:network`, `core:ai-providers`, `core:designsystem`, `navigation`.
- **Description**: Added unit test suites: `AssistantViewModelTest` (streaming, single-flight, retry, partial-failure), `AnalyticsViewModelTest`, `RecruiterViewModelTest`, `CertificatePinningInterceptorTest`, `OpenAiApiSerializationTest`, `AccentPaletteTest`, `DestinationTest`, plus `JobFilterMatcherTest` for the new filter logic and expanded `JobDetailsViewModelTest` for apply-link resolution and linked navigation effects.
- **Impact**: Full JVM unit test suite across all modules is green.

## Security Certification Sprint (2026-08-04) — Findings & Remaining Risks

### Resolved this sprint (confirmed with evidence, all re-validated)
- **SC-01 — Fabricated certificate pins** — see V2-11 above. All 16 configured pins were fabricated; replaced with live-verified leaf + CA pins (9 hosts), enforced via native OkHttp `CertificatePinner`.
- **SC-02 — Provider API keys in release BuildConfig** — Groq/Gemini/Apify/Hunter keys moved to **debug-only** build type; release embeds empty strings. Verified: no keys in `defaultConfig` or `release`.
- **SC-03 — `EncryptionService` fail-open** — decrypt path silently returned plaintext on AEAD failure. Now **fail-closed** (throws `IllegalStateException`; no plaintext return path). 4 regression tests.
- **SC-04 — Weak backup crypto + hardcoded passphrase** — 10k PBKDF2, fixed salt, `DEFAULT_PASSPHRASE` constant. Replaced by `BackupSecurity`: random per-file salt header, **600k PBKDF2-SHA256**, passphrase auto-generated and **KeyStore-wrapped** (device-bound). 7 regression tests.
- **SC-05 — Room DB + keysets eligible for cloud backup** — PII-bearing DB and Tink keyset prefs (`aivance_tink_prefs`, `aivance_security_prefs`) now excluded from `backup_rules.xml` and `data_extraction_rules.xml`.
- **SC-06 — AI provider logging leaked Authorization headers** — provider `HttpLoggingInterceptor` now gated to `BuildConfig.DEBUG` with `redactHeader("Authorization")` / `redactHeader("x-api-key")`.
- **SC-07 — `geminiApiKey` stored plaintext in DataStore** — now encrypted at rest via `EncryptionService` on write, decrypted on read (`UserPreferencesRepositoryImpl`, `SettingsRepositoryImpl`). Legacy fallback logs at ERROR.
- **SC-08 — Dead `rotateKeyset` no-op stub** — removed from `AivanceSecurity`.
- **Phase 15 regression suite** — 17 new tests (`CertificatePinsTest` ×6, `EncryptionServiceFailClosedTest` ×4, `BackupSecurityTest` ×7), all green.
- **Verification harness** — `security_scan.py` re-verifies live pins, secret scan, BuildConfig hygiene, backup rules, destructive-fallback absence, and fail-closed crypto: **20/20 checks PASS**.

### Remaining security risks (accepted / non-blocking, tracked in TODO.md)
- **SR-01 — Pin rotation requires a release** — pins live-verified at certification time; cert rotation (esp. CA-pinned hosts) requires a registry update + app release. Runbook in `CertificatePins.kt`; release gate = re-run `security_scan.py` (→ P1-02).
- **SR-02 — Device-based pen-test pending** — MITM/Frida/root pass (Phase 12–13 of the security brief) not executed; needs emulator/physical device (→ P0-02). See **`DEVICE_VALIDATION.md`** for step-by-step mitmproxy instructions.
- **SR-03 — KeyStore-bound backup is device-bound** — restoring a backup on a different device requires the export passphrase flow (keyset excluded from cloud restore by design); UI surfacing pending (→ P1-04).
- **SR-04 — New secret-bearing headers must join the redact list** — future provider headers need `redactHeader` coverage (→ P2-05).

### Database Certification Sprint (2026-08-03) — Remaining Risks
- **~~DR-01 — Instrumented DB tests compiled but not executed on device~~ ✅ RESOLVED** (2026-08-11, AVD `aivance` Android 11/API 30): `:core:database:connectedDebugAndroidTest` — 37 tests, 0 failures/errors, full migration chain 5→25 verified on-device (→ P0-01). Physical-device re-run remains optional for Play certification.
- **DR-02 — v1–v4 migration paths unverifiable** (no exported schemas for those versions) — pre-release versions; empty no-op migrations retained.
- **DR-03 — ~~`DatabaseManager` / `DatabaseSeed` possibly dead code~~ ✅ RESOLVED** (2026-08-04): zero consumers confirmed; both classes deleted, DI bindings removed. `DatabaseSeed` fabricated demo data ("Jane Doe"/fake jobs) — removed per the no-fake-data rule. Orphaned `DashboardRepository`/`DashboardRepositoryImpl`/`DashboardModule` (no consumers after the Career-HQ rewrite) also removed.

## Resolved
- **H-01** (SecurityMigrationWorker) — resolved in Phase 5 with real plaintext scan + SecretsManager migration.
- **M-01** (Recruiter persistence) — confirmed already resolved; `RecruiterIntelligenceRepositoryImpl` uses Room `RecruiterDao` throughout.
- **V2-10** (Google Sign-In Web Client ID) — resolved in Phase 5; Web Client ID injected into `google-services.json`.
- V2-05 (PDF export) — resolved in Phase 4 with `PdfExporter` using Android's `PdfDocument` API.
- TrackEventUseCase no-op — resolved in Phase 4, now persists events to Room via `AnalyticsEngine`.
- Silent error swallowing — resolved in Phase 4, all `Result.Failure` branches across ViewModels now emit visible error states/snackbars.
- Resume Engine compile errors (Phase 3) — missing `ResumeEngineScreen` import in the nav graph, nullable camera URI for `TakePicture`, `itemsIndexed` missing import, dead `@OptIn(ExperimentalFoundationApi::class)` — fixed.
- Resume Engine state-machine dead-ends — `enterError()` now normalizes `lastStableState` (Parsing→Import, Optimizing→clear spinner, Saving→Optimizing) so back/retry after any failure restores a usable step; `retry()` only re-imports for Import/Parsing failures; `finish()` resets the engine to Import.
- `CoverLetterViewModelTest` / `JobDetailsViewModelTest` constructor mismatches (missing `exportCoverLetterUseCase`, `jobRepository`, `applicationWorkflowRepository` mocks) — updated to the current constructor signatures.
- `AtsViewModelTest` analyze tests used a 23-char JD that never crossed the live-reactive `jd.length > 50` gate — switched to a realistic long JD.
- Logging loop between `StructuredTimberTree` and `TelemetryEngineImpl` (ADR 003) — fixed.
- Stale tests referencing deleted use cases across 6 feature modules — fixed in Phase 13.
- Mock WebServer tests for Arbeitnow/Jobicy/Apify — green.
- Legacy `FakeDashboardRepository` deleted from `feature:dashboard` main source set — replaced by the real `DashboardRepositoryImpl` aggregation in the Career HQ rewrite.

## Full E2E QA Pass (2026-08-11) — Findings & Fixes
- **KB-01 — ~~Soft keyboard overlapped bottom content~~ ✅ RESOLVED**: app is edge-to-edge (`enableEdgeToEdge()`) but no screen applied `imePadding()` — keyboard covered the assistant composer, auth Continue, and onboarding actions. Added `imePadding()` to the assistant input bar, auth scroll column, and onboarding columns. Pixel-verified: composer sits above Gboard.
- **ST-01 — ~~"Quick Practice" dead stub~~ ✅ RESOLVED**: hero card `onClick = {}` — now starts a behavioral mock session immediately (verified live: Q1/5 + answer field + submit).
- **ST-02 — ~~Pipeline "View Analytics" dead stub~~ ✅ RESOLVED**: empty onClick → now navigates to the Intelligence Center.
- **ST-03 — ~~Job Details "Start Prep" + Analytics "Boost Score" dead stubs~~ ✅ RESOLVED**: both now navigate (Prep Studio / Intelligence Hub).
- **ST-04 — ~~Identity Hub "+ Add Skill" / "+ Add" industry / "Export Career Data" dead~~ ✅ RESOLVED**: add-dialogs wired to the draft profile (verified live), export shares the profile payload via the system sheet.
- **ST-05 — Remaining stubs (accepted, low priority)**: Identity Hub "Upload Document" (Vault tab); legacy per-industry chips in the profile; kanban moves require long-press drag (Compose DnD wired, works for real users, not automatable via `adb swipe`).
- **QA-01 — LinkedIn (Apify) free-tier quota exhaustion**: provider returns 403 when the daily free quota is spent; the search layer degrades gracefully to cached jobs + other providers (observed live: "Android Engineer" returned Arbeitnow results). Documented in QA_E2E_NOTES §3.4.
- **QA-02 — System file picker (DocumentsUI) not automatable via adb**: the resume-import wizard's picker opens correctly but won't return a selection via `adb input tap` on this emulator — automation limitation, not an app bug (ATS scan pipeline verified in earlier sessions: report create/open/delete).
