---
name: android-compose-craft
description: Android native development and UI craft with Kotlin + Jetpack Compose + Material 3 — project configuration, build troubleshooting, Compose patterns, accessibility, and testing. Read this before Android native application development or when modifying Android UI code. Inherited from MiniMax-AI/skills (android-native-dev) and the Android platform guidance in google/skills.
---

# Android Compose Craft

Android native application development and UI design guide: Material Design 3, Kotlin
and Compose development, project configuration, accessibility, and build
troubleshooting.

## 1. Project Scenario Assessment

| Scenario | Characteristics | Approach |
|----------|-----------------|----------|
| **Empty directory** | No files | Full initialization, including Gradle Wrapper |
| **Has Gradle wrapper** | `gradlew` + `gradle/wrapper/` exist | Use `./gradlew` directly |
| **Android Studio project** | Complete structure, may lack wrapper | Check wrapper, run `gradle wrapper` if needed |
| **Incomplete project** | Partial files | Check missing files, complete configuration |

**Key principle:** before writing business logic, ensure `./gradlew assembleDebug`
succeeds.

## 2. Build Configuration

### gradle.properties

```properties
android.useAndroidX=true
org.gradle.parallel=true
kotlin.code.style=official
# Increase -Xmx if you hit OutOfMemoryError; large projects may need 8GB+
```

### Version Catalog

Use `gradle/libs.versions.toml` for dependency versions — one source of truth, shared
across modules. Keep AGP, Kotlin, and Compose versions compatible (check the
Compose-BOM's AGP compatibility matrix).

## 3. Kotlin + Compose Conventions

- **State hoisting:** state lives in ViewModels; composables receive state + callbacks.
  Use `collectAsStateWithLifecycle` for collection (never plain `collectAsState` in
  production code).
- **Unidirectional data flow:** UI event → ViewModel → new state → recomposition.
- **Prefer immutable UI state** (data classes) — never expose raw mutable state.
- **Recomposition hygiene:** don't allocate unnecessarily in composable bodies; use
  `remember` for expensive objects and derived state via `derivedStateOf`.
- **Preview composables** for every screen — they make design iteration and review
  possible without a device.
- **Material 3:** use `MaterialTheme(colorScheme = …, typography = …, shapes = …)` with
  dynamic color on Android 12+ and a fallback palette below. Light/dark schemes both
  required.

## 4. Accessibility (non-negotiable)

- Every interactive element needs a content description (or a meaningful text label).
- Touch targets ≥ 48dp.
- Don't rely on color alone for state; pair with icons/shape/text.
- Test with font scale and display size maxed, and with TalkBack on.
- Respect reduced motion: gate animations behind `LocalSystemTheme`/system setting.

## 5. Navigation

Prefer a typed navigation library (Navigation Compose with serializable routes or
Navigation for Compose Multiplatform). Centralize the graph in one place; every
destination has a clear route, arguments are typed, and there's a fallback for invalid
routes. Deep links map to the same routes.

## 6. Testing

- **Unit tests:** ViewModels with coroutines test dispatchers, use cases, repositories,
  and pure logic — fast, JVM-only.
- **Compose UI tests:** `createComposeRule` + semantics matchers for screens and
  flows; keep them few but covering the critical paths.
- **Mock the boundaries** (repository interfaces, SDKs), never mock what you're testing.
- Run `testDebugUnitTest` for unit tests and `connectedDebugAndroidTest` for
  instrumented tests.

## 7. Build Troubleshooting

| Symptom | Likely fix |
|---|---|
| OutOfMemoryError | Raise `org.gradle.jvmargs=-Xmx` |
| Stale/dex weirdness | `./gradlew clean` then rebuild |
| Dependency conflicts | `./gradlew :app:dependencies` to inspect |
| Compose compiler mismatch | Align Kotlin/Compose versions via the BOM |
| R8/minify issues in release | Check `proguard-rules.pro` for missing keep rules |
| APK too large | Shrink per-ABI splits, review native libs (e.g. `.task` model files) |

## 8. Play Quality Baseline

- Declared permissions match actual usage.
- Data safety form matches the real data flows (this app encrypts and stores
  everything on-device — say so).
- App icons, adaptive icons, and splash screen present.
- Release build signed; `lintVitalRelease` passes.
