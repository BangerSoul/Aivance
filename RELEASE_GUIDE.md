# AiVance Release Guide

This guide defines how AiVance versions are managed and how a release is cut, validated, and shipped.

## Versioning

- **Scheme**: Semantic Versioning (`MAJOR.MINOR.PATCH`).
- **Current**: `1.0.0` (`versionCode 1`).
- **Rules**:
  - `MAJOR` — breaking changes to public APIs, DB schema semantics, or design-system contracts.
  - `MINOR` — backward-compatible features.
  - `PATCH` — backward-compatible fixes.
- `versionCode` increments monotonically for every Play upload.
- Debug builds append `-debug` to `versionName`.

## Release Types (CI `workflow_dispatch`)

The `Release` workflow (`.github/workflows/release.yml`) takes:

| Input | Default | Effect |
| :--- | :--- | :--- |
| `release_type` | `stable` | `stable` maps to the Play `production` track with a staged rollout; `alpha`/`beta`/`rc` map to `beta`/`draft`. |
| `publish_to_play` | `false` | Uploads the AAB. Requires the `PLAY_SERVICE_ACCOUNT_JSON` secret. |
| `create_github_release` | `true` | Attaches the AAB to a **draft** GitHub release. |

It also runs on any `v*` tag push, where `release_type` does not apply and Play upload is skipped.

The workflow **builds, signs and verifies** on every run. Only publishing is opt-in, so a signing or R8 regression is caught whether or not anyone intends to ship.

## Release Process

### 1. Pre-release checks (Release Candidate)
- [ ] Full test suite green: `./gradlew testDebugUnitTest`.
- [ ] Lint + static analysis clean.
- [ ] `assembleDebug` and `bundleRelease`/`assembleRelease` succeed.
- [ ] Instrumented tests pass (CI: API 35, `google_apis` image, x86_64).
- [ ] Manual QA checklist complete (see `TEST_PLAN.md`).
- [ ] `KNOWN_ISSUES.md` reviewed — no release-blocking issues.
- [ ] Telemetry sweep — no credentials in logs.

### 2. Bump version
- Update `versionCode` and `versionName` in `app/build.gradle.kts`.
- Update `CHANGELOG.md` under `[Unreleased]` → new version heading.

### 3. Tag
```bash
git tag -a v1.0.0 -m "AiVance 1.0.0 — Production Launch"
git push origin v1.0.0
```

### 4. Build & sign
- Run the `Release` workflow (`.github/workflows/release.yml`). It fails at a
  preflight step listing any missing `AIVANCE_*` secrets, builds the signed AAB
  and per-ABI APKs with `-Paivance.requireSigning=true`, and then verifies the
  outputs: `jarsigner` on the bundle plus an explicit check for its `META-INF`
  signature block, and `apksigner verify --print-certs` on every APK, asserting
  they all share one signing certificate.
- Artifacts: `app-release.aab`, per-ABI `-release.apk`s, `mapping.txt`, native
  symbol table.
- Without the secrets this workflow cannot run, and PR CI compiles the release
  variant unsigned instead, so R8 regressions still surface on every PR.

### 5. Play Console submission
- **Recommended**: re-run the `Release` workflow with `publish_to_play: true`
  and `release_type: stable` (staged `userFraction 0.1`).
- **Manual alternative**: Play Console → App bundle explorer → upload AAB → release notes → rollout.
- Upload `mapping.txt` to Play Console for crash deobfuscation.

### 6. Post-release
- Monitor crash-free sessions and KPIs (see `OBSERVABILITY_GUIDE.md`).
- Freeze: **only hotfixes** on v1.0.0. Public APIs, DB schema, design system, provider SDK, navigation, and domain models are frozen.
- Record the release in `IMPLEMENTATION_LOG.md`.

## Release Notes Template

```markdown
### Highlights
- <feature>
### Fixes
- <fix>
### Known Issues
- See KNOWN_ISSUES.md (H-01 …)
### Migration Notes
- Database v20; no breaking data changes for v19 → v20 upgrade.
```

## Rollback Plan

1. Play Console → roll back to previous version.
2. If data-affecting (e.g., migration issue), halt rollout immediately; ship hotfix.
3. Post-mortem recorded; regression test added.
