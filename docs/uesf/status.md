# UESF Adoption — Governance Status Snapshot

*2026-08-08 · Second workflow run: on-device offline LLM (Gemma) + provider toolkit completion*
*Owned by `uesf-gv-project-governance` discipline*

## Run 2 — On-device offline LLM + gap cleanup (2026-08-08)

### Change under review

- **New capability**: `GemmaOnDeviceProvider` (`core:ai-providers`) — a keyless
  AI provider running a quantized Gemma 3N E2B int4 model fully offline via
  MediaPipe LLM Inference (`tasks-genai`). Runtime model download with progress
  (`ModelDownloadable` SDK interface + `OkHttpModelFileDownloader`), lazy
  Mutex-guarded engine, streaming via `generateResponseAsync` ProgressListener,
  honest `InvalidConfiguration` until the model is present so keyed cloud
  providers still win selection.
- **Provider toolkit completion**: `anthropic` (and the new `gemma`) added to
  `ProviderRefreshWorker.knownProviders`, `GetAvailableModelsUseCase` defaults,
  and `AiSettingsViewModel` — Claude was previously never health-checked.
- **Dead code removal**: app template theme (`app/ui/theme/*`) deleted — zero
  references; real theme is `core:designsystem`.
- **UI**: Provider Management card gains download-progress / delete / ready
  states for on-device providers; Save/Test correctly hidden for keyless ones;
  strings EN + HI.

### Verification evidence

| Gate | Result | Evidence |
|------|--------|----------|
| Compile (touched modules) | BUILD SUCCESSFUL | sdk, ai-providers, domain, profile, navigation, app |
| Unit tests | 70 green, 0 failing | ai-providers 20 (11 new Gemma), profile 7 (3 new VM), navigation 8, app 35 |
| Full assemble | BUILD SUCCESSFUL | `libllm_inference_engine_jni.so` packaged |
| UESF validator | PASS — 41 skills, 0 errors | `python3 uesf/tools/validate_framework.py` |
| UESF framework suite | OK | `python3 -m unittest discover -s uesf/tests` |
| Install drift | 0 drift | `diff -rq uesf/core/uesf-co-* .agents/skills/` |
| External review | All findings addressed | keyless UX, KDoc distribution/ToU, unused import, naming, isConfigured, no-op chip |

### Risk register (run 2)

| Risk / follow-up | Owner | Mitigation |
|------------------|-------|------------|
| Default model URL is a community HF mirror (supply chain) | Operator | Documented in KDoc; `modelUrl` config overrides; self-host before release |
| Gemma ToU requires end-user flow-down | Legal | KDoc note; app ToS must bind users to Gemma Prohibited Use Policy |
| Device RAM for int4 model (~3–4 GB free) | Device | Int4 quantization chosen for low storage/RAM; backend DEFAULT lets runtime pick CPU/GPU |


## Milestones

| Milestone | Exit criterion | Status |
|-----------|----------------|--------|
| Install core loop skills | 6 skills in `.agents/skills/` with provenance README | ✅ done |
| CI gate wired | `.github/workflows/uesf.yml` validates framework + drift-checked installs | ✅ done |
| M-03/P2-01 baseline snapshot | Real snapshot on first dashboard view; tests green | ✅ done |
| M-03 data-layer guarantee (follow-up) | `AnalyticsRepositoryImplTest` 5/5; guarantee applies to all consumers | ✅ done |
| Framework proof | Full workflow run with artifacts | ✅ done |

## Change under review

- **Fix (final shape):** the baseline guarantee lives in the **data layer**.
  `AnalyticsRepositoryImpl.getSnapshots()` is self-healing — it captures a real
  baseline `AnalyticsSnapshot` (derived from actual applications / interview
  sessions / ATS results, never fabricated) whenever history is empty, guarded by
  a `Mutex` so concurrent collectors can't double-insert. Every consumer
  (analytics dashboard, `CareerStateEngine`, assistant context) inherits the
  guarantee, not just the dashboard view.
- **Refactor:** duplicated ATS-report/readiness/recruiter derivation shared by
  `createSnapshot()` and `getCareerIntelligence()` extracted into
  `ResumeAnalysisEntity.toAtsReport()` (mapper) + private helpers
  (`toAtsReports`, `calculateReadiness`, `collectRecruiters`).
- **Removed:** `AnalyticsViewModel.ensureBaselineSnapshot()` and its two tests
  (behavior now owned by the repository); `createSnapshot()` stubs cleaned from
  the remaining ViewModel tests.
- **Scope:** `core:data` + `feature:analytics`. No interface changes; the weekly
  worker and all existing consumers are unaffected (baseline only fires on an
  empty table).

## Verification evidence

| Gate | Result | Evidence |
|------|--------|----------|
| RED (failing test first) | `captures baseline snapshot…` FAILED pre-fix | `:feature:analytics:testDebugUnitTest` (6 tests, 1 failed) — first UESF run |
| GREEN (minimal fix) | BUILD SUCCESSFUL | `:feature:analytics:testDebugUnitTest` 6/6 → ViewModel-level fix shipped |
| Data-layer refactor | BUILD SUCCESSFUL | `:core:data:testDebugUnitTest` 91/91 incl. new `AnalyticsRepositoryImplTest` 5/5; `:feature:analytics:testDebugUnitTest` 4/4 |
| UESF validator | PASS — 41 skills, 0 errors, 0 warnings | `python3 uesf/tools/validate_framework.py` |
| UESF framework suite | 14/14 OK | `python3 -m unittest discover -s uesf/tests` |
| Install drift | 0 drift | `diff -rq uesf/core/uesf-co-* .agents/skills/` |

## Review verdict (uesf-co-review + external reviewer)

- **Verdict: APPROVE.**
- Reviewer findings addressed:
  1. CI did not validate installed copies → drift check added to `uesf.yml`.
  2. `createSnapshot()` failure silently dropped → KDoc now states it is
     intentional (worker backstop + next-view retry).
  3. Refresh race could double-capture → **eliminated** by moving the guard into
     the repository: `Mutex` + emptiness re-check makes concurrent collectors
     safe by construction.
  4. Repository-level `createSnapshot()` test (honesty guarantee) → **closed**:
     new `AnalyticsRepositoryImplTest` (5 tests) proves real-data derivation,
     the self-healing baseline, and no double-insert.

## Follow-ups (risk register)

| Risk / follow-up | Owner | Mitigation |
|------------------|-------|------------|
| ~~`createSnapshot()` derivation untested at repo level~~ | UESF adoption | ✅ **RESOLVED** — `AnalyticsRepositoryImplTest` 5/5 green (real-data honesty proof) |
| Baseline capture only on dashboard view (app startup not covered) | UESF adoption | Optional: startup capture in `AivanceApp` (deferred, weekly worker backstops) |
| `.agents/skills/` copies need refresh on framework bumps | CI drift check | `uesf.yml` fails on drift; README documents the refresh command |

## Decision log

- **2026-08-07** — Adopted UESF core loop into Aivance; validated the framework's
  own tooling as a CI gate. First real issue (M-03/P2-01) fixed end-to-end through
  the triage → implement → verify workflow.
- **2026-08-07** — Scope decision: fix at the ViewModel level (view-triggered
  baseline) rather than app-startup, keeping the change minimal and fully unit-
  testable; startup capture recorded as a follow-up.
- **2026-08-07** — Follow-up executed: baseline guarantee moved to the data layer
  (`self-healing getSnapshots()`), closing the repo-level-test follow-up and the
  refresh-race finding. Motivated by the brainstorm review: the ViewModel
  write-on-read healed only one consumer and duplicated derivation that
  `getCareerIntelligence()` already performed.
