# On-Device Gemma SFT — Design for STAR Interview Coaching

**Status**: Draft · **Author**: AiVance engineering · **Date**: 2026-08-11
**References**: [`docs/LLM_TRAINING_NOTES.md`](./LLM_TRAINING_NOTES.md) (SFT loss-mask pattern), `GemmaOnDeviceProvider.kt`, `MediaPipeOnDeviceLlmEngine.kt`, `OkHttpModelFileDownloader.kt`, `GemmaModelDownloadWorker.kt`, `STARPrepGenerator.kt`, `InterviewQuestion.kt`.

---

## 1. Goal

Give the on-device Gemma model a lightweight, domain-specialized **STAR interview-coaching behavior**: given a behavioral/technical/leadership question and the user's target role, it should produce a structured **STAR-format answer** (Situation → Task → Action → Result) with the same shape the app's `STARPrepGenerator` already emits — so the Interview engine's prep packs and live mock sessions stay consistent offline.

The mechanism is **SFT with an assistant-only loss mask** (the pattern distilled in the training notes), applied as a **LoRA adapter** over the existing on-device model, downloaded like the base model file and loaded through MediaPipe's existing LoRA support.

## 2. Reality check — what "fine-tuning on-device" can and cannot mean here

- The `.task` files the app downloads (`gemma-3n-e2b-it-int4.task`, `artha_functiongemma_v9_0_0.task`) are **pre-converted MediaPipe inference bundles** (weights + SentencePiece tokenizer baked in). They are **inference-only**: no gradient training can run against them in-app. Any training happens **host-side**; the device consumes the result.
- MediaPipe LLM Inference supports **LoRA adapters** for Gemma on the **GPU backend**, attention layers only (`q_proj`, `k_proj`, `v_proj`, `o_proj`). The engine is configured with `LlmInferenceOptions.builder().setLoraPath(...)`.
- ⚠️ **The MediaPipe LLM Inference API is now in maintenance-only mode** (Google directs new work to LiteRT-LM). This design keeps the runtime behind the existing `OnDeviceLlmEngine` seam so a future LiteRT-LM migration (which has its own fine-tuning story) is contained; see §9 Risks.

**Consequence**: the deliverable is a **trained LoRA adapter shipped as a second model artifact**, not a modified base model. The app's model-management machinery (resumable download, foreground worker, provider status) is reused almost verbatim.

## 3. Target base model

| | Compact (preferred) | Primary |
|---|---|---|
| Artifact in use | `artha_functiongemma_v9_0_0.task` (FunctionGemma 270M int8, ≈271 MiB) | `gemma-3n-e2b-it-int4.task` (Gemma 3N E2B int4, ≈2.9 GiB) |
| Why | Matches constrained-device story; 10× smaller LoRA inference | Stronger base, but 10× the download + RAM |
| LoRA risk | **Unverified** — the community-converted bundle may or may not have been built with `supported_lora_ranks` | Also needs verification for the `.task` conversion |

**Gate G1 (before any training)**: confirm LoRA support of the exact `.task` files in use (loading with `setLoraPath` succeeds / conversion metadata includes LoRA ranks). If the compact bundle cannot carry LoRA, options in order: (a) re-convert a FunctionGemma 270M checkpoint with the MediaPipe converter including LoRA ranks; (b) fall back to the primary Gemma 3N E2B artifact. **Verdict (2026-08-11): G1 FAILS for the current compact artifact — see §3a.**

### 3a. Gate G1 verdict (2026-08-11) — the current artifacts cannot carry a MediaPipe LoRA

Executed the Phase-0 spike against primary sources (no training spend incurred):

| Evidence | Source | Implication |
|---|---|---|
| The compact bundle was converted via **`litert-torch v0.8+ → mediapipe bundler`** | Artha model card (HF) | This is the LiteRT-Torch route |
| Google's LLM Inference guide: LoRA is *"not compatible with models converted with the LiteRT Torch Generative API"* | Official docs | The Artha `.task` cannot load a LoRA, regardless of ranks |
| The official path for Gemma **3 270M** → MediaPipe is the LiteRT-Torch notebook (same route) | gemma-cookbook | Option (a)'s re-convert must also go through LiteRT-Torch → **also no LoRA**; the classic converter's Gemma support targets 2B/7B-class checkpoints |
| LoRA inference is **GPU-backend-only**, and the Artha card warns GPU has known issues with int8 models on some devices | Official docs + model card | Even a working LoRA would be brittle on the constrained devices the compact model exists for |

**Consequence — the design's premise narrows to one of three revised options:**

- **Option A — re-convert a different base for LoRA** (e.g. a Gemma-2-2B-class checkpoint via the classic converter, which *is* the supported LoRA path): viable but swaps the compact 270M for a multi-GB base — contradicts the constrained-device story. **Rejected for v1 of this feature.**
- **Option B — strategic: defer fine-tuning until the LiteRT-LM migration.** LiteRT-LM is the maintained runtime (the LLM Inference API is maintenance-only) and has its own fine-tuning story. The `OnDeviceLlmEngine` seam already contains the swap. **Right long-term home for SFT; not shippable now.**
- **Option C — no training, ship the coaching behavior now**: keep the deterministic `STARPrepGenerator` as the offline structured-answer path, strengthen the **prompt guidance** on every interview AI call, and gate quality with a **deterministic STAR rubric**. At 270M, prompting + rubric-gated evaluation captures most of the format-consistency win with **zero training infra, zero new artifacts, zero model churn**. §5's own expectation-setting supports this: small-model SFT buys behavior consistency, which a well-constructed prompt also provides — the SFT advantage only matters once the corpus is large and the behavior is *unpromptable*.

**Recommendation**: ship **Option C** as the immediate deliverable, and treat the SFT/LoRA design (§4–§9) as the **Option B payload** for the LiteRT-LM migration — keep this document as the training blueprint so the fine-tune is ready the day the runtime supports it. §10 is updated to reflect this ordering.

**Option C — implementation note (2026-08-11)**: implemented as **shared domain-side prompts + a deterministic scorer** rather than a change to `GemmaOnDeviceProvider.toPrompt()`. Rationale: the interview paths (`GenerateStarPackUseCase`, `InterviewRepositoryImpl.generateQuestions`/`evaluateAnswer`) reach the provider as raw prompt strings, so provider-side injection would be unreachable for them; domain-side injection (a) applies to the on-device Gemma *and* every cloud provider, (b) is JVM-unit-testable, and (c) keeps the provider generic. Delivered: `STARCoachingPrompts` (single source of truth for the four component labels, `idealAnswer` guidance, session-question prompt, evaluation prompt with explicit component grading) and `STARAnswerScorer` (deterministic 0–100 per-component rubric). `evaluateAnswer` now fills `starMethodScore` from the scorer when the AI omits it. 13 new/updated tests; full suite green.

## 4. Training data — what we have, what we build, what we exclude

### Corpus sources

1. **The deterministic STAR pool (primary, first release)** — `STARPrepGenerator` already emits role-interpolated questions with `expectedKeyPoints` and `idealAnswer`. It is project-authored content: clean, license-clean, PII-free, and perfectly aligned with the target behavior. A release ships with a few hundred curated (question → ideal STAR answer) pairs derived from this pool, **hand-audited and expanded by the team** (the generated `idealAnswer`s are coaching drafts, not polished training text).
2. **Curated expert pairs (secondary)** — team-written STAR answers for common roles (SWE, Android, data, PM…) across the three categories (BEHAVIORAL/TECHNICAL/LEADERSHIP). This is where quality lives; budget effort here.

### Explicit exclusions (privacy + quality)

- **Raw user interview answers / messages / evaluations are NOT training data.** They are PII (AES-GCM-encrypted at rest), would require consent, review, and are noisy. The training pipeline must consume a **statically bundled corpus asset**, never the live DB. This also keeps the corpus deterministic and versionable.
- **No external scraped data** — consistent with the project's no-AGPL/no-license-risk rule; the corpus is 100% project-owned text.

### Encoding — adapt the loss-mask pattern to *this* model

The notes' `encode_chat` → masked-NLL pattern transfers **verbatim in structure**, with two model-specific changes:

- **Chat markers**: Gemma's native format, not the tutorial's `<|user|>` text markers — the base model already speaks Gemma format and the LoRA must not fight it:
  ```
  <start_of_turn>user
  You are a STAR interview coach. Answer in STAR format: Situation, Task, Action, Result.
  Question: Tell me about a time you led a team through a conflict.<end_of_turn>
  <start_of_turn>model
  Situation: ... Task: ... Action: ... Result: ...<end_of_turn>
  ```
- **Tokenization**: the same SentencePiece tokenizer baked into the `.task` (Gemma tokenizer, `gemma_tokenizer.model`) — token-id alignment between training and inference is mandatory for SFT. (The notes' tiktoken `r50k_base` is replaced wholesale.)
- **Mask**: 1 on `model`-turn tokens + its terminating `<end_of_turn>`; 0 on prompt, role markers, and user content. Loss = masked cross-entropy **normalized by masked-token count** (packing-density-independent), exactly `sft_loss` from the notes.

### Dataset shape

Flat file per release, e.g. `star_sft_v1.jsonl`: `{ "role": "...", "content": "..." }` turns pre-rendered in Gemma format. Build a tiny generator (`tools/build_star_sft.py`) that renders the pool + curated pairs, applies the mask, packs to fixed length, and writes train/val splits (90/10, held-out **by question**, not by row, to avoid leakage of near-duplicate roles).

## 5. Training recipe (host-side)

Adapt `train_sft.py` from the notes' repo as the skeleton (pure-function losses + thin trainer). LoRA via PEFT, matching the MediaPipe constraint:

```python
from peft import LoraConfig
config = LoraConfig(
    r=8,                                   # rank 8 for a 270M base; 16 if data grows
    target_modules=["q_proj", "v_proj", "k_proj", "o_proj"],   # attention-only (MediaPipe)
    lora_alpha=16,
    lora_dropout=0.05,
)
```

- **Optimizer**: AdamW, `lr ≈ 2e-4` with cosine decay + 5% warmup (small model, small set → no bf16 needed on a host GPU; keep fp32).
- **Epochs**: 3–5 on the curated set; watch val masked-NLL; stop on val plateau (overfitting a 270M model on a few hundred pairs is fast).
- **Packing**: 512-token rows, EOT-separated (same `pack_examples` idea).
- **Smoke**: a CPU-runnable mini config (subset of pairs, rank 2, 2 steps) proves the pipeline before the real run — the repo's smoke-config discipline.
- **Eval**: held-out STAR questions, greedy decode, then score with the existing Interview evaluation rubric (see §8). Track `val_masked_nll` and rubric score per epoch.

**Expectation setting**: 270M SFT on a few hundred curated pairs buys **format/behavior consistency** (always-STAR structure, role-aware answers, correct tag shape), not new knowledge. That is precisely the offline-coaching win the product needs.

## 6. Conversion & distribution

- Train → `adapter_model.safetensors` (PEFT). Convert with the MediaPipe converter (GPU backend, the ONLY LoRA-supported backend):

```python
from mediapipe.tasks.python.genai import converter
config = converter.ConversionConfig(
    base_model=..., lora_ckpt="adapter_model.safetensors",
    lora_rank=8, lora_output_tflite_file="star_lora.tflite",
    backend="gpu", ...
)
converter.convert_checkpoint(config)
```

- **Self-host the artifact** (project rule: no third-party URLs in production paths — the existing model URLs are already flagged as community mirrors to replace). Expected size: a rank-8 LoRA over a 270M model is on the order of **1–5 MB** — trivial next to the 271 MiB base.
- **Versioned filename + verified byte size**: mirror the existing pattern (`DEFAULT_MODEL_SIZE_BYTES` verified via HTTP Range) — add `STAR_LORA_URL` / `STAR_LORA_SIZE_BYTES` constants verified the same way at release time.
- Gemma license compliance: fine-tuning + redistribution keeps the model under the Gemma Terms of Use; the app already binds users via ToS, and the LoRA artifact must carry the same attribution/prohibited-use notice.

## 7. On-device integration (reuses existing machinery)

### Engine (1 seam change)

`OnDeviceLlmEngine` gains an optional LoRA path; the real engine sets it plus the GPU backend:

```kotlin
// MediaPipeOnDeviceLlmEngine.create(context, modelPath, loraPath: String? = null)
val options = LlmInferenceOptions.builder()
    .setModelPath(modelPath)
    .setLoraPath(loraPath)              // null → base-only engine (today's behavior)
    .setPreferredBackend(LlmInference.Backend.GPU)   // LoRA is GPU-only
    .setMaxTokens(512)
    .build()
```

- `loraPath == null` keeps current behavior exactly (default backend, no GPU dependency).
- Engine is already an interface with a test fake — provider unit tests stay JVM-runnable.

### Provider (2nd artifact)

`GemmaOnDeviceProvider` gains a companion artifact alongside the base model:
- `loraFile = File(modelDir, "star_lora_v1.tflite")`, `isLoraReady`, plus `downloadLora(url, onProgress)` / `deleteLora()` mirroring `downloadModel`/`deleteModel` (same `loadMutex`, same `.part` resume via the existing `OkHttpModelDownloader`).
- Engine selection: when `isModelReady && isLoraReady` → create engine **with** lora path; else today's base-only engine. **Base behavior is untouched until the lora lands** — the download is opportunistic, never blocking.
- Status surface: keep `ProviderStatus` semantics for the *base* model (so `ProviderManager` selection logic is untouched); expose lora readiness as a separate, additive state the Provider Management UI can render ("STAR coach active").

### Distribution (reuse the worker)

Extend `GemmaModelDownloadWorker` with a `KEY_ARTIFACT = "base" | "lora"` input rather than a new worker class: same foreground notification, resumable `.part`, retry/backoff, `ExistingWorkPolicy` uniqueness per artifact. Trigger it on the same Provider Management "Download" surface (a second, small row for the STAR coach).

### Routing (which prompts use the lora)

The lora is **domain-specific — do not route general chat through it**. In the assistant orchestration (`GetAssistantResponseUseCase` / interview call sites):
- **Interview engine prompts** (STAR pack generation, mock-session evaluation prompts that ask for STAR structure) → lora engine when ready, else base engine.
- Everything else → base engine.
- Track events: `star_lora_active`, `star_lora_fallback`, `star_lora_download_start/success/failed` (existing analytics pattern).
- Developer/experiment toggle in Provider Management: `use_star_coach` (default on once downloaded) for A/B.

### Fallback matrix

| Condition | Behavior |
|---|---|
| Lora not downloaded / deleted / corrupt | Base engine — identical to today |
| GPU backend unavailable (low-end device) | Catch engine-create failure → base engine (DEFAULT backend) |
| Lora file fails `loadEngine` | Delete artifact, surface snackbar, base engine (mirrors existing corrupt-model handling) |
| New lora version shipped | Versioned filename → old file GC'd; a failed new version never regresses the working one until it loads |

## 8. Verification & guardrails

1. **Host-side**: held-out STAR eval — greedy decode on val questions, parse the response for the four STAR markers, score with the Interview engine's existing evaluation rubric; require **val rubric score ≥ baseline** (no-lora base model on the same set) before shipping an adapter.
2. **On-device smoke**: a PromptLab-style check in Provider Management ("Test STAR coach") that generates one answer for a fixed question and asserts the STAR structure — instrumented test + manual.
3. **Unit tests**: provider lora path selection (base/lora/both/missing), worker artifact routing, engine factory fallback — all JVM with the existing `OnDeviceLlmEngine` fake.
4. **Integrity**: verified byte-size constants (Range-checked at release), `.part` resume, corrupt-file deletion — the exact invariants `OkHttpModelFileDownloader` already guarantees.
5. **Privacy**: corpus is a bundled static asset (project-authored); zero reading from the encrypted DB; nothing user-generated enters the training set.
6. **Rollback**: delete lora artifact → byte-identical base behavior; ship a bad adapter → gate G4 (rubric regression) blocks release, and a previous version remains selected.

## 9. Risks & open questions

- **MediaPipe LLM Inference is maintenance-only** → LiteRT-LM is the strategic runtime. Mitigation: everything above sits behind `OnDeviceLlmEngine`; LiteRT-LM has its own fine-tuning path that should be re-evaluated before v2 work. **Open question**: does LiteRT-LM's serving support the same LoRA adapter format?
- **Community `.task` LoRA compatibility unverified** → gate G1 before training spend; re-convert route planned.
- **LoRA is GPU-backend-only** → low-end devices run base-only; acceptable (lora is an enhancement, base already works).
- **Data volume vs. expectation** — a few hundred curated pairs is the *right* first release (behavior consistency), not a claims-making dataset. Growing the corpus means team-authored expansion, which is effort-bounded, not magic.
- **Gemma license** for the fine-tuned derivative — reuse the existing ToS binding; keep attribution in the artifact metadata.

## 10. Phased plan

| Phase | Work | Effort |
|---|---|---|
| **C-0** | **Option C (ship now)**: STAR system prompt in `toPrompt()` + interview-prompt routing + rubric-gated eval (held-out STAR questions) + A/B tracking | S |
| **C-1** | On-device smoke on the `aivance` AVD; evaluate vs. no-prompt baseline; promote or iterate | S |
| **B-0** | LiteRT-LM migration spike (runtime swap behind `OnDeviceLlmEngine`, model re-bundle) | M |
| **B-1** | On LiteRT-LM: re-run Gate G1 against its fine-tuning/LoRA path; `tools/build_star_sft.py` + curated v1 corpus | M |
| **B-2** | Host training → eval gate (val rubric ≥ baseline) → converter → self-host artifact + verified size | M |
| **B-3** | Engine lora seam + provider 2nd artifact + worker `KEY_ARTIFACT` + Provider Management surface + tests | M |
| **B-4** | Routing, fallback matrix, tracking, on-device smoke, docs | S |

**Now**: Phase C-0/C-1 only — a small, fully JVM-verifiable increment plus an AVD smoke. **Later**: B-0 → B-4 as the LiteRT-LM migration lands, carrying this design's SFT/LoRA machinery.

---

*Companion technique reference: `docs/LLM_TRAINING_NOTES.md` §4 (masked SFT loss), §6 (sequence packing), §10 (RLVR eval discipline), §12 (application mapping).*
