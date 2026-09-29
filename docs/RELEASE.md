# Aivance Release Build Guide

How to produce a **signed, Play-ready release** from a fresh checkout. For the
pre-flight *what-to-check* list, see [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md).

---

## 1. Keystore setup

The release APK/AAB is signed with a PKCS12 keystore at the **repo root**:
`keystore.jks`. The build config (`app/build.gradle.kts`) reads it as
`file("../keystore.jks")` from the `app` module, and only applies signing when
the file **and** all three env vars (section 2) are present — otherwise the
release artifacts come out **unsigned** (`*-unsigned.apk`).

`keystore.jks` is gitignored. So is `keystore.env` (section 2).

### Generate a new keystore

```bash
keytool -genkeypair -v -keystore keystore.jks -storetype PKCS12 \
  -alias aivance -keyalg RSA -keysize 2048 -validity 9125 \
  -storepass '<A_STRONG_PASSWORD>' \
  -dname "CN=Aivance, OU=Mobile, O=Bangersoul, C=IN"
```

Notes:

- **25 years** (9125 days) validity — this is the signing identity for the app's
  lifetime. Keep it *forever*; losing it means you can never publish an update.
- PKCS12 requires the **store password and key password to be the same** —
  `-keypass` is ignored, the store password applies to the key too.
- Back up `keystore.jks` **and** the password somewhere secure, outside the repo.
  This is the single most important release asset.

## 2. Signing env vars

The build reads these from the environment (never hardcoded, never committed):

| Env var | Value |
|---|---|
| `AIVANCE_STORE_PASSWORD` | The keystore password |
| `AIVANCE_KEY_ALIAS` | `aivance` (the alias used at generation) |
| `AIVANCE_KEY_PASSWORD` | Same as store password (PKCS12) |

Keep them in a gitignored `keystore.env` at the repo root:

```bash
export AIVANCE_STORE_PASSWORD=...
export AIVANCE_KEY_ALIAS=aivance
export AIVANCE_KEY_PASSWORD=...
```

Source it before any release build:

```bash
source keystore.env && ./gradlew :app:assembleRelease
```

## 3. Build the release artifacts

The app ships ~40 MB of native libs per ABI (on-device LLM inference engine +
ML Kit OCR), so releases are **per-ABI split APKs**. AGP forbids split APKs and
a bundle in one build (issue 402800800), so the two artifacts are built in
separate invocations.

### Signed per-ABI APKs (direct distribution / sideloading)

```bash
source keystore.env && ./gradlew :app:assembleRelease
```

Outputs (all signed, R8-minified):

```
app/build/outputs/apk/release/app-arm64-v8a-release.apk      # ~51 MB
app/build/outputs/apk/release/app-armeabi-v7a-release.apk    # ~39 MB
app/build/outputs/apk/release/app-x86_64-release.apk         # ~55 MB
app/build/outputs/apk/release/app-x86-release.apk            # ~57 MB
app/build/outputs/mapping/release/mapping.txt                # R8 mapping (save for crash retracing)
```

A universal APK is **not** produced (`isUniversalApk = false` in
`app/build.gradle.kts`). To restore one for sideloading-everywhere, set it back
to `true`.

### Signed AAB (Play Store)

```bash
source keystore.env && ./gradlew :app:bundleRelease -Paivance.disableAbiSplits=true
```

Output: `app/build/outputs/bundle/release/app-release.aab` (~82 MB, contains
all four ABIs — Play splits per device automatically).

> To build both artifacts in one pass for local testing, run the two commands
> above back to back; do **not** combine them in a single Gradle invocation.

### Debug builds

Debug builds are also per-ABI splits now: `app-x86_64-debug.apk` etc. (the old
`app-debug.apk` no longer exists). The debug app id has a `.debug` suffix
(`com.bangersoul.aivance.debug`), so debug and release can coexist on one device.

## 4. Verify the signed artifacts

### Signature check

```bash
$ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-x86_64-release.apk
# Verifies
# V2 Signer: certificate DN: CN=Aivance, OU=Mobile, O=Bangersoul, C=IN
```

v1 (JAR) signing is off by design — minSdk 26 means v2/v3 are what Play wants.

### Live smoke test (emulator)

```bash
adb install -r app/build/outputs/apk/release/app-x86_64-release.apk   # x86_64 emulator
adb shell am start -n com.bangersoul.aivance/com.bangersoul.aivance.MainActivity
```

Then walk the core flows: onboarding → dashboard → a real job search
(Retrofit/serialization under R8) → Intelligence Hub (Room under R8).
Watch logcat for `FATAL EXCEPTION` — the ProGuard rules are tuned so R8 must
not strip Hilt, Room, serialization, Retrofit/OkHttp, or navigation.

## 5. CI (GitHub Actions)

`.github/workflows/ci.yml` builds and signs release artifacts automatically when
the following **repository secrets** exist (Settings → Secrets and variables →
Actions):

| Secret | Value |
|---|---|
| `AIVANCE_KEYSTORE_BASE64` | `base64 -w0 keystore.jks` |
| `AIVANCE_STORE_PASSWORD` | Keystore password |
| `AIVANCE_KEY_ALIAS` | `aivance` |
| `AIVANCE_KEY_PASSWORD` | Same as store password |

Secrets absent → the pipeline degrades to debug-only builds. The CI job
uploads the four split APKs, the AAB, and the R8 mapping file as artifacts.

## 6. Rotation / recovery

If you **ever** lose `keystore.jks` or its passwords, the installed app can no
longer be updated (signature mismatch). The only mitigation is a fresh keystore
→ version bump → reinstall for users (data loss). Keep the keystore backed up
in at least two places. If the keystore is ever exposed, rotate the passwords
by regenerating `keystore.jks` **before any user installs an update signed
with the leaked one** — and update `keystore.env` and the CI secrets together.
