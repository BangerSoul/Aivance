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

## G11 — Studio ▸ Practice is two tabs, not five (AUDIT 10–14)
```bash
grep -c "stringResource(R.string.practice)\|stringResource(R.string.question_bank)" \
  feature/interview/src/main/java/com/bangersoul/aivance/feature/interview/ui/PrepStudioScreen.kt
grep -rn --include=*.kt --exclude-dir=build -E "ResearchTab|HistoryTab" feature navigation app core
./gradlew :feature:interview:testDebugUnitTest :app:compileDebugKotlin --console=plain
```
EXPECT: the first grep prints **2** (the tab row is Practice + Question Bank
only); the second grep prints **no matches** (the Research and History tab
composables are gone). BUILD SUCCESSFUL. Research is folded into Practice as the
`RoleIntelligenceCard` (rendered only when `targetRole` + `skills` are non-blank),
History is folded in as `HistorySection`, and `LearnTab` is reachable only when a
skill seed arrives from the dashboard skill-gap chip — the tab-jump
`LaunchedEffect` that used to switch tabs on seed is removed. Practice keeps one
`verticalScroll` surface (no nested same-direction scroll).

## G12 — Analytics is one surface, showing only measured numbers (AUDIT 19)
```bash
! grep -q "TabRow" feature/analytics/src/main/java/com/bangersoul/aivance/feature/analytics/AnalyticsScreen.kt && echo "no tab row: ok"
! grep -rq --include=*.kt --exclude-dir=build -E "CareerTrendsTab|CareerSimulatorTab|buildHeatMapData|RecommendationCard" feature/analytics && echo "legacy sections gone: ok"
./gradlew :feature:analytics:testDebugUnitTest :app:compileDebugKotlin --console=plain
```
EXPECT: both guards print `ok` — the 3-tab row is gone, and the
Trends/Simulator tab composables plus the two dead helpers
(`buildHeatMapData`, `RecommendationCard`) are deleted — and BUILD SUCCESSFUL.
The single surface folds the Trends **Score Progression** chart in as a section
rendered only when two or more snapshots exist (one point is the empty 160 dp
canvas the audit flagged, `AnalyticsCharts.LineChart` bails on empty values); the
"Dimension Trends" bar chart is dropped because it duplicates Health Dimensions
from the same source. The Outcome Simulator renders only when `careerScore !=
null` — the model's own "nothing has been measured" signal (R3-1) — and its
projected score falls back to the current score instead of `—`. When nothing is
measured the surface shows one honest empty state pointing at scoring, rather
than the `—` / `0%` placeholders across three tabs.

## G13 — Identity Hub is one profile editor, not two (AUDIT 20)
```bash
! grep -q "fun PreferencesTab\|Save Preferences" feature/profile/src/main/java/com/bangersoul/aivance/feature/profile/IdentityHubScreen.kt && echo "one editor: ok"
grep -q 'val tabs = listOf("Identity", "Providers", "Vault", "System")' feature/profile/src/main/java/com/bangersoul/aivance/feature/profile/IdentityHubScreen.kt && echo "four tabs: ok"
./gradlew :feature:profile:testDebugUnitTest :app:compileDebugKotlin --console=plain
```
EXPECT: both guards print `ok` — the `PreferencesTab` composable and its second
`Save Preferences` button are gone, and the hub is four tabs — and BUILD
SUCCESSFUL. Identity and Preferences were the same `UserProfile`: both wrote
`draftProfile` through `UpdateDraftProfile` and both committed the whole record
through `SaveDraftProfile`, so the career preferences (remote/visa, target role,
skills, salary, industries, with their Add dialogs) are now a section of the
Identity tab under the same single Edit → Save flow. Read mode renders them
through the existing `IdentityField` (blank values still read "Not provided"
rather than a fake `—`), which also stops a preference edit from being stranded
when the user leaves without pressing the second Save. Tab indices shift
(Providers 2→1, Vault 3→2, System 4→3), and the `when` falls back to Identity so
a `selectedTab` saved before the merge cannot land on a blank screen.

## G14 — One pre-auth entry: Welcome is gone, Auth carries the brand (AUDIT 6)
```bash
! grep -rq --include=*.kt --exclude-dir=build "WelcomeScreen\|Destination\.Welcome" feature core app navigation && echo "welcome gone: ok"
./gradlew :navigation:testDebugUnitTest :feature:profile:testDebugUnitTest :app:compileDebugKotlin --console=plain
```
EXPECT: the guard prints `ok` — `WelcomeScreen`, its `Destination.Welcome`
label arm and its membership of `authDestinations` are all deleted — and BUILD
SUCCESSFUL. `Splash`'s unauthenticated arm now resolves to `Destination.Auth`
directly, and Auth absorbs the brand wordmark + tagline that Welcome carried,
single-sourcing the pre-auth surface. Welcome's two affordances went to the same
place ("Skip for now" and "Get Started" were both `Destination.Auth`), and its
six marketing bullets and shimmer CTA are deleted with the screen.

**Back-integrity:** because Splash was the screen *below* Welcome, Auth's
`onBackToWelcome` return path is gone; `onNavigate` now replaces Splash when it
hands off to the first pre-auth destination. The auth stack root becomes the
destination itself (single entry), so `BackHandler(enabled = size > 1)` leaves
system back alone and it exits the app, instead of popping to Splash and
re-running the splash forever.

## G15 — One Providers surface, two groups, no duplicate route (AUDIT 22)
```bash
! grep -rqE --include=*.kt --exclude-dir=build "ProviderManagementScreen|Destination\.ProviderManagement" feature navigation && echo "single provider surface: ok"
./gradlew :navigation:testDebugUnitTest :feature:profile:testDebugUnitTest :app:compileDebugKotlin --console=plain
```
EXPECT: the guard prints `ok` — `ProvidersTab`/`ProviderManagementScreen` and the
`Destination.ProviderManagement` arm are all gone, and every provider list in the
hub ships the same metadata-driven list (AI providers, then Job providers) from a
single `ProviderInfo` source. The second surface — the Identity Hub ▸ Providers
tab with its own badges plus the separate Provider Management screen — collapsed
into one "Providers" surface: AI first, then Job boards (the enrichment group
stays out of the default list, since enrichment is a step in the onboarding
gate, not a user-facing surface). The hub now owns provider management end to
end: card, enable/disable switch, health chip, model picker, credentials card,
download/delete for on-device models, Test connection, and Save. The config is
still the single source of truth (`ProviderRepository.getProviderConfigs()`),
so no provider list can drift from what is persisted.

## G6 — Whole-graph verification (all touched modules)
```bash
./gradlew :core:designsystem:testDebugUnitTest :navigation:testDebugUnitTest \
  :app:compileDebugKotlin --console=plain
```
EXPECT: `BUILD SUCCESSFUL` — the release gate for the full combo.
