# Changelog

All notable changes to AiVance are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed — Keyboard/IME overlap on edge-to-edge screens (2026-08-11)
- The app runs `enableEdgeToEdge()` but no screen applied `imePadding()`, so the soft keyboard overlapped bottom content (assistant composer, auth Continue button, onboarding actions). Added `imePadding()` to the assistant input bar, the auth scroll column, and the onboarding step columns. Verified live: the composer now sits above Gboard (screenshot pixel-checked).

### Fixed — Dead buttons wired (Quick Practice, Analytics, Prep, Export, Add Skill/Industry) (2026-08-11)
- **Prep Studio "Quick Practice"** had an empty `onClick` (`/* Start session for upcoming */`) — now starts a behavioral mock immediately (upcoming interview role, or a default "Software Engineer" session). Verified live: a 5-question mock session starts.
- **Pipeline "View Analytics"** had an empty onClick — now navigates to the Intelligence Center (verified live).
- **Job Details "Start Prep"** (Readiness tab) had an empty onClick — now navigates to Prep Studio.
- **Analytics "Boost Score"** had an empty onClick — now navigates to the Intelligence Hub.
- **Identity Hub "+ Add Skill" / "+ Add" (industry)** chips were dead — now open a text dialog that adds to the draft profile (verified live: "Jetpack Compose" added).
- **Identity Hub "Export Career Data"** was dead — now shares the profile as portable text via the system share sheet (verified live).
- **Known remaining stubs**: Identity Hub "Upload Document" (Vault tab) and the legacy per-industry chips (`onClick = {}`) — lower priority, no wiring exists yet.

### Fixed — Assistant intent routing keys off the raw user message (2026-08-11)
- The keyword intent detector ran against the **orchestrated** prompt, which embeds platform context including "Latest ATS Score: 0%". Because `ats` is a route keyword, **every** message was misrouted to `ANALYZE_RESUME` and short-circuited to a canned response — the LLM (cloud or on-device) was never reached, so the assistant could never hold open-ended conversation.
- `AssistantRequest` now carries `rawUserMessage` (defaults to `userMessage` for compat); `GetAssistantResponseUseCase` routes intents off the raw text only, while the context-rich prompt still goes to the LLM. `AssistantViewModel` passes the unmodified user text as `rawUserMessage`.
- **Tests**: `routes intent on the raw message, not the context-laden orchestrated prompt` + `still routes capability intents detected from the raw message`. Verified live: with airplane mode on, "What is the capital of France" now reaches the on-device Gemma model instead of returning "No resume found".

### Fixed — Assistant provider chip only labels AI providers (2026-08-11)
- The chip picked the first *Ready* provider of any type, so a healthy job feed (e.g. "Naukri") was labeled as the assistant's AI provider. It now filters to providers with the AI Chat capability (verified live: chip shows "Ollama · Ready" / "Gemma", never a job provider).
- **Test**: `provider status never labels a job provider as the AI provider`.

### Added — Delete saved ATS reports from the Intelligence Hub (2026-08-11)
- Each Recent ATS Scans card now has a **delete icon**; tapping it opens a confirmation dialog ("Delete ATS report? … removed permanently") and the confirmed report is removed via `AtsRepository.deleteReport` — the hub's live `getAllReports` flow drops the card immediately.
- **Test**: `IntelligenceHubViewModelTest.deleteReport delegates to the repository`. Verified live on the emulator: seeded a stale 0% scan, deleted it through the UI, hub returned to the empty state and the DB row count hit 0.

### Fixed — Crash on on-device (Gemma) model download — missing foregroundServiceType (2026-08-11)
- **Android 14+ crash**: tapping **Download model** (or advancing onboarding with Gemma selected) started WorkManager's `SystemForegroundService` with `FOREGROUND_SERVICE_TYPE_DATA_SYNC` while the merged manifest declared no `foregroundServiceType` — `IllegalArgumentException: foregroundServiceType 0x1 is not a subset of 0x0` killed the app (caught live during QA E2E, logcat 18:51:54).
- Fix: `app/src/main/AndroidManifest.xml` now declares `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` permissions and overrides the merged `SystemForegroundService` with `android:foregroundServiceType="dataSync"` (`tools:node="merge"`).
- Verified live: after the fix the resumed WorkManager download started its foreground service, stayed running, and progressed (0% → 8.7% → …) with the app alive.

### Fixed — Apify/LinkedIn provider: real actor schema + input, canonical actor (2026-08-11)
- **DTO schema mismatch**: real LinkedIn-scraper actors emit `companyName` / `postedDate` / `contractType`, but `ApifyDatasetItem` only read `company` / `postedAt` / `type` — company, contract type and posted dates were silently dropped. Both names are now parsed (`@SerialName` fallbacks) and the mapper prefers the real-schema value.
- **Actor input mismatch**: `ApifyJobProvider` sent `{"search": …}` which the LinkedIn actors ignore — every run returned evergreen "always hiring" postings (verified: `valig~linkedin-jobs-scraper` and `curious_coder~linkedin-jobs-scraper` both returned "General Apply", "JOIN THE Family", "Careers"… for any keyword). The provider now sends `keywords` + `location`/`country` + `maxItems` — the AI-search filter the actor actually keys off (its own error: *"Provide either LinkedIn jobs search URLs, or fill in the AI search filters (keywords, location, etc.)"*). Live-verified on-device with `keywords="Android Engineer"`: real LinkedIn roles — Software Engineer II, Android Engineering (Axon), Software Engineer II, Android (Pinterest), Software Engineer, Android — All Teams (DoorDash), Android Engineer, Applied Foundations (OpenAI), Stellantis, EVgo, Red Cat Holdings. The client-side keyword filter still trims junk.
- **Actor swap**: LinkedIn now uses the canonical `curious_coder~linkedin-jobs-scraper` (45 prior runs on the QA token) instead of `valig~linkedin-jobs-scraper`.
- **Poll budget**: the actor can take 60–120s+ to finish; the old 30×2s (60s) poll budget gave up before the dataset was ready, so LinkedIn silently contributed nothing. Now 90×3s (≈4.5 min), covering the actor's 300s timeout.
- **Tests**: real-schema mapper case + a JSON roundtrip that parses a captured live actor item + an `executeSearch` input-shape test asserting the POST body carries `keywords`/`location`/`country`/`maxItems` and never `positions`/`search` (MockWebServer). Full `testDebugUnitTest` green.

### Verified — Real Apify + Groq keys end-to-end on emulator (2026-08-11)
- **Groq** (`gsk_…`): saved via Provider Management, `GET /models` → 200, card HEALTHY with key masked. **Apify** (`apify_api_…`): `GET /acts` health 200; actor run starts (HTTP 201) and the full poll→dataset→persist pipeline works (100 LinkedIn rows written). **Quota note**: today's verification runs exhausted the token's free-tier monthly compute budget — Apify now rejects new runs with 403 `"Monthly usage hard limit exceeded"`; the app degrades gracefully (circuit breaker → cached per-provider jobs).
- A zero-result search was traced to provider-side failures, NOT the filters: with every filter at "All", `JobFilterMatcher` skips empty lists and `experienceLabel` returns "Experience" only when no years filter is set. Real results confirmed for a broader query ("developer"): *Senior Independent Software Developer — A.Team — 80 Good Match — Remote/Contract*, *Full-Stack Developer / App / AI — Berlin*, etc. from Arbeitnow/RemoteOK/Remotive feeds (1,755 / 114 / 20 rows persisted).
- Full walkthrough + logcat evidence: `docs/QA_E2E_NOTES.md`.

### Added — On-device model download UX in onboarding + green Downloaded status (2026-08-11)
- Selecting an on-device AI provider (Gemma) during onboarding now shows a **Download model** button (with live WorkManager progress) instead of the old `Model file URL` text field — the model file *is* the configuration. Once downloaded, the step shows a green **Downloaded** panel and unlocks Continue; the validation guard blocks Continue until the model is ready.
- Provider Management's ready on-device chip is now green with a check icon and reads **Downloaded** (was a neutral "Model ready" chip).

### Added — Multiple job providers selected in Provider Management now actually gate the search (2026-08-11)
- `JobRepositoryImpl.searchJobs` consults each provider's persisted `isEnabled` flag: a provider toggled off in Provider Management is no longer queried even when it holds valid credentials; providers without a saved config stay enabled by default (keyless boards work with zero setup).
- **Fixed the "dummy results" bug**: `RestJobProvider`'s offline cache fallback was returning the *entire* shared DB on failure — every unconfigured provider echoed all previously cached jobs as its own results. The cache is now attributed per provider (`sourceProvider`), so a provider can only serve jobs it actually fetched. Dedup across providers + full client-side filter application already handled result conflicts.

### Added — Cover Letter generation without a job (2026-08-11)
- The Cover Letter Intelligence empty state now offers **Generate Cover Letter** (primary, generates a generic letter from the user's primary resume) alongside Find Jobs — previously it dead-ended with no generation option, and the ATS "Generate tailored cover letter" entry passed `jobId = null` straight into that dead end.
- `GenerateCoverLetterRequest.jobId` is now nullable through use cases + repository; a null job id skips validation and generates against a neutral target ("Your Next Employer"). `ImproveCoverLetterUseCase` passes the letter's real (possibly null) job id instead of rewriting it to 0.

### Added — Missing navigations wired (2026-08-11)
- Identity Hub → System now links to **Appearance & Theme** and **Privacy & Security** (both destinations existed but were unreachable).
- Tapping a job's company name in Job Details now opens the Company detail screen.

### Added — Typed job-search input survives recreation (2026-08-11)
- The Discovery query is written to `SavedStateHandle` on every search and restored when the ViewModel is recreated; the search field uses `rememberSaveable` so rotation/process recreation no longer wipes what was typed.

### Fixed — ATS legacy analyze path now scores from the AI answer (2026-08-11)
- **`ResumeRepositoryImpl.analyzeResume`** no longer hardcodes `overallScore = 80` (the `// TODO: parse aiResponse for a real score`). The AI's free-text analysis is now scored defensively: fence-tolerant JSON payload (`overallScore`/`score` key) → explicit prose (`"score: 87"`, `"match score is 87/100"`, never a `0` and never hyphen-range endpoints like the prompt's `"0-100"`) → first standalone 0-100 integer. Unparseable responses keep the neutral 80 fallback.
- **Tests**: 3 new `ResumeRepositoryImplTest` cases (JSON score 91, prose 87/100, and a "0-100 range description is not a score" regression guard). Full `testDebugUnitTest` green.

### Added — STAR Coaching Prompts + Rubric Gate (On-Device Coach, Option C) (2026-08-11)
- **`STARCoachingPrompts`** (`core:domain`): single source of truth for the four STAR component labels and the `idealAnswer` guidance — every interview AI path (`GenerateStarPackUseCase` packs, `InterviewRepositoryImpl` session questions + answer evaluation) now carries explicit STAR formatting/coaching instructions, so the on-device Gemma (and every cloud provider) receives the same guidance. Prompt-based coach per the `ONDEVICE_GEMMA_SFT_DESIGN.md` Option C (Gate G1 ruled out MediaPipe LoRA on the current artifacts).
- **`STARAnswerScorer`** (`core:domain`): deterministic 0–100 STAR rubric — detects Situation/Task/Action/Result via labeled paragraphs with hint-vocabulary fallback, bounded 25 pts/component, anti-gaming (repeated hints never inflate). `evaluateAnswer` now fills `starMethodScore` from the scorer when the AI omits it, so the review screen always has a grounded STAR score.
- **Tests**: 9 `STARAnswerScorerTest` (full/partial/gibberish/blank/lowercase/unlabeled/anti-gaming + the template pool's own idealAnswers must pass the gate), 4 `STARCoachingPromptsTest`, 1 `GenerateStarPackUseCaseTest` prompt-guidance case. Full `testDebugUnitTest` + `assembleDebug` green.

### Fixed — P0-01: Instrumented DB Suite Executed On-Device (2026-08-11)
- **`P0-01` closed**: `:core:database:connectedDebugAndroidTest` executed on the `aivance` emulator (Android 11 / API 30) — **BUILD SUCCESSFUL, 37 tests, 0 failures/errors**. The full Room migration chain 5→25 ran on-device for the first time, including the newest `migrate24To25_dropsLegacyResumeAnalyses` / `migrate10To25_legacyResumeAnalysesDropped` paths, the cross-version rebuild chains (`migrate5To24_fullChainPreservesUserData`, `migrate16To24_jobDataSurvivesAllRebuilds`, `migrate17To24_stressDataset`), and all five DAO suites.
- **Runbook corrected**: `DEVICE_VALIDATION.md` expected-count updated from the stale 47 to the actual 37 (post T-04 table drop). `TODO.md` P0-01 and `KNOWN_ISSUES.md` DR-01 marked ✅ RESOLVED.

### Changed — ViewModel Test-Strength Pass (T-05) (2026-08-11)
- **Tautological initial-state assertions replaced with behavior checks**: ATS init now verifies `loadResumes()` populates the `resumes` flow (plus the resume-load failure path); Home asserts the Success state's content (full quick-action set + greeting); bare `initial state is X` tests replaced with real transitions (cover-letter Load → Success, OCR/JSON imports → Preview).
- **+33 event-verification tests across 7 features**: `InterviewViewModel.SubmitAnswer` success/failure/no-op (answer persists with `MessageSender.USER`); ATS `GenerateCoverLetter` + `ExportReport` effects; cover-letter inline edit mode (`ToggleEdit`/`UpdateSection`/`SaveEdits` with draft capture + failure snackbar), `CopyAll`, PDF `Export`, `RegenerateSection` failure, and both `GenerateForJob` paths; jobs `ClearFilters` (non-query dimensions reset, query kept) + `Refresh`; saved-jobs `ViewDetails`/`Refresh`/remove-failure; resume-engine OCR import (valid + blank), JSON Resume import (valid + malformed), `UpdateSectionContent`, `DiscardSuggestion`; notifications `Refresh` tracking + safe no-op `MarkAsRead`/`Delete`.
- **Verification**: full `testDebugUnitTest` + `assembleDebug` green across all modules (1085 tasks).

### Added — Apply-Assist Keyword Rules + Daily Quota (R-07) (2026-08-11)
- **Job filter whitelist/blacklist chips**: `JobSearchFilter` gains `includedKeywords` (all terms must appear in title/company/description) and `excludedKeywords` (no term may appear — e.g. "unpaid", "commission-only"); `JobFilterMatcher` enforces both as the last filter stage. The discovery filter bar adds a "Must include" + "Exclude" field row (committed on IME search / focus loss, same pattern as the R-02 tech-stack input).
- **Tracker daily quota**: new `ApplicationPreferencesRepository` (domain) backed by DataStore (`PreferencesManager.getIntFlow` + a `daily_application_cap` key, default 5, clamped 1–100) — implemented in `core:data` and bound in `RepositoryModule`. `TrackerViewModel` computes today's application count from `dateApplied` and surfaces it with the live cap; the Pipeline hero shows a "Daily Application Quota" card (count vs. cap, warning tint + progress bar when reached) with a preset-cap picker dialog (3/5/10/15/20).
- **Patterns reimplemented from scratch** — blacklist/whitelist + daily-quota UX only; no code from the AGPL/unlicensed reference repos.
- **Tests**: 5 new `JobFilterMatcherTest` (include-ALL semantics, company/title matching, blacklist rejection, combined whitelist+blacklist, case/blank tolerance) + 3 new `TrackerViewModelTest` (today's count vs cap, null-date exclusion, cap persistence). Full `testDebugUnitTest` + `assembleDebug` green.

### Added — Remote-Work Resources Hub (R-06) (2026-08-11)
- **`RemoteResourcesScreen` wired into navigation**: the previously orphaned screen is now reachable via a new `Destination.Resources` (icon + localized label), an entry card in **About AiVance** (per the AC), and an "About & Resources" section in the Profile → System tab (which also restores the previously dead **About** destination's entry point).
- **Categorized + localized**: resources grouped into four sections — Job Boards, Curated Lists, Interview Prep, and a new **Remote Companies** category (Automattic, GitLab, Zapier, Shopify careers pages) — with all screen chrome and category headers moved to `strings.xml`. Item titles/descriptions stay catalog data (platform proper-nouns that don't translate); external links open via the standard browser intent.
- **Catalog grew to 13 links** (added USAJobs, remoteintech.org, and the four remote-first companies; 7 pre-existing links kept).
- **Tests**: `ResourceHubCatalogTest` (3 — category coverage, URL validity, R-06 categories present). Full `testDebugUnitTest` + `assembleDebug` green.

### Added — STAR Prep Packs (R-05) (2026-08-11)
- **`GenerateStarPackUseCase`** (`core:domain`): role-specific STAR-format question packs generated through the existing streaming AI path (`AiRepository.streamAnalyzeText`), with fence-tolerant JSON parsing. **Graceful degradation is structural** — no provider, stream failure, or unparseable response falls back to the deterministic `STARPrepGenerator` template pack (moved into `core:domain` and generalized to a role-interpolated 6-question pool), so the flow never dead-ends empty.
- **Answers now persist into sessions**: new `InterviewRepository.persistPackQuestions(sessionId, questions)` writes pack questions onto the session row. `InterviewViewModel` seeds a session with an explicitly supplied pack, and the STAR fallback path (previously in-memory-only) is now persisted too — submitted answers are recorded against real session questions and survive reloads.
- **Prep Studio UI**: the Practice tab gains a "STAR Prep Packs" section — target-role input, "Generate STAR Pack", a rendered pack (question, category/difficulty chips, STAR key points) and a "Practice this pack" button that launches a mock session seeded with the pack.
- **Tests**: `GenerateStarPackUseCaseTest` (9 — streaming parse, fences, count clamp, empty/throw/unparseable fallbacks, blank-role, template integrity) + 3 new `InterviewViewModelTest` (pack generation into state, pack-seeded session persistence, fallback persistence). Full `testDebugUnitTest` + `assembleDebug` green.

### Added — Remote-Company Catalog (R-02) (2026-08-11)
- **Bundled catalog snapshot**: 882 remote-friendly companies from the ISC-licensed remoteintech/remote-jobs dataset (`remote_policy`, `company_size`, `region`, `technologies`, `careers_url`) shipped as `core:data`'s `company_catalog.json` asset — zero network calls at runtime. Generated by the new `refresh_company_catalog.py` (fetches the upstream `src/companies/*.md` frontmatter, emits a compact sorted snapshot).
- **Discovery filtering**: `JobSearchFilter` gains `remotePolicy` + `technologies`; `JobRepositoryImpl` applies them against the catalog after the listing-level matcher. Unknown companies are excluded from catalog-filtered searches on purpose (the app won't claim a policy/stack it can't verify). Filter bar adds a Remote-policy dropdown (Fully remote / Remote-friendly / Hybrid — fully-remote and remote-first grouped as distributed-first) and a Tech-stack input committed on search/focus-loss.
- **Company enrichment**: `CompanyDetailViewModel` resolves the catalog entry (name then domain fallback) via a new `CompanyCatalogRepository` (domain) backed by `CompanyCatalog` (`core:data`); the company screen shows a policy badge, size/region facts, technology chips and a "Visit careers page" action.
- **Tests**: `CompanyCatalogTest` (15 — parsing, normalization lookups, domain extraction, policy grouping, tech intersection, unknown-company rejection, bundled-snapshot integrity), `JobRepositoryImplTest` remote-policy end-to-end case, `CompanyDetailViewModelTest` (4 — name/domain resolution, no-entry, error). Full `testDebugUnitTest` + `assembleDebug` green.

### Added — JSON Resume Import/Export (R-03) (2026-08-11)
- **`JsonResumeConverter` + schema models moved to `core:domain`** (`usecase/resume/jsonresume`): the standard JSON Resume v1.0.0 converter now lives beside the export use case, so every export path emits the same schema the importer reads.
- **`ExportResumeUseCase` JSON export fixed**: `ExportFormat.JSON` previously emitted an ad-hoc truncated pseudo-JSON (content cut at 100 chars, no escaping) that the importer couldn't read. It now produces a proper JSON Resume document (`basics`/`work`/`education`/`skills`) via the converter — an exported file round-trips back into the app.
- **Round-trip tests**: new `JsonResumeConverterTest` (6 tests) — export→import preserves section order/types/content, user basics carried into the schema, quotes/newlines escaped, unknown standard sections (`awards`/`volunteer`/`interests`) tolerated, empty-document and position/studyType mapping. `ExportResumeUseCaseTest` JSON case now asserts the standard schema and re-imports the output.
- **Full `testDebugUnitTest` + `assembleDebug` green.**

### Added — AI Job-Fit Scoring (R-04) (2026-08-11)
- **`ScoreJobFitUseCase`** (`core:domain`): batches up to 10 listings into one AI prompt scored against the user's `ProfileState` (target role, skills, work preference, active query); parses fence-tolerant JSON `{id: score}`, clamps 0–100, and caches per (jobId, profile signature) so re-scoring is free.
- **Graceful degradation**: no AI provider / provider failure / unparseable response returns what's cached — callers fall back to the deterministic rule-based `JobFitScorer`, so discovery never blocks on AI.
- **Discovery UI**: `JobsViewModel` computes a merged fit-score map per search (single-flight, cleared on new search) exposed via `JobsUiState.fitScores`; the job card's fit badge is now AI-upgraded, and a "Best match" chip sorts the list by fit.
- **Tests**: 10 `ScoreJobFitUseCaseTest` (parsing, clamping, caching, batch limit, fallback paths) + 3 `JobsViewModelTest` (merge, fallback, clear-on-new-search). Full `testDebugUnitTest` + `assembleDebug` green.

### Changed — T-03 ProviderFactory Config Path for Keyed Job Providers (2026-08-11)
- **SDK**: `ProviderFactory.createProvider(ProviderConfiguration)` typed overload + the shared factory config-map contract (`ProviderConfiguration.toFactoryMap()`, `credential()`), with KDoc documenting the registration contract for every provider module (settings = plaintext preferences, secrets = encrypted credentials).
- **Job providers**: `adzuna` + `usajobs` `ProviderFactory.Factory` bindings registered in `JobProvidersModule` (previously absent — `createProvider` threw for job providers); factories construct configured instances from persisted settings/secrets.
- **Provider Management**: metadata-driven credential onboarding — Adzuna (App ID + API Key) and USAJobs (API Key) now render one input per `ConfigField`, with sensitive/PASSWORD fields routed to encrypted secrets and everything else to plaintext settings. Replaces the single API-key field and the `adzuna` `"appId:appKey"` special-case.
- **Tests**: `ProviderFactoryTest` (6), `JobProvidersModuleTest` factory cases (5), `ProviderManagementViewModelTest` secret-routing case (1). Full `testDebugUnitTest` + `assembleDebug` green.

### Changed — T-04 AtsReport Migration Completed + Documentation Sync (2026-08-11)
- **T-04 completed** — `AtsResult`, `ResumeAnalysis`, `ResumeAnalysisDto`/`AtsResultDto`/`ResumeAnalysisRequest`, `ResumeAnalysisEntity` and all legacy mappers/DAO methods are **removed**; the ATS/resume-analysis path now runs entirely on `AtsReport`:
  - `ResumeRepository.analyzeResume` returns a persisted `AtsReport` (`ats_reports` row); the job description is saved first so the enforced `jobDescriptionId` FK holds; legacy `getAtsResults`/`saveAtsResult` removed.
  - `CalculateATSScoreUseCase`/`AnalyseResumeUseCase` return `AtsReport`; score clamping kept at the domain boundary; the legacy `formattingScore` now surfaces as a "Formatting" `OptimizationTip`.
  - `AtsDao` legacy `resume_analyses` methods replaced by `getAllReports()`; analytics derives reports from `ats_reports`.
  - **DB v25** (`MIGRATION_24_25`): drops the legacy `resume_analyses` table; `25.json` schema exported; new `migrate24To25`/`migrate10To25` instrumented migration tests; `migration_validate.py` + `db_certify.py` replayed to v25 (all 20 upgrade paths OK) and `test_sql_check.py` validates the new test SQL.
  - Evidence: full `testDebugUnitTest` + `assembleDebug` green across all modules.
- **TECHNICAL_DEBT_REPORT.md**: T-01 (`SecurityMigrationWorker`) and T-02 (`RecruiterIntelligenceRepositoryImpl`) confirmed fully implemented by code audit and marked ✅ RESOLVED. T-03b (Keystore recovery UX) marked ✅ RESOLVED (delivered in P1-04). T-04 marked ✅ RESOLVED. Debt summary table corrected + updated.
- **PROJECT_STATE.md**: Room version corrected to v25; stale Known Issues notes removed; Post-Launch Additions section added; open-items list updated (T-04 done).
- **KNOWN_ISSUES.md**: SR-02 and DR-01 updated with `DEVICE_VALIDATION.md` cross-references.
- **DEVICE_VALIDATION.md**: New runbook with step-by-step instructions for executing P0-01 (`:core:database:connectedDebugAndroidTest`) and P0-02 (mitmproxy MITM pen-test) on a real device/emulator.

### Added — Resumable Background Model Downloads via WorkManager (2026-08-08)
- **Download survives app backgrounding**: the on-device Gemma model download now
  runs in a foreground `@HiltWorker` (`GemmaModelDownloadWorker`, `feature:profile`)
  enqueued through WorkManager with `CONNECTED` network constraint and exponential
  backoff (30s, 5 attempts). Provider Management's *Download model* button
  enqueues it; the screen streams live progress via `ModelDownloadScheduler.observe()`
  and refreshes when the work succeeds/fails — including when the app was
  backgrounded or killed mid-download.
- **Ongoing progress notification**: `ModelDownloadNotifier` owns a dedicated
  `model_downloads` channel; the worker promotes itself to a foreground service
  (`dataSync` type, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`
  permissions declared in the library manifest) and refreshes the notification
  (throttled to 1/s) with percent + determinate progress bar; posts a terminal
  success/failure notification. Strings in EN + HI.
- **Resumable + retryable transfers**: `OkHttpModelFileDownloader` now writes to a
  `.part` file and resumes from it via HTTP `Range: bytes=` on retry (validates
  206 responses), so a network drop re-downloads only the remaining bytes.
  Transient failures (no network, interrupted transfer, 5xx/408/429) return
  `retry()`; permanent 4xx return `failure()`.
- **Cancellation**: deleting the model cancels any in-flight work so it cannot
  recreate the file immediately after removal.
- **Tests**: `OkHttpModelFileDownloaderTest` (6 MockWebServer cases incl.
  mid-stream interruption + Range resume), `GemmaModelDownloadWorkerTest`
  (7 Robolectric + work-testing cases: success/progress, already-ready
  short-circuit, permanent vs transient classification, offline defer, missing
  provider, compact URL forwarding), updated `ProviderManagementViewModelTest`
  (enqueue + observe-driving). 52 tests green across the touched modules.

### Added — Training-Ready JSONL Corpus for All 41 Skills (2026-08-08)
- **`skills-pack/training/`**: every skill in the pack exported to strict JSONL
  for fine-tuning another model — `skills_corpus.jsonl` (record format:
  frontmatter `name`+`description` as the `system` prompt, SKILL.md body as
  `instructions`, plus a distinct `trigger`/`task` pair per skill) and
  `skills_chat.jsonl` (same content as `{system, user, assistant}` messages
  for direct SFT). Both carry `skill` + `tier` (uesf-core/community/original/
  learned) so the corpus is filterable by provenance.
- **Regenerable**: new `skills-pack/tools/export_training_jsonl.py`
  (stdlib-only) scans every `SKILL.md` under `skills-pack/`, writes both
  corpora, and self-verifies line counts + schema before exiting — safe to run
  in CI after any skill edit. `training/README.md` documents usage for
  Hugging Face datasets / Axolotl / LLaMA-Factory and which tier is the
  cleanest training signal; MANIFEST directory layout updated.

### Added — Feature Interlinking: Saved Jobs → Resume / Tracker / Assistant (2026-08-08)
- **Per-card actions on Saved Jobs**: each saved job now offers *Create tailored
  resume* (jumps to the Resume Engine with the job description preloaded as the
  ATS-scan JD — `Destination.ResumeEngine(jobDescription)` + a new
  `SetInitialJobDescription` engine event that flows into the ATS step),
  *Track application* (opens the Pipeline with the job pre-selected — new
  `Destination.TrackApplication(jobId)`; the tracker selects the existing
  application for it via id normalization, or pre-fills the Add dialog with
  the job's company/role so one tap adds it), and *Ask AI* (opens the global
  assistant overlay with the job as context).
- **Assistant with job context from any screen**: new `AssistantJobContext`
  domain model; `AppShellState`/`LocalAppShellState` moved into
  `core:designsystem` so scaffolds open the assistant overlay globally
  (`AivanceWorkspaceScaffold`'s AI action now toggles the overlay by default);
  `PromptOrchestrator.buildCopilotPrompt(…, jobContext)` renders a CURRENT JOB
  CONTEXT block (title/company/description) so answers are tailored to the
  role; `AssistantScreen(initialJobContext)` + `AssistantViewModel.setJobContext`
  thread it into the next prompt.
- **Tests**: Destination (ResumeEngine/TrackApplication routes), ResumeEngine
  (preloaded JD carried into ATS step), Tracker (`trackJob` selects existing vs
  pre-fills pending, clears pending), Assistant (job context passed into the
  orchestrated prompt). 97 unit tests green across the touched modules.

### Added — Learned Skills Tier + Updated Skills Pack (2026-08-08)
- **16 new "learned" skills** (`.agents/skills/` + `skills-pack/learned/`),
  original model-agnostic syntheses distilled from 11 public skill repos
  (anthropics/skills, obra/superpowers, mattpocock/skills,
  emilkowalski/skills, MiniMax-AI/skills, MengTo/Skills, slavingia/skills,
  google/skills, vercel-labs/skills, VoltAgent/awesome-openclaw-skills,
  multica-ai/andrej-karpathy-skills): `coding-discipline`,
  `plan-driven-implementation`, `root-cause-debugging`,
  `verification-before-completion`, `brainstorm-before-build`,
  `writing-for-agents`, `skill-lifecycle`, `motion-design`,
  `distinctive-design`, `android-compose-craft`, `document-generation`,
  `product-validation`, `issue-triage`, `skill-discovery`,
  `mcp-server-builder`, `web-visual-effects`.
- **Skills pack grown to 41 SKILL.md files (40 unique)** across 5 tiers
  (UESF core 6, community 14, original 5, learned 16); `MANIFEST.md` updated
  with the learned tier; `tools/install.sh --all` now installs all five tiers.
- **Catalog updated**: `.agents/skills/README.md` documents the learned tier
  with per-skill inheritance provenance.

### Added — Offline AI Assistant via On-device Gemma Fallback (2026-08-08)
- **Zero-connectivity Assistant**: `GetAssistantResponseUseCase` now routes to
  the on-device Gemma model when no cloud provider is configured **or** the
  configured cloud provider is unreachable (airplane mode, provider outage).
  The fallback order is: best cloud provider → ready on-device model
  (streaming, else chat) → context-aware Copilot fallback. Identity-guarded so
  the same provider instance is never invoked twice; the "no provider" error
  is surfaced only when neither a cloud provider nor a downloaded model exists.
- **SDK selection helper**: `ProviderManager.getOnDeviceProviderFor(capability)`
  returns the best Active/Ready provider implementing `ModelDownloadable` whose
  model is actually downloaded — used by the Assistant and reusable by any
  feature needing an offline LLM fallback.
- **Tests**: 9 new `GetAssistantResponseUseCaseTest` cases (cloud-unreachable→
  on-device, no-cloud→on-device, cloud-preferred, both-fail→Copilot, identity
  guard, one-shot invoke paths, no-provider error) + 4 new `ProviderManagerTest`
  cases (ready-only, null-when-idle, ignores cloud, Active>Ready).

### Added — Device-Capability Gate for On-device Model Downloads (2026-08-08)
- **Pre-download capability check**: tapping *Download model* on an on-device
  provider now inspects free storage (`StatFs`, ≥2 GiB required) and total RAM
  (`ActivityManager`, ≥4 GiB recommendation) via a new `DeviceCapabilityProvider`
  (`feature:profile`, Hilt-bound, IO-dispatched). A confirmation dialog shows the
  **exact model size** in bytes, free storage, and — when the device is
  constrained — a RAM warning and/or a storage-blocked notice.
- **Compact model alternative**: on constrained devices the dialog offers a
  genuinely smaller model — FunctionGemma 270M int8 MediaPipe `.task`
  (284,342,855 bytes ≈ 271 MiB) vs the primary Gemma 3N E2B int4
  (3,136,226,711 bytes ≈ 2.9 GiB) — as a separate download button that routes
  the compact URL to the downloader. When neither model fits, the download is
  hard-blocked with an honest snackbar instead of failing mid-download.
- **Honesty fix**: the provider previously advertised "~1.3 GB" — the real,
  live-verified artifact size is ~2.9 GiB. `GemmaOnDeviceProvider` now exposes
  exact verified sizes (`modelSizeBytes`, `compactModel` via the extended
  `ModelDownloadable` SDK interface) so the UI never understates the footprint.

### Added — On-device Offline LLM + Provider Toolkit Completion (2026-08-08)
- **On-device Gemma provider (offline, keyless)**: new `GemmaOnDeviceProvider`
  in `core:ai-providers` runs a quantized Gemma 3N E2B int4 model fully offline
  via MediaPipe LLM Inference (`tasks-genai` 0.10.35, `libllm_inference_engine_jni`
  packaged in the APK). No API key and no cloud once the ~1.3 GB model is
  downloaded. `Provider Management` gains a per-provider **Download model**
  button with live progress, a **Delete model** action, and honest
  not-downloaded/ready states. The provider self-reports `InvalidConfiguration`
  until the model is present, so provider selection never picks an unusable
  offline model (keyed cloud providers still win when configured). Model URL is
  configurable; default source + Gemma Terms-of-Use flow-down documented in KDoc.
- **Gap fixes**: `ProviderRefreshWorker.knownProviders`, `GetAvailableModelsUseCase`
  defaults, and `AiSettingsViewModel`'s provider list now include **Anthropic
  Claude** and the on-device Gemma provider (Claude was previously never
  health-checked or refreshed).
- **Dead code removed**: the app template theme (`app/ui/theme/*` — the default
  Android Studio Purple80 scaffold) was deleted; it had zero references — the
  real tokenized theme lives in `core:designsystem` (`AivanceTheme`, 4 modes +
  accents + dynamic color).

### Added — UESF Adoption (2026-08-07)
- **UESF framework adopted**: core loop skills installed to `.agents/skills/`
  (planning, implementation, testing, debugging, review, refactoring) from the
  self-validating UESF framework (`uesf/`); the framework validator + test suite
  are now a hard CI gate (`.github/workflows/uesf.yml`), including a drift check
  that the installed skills match the source.
- **Analytics baseline snapshot (M-03/P2-01)**: the guarantee lives in the data
  layer — `AnalyticsRepositoryImpl.getSnapshots()` is self-healing: when the
  snapshot list is empty it captures a real baseline snapshot (derived from real
  applications/interview sessions/ATS results, never fabricated) before forwarding
  the Room flow, `Mutex`-guarded so concurrent collectors can't double-insert.
  Every consumer (analytics dashboard, career state engine, assistant context)
  inherits the guarantee. Shared ATS-report/readiness/recruiter derivation
  extracted (`ResumeAnalysisEntity.toAtsReport()` + private helpers reused by
  `createSnapshot` and `getCareerIntelligence`); ViewModel-level
  `ensureBaselineSnapshot()` removed. New `AnalyticsRepositoryImplTest` proves
  real-data derivation and idempotency. Weekly `AnalyticsSnapshotWorker` continues
  long-term accumulation.

## [1.0.0] - 2026-07-31

### Added — Platform (Phases 1–11)
- **Provider Platform**: Metadata-driven provider SDK with AI, Job, and Enrichment provider types; lifecycle orchestration; validate-before-save onboarding.
- **AI Providers**: Google Gemini, Anthropic Claude, Groq, OpenRouter, OpenAI, Ollama.
- **Job Providers**: LinkedIn, Indeed, Greenhouse, Lever, RemoteOK, Remotive, Apify (real actor pipeline), plus four free global engines — Arbeitnow (EU/Germany, keyless), Jobicy (global remote, keyless), Adzuna (free tier, 16 countries), USAJobs (US federal).
- **Enrichment Provider**: Hunter.io — real domain search and email verification.
- **Resume Intelligence**: AI parsing, PDF/DOCX import, multi-version storage, TXT/MD/JSON export.
- **ATS Intelligence**: Semantic resume-to-JD matching, keyword gap analysis, formatting score, optimization insights.
- **Job Discovery**: Unified multi-provider aggregation, normalization, caching, and search.
- **Professional Network**: Recruiter CRM, AI outreach generation, communication history.
- **Cover Letter Intelligence**: Structured, versioned, sectional AI cover letters.
- **Interview Intelligence**: Mock interview orchestration and per-answer AI evaluation.
- **Application Workflow**: Career pipeline with application lifecycle and stage management.
- **Career Analytics**: KPI dashboards, explainable Career Score, prioritized AI recommendations.
- **AI Career Assistant**: Domain-aware conversational orchestrator.
- **Security & Privacy**: AES-GCM on-device encryption (Google Tink + Android Keystore), encrypted DataStore for secrets, Privacy Center with data export/deletion, audit logs.

### Added — UX & Design System (Phase 12)
- Tokenized design system (`:core:designsystem`): color, type, spacing, shape, elevation, motion.
- Themes: Light, Dark, AMOLED, Dynamic (Material You), custom accents.
- Reusable component library (buttons, cards, states, banners, charts, gauges, skeletons, top bars).
- Redesigned Dashboard (Command Center), Assistant (OS-style streaming), Analytics (interactive charts), Tracker (Kanban + drag-and-drop), Profile, Jobs, Resume, Interview, Recruiter.
- Honest state-driven UI: loading, empty, success, error, partial — no mock data, no dead controls.

### Added — Quality Engineering & Release (Phase 13)
- 20+ stale test files repaired against current contracts across `core:domain`, `core:data`, `app`, and 6 feature modules.
- Full project `testDebugUnitTest` green.

### Added — Production Launch (Phase 14)
- Release build variant with R8 minification + resource shrinking, ProGuard mapping, native symbol table.
- Play-ready signing configuration (env-var secrets, `keystore.jks`).
- Complete CI/CD pipeline: quick-check, code quality (detekt/lint), unit tests (16-module matrix), emulator tests (API 29/34), coverage, security scan, release build (AAB/APK/mapping), benchmarks, Play Store upload, notifications.
- Crash reporting (`CrashReporter`) and operational telemetry with privacy controls.
- Documentation suite: README, CONTRIBUTING, Architecture, API, Database Schema, Security Guide, Design System, Testing Guide, Deployment Guide, Release Guide, Operations Guide, Observability Guide, Known Issues, Test Plan, Changelog, Roadmap, LICENSE.

### Fixed
- Circular dependency between `StructuredTimberTree` and `TelemetryEngineImpl` (logging loop) — ADR 003.
- Stale tests referencing deleted use cases (`ApplyToJobUseCase`, `BookmarkJobUseCase`, `SaveJobUseCase`, `RemoveSavedJobUseCase`, `SearchSavedJobsUseCase`) and changed ViewModel contracts.
- `MockWebServer` API tests for Arbeitnow, Jobicy, and Apify pipeline.

### Security
- All API keys removed from SQLite; moved to encrypted DataStore.
- PII columns (email, raw text, outreach content) encrypted at rest (AES-GCM).
- No secrets in source code; release signing via CI secrets.

### Known Limitations
- `RecruiterIntelligenceRepository` persistence still uses mock logic; Hunter.io API integration is real.
- Adzuna & USAJobs dormant until free API keys are entered.
- `SecurityMigrationWorker` is a skeleton; destructive plaintext cleanup planned for v1.1 (DB v21).
- Localization (i18n) and cloud sync are roadmap items, not in v1.0.0.

## [1.0.1] - 2026-08-06

### Fixed — End-to-End Core Bug Fixes & Stability
- **Google Sign-In Activity Context**: Fixed Credential Manager bottom sheet invocation by passing `LocalContext.current` Activity context in `AuthScreen.kt` and `AuthViewModel.kt`.
- **Resume Engine Step 4 Text Input**: Resolved automatic text clearing on keystroke/paste by keying `AnimatedContent` on `state.stepIndex()` instead of target `state` object.
- **Resume Engine Step 5 Optimization**: Fixed section optimization failure for in-memory resumes (`versionId == 0`) by passing current section content directly to `StreamImproveSectionUseCase.kt`.
- **Resume Engine Step 7 Export**: Corrected PDF & DOCX export effect handling and intent chooser launching with `FLAG_ACTIVITY_NEW_TASK`.
- **Job Discovery Filtering & Fallbacks**: Fixed nested `LazyColumn` height constraint issue in `JobsScreen.kt`, updated `JobFilterMatcher.kt` structured location matching, and added automatic database seed fallback (`seedDefaultJobsIfEmpty`) in `JobRepositoryImpl.kt`.
- **Prep Studio Real Data Integration**: Dynamic practice hours computation, candidate profile-driven company research and interview edge insights in `PrepStudioScreen.kt`.
- **AI Assistant Copilot Fallback**: Added context-aware local Copilot response generator in `GetAssistantResponseUseCase.kt` when remote LLM providers are unconfigured or unreachable.
- **Package Visibility**: Added `<queries>` block for `ACTION_SEND` intents in `AndroidManifest.xml`.

