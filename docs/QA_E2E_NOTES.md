# QA E2E Notes — Real-API Walkthrough (2026-08-11)

Companion to `RELEASE_CHECKLIST.md`. This is the **evidence log** for the live
end-to-end pass with real Apify + Groq keys on the `aivance` emulator
(x86_64, Android 11), including every navigation/feature touched, what was
found, and what was fixed.

## 1. Real provider keys

| Provider | Key | Result |
|---|---|---|
| Groq | `gsk_opeeq…6AM9` | ✅ Entered via Provider Management → saved → **HEALTHY** (live `GET /models` 200) |
| Apify | `apify_api_cpq4…ES8K` | ✅ `GET /v2/acts?token=…` **HTTP 200**; actor run POST **HTTP 201** |
| Apify (mistyped) | `apify_api_CPQ4…` (uppercase) | ❌ 401 on every endpoint — Apify tokens are case-sensitive. Was stored by an earlier typo; corrected via the LinkedIn card's Apify API Key field |

**Bug found (fixed)**: the stored token had `CPQ4` uppercased. Requests logged
in OkHttp showed `token=apify_api_CPQ4Uvdw…` → `Apify run failed to start: 401`
and the circuit breaker tripped LinkedIn to DEGRADED. Re-entering the key with
the correct case fixed it — verified by the live request log
(`token=apify_api_cpq4Uvdw…`) and the subsequent successful actor run.

## 2. Filters do NOT block results

The zero-result trap was investigated end-to-end:

- With every filter at "All" (`Country/State/City/Workplace/Type/Remote`),
  `JobSearchFilter` has empty lists/nulls and `JobFilterMatcher` skips
  filtering for empty collections — nothing is dropped client-side.
- The `Experience` dropdown label "Experience" is the placeholder for
  `minExperienceYears == null` (`experienceLabel()` in `JobsScreen.kt`) — it is
  **not** an active filter.
- The real causes of a zero-result search were provider-side: the Apify actor
  returned evergreen junk (keyword ignored → filtered out by the client query
  pass) and the free feeds had few matching listings for narrow queries.
- **Proof of real results**: query `developer` rendered
  `Senior Independent Software Developer — A.Team — 80 Good Match —
  Remote · Contract`, `Full-Stack Developer / App / AI (m/w/d) — Factory
  Innovations GmbH — Berlin`, etc. Persisted row counts:
  `1755 arbeitnow · 114 remoteok · 100 jobicy · 20 remotive · 100 linkedin`.

## 3. Bugs found & fixed

### 3.1 CRASH — Gemma model download (foregroundServiceType) ★
- **Repro**: onboarding → select Gemma (On-device) → Continue starts the model
  download worker.
- **Crash** (logcat 18:51:54):
  ```
  java.lang.IllegalArgumentException: foregroundServiceType 0x00000001 is not a
  subset of foregroundServiceType attribute 0x00000000 in service element ...
  androidx.work.impl.foreground.SystemForegroundService.startForeground(...)
  ```
- **Root cause**: `GemmaModelDownloadWorker` requests
  `FOREGROUND_SERVICE_TYPE_DATA_SYNC` but the merged manifest declared no
  `foregroundServiceType` on `SystemForegroundService` (required on Android 14+).
- **Fix** (`app/src/main/AndroidManifest.xml`): added
  `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` permissions and
  `<service android:name="androidx.work.impl.foreground.SystemForegroundService"
  android:foregroundServiceType="dataSync" tools:node="merge"/>`.
- **Verified live**: after reinstall, WorkManager resumed the persisted
  download — foreground service started (visible in `dumpsys activity
  services`), progress advanced `0.08704 → 0.08705 → …` in
  `WM-WorkProgressUpdater`, UI showed "Downloading model… 8%", app stayed alive.

### 3.2 Apify DTO schema mismatch
- Real actor items emit `companyName`, `postedDate`, `contractType`; the DTO
  only read `company`, `postedAt`, `type` → company/type/date silently lost.
- Fixed with `@SerialName` fallbacks + mapper preferring the real-schema value.

### 3.3 Apify actor input mismatch
- `{"search": …}` is ignored by the LinkedIn actors — every run returned
  evergreen postings ("General Apply", "JOIN THE Family", "Careers",
  "Don't fit another role…") regardless of keyword (reproduced with curl on
  both `valig~linkedin-jobs-scraper` and `curious_coder~linkedin-jobs-scraper`,
  with and without `keywords`/`locationIds`).
- **Final verified input** (live, 2026-08-11): the actor keys off its AI-search
  filters — `keywords` (string) + `location`/`country` + `maxItems`. With
  `keywords="Android Engineer"` + `location="United States"` it returns real
  keyword-relevant LinkedIn roles: Software Engineer II, Android Engineering
  (Axon) · Software Engineer II, Android (Pinterest) · Software Engineer,
  Android — All Teams (DoorDash) · Android Engineer, Applied Foundations
  (OpenAI) · Stellantis · EVgo · Red Cat Holdings · Waymo… `positions[]` with an
  empty location returns an explicit actor error ("Provide either LinkedIn jobs
  search URLs, or fill in the AI search filters (keywords, location, etc.)").
- **Poll budget**: the actor takes 60–120s+; the old 30×2s (60s) poll budget
  timed out before the dataset was ready (run `C3Tc7OFyczet2uDyg`), so LinkedIn
  silently contributed nothing. Bumped to 90×3s (≈4.5 min), covering the
  actor's 300s timeout.
- Switched the LinkedIn provider to the canonical
  `curious_coder~linkedin-jobs-scraper`. Client-side keyword filtering still
  trims non-matching items.

### 3.4 Apify free-tier quota exhaustion (upstream, not an app bug)
- After ~8 verification runs the token's free-tier monthly compute budget was
  exhausted: new runs return **403 "Monthly usage hard limit exceeded"**
  (user plan FREE, $5/month, 625 compute units). The app degrades gracefully:
  circuit breaker trips LinkedIn, then the per-provider cache fallback returns
  only LinkedIn's own cached jobs ("Network failed for linkedin, returning 1
  cached jobs") — no cross-provider echo.

## 4. Navigation & features exercised

- **Auth**: fresh account `qa.e2e.2026@gmail.com` created via Continue —
  Welcome → onboarding → Dashboard.
- **Onboarding**: Gemma step shows **Download model** button, no URL field
  (previous session's fix still intact); Continue gated on download.
- **Dashboard**: greeting, Naukri chip, Career Score 18, ATS Match 0%,
  quick commands, suggested advice — all render.
- **Identity Hub** (Dashboard → profile icon): tabs Identity / Preferences /
  Providers / Vault / System; Provider Center lists per-provider health +
  masked keys + refresh + toggle.
- **Provider Management** ("Manage Providers — API Keys & Models"): AI /
  Job / Enrichment sections; per-provider Save/Test; key fields (Groq API Key,
  Apify API Key, USAJobs, OpenAI, Hunter…).
- **Job Discovery**: query + structured filters; real results render below the
  filter panel (scroll past "Best match"); jobs persisted per source provider.
- **Intelligence Hub**: Your Resumes, Recent ATS Scans (legacy 0% report from
  a malformed earlier scan remains in the DB — flagged, see §6).

## 5. Screenshot / logcat backlog

- `/tmp/qa/screen_now.png` — Job Discovery with filters ("All").
- `/tmp/qa/dash.png` — Dashboard (compact foldable posture).
- logcat evidence: `Apify run started: 3IKzbuA9KFlNGHn27 (READY)`,
  `Provider linkedin request failed (3/3)` (pre-fix 401), dataset fetch of
  `linkedin.com/jobs/view/…` URLs (post-fix), `WM-WorkProgressUpdater` progress
  ticks, FATAL EXCEPTION trace §3.1.

## 6. Leftover / follow-ups

- ~~The stale 0% ATS Scan row in the Intelligence Hub —~~ **done**: delete
  affordance added and verified live (see CHANGELOG, 2026-08-11).

## 7. Gemma on-device download & offline verification (2026-08-11) ★

**Green Downloaded state — verified visually.** The 3,136,226,711-byte
`gemma-3n-e2b-it-int4.task` completed at 19:15. In Provider Management →
AI Providers → Gemma (On-device), the card shows a **"Downloaded" chip**
whose background samples as `#DCFCE7` (emerald-100) with `#052E16`
(emerald-950) text — the success green — plus a **Delete model** action and
`gemma-3n-e2b-it-int4` as the selected model. (Screenshot:
`/tmp/qa/gemma3.png`, chip pixels verified via ImageMagick histogram.)

**Offline answer — proven end-to-end, airplane mode.**
1. `airplane_mode_on=1` (verified via `settings get global airplane_mode_on`).
2. Sent "What is the capital of France Answer in only one word" in the AI
   Assistant. **First attempt was misrouted** — see the routing bug below.
3. After the fix, the on-device Gemma streamed: *"The capital of France is
   **Paris**. It seems like you're focused on building your career…"*
   with the network still off. Screenshot: `/tmp/qa/offline_gemma_answer.png`.
4. `pidof` confirmed the app stayed alive through generation; XNNPack weight-
   cache loads in the process log confirm MediaPipe ran the local model.

### 7.1 BUG (fixed) — intent routing starved the LLM (incl. on-device) ★

`GetAssistantResponseUseCase` matched route keywords against the
**orchestrated** prompt, which embeds "Latest ATS Score: 0%" from
`ContextEngine`. Since `ats` is a route keyword, **every** assistant message
was short-circuited to `ANALYZE_RESUME` → "No resume found…" and the LLM was
never invoked. This is why the first offline prompt failed, and why general
chat never worked.

Fix: `AssistantRequest.rawUserMessage` (defaults to `userMessage`); intent
routing now runs on the raw text only, while the context-rich prompt still
reaches the LLM. Regression tests cover both directions.

### 7.2 BUG (fixed) — assistant chip labeled a job provider as the AI provider

The "AI provider" chip picked the first *Ready* provider of any type, so a
healthy job feed showed "Naukri · Ready" as the assistant's chat provider.
Now filtered to AI-Chat-capable providers only (verified live: "Ollama ·
Ready" / Gemma instead).
- Free LinkedIn actors return evergreen postings for keyword searches — with
  the fixed input + client-side filter they no longer pollute results, but a
  paid actor (or `locationIds` from LinkedIn geo codes) is needed for
  genuinely keyword-targeted LinkedIn results.
- The in-memory backstack still resets the current tab on full process death
  (pre-existing architecture limitation; typed data and DB state persist).

## 8. Full re-test pass with real APIs (2026-08-11) ★

Rebuilt + reinstalled the debug APK, configured **Groq** (`gsk_…6AM9`,
health 200, enabled) and confirmed **LinkedIn/Apify** (`apif_…ES8K`, HEALTHY)
persisted. End-to-end results:

- **Discovery**: "Android Engineer" search returned **real results**
  (Arbeitnow: "Full-Stack Developer / App / AI — Factory Innovations —
  90 High Match"; "Senior Graphic Designer — Lemon.io — 80 Good Match").
  LinkedIn contributed cached jobs because Apify's free tier is quota-exhausted
  (403, documented §3.4) — graceful degradation confirmed.
- **Job Details → Track**: added the job to the Pipeline kanban (Saved column,
  "1 of 5 applied today"). Kanban drag-and-drop exists (Compose DnD) but is
  not automatable via `adb swipe` (needs real long-press drag events).
- **Prep Studio**: "Quick Practice" was a **dead stub** (`onClick = {}` — the
  hero card had no callback). Fixed; live mock session now starts (Q1/5
  behavioral question + answer field + submit).
- **Pipeline "View Analytics"** was a dead stub → now opens the Intelligence
  Center (verified).
- **Job Details "Start Prep"** and **Analytics "Boost Score"** were dead
  stubs → now navigate (Start Prep → Prep Studio; Boost Score → Intelligence
  Hub).
- **Identity Hub**: "+ Add Skill" / "+ Add industry" chips were dead → now
  open add-dialogs (verified: "Jetpack Compose" added to Skills of Interest);
  "Export Career Data" was dead → now shares a text payload via the system
  sheet (verified). "Upload Document" (Vault) remains a stub.
- **Keyboard/IME**: the app is edge-to-edge but no screen applied
  `imePadding()` — the soft keyboard overlapped the assistant composer and
  auth/onboarding forms. Added `imePadding()` to the assistant input bar,
  AuthScreen scroll column, and onboarding columns. Verified: composer now
  sits above Gboard (pixel-checked).
- **Known automation limitation (not an app bug)**: the system file picker
  (DocumentsUI) does not return a selection via `adb input tap` on this
  emulator, so resume import through the wizard couldn't be driven here — the
  ATS scan pipeline itself was verified in §5/earlier sessions (report create,
  open, delete).
