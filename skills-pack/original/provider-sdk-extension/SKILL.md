---
name: provider-sdk-extension
description: Add a new AI, job, or enrichment provider to the Aivance provider SDK — the full checklist: interface contract (AIProvider/JobProvider/EnrichmentProvider), metadata/config fields, Hilt registration (IntoSet multibinding + factory), refresh/health/list wiring, and tests. Use whenever a user asks to add or swap a provider (Gemini/Claude/Groq/OpenAI/OpenRouter/Ollama, LinkedIn/Indeed/Adzuna/Hunter, a new free API from public-apis-reference, or an on-device model), or when a provider is "registered but never refreshed".
---

# Provider SDK Extension

Aivance's provider platform is metadata-driven: providers are self-describing
(`ProviderMetadata` + `configFields`) and registered via Hilt multibinding, so
the UI, health checks, and selection are all generated. Adding a provider is a
**mechanical, checklist-able task** — this skill makes it repeatable and
complete.

## The 8-step checklist

### 1. Implement the contract

- **AI**: extend `AIProvider` (abstract `AIProvider` class in `core:sdk`):
  `generateText`, `chat`, `streamText`, `streamChat`, `listModels`.
- **Job**: implement `JobProvider` (`searchJobs` + `getJobDetails`).
- **Enrichment**: implement `EnrichmentProvider`.
- **On-device/keyless LLM**: implement `AIProvider` + `ModelDownloadable` — see
  the `on-device-llm-integration` skill.
- Lifecycle: override `onInitialize/onStart/onStop/onDispose`; self-mark
  `InvalidConfiguration` in `onInitialize` when credentials are missing so the
  orchestrator never clobbers it with `Ready` (that would fire doomed 401s).

### 2. Declare honest metadata

```kotlin
ProviderMetadata(
    id = "provider-id",          // stable, snake/kebab — used in EVERY list
    name = "Display Name",
    type = ProviderType.AI,      // or JOB / ENRICHMENT
    description = "...",
    configFields = listOf(ConfigField(key = "apiKey", label = "API Key", isSensitive = true, ...)),
    supportedModels = listOf(...) // empty → filled from GetAvailableModelsUseCase
)
```

Set `isConfigured`/`hasCredentials` correctly — they drive `getBestProviderFor`
selection tiers (Active+credentials > Ready+credentials > Active+configured >
Ready+configured > anything). A keyless local provider must return
`hasCredentials = false` so real keys win.

### 3. Register in DI — the most-forgotten step

In `AiProvidersModule` (or the job/enrichment equivalent):
1. Add the provider to the `@Provides @IntoSet fun provideXxxProvider(...)`
   **or** a `ProviderFactory.Factory` entry so it's discovered dynamically.
2. **The classic bug**: a provider registered in DI but absent from every
   *hardcoded list* — it's then never health-checked, refreshed, or selectable.
   Add the id to ALL of:
   - `ProviderRefreshWorker.knownProviders` (app module)
   - `GetAvailableModelsUseCase` defaults (core:domain)
   - `AiSettingsViewModel` provider list (feature:profile)
   - any onboarding provider list

### 4. Wire config persistence

- `ProviderRepository.saveProviderConfig` persists `ProviderConfiguration`
  (settings map + secrets map). `secrets` go to encrypted DataStore.
- On save, call `ProviderManager.reconfigure(id, config)` so the singleton
  provider re-initializes **immediately** — persisting without re-applying
  leaves the provider unconfigured until app restart (a historical bug).
- Adzuna-style multi-field keys (appId:appKey) need a split in the ViewModel.

### 5. Selection & health

- `ProviderManager.getBestProviderFor(capability)` — no change needed; tiers are
  automatic from status/credentials.
- `validateProvider` applies the candidate config, runs `onInitialize` +
  `checkHealth`, and maps rejected statuses to friendly messages.

### 6. UI (only if the provider is user-facing)

Provider cards are generated from `ProviderInfo` — the UI needs no per-provider
code unless it's **on-device** (download button instead of API-key field — see
the `on-device-llm-integration` skill).

### 7. Tests — mirror the existing suites

- Provider behavior: fake the HTTP layer (MockWebServer) or engine/downloader.
- `ProviderRefreshWorker` test: update `hasKnownProviders` to the new full set.
- Registry/selection: extend `ProviderManagerTest` if status tiers changed.

### 8. Docs + verification

- Update CHANGELOG (Added section) and KNOWN_ISSUES if fixing a gap (M-number).
- **Verify live facts** (URLs, free tiers, response shapes) per the
  `verify-before-claim` skill. Never ship a provider whose endpoint you
  haven't confirmed.

## Anti-patterns

- ❌ Registering in DI but not in the refresh/selection lists (Claude was missed
  this way for a full release cycle).
- ❌ `filterIsInstance<X>()` when you need `BaseProvider.status` — use a
  predicate (`it is X && it.isXReady`) to keep the supertype.
- ❌ Assuming a keyed provider wins selection — `hasCredentials` must be true or
  a keyless localhost Ollama beats your real Groq key.
- ❌ Shipping an API call whose shape you never validated against real docs.
