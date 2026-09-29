# AiVance — Device Validation Runbook

**Purpose**: Step-by-step instructions for executing the two remaining P0 release blockers
that require a real Android device or emulator.

---

## P0-01 — Instrumented Database Test Suite

**Blocker**: `:core:database:connectedDebugAndroidTest` has never been executed on real hardware.
The SQL is proven by `migration_validate.py` + `db_certify.py` (SQLite3 replay), but
instrumented Room tests exercise byte-level migration edge cases that static replay cannot cover.

### Prerequisites

| Requirement | Detail |
|---|---|
| Android SDK | Installed, `adb` on PATH |
| Java | JDK 17+ |
| Device or emulator | API 26–35; USB debugging enabled or emulator running |
| Network | Not required |

### Verify device connectivity

```bash
adb devices
# Expected: at least one device listed as "device" (not "offline" or "unauthorized")
```

### Run the instrumented suite

```bash
# From the project root:
./gradlew :core:database:connectedDebugAndroidTest
```

### Expected pass output

```
Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESSFUL
```

> **Note (2026-08-11)**: the earlier "47" figure was stale — the suite is 37 tests (26 `MigrationTest` cases + DAO suites) since the T-04 `resume_analyses` table drop. P0-01 is now ✅ RESOLVED: executed on the `aivance` AVD (Android 11 / API 30), 37 tests, 0 failures, migration chain 5→25 verified on-device.

Key assertions to confirm in the output:
- **Migration tests 5→25**: each `MIGRATION_X_Y` runs without `SQLiteException` (includes the newest `migrate24To25_dropsLegacyResumeAnalyses` and `migrate10To25_legacyResumeAnalysesDropped`)
- **`PRAGMA foreign_key_check`**: all tests that explicitly run this check must return 0 rows
- **DAO tests** (AivanceFeatureDaoTest, AtsFeatureDaoTest, InterviewFeatureDaoTest, JobFeatureDaoTest, ProfileFeatureDaoTest): 0 failures

### If a test fails

1. Check the failure message for the migration number that failed.
2. Open `AivanceDatabase.kt` and locate the corresponding `MIGRATION_X_Y` object.
3. Re-run the static validator to confirm: `python3 migration_validate.py`
4. Fix the migration SQL, bump no version numbers (same schema, same version), re-run.
5. Record evidence (test output) before closing the blocker.

### Closing P0-01

- Copy the `BUILD SUCCESSFUL` + test count line to `TODO.md` under P0-01.
- Update `KNOWN_ISSUES.md` DR-01 to ✅ RESOLVED with date + device model.
- Update `TODO.md` P0-01 to ✅ RESOLVED.

---

## P0-02 — MITM Pen-Test (Certificate Pinning Validation)

**Blocker**: TLS certificate pinning is statically verified by `security_scan.py` (20/20 checks pass),
but Phases 12–13 of the security brief require *on-device* proof that the OkHttp
`CertificatePinner` actively blocks interception.

### Prerequisites

| Requirement | Detail |
|---|---|
| mitmproxy | v10+ recommended; install: `pip install mitmproxy` |
| Android device | API 26+; must support custom CA installation (non-rooted is fine) |
| Wi-Fi | Device and laptop on the same network |
| AiVance debug APK | `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` |

### Step 1 — Start the intercepting proxy

```bash
mitmproxy --listen-host 0.0.0.0 --listen-port 8080
```

Or in flow-dump mode (no TUI):

```bash
mitmdump --listen-host 0.0.0.0 --listen-port 8080 -w /tmp/aivance_flows.mitm
```

### Step 2 — Configure the device to route through mitmproxy

On the Android device:
1. Settings → Wi-Fi → long-press your network → Modify → Advanced → Proxy: Manual
2. Proxy hostname: `<your laptop IP>`, port: `8080`
3. Navigate to `http://mitm.it` in any browser and install the mitmproxy CA certificate.

### Step 3 — Run AiVance and trigger provider calls

Install the debug APK:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open AiVance and exercise each provider:
- Configure at least one AI provider (e.g., Gemini, Groq, OpenAI, Claude, OpenRouter)
- Run a resume analysis (triggers Gemini or Groq)
- Run a job search (triggers LinkedIn/Indeed/Apify, Adzuna, USAJobs if configured)
- Run a recruiter search (triggers Hunter.io)

### Step 4 — Verify active pinning

**Pass criterion**: For every pinned host, the app shows a connection error (network error snackbar,
or the request simply fails). mitmproxy's flow list should show `<<< (SSL handshake failed)` or
no successful response for the pinned hosts.

**Pinned hosts to check** (from `CertificatePins.kt`):

| Host | Provider |
|---|---|
| `generativelanguage.googleapis.com` | Gemini |
| `api.groq.com` | Groq |
| `api.openai.com` | OpenAI |
| `openrouter.ai` | OpenRouter |
| `api.anthropic.com` | Claude |
| `api.apify.com` | Apify |
| `api.adzuna.com` | Adzuna |
| `data.usajobs.gov` | USAJobs |
| `api.hunter.io` | Hunter.io |

### Step 5 — Verify graceful degradation

With the proxy active (blocking all pinned hosts):
- [ ] AI Assistant shows an error or falls back to on-device Gemma / Copilot (no crash)
- [ ] Job search shows an error state (not a silent empty list)
- [ ] No secrets appear in logcat: `adb logcat | grep -i "apikey\|token\|secret\|bearer"`

### Step 6 — Verify offline path

Disconnect the device from Wi-Fi entirely:
- [ ] App launches without crash
- [ ] AI Assistant falls back to on-device Gemma (if downloaded) or Copilot
- [ ] Job search shows an empty state with a retry prompt

### Closing P0-02

- Record each host's pinning behaviour (pass/fail) in a table.
- Record logcat evidence (no secrets leaked).
- Update `KNOWN_ISSUES.md` SR-02 to ✅ RESOLVED with date + device model.
- Update `TODO.md` P0-02 to ✅ RESOLVED.

---

## After Both P0s Are Closed

All Play Store submission blockers will be cleared. Update:

1. `TODO.md` — change P0-01 and P0-02 headings to ✅ RESOLVED
2. `KNOWN_ISSUES.md` — mark SR-02 and DR-01 resolved
3. `PRODUCTION_READINESS_REPORT.md` — final signoff note
4. Open a PR / tag `v1.0.0-rc2` (or submit directly to Play Store staged rollout)

---

*Runbook created*: 2026-08-10 · *Owner*: AiVance Engineering
