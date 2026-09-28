# GATES.md — Aurora Design System (Kit B + N1 + F1 + I3)

Acceptance ledger for the AI-native rework. Every gate is a runnable command;
a gate passes only when its command exits 0 with the expected tokens.

## G1 — Design tokens compile and compose per kit
```bash
./gradlew :core:designsystem:compileDebugKotlin :core:designsystem:testDebugUnitTest --console=plain
```
EXPECT: `BUILD SUCCESSFUL`; DesignTokensTest green (three kits expose distinct
radii; Aurora gradient is teal→indigo; forKit round-trips).

## G2 — Design-kit preference persists and falls back safely
```bash
./gradlew :core:datastore:compileDebugKotlin --console=plain
```
EXPECT: `BUILD SUCCESSFUL`. `UserPreferences.designKit` defaults to
`AURORA_GLASS`; unknown serialized names fall back to Aurora in
AppThemeViewModel (runCatching guard).

## G3 — F1 type system (Space Grotesk + Inter) is bundled offline
```bash
ls core/designsystem/src/main/res/font/spacegrotesk_variable.ttf \
   core/designsystem/src/main/res/font/inter_variable.ttf
./gradlew :core:designsystem:compileDebugKotlin --console=plain
```
EXPECT: both TTFs exist with OFL license files; BUILD SUCCESSFUL (AuroraTypefaces
maps weights onto the variable axis; app renders identically with no network).

## G4 — I3 duotone icon intents: every destination resolves in both variants
```bash
./gradlew :navigation:compileDebugKotlin :navigation:testDebugUnitTest --console=plain
```
EXPECT: BUILD SUCCESSFUL; DestinationTest proves every authenticated destination
has a non-null outlined AND filled resolution, gate destinations an empty intent.

## G5 — N1 navigation: four workspaces + AI orb, back-integrity intact
```bash
./gradlew :navigation:testDebugUnitTest --console=plain
```
EXPECT: BUILD SUCCESSFUL; DestinationTest asserts rootDestinations is exactly
[Dashboard, Discovery(), Pipeline(), Studio()], the orb is not a root, every root
is authenticated, and no auth/authenticated overlap.

## G5b — Subtraction-first nav prune (AUDIT §3.2)
```bash
grep -rn --include=*.kt --exclude-dir=build -E "Destination\.(Assistant|Intelligence|PrepStudio|LearnSkill|DiscoverBySkill|TrackApplication)\b" app feature core navigation
./gradlew :navigation:testDebugUnitTest :navigation:compileDebugAndroidTestKotlin --console=plain
```
EXPECT: the grep prints **no matches** (the six legacy destinations are gone);
BUILD SUCCESSFUL; seeded variants (`Studio(PRACTICE)`, `Discovery(query)`,
`Pipeline(jobId)`) still resolve as authenticated, `Resources` is in
`authenticatedDestinations`, and DeepLinkHandler maps chat → AssistantOrb,
interview → Studio ▸ Practice, resume → Studio ▸ Resumes.

## G7 — Blocker remediation (B1–B5)
```bash
./gradlew :feature:resume:testDebugUnitTest :feature:jobs:testDebugUnitTest --console=plain
```
EXPECT: BUILD SUCCESSFUL; ResumeEngineViewModelTest asserts an imported JSON/OCR
resume is persisted as a draft (preview carries the stored resume + version ids,
so ATS and Save have FK parents), and JobsViewModelTest asserts Discovery opens
on the cached corpus when no search has run.

## G8 — Whole-suite release gate
```bash
./gradlew testDebugUnitTest --console=plain
```
EXPECT: `BUILD SUCCESSFUL` across every module — zero test breakages.

## G9 — Discovery is selection-first, not filter-first (AUDIT 46/48/49)
```bash
./gradlew :feature:jobs:testDebugUnitTest :app:compileDebugKotlin --console=plain
```
EXPECT: BUILD SUCCESSFUL; JobsViewModelTest asserts a pending search reports
`isSearching` with nothing on screen (so the skeleton — never "No matches
found" — covers the wait) and that a settled empty search clears it. The screen
keeps four controls (keywords, location, experience, remote-only) and pushes
type / workplace / remote policy / tech stack / must-include / exclude behind
one Filters sheet; the Discovery hero card is gone; Quick Match is guarded and
offers "Set target role" when the profile has none.

## G10 — One assistant surface, and a badge that tells the truth (AUDIT 15/32/41)
```bash
grep -rn --include=*.kt --exclude-dir=build "showAssistantAction = true" feature core navigation
./gradlew :feature:assistant:testDebugUnitTest :app:compileDebugKotlin --console=plain
```
EXPECT: the grep matches **only JobDetailsScreen** — the one job-context entry;
every other top-bar assistant action is gone (the orb owns the plain
assistant). BUILD SUCCESSFUL; AssistantViewModelTest asserts a provider that is
`Ready` but has no persisted configuration does not light the badge, and a
configured + ready provider does.

## G6 — Whole-graph verification (all touched modules)
```bash
./gradlew :core:designsystem:testDebugUnitTest :navigation:testDebugUnitTest \
  :app:compileDebugKotlin --console=plain
```
EXPECT: `BUILD SUCCESSFUL` — the release gate for the full combo.
