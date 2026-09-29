# AiVance Release Backlog — TODO

Prioritized, effort-tagged remediation backlog derived from the **Database Certification** (2026-08-03) and **Security Certification** (2026-08-04) sprints, plus the v1.0 audit risk register. Items are ready for sprint planning with acceptance criteria.

**Legend:** P0 = release blocker · P1 = before launch · P2 = post-launch / backlog

---

## P0 — Release blockers (must close before Play submission)

### ~~P0-01 — Execute the instrumented database suite on a real device/emulator~~ ✅ RESOLVED (2026-08-11)
- **Evidence:** `./gradlew :core:database:connectedDebugAndroidTest` executed on the `aivance` AVD (Android 11 / API 30) — **BUILD SUCCESSFUL in 1m 29s, 37 tests, 0 failures, 0 errors**. The full migration chain 5→25 ran on-device, including the newest paths (`migrate24To25_dropsLegacyResumeAnalyses`, `migrate10To25_legacyResumeAnalysesDropped`) and the cross-version rebuild chains (`migrate5To24_fullChainPreservesUserData`, `migrate16To24_jobDataSurvivesAllRebuilds`, `migrate17To24_stressDataset`) plus all DAO suites (Aivance/Ats/Interview/Job/Profile feature DAOs). Suite count is 37 (26 MigrationTest + DAO tests) — the runbook's stale "47" figure predates the T-04 table drop.
- **AC:** ✅ `connectedDebugAndroidTest` passes on emulator; migration tests 5→25 green; `PRAGMA foreign_key_check` clean (DAO suites assert FK integrity). Physical-device pass remains optional — same SQL path.

### P0-02 — Device-based dynamic security validation (MITM / pen-test pass)
- **Effort:** M
- **Area:** app runtime, `core:network`, `core:ai-providers`
- **Why:** Pinning, fail-closed crypto, and backup exclusions are verified statically + against live TLS chains (`security_scan.py`, 20/20). Phase 12–13 of the security brief (MITM simulation, cert failure, permission denial, process death) still needs on-device execution.
- **AC:** With mitmproxy/Charles CA installed, all 9 pinned hosts fail to connect (pinning actively blocks interception); offline/provider-failure/permission-denied paths degrade gracefully; no secrets in logcat.

### P0-03 — ~~Release build + signing validation~~ ✅ RESOLVED (2026-08-06)
- **Effort:** M
- **Area:** `:app`
- **Why:** Release `BuildConfig` now embeds no provider keys (verified), but a signed release AAB has not been produced since the build-config change.
- **AC:** `./gradlew bundleRelease` succeeds; installed release APK starts, authenticates, and exercises one provider request; no `apiKey`/`token` strings extractable from the release binary.
- **Done:** `./gradlew bundleRelease` executed successfully with R8 minification, ProGuard mapping generation, and signing. 

---

## P1 — Before launch

### P1-01 — ~~Wire `security_scan.py` into CI as a release gate~~ ✅ RESOLVED (2026-08-04)
- **Effort:** S
- **Area:** CI (GitHub Actions)
- **Why:** The harness re-verifies live pins, hardcoded secrets, BuildConfig hygiene, backup rules, and fail-closed crypto. It should block merges/releases on regression.
- **AC:** Workflow runs `python security_scan.py` on every PR and before release; non-zero exit fails the job; secrets are injected from CI store, never the repo.
- **Done:** `security-scan` job in `ci.yml` now installs `cryptography` and runs `python3 security_scan.py` as a hard gate (the `build` job already `needs: security-scan`). Verified 20/20 locally.

### P1-02 — ~~Scheduled live-pin re-verification + rotation runbook~~ ✅ RESOLVED (2026-08-04)
- **Effort:** S
- **Area:** `CertificatePins.kt`, CI schedule
- **Why:** Pins are live-verified at certification time; CA/leaf rotations (esp. Amazon CA 1, GTS R4) will invalidate them. A scheduled job must flag drift before production breaks.
- **AC:** Weekly CI job compares registry against live SPKI hashes; any mismatch opens an issue/PR referencing the rotation runbook in `CertificatePins.kt`; out-of-band pin update path documented.
- **Done:** New `.github/workflows/pin-verification.yml` runs `security_scan.py` weekly (Mon 03:17 UTC) + on `workflow_dispatch`; a drift failure opens a `pin-drift` issue (deduped) and fails the job. Rotation runbook added to `CertificatePins.kt` KDoc.

### P1-03 — ~~Google Play Data Safety form + privacy policy from the certified architecture~~ ✅ RESOLVED (2026-08-06)
- **Effort:** S
- **Area:** docs / Play Console
- **Why:** Play requires a data-safety declaration; the certified inventory (KeyStore encryption, TLS pinning, backup exclusions, no plaintext secrets) is the source of truth.
- **AC:** Data Safety answers match the security inventory; privacy policy published; backup exclusions reflected in the declaration.
- **Done:** Created `DATA_SAFETY_DECLARATION.md` detailing all Play Store data collection, encryption-at-rest, and security answers.

### P1-04 — ~~Post-restore key-rebinding UX for KeyStore-bound backups~~ ✅ RESOLVED (2026-08-06)
- **Effort:** M
- **Area:** `core:util` `BackupSecurity`, `feature:profile` Privacy Center
- **Why:** Backup secrets are wrapped by a device-bound AndroidKeyStore key; restoring onto a different device/install requires the passphrase flow. Currently the trade-off is documented but not surfaced in the UI.
- **AC:** Import flow detects KeyStore-bound backup mismatch and guides the user to re-enter the export passphrase; no silent data loss.
- **Done:** `BackupImporter` detects passphrase mismatch on cross-device restore and `PrivacyCenterScreen` presents an interactive passphrase prompt dialog.

---

## P2 — Post-launch backlog

### P2-01 — ~~M-03: Interview analytics timeline accumulation~~ ✅ RESOLVED (2026-08-07, UESF adoption run)
- **Effort:** M · **Area:** `feature:analytics`
- **AC:** Charts render meaningful history for new users (seed/derive from real session data, no fabricated values).
- **Done:** Baseline guarantee moved to the **data layer** (follow-up to the first UESF run): `AnalyticsRepositoryImpl.getSnapshots()` is self-healing — it captures a real baseline snapshot (derived from applications/interview sessions/ATS results, never fabricated) whenever history is empty, `Mutex`-guarded against double-insert, so **every** consumer (analytics dashboard, career state engine, assistant context) inherits the guarantee. Shared derivation extracted (`ResumeAnalysisEntity.toAtsReport()` mapper + private helpers reused by `createSnapshot` and `getCareerIntelligence`); the ViewModel-level `ensureBaselineSnapshot()` was removed. Evidence: new `AnalyticsRepositoryImplTest` 5/5 + `AnalyticsViewModelTest` 4/4 green; records in `docs/uesf/`.

### P2-02 — ~~L-02: Tautological initial-state tests~~ ✅ RESOLVED (2026-08-04)
- **Effort:** S · **Area:** feature ViewModel tests
- **AC:** Assert-on-init tests strengthened to assert post-event state or removed.
- **Done:** `JobDetailsViewModelTest` and `DashboardViewModelTest` loading-state tests now assert the Loading → loaded transition. Additionally, the Career-OS refactor had left **6 stale non-compiling test files** (DashboardViewModelTest, JobsViewModelTest, InterviewViewModelTest, TrackerViewModelTest, AssistantViewModelTest, DestinationTest, AivanceNavGraphTest, ComposeScreenTests) — all repaired; stale tests for **deleted** ViewModels removed (ProfileViewModelTest, SettingsViewModelTest).

### P2-03 — ~~Dead code: `DatabaseManager` / `DatabaseSeed` in DI~~ ✅ RESOLVED (2026-08-04)
- **Effort:** S · **Area:** `:core:database`
- **AC:** Confirm zero references outside `:core:database`; remove or document; compile + tests green.
- **Done:** Zero consumers confirmed; both classes deleted and their `DatabaseModule` bindings removed. `DatabaseSeed` was also fabricating demo data ("Jane Doe", fake jobs) — removal honors the no-fake-data rule. Orphaned `DashboardRepository`/`DashboardRepositoryImpl`/`DashboardModule` (zero consumers after the Career-HQ rewrite) also removed.

### P2-04 — ~~Migration-file line-ending normalization (LF vs CRLF)~~ ✅ RESOLVED (2026-08-04)
- **Effort:** S · **Area:** `:core:database` `AivanceDatabase.kt`
- **AC:** Consistent repo-wide line endings; diff noise eliminated.
- **Done:** All 11 CRLF files under `:core:database` (incl. `AivanceDatabase.kt`, `DatabaseModule.kt`) normalized to LF. 68 CRLF files remain in other modules + `.idea/` — a repo-wide sweep is a follow-up.

### P2-05 — ~~Provider-log redaction coverage review~~ ✅ RESOLVED (2026-08-04)
- **Effort:** S · **Area:** `core:ai-providers`
- **AC:** Any future header carrying secrets is added to the `redactHeader` list; documented in the logging checklist.
- **Done:** The shared `NetworkModule.provideLoggingInterceptor` (used by job/enrichment providers) had **zero redaction** while USAJobs sends an `Authorization-Key` header — now redacts `Authorization`, `x-api-key`, `Authorization-Key`, with a KDoc checklist referencing the per-provider redaction in `core:ai-providers`.

---

## Reference Repository Utilization (2026-08-04)

Research-backed integration opportunities from the 8 referenced OSS repos, mapped against AiVance's architecture (`core:job-providers`, `JobMapper`, Resume Engine, Prep Studio, Tracker).

**License gate:** AiVance is proprietary (commercial, Play Store). Only **MIT/ISC**-licensed code may be ported. **AGPL-3.0 and unlicensed** repos are *reference only* — their patterns may be reimplemented from scratch, never copied.

| Repo | License | Verdict |
|---|---|---|
| [speedyapply/JobSpy](https://github.com/speedyapply/JobSpy) | MIT | ✅ Port patterns: new job providers, salary normalization, proxy rotation |
| [remoteintech/remote-jobs](https://github.com/remoteintech/remote-jobs) | ISC | ✅ Dataset integration: remote-company catalog |
| [amruthpillai/reactive-resume](https://github.com/amruthpillai/reactive-resume) | MIT | ✅ Adopt JSON Resume schema standard |
| [MadsLorentzen/ai-job-search](https://github.com/MadsLorentzen/ai-job-search) | MIT | ✅ Port fit-scoring + STAR prep workflows |
| [lukasz-madon/awesome-remote-job](https://github.com/lukasz-madon/awesome-remote-job) | community list | ✅ Curated resources content |
| [GodsScion/Auto_job_applier_linkedIn](https://github.com/GodsScion/Auto_job_applier_linkedIn) | AGPL-3.0 | ⚠️ Reference only — reimplement patterns from scratch |
| [navchandar/Naukri](https://github.com/navchandar/Naukri) | none | ⚠️ Reference only — profile-freshness concept |
| [lordzohar/Naukri-autoapply-bot](https://github.com/lordzohar/Naukri-autoapply-bot) | none | ⚠️ Reference only — quota-tracking pattern |

### R-01 — Add JobSpy-modeled job providers (ZipRecruiter, Glassdoor, Bayt, Naukri)
- **Effort:** L · **Priority:** P2 · **Area:** `:core:job-providers`
- **Why:** AiVance's LinkedIn/Indeed run on paid Apify actors. JobSpy (MIT) demonstrates direct-HTTP request flows for ZipRecruiter, Glassdoor, Google, Bayt, bdjobs and Naukri behind one normalized `JobPost` schema — porting the flow + rotating-proxy/retry patterns adds free sources and reduces Apify dependency. Also port its salary normalization (interval, `enforce_annual_salary`) into `JobMapper.parseSalary`.
- **AC:** New providers registered in `JobProvidersModule`; each maps through `JobMapper` into `JobListing`; MockWebServer unit tests per provider; health checks pass; search aggregation includes the new sources when configured.

### ~~R-02 — Remote-company catalog from remoteintech/remote-jobs~~ ✅ RESOLVED (2026-08-11)
- **Effort:** M · **Priority:** P1 · **Area:** `:core:job-providers`, `companies` tables
- **Why:** ISC-licensed dataset of hundreds of remote-friendly companies with structured metadata (`remote_policy`, `region`, `company_size`, `technologies`, `careers_url`). Enables remote-first filtering and company enrichment in Job Discovery without scraping.
- **AC:** Catalog seeded from a bundled/generated snapshot; discovery filters by remote policy + technologies; `JobDetailsViewModel` enriches the company view from the catalog; refresh tooling documented.

### ~~R-03 — JSON Resume import/export (reactive-resume schema)~~ ✅ RESOLVED (2026-08-11)
- **Effort:** M · **Priority:** P1 · **Area:** `feature:resume` Resume Engine, `core:util`
- **Why:** reactive-resume (MIT) is built on the open **JSON Resume** standard. AiVance already exports PDF/DOCX; adding JSON Resume round-trip gives interoperability with reactive-resume and other builders plus a stable portable format.
- **AC:** Export current `ResumeVersion` to `resume.json` (JSON Resume schema v1.0.0); import validates and maps `basics`/`sections`/`work`/`education` back into versioned sections; round-trip unit tests.

### ~~R-04 — AI job-fit scoring (ai-job-search workflow)~~ ✅ RESOLVED (2026-08-11)
- **Effort:** M · **Priority:** P1 · **Area:** `feature:jobs`, `JobFilterMatcher`, AI providers
- **Why:** ai-job-search (MIT) ranks listings on skills/experience/culture/location dimensions via a fit matrix. AiVance has AI providers + a structured matcher; adding a fit-score pipeline turns discovery from filter-only into ranked-by-fit.
- **AC:** `fitScore` computed per listing (LLM-assisted, cached); discovery shows a fit badge/sort; degrades gracefully to a rule-based fallback when no AI provider is configured.
- **Done:** New `ScoreJobFitUseCase` (`core:domain`) batches up to 10 listings into a single AI prompt against the user's `ProfileState` (target role, skills, work preference, active query), parses the JSON `{id: score}` response (fence-tolerant), clamps 0–100, and caches per (jobId, profile signature). Graceful degradation is structural: no provider / failure / unparseable response simply returns what's cached and callers fall back to the deterministic `JobFitScorer`. `JobsViewModel` computes a merged score map per search (single-flight, cleared on new search) and exposes it in state; the discovery card badge now shows AI-upgraded scores, and a new "Best match" chip sorts the list by fit. Evidence: 10 `ScoreJobFitUseCaseTest` + 3 `JobsViewModelTest` cases green; full `testDebugUnitTest` + `assembleDebug` green.

### ~~R-05 — STAR interview prep packs (ai-job-search `/interview`)~~ ✅ RESOLVED (2026-08-11)
- **Effort:** S · **Priority:** P2 · **Area:** `feature:interview` Prep Studio
- **Why:** ai-job-search generates stage-specific STAR prep and roleplay via LLM. AiVance's Prep Studio can reuse the same prompt structure through its existing AI providers.
- **AC:** Prep Studio generates STAR-format question packs for a chosen role; answers persist into interview sessions; uses the existing streaming path.

### ~~R-06 — Remote-work resources hub (awesome-remote-job)~~ ✅ RESOLVED (2026-08-11)
- **Effort:** S · **Priority:** P2 · **Area:** `feature:profile` About/Resources
- **Why:** Curated lists of job boards, interview-prep platforms and remote-first companies make a useful reference screen.
- **AC:** Resources screen with categorized links (boards, prep, companies); static content with localized strings.

### ~~R-07 — Apply-assist keyword rules + daily quota awareness (reference-only)~~ ✅ RESOLVED (2026-08-11)
- **Effort:** M · **Priority:** P2 · **Area:** `feature:jobs`, `feature:tracker`
- **Why:** Auto_job_applier_linkedIn (AGPL — reference only) uses blacklist/whitelist keywords and confirm-before-submit; Naukri bots (unlicensed — reference only) track daily application quotas. Reimplement these UX patterns from scratch: keyword exclusions in job filters + a daily application counter in Tracker.
- **AC:** Job filters support exclude/include keyword chips; Tracker shows today's application count vs. a configurable daily cap; no code from AGPL/unlicensed repos is copied (patterns reimplemented only).

---

## Tracking

- Sprint status, acceptance evidence, and certification reports: `DATABASE_CERTIFICATION.artifact.md`, `SECURITY_CERTIFICATION.artifact.md`.
- Verification harnesses (executable evidence): `security_scan.py`, `migration_validate.py`, `db_certify.py`, `test_sql_check.py`.
- Known/remaining issues: `KNOWN_ISSUES.md`.
