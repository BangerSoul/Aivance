---
name: on-device-llm-integration
description: Integrate a fully-offline, keyless LLM into an Android app using MediaPipe LLM Inference (tasks-genai) — model download at runtime, device-capability gating (free storage and RAM), a compact alternative model, and offline fallback routing so AI features work with zero connectivity. Use when adding an on-device model provider, wiring offline fallback for an assistant or AI feature, or gating a large download behind a device check.
---

# On-Device LLM Integration

Pattern proven in Aivance (`core:ai-providers` → `GemmaOnDeviceProvider` +
MediaPipe `tasks-genai`): a keyless, offline-capable LLM that downloads its
~2.9 GB model once, then runs with zero network. This skill captures the full
integration: SDK contract, engine wrapper, downloader, capability gate, compact
model, and offline fallback.

## Architecture

```
core:sdk        ModelDownloadable (interface)   ← host UI manages model lifecycle
core:ai-providers
  ├─ OnDeviceLlmEngine          (interface: generateResponse/streamResponse/close)
  ├─ MediaPipeOnDeviceLlmEngine (native wrapper, callbackFlow streaming)
  ├─ ModelFileDownloader        (interface: download(url, dest, onProgress))
  ├─ OkHttpModelFileDownloader  (streams to .part then renames)
  └─ GemmaOnDeviceProvider      (AIProvider + ModelDownloadable, keyless)
feature:profile DeviceCapabilityProvider (StatFs + ActivityManager, suspend, IO)
```

## 1. SDK contract — `ModelDownloadable`

The host UI needs real metadata to gate the download, so the interface exposes:

```kotlin
interface ModelDownloadable {
    val isModelReady: Boolean          // model file exists && length > 0
    val modelSizeBytes: Long           // EXACT verified size (see verify-before-claim)
    val compactModel: CompactModel?    // smaller alternative for constrained devices
    suspend fun downloadModel(url: String? = null, onProgress: (Float) -> Unit = {}): Result<Unit>
    suspend fun deleteModel(): Result<Unit>
}
data class CompactModel(val name: String, val sizeBytes: Long, val url: String)
```

Rules:
- Interface declares the default `url: String? = null`; **overrides must not
  redeclare defaults** (Kotlin compiler error).
- `isConfigured` should equal `isModelReady`; `hasCredentials` = false so keyed
  cloud providers win selection when both are set up.

## 2. MediaPipe engine wrapper

```kotlin
// tasks-genai 0.10.35 (verify version: dl.google.com maven-metadata.xml)
val options = LlmInferenceOptions.builder()
    .setModelPath(modelPath)
    .setMaxTokens(1024)
    .setPreferredBackend(LlmInference.Backend.GPU) // or CPU / DEFAULT
    .build()
val llm = LlmInference.createFromOptions(context, options)

// Streaming — the ProgressListener gives (partial, done):
llm.generateResponseAsync(prompt) { partial, done -> ... }
// One-shot:
val text = llm.generateResponse(prompt)
llm.close()
```

Stream partials to a `callbackFlow`; `trySend` each partial, `close()` when done.
Wrap in an interface (`OnDeviceLlmEngine`) so the provider is unit-testable with
a fake engine — never unit-test the native call.

## 3. Downloader

Stream to a `.part` file, then atomic-rename into place (delete stale model
first). Use OkHttp; report progress from `contentLength()`. On any failure,
delete the `.part`. See `ModelFileDownloader.kt` in this repo for the reference
implementation.

## 4. Capability gate — before offering the download

Check the device before showing the Download button (see
`DeviceCapabilityProvider` in `feature:profile`):

- **Free storage** (on the app-data volume, where the model lands): `StatFs(context.filesDir.path).availableBytes`
- **Total RAM**: `ActivityManager.MemoryInfo().totalMem`

Policy (implemented in the ViewModel, pure + unit-tested):

```kotlin
val primaryFits = free >= maxOf(2 GiB, modelSizeBytes * 1.15)  // ≥2 GiB AND room for the file
val compactFits = compact != null && free >= compactSizeBytes * 1.15
if (!primaryFits && !compactFits) → hard-block snackbar (no dialog)
dialog.storageBlocked = !primaryFits          // only compact offered
dialog.ramWarning   = totalRam < 4 GiB        // warn, still allow
dialog.offersCompact = compact fits && (blocked || low RAM)
```

- Run `StatFs`/`getMemoryInfo` on `Dispatchers.IO` (suspend the provider).
- Show the **exact byte count** in the confirmation dialog — rounded GB alone is
  a lie (2.9 GiB ≈ 3.1 GB decimal).
- Do the math in `Long` (`Long * Double` is a compile error in `maxOf`).

## 5. Offline fallback routing

When a cloud provider is configured but unreachable (airplane mode), the
fallback order must be: **best cloud provider → ready on-device model → canned
local reply**. Add an SDK helper:

```kotlin
fun getOnDeviceProviderFor(capability: ProviderCapability): BaseProvider? =
    registry.getProvidersByCapability(capability)
        .filter { it is ModelDownloadable && it.isModelReady }  // predicate, NOT filterIsInstance — preserves status
        .firstOrNull { it.status == Active } ?: firstOrNull { it.status == Ready } ?: firstOrNull()
```

In the use case:
- Try the best cloud provider (streaming, else chat).
- If blank, try `getOnDeviceProviderFor` with an **identity guard**
  (`onDeviceProvider !== primaryProvider`) — when the on-device model is already
  the best provider you must not re-invoke it.
- Only then fall back to the canned reply.
- "No provider configured" errors only when there is *neither* a cloud provider
  *nor* a downloaded model.

## 6. Gotchas

- **Licensing**: Gemma = Gemma Terms of Use (not Apache-2.0). Apps exposing the
  model to end users must bind them to the Prohibited Use Policy via ToS. Note
  it in KDoc.
- **Model source**: community HF mirrors work but are supply-chain risk; make
  the URL configurable and document the default source honestly.
- **APK size**: the native lib (`libllm_inference_engine_jni.so`) ships in the
  APK — verify it's packaged (`unzip -l app-x86_64-debug.apk | grep llm_inference`; debug APKs are per-ABI splits).
- **Storage honesty**: the file name on disk should not claim a specific model
  if the compact variant can replace it.
