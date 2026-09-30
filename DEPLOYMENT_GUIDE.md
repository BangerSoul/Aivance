# AiVance Deployment Guide

This guide covers building, signing, and deploying AiVance across the four environments: **Development → QA → Beta → Production**.

## Build Variants & Environments

| Environment | Build type | ApplicationId | Notes |
| :--- | :--- | :--- | :--- |
| Development | `debug` | `com.bangersoul.aivance.debug` | Minification off, `-debug` version suffix. |
| QA | `debug` + test tracks | `com.bangersoul.aivance.debug` | Play internal/app testing tracks. |
| Beta | `release` (signed) | `com.bangersoul.aivance` | Play closed/open beta track. |
| Production | `release` (signed) | `com.bangersoul.aivance` | Play production track. |

- `compileSdk`/`targetSdk` 37, `minSdk` 26.
- Version: `versionCode 1`, `versionName "1.0.0"` (semantic versioning; see `RELEASE_GUIDE.md`).

## Prerequisites

1. JDK 17, Android SDK with `platforms;android-37`.
2. Release signing credentials, from **either** source (see below).
3. CI repository secrets: `AIVANCE_KEYSTORE_BASE64`, `AIVANCE_STORE_PASSWORD`,
   `AIVANCE_KEY_ALIAS`, `AIVANCE_KEY_PASSWORD`.

### Signing credentials

`app/build.gradle.kts` resolves signing credentials in this order:

| Source | Use for | Contents |
| :--- | :--- | :--- |
| Environment variables | CI | `AIVANCE_STORE_FILE`, `AIVANCE_STORE_PASSWORD`, `AIVANCE_KEY_ALIAS`, `AIVANCE_KEY_PASSWORD` |
| `keystore.properties` (gitignored, repo root) | local release builds | `storeFile`, `storePassword`, `keyAlias`, `keyPassword` |

Both sources must be **complete**. Setting only some of the environment
variables is a hard error rather than a partially-signed build, because a
half-configured release produces an artifact that looks signed and cannot be
updated later.

### Unsigned vs. required signing

By default, `assembleRelease` / `bundleRelease` succeed with **no** keystore and
emit an unsigned artifact. That is intentional: it lets CI compile the release
variant on every PR, which is the only way R8 rule regressions and
resource-shrinker failures surface before release day.

Any build that is meant to ship must pass `-Paivance.requireSigning=true`,
which turns a missing or incomplete keystore into a `GradleException` before
compilation starts. The release workflow sets this flag.

## Building

```bash
# Debug APK
./gradlew assembleDebug

# Release AAB (Play)
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab

# Universal release APK
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk

# Signed release — fails loudly if the keystore is missing
./gradlew bundleRelease -Paivance.requireSigning=true
```

Release builds run R8 (minify + shrink resources) and emit:
- `app/build/outputs/mapping/release/mapping.txt` (ProGuard mapping — keep private).
- `app/build/outputs/native-debug-symbols/` (symbol table for crash symbolication).

## Verification Before Deploy

```bash
./gradlew testDebugUnitTest lintDebug bundleRelease assembleRelease
```

CI does this automatically in the `build` job (gated on `code-quality`, `unit-tests`, `security-scan`).

## Deployment Pipeline

The CI `release` job (manual `workflow_dispatch`) uploads to Google Play:

1. Downloads the release AAB + ProGuard mapping artifacts.
2. Extracts version from `app/build.gradle.kts`.
3. Uploads via `r0adkll/upload-google-play@v1`:
   - Track: `production`, status `completed`, `userFraction 0.1` (staged rollout).
4. Uploads the mapping file for crash symbolication.

**Required secrets**: `PLAY_SERVICE_ACCOUNT_JSON`, plus signing vars listed above.

## Rollout & Rollback

- **Staged rollout**: `userFraction: 0.1` → monitor KPI dashboards → 50% → 100%.
- **Rollback**: use Play Console "Rollback" to the previous AAB version; app versionCode must be monotonically increasing for future releases.
- **Hotfix**: tag `v1.0.x`, bump `versionCode`, ship through the same pipeline; only hotfixes permitted on the frozen v1.0.0 contracts.

## Local Configuration (no secrets in source)

- `local.properties` should contain only `sdk.dir` (and CI-safe test values). Real keys are injected via environment variables or the Settings → Provider screens (stored encrypted).
- Never commit `local.properties`, `keystore.jks`, or env values.
