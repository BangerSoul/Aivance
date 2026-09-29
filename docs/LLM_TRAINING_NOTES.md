# LLM Training From Scratch — Internalized Notes

**Source studied**: [`FareedKhan-dev/train-llm-from-scratch`](https://github.com/FareedKhan-dev/train-llm-from-scratch) (MIT).
**Date**: 2026-08-11 · **Purpose**: distilled knowledge artifact from a deep read of the model, losses, and trainers — the transferable engineering decisions, not a re-typed tutorial.

---

## 1. The whole pipeline in one line

```
raw text -> tokens -> decoder-only Transformer -> next-token loss -> base model
base -> SFT (masked NLL) -> Reward Model (Bradley-Terry) -> {PPO, DPO/ORPO/KTO} -> GRPO -> greedy GSM8K eval
```

The unifying idea: **the model never changes; only the data and the loss change per stage.** Post-training is composed around one addition to the backbone — `forward_hidden()` (hidden states after the final LayerNorm, right before `lm_head`) — and auxiliary heads (reward head, value head) and all log-probability math plug into that single seam.

## 2. Model architecture decisions (and why)

- **Decoder-only, pre-norm residual blocks**: `x = x + attn(ln1(x))`, `x = x + mlp(ln2(x))`. Pre-norm (norm first) is what makes deep stacks trainable; the residual path lets gradients flow. Post-norm was the 2017 paper; modern practice is pre-norm.
- **Learned absolute position embeddings** (`nn.Embedding(context_length, n_embed)`) — simpler than RoPE and fine for a teaching model; it caps sequences at `context_length`, so all generation/rollout code must enforce `prompt_len + max_new_tokens <= context_length`.
- **`.reshape` not `.view` on target slices**: targets come from a non-contiguous slice of the batch tensor; `.view` raises on CPU while `.reshape` handles both. (The `.to('cuda')` copy happens to make it contiguous — a classic CPU-vs-GPU-only bug.)
- **`forward_embedding` returns `(out, residual)`** — keeps the residual stream available for the embedding-level reward model variant.
- **Gradient checkpointing is opt-in off-by-default** (`use_reentrant=False`), so numerics are byte-identical unless explicitly enabled to trade compute for VRAM.
- **Memory flags with no default behavior change**: `--amp`, `--grad-checkpointing`, `--grad-accum` are all opt-in in the pretraining script.

## 3. Data pipeline — the details that matter

- **Tokenizer**: OpenAI `tiktoken` `r50k_base` (GPT-3's tokenizer). Every document gets a trailing `<|endoftext|>` (id 50256) so the model learns stop boundaries.
- **No new special tokens**: `r50k_base` has only ONE special token, so chat roles are **plain-text markers** (`<|user|>\n`, `<|assistant|>\n`) that tokenize as ordinary multi-token strings and are simply *learned* during SFT. This is a genuinely important constraint-driven design choice: don't burn vocabulary on special tokens you can learn as text.
- **The loss mask is the whole game in SFT**: `encode_chat` returns `(ids, mask)` where mask=1 ONLY on assistant content + its terminating EOT; 0 on role markers and user content. SFT then trains the model to *write answers*, never to parrot the prompt.
- **Sequence packing**: examples (already EOT-terminated) are concatenated and sliced into fixed `context_length` rows; EOT acts as the separator. Trailing partial rows dropped.
- **Reasoning structure is plain text too**: `<think>...</think><answer>42</answer>` — no special handling anywhere; the reward parser reads the number out of the tags.
- **Defensive decode**: the model vocab is padded to 50304 but `r50k_base` only decodes 0..50255, so an undertrained model emitting padding ids would crash decoding — `decode()` filters ids `>= EOT_ID`.

## 4. SFT loss

```
logits = logits[:, :-1, :]      # predict token t+1 from t (same shift as pretraining)
targets = tokens[:, 1:]
mask = loss_mask[:, 1:]
ce = cross_entropy(logits.reshape(-1, V).float(), targets.reshape(-1), reduction="none")
loss = (ce.view(targets.shape) * mask).sum() / mask.sum().clamp(min=1)
```

Key detail: loss is **normalized by the number of masked (assistant) tokens**, not total tokens — so packing density doesn't distort the learning signal.

## 5. Reward model

- A `nn.Linear(n_embed, 1, bias=False)` head on `forward_hidden`, **zero-initialized** so training starts from near-zero rewards.
- Sequence reward = head output at the **last real token** (InstructGPT convention), with `gather_last` when rows are padded.
- Trained with the **Bradley-Terry** pairwise loss: `-log_sigmoid(chosen - rejected)`.
- Headline metric: **preference accuracy** (fraction of held-out pairs where chosen scores higher); 0.574 on ~8k real pairs beats the 0.5 chance line.

## 6. DPO / ORPO / KTO (preference alignment without an RL loop)

- All operate on **sequence-level summed log-probs** of chosen/rejected responses.
- **DPO**: `logits = (pi_chosen - pi_rejected) - (ref_chosen - ref_rejected)`, `loss = -log_sigmoid(beta * logits)`. Implicit rewards `beta * (pi_logp - ref_logp)` are *detached diagnostics*. Needs a frozen reference copy of the SFT policy.
- **ORPO** (reference-free): folds SFT + alignment into one stage — `NLL(chosen) + lambda * -log_sigmoid(log_odds_chosen - log_odds_rejected)` where `log_odds = mean_logp - log(1 - exp(mean_logp))`. Uses per-token *mean* log-probs (length-normalized) and a numerically stable `_log1mexp` for `log(1-exp(x))`.
- **KTO** (unpaired signal): KL baseline estimated from the batch's mean log-ratio, **detached**, with separate desirable/undesirable weights.
- **Accuracy metric**: fraction of pairs where implicit reward prefers chosen.

## 7. PPO (classic RLHF, from scratch)

- **Rollout → score → GAE → clipped epochs**, all in an "action frame" of length `L = T-1` where action `t` produces token `t+1`.
- **GAE**: `delta = r_t + gamma * V(s_{t+1}) * nonterminal - V(s_t)`, `A_t = delta + gamma*lam*nonterminal*A_{t+1}` — with a crucial detail: **the episode is terminal after the last response token (no bootstrap past it)**. Rewards = per-token KL penalty + task reward at the final response token.
- **Advantage whitening** (zero mean/unit std over response positions only) — a stable-PPO staple.
- **Clipped surrogate**: `min(ratio*adv, clip(ratio, 1±0.2)*adv)`, plus a clipped value loss `0.5 * max((v-returns)², (v_clip-returns)²)` — the clip prevents both the policy and the value function from taking large steps.
- **`approx_kl = mean(old_logp - new_logp)`** over response tokens as the drift health metric.

## 8. GRPO (DeepSeek-R1 style, no critic)

- For each prompt, sample a **group of G completions**, score with a verifiable reward, and use the **group's own mean/std as the baseline**: `adv = (r - group_mean) / (group_std + eps)` — the baseline is the group, not a learned value network.
- **KL penalty uses Schulman's k3 estimator**: `exp(ref_logp - new_logp) - (ref_logp - new_logp) - 1` — unbiased, non-negative, per-token. (k3 ≈ log(2)-corrected surrogate; the repo's comment calls it "unbiased, non-negative" — the non-negativity comes from `e^x ≥ x+1`.)
- Token-level clipped surrogate + `kl_coef * k3_kl` per token, minibatching **by group** (a minibatch is ~one group's worth of rows).
- **Warm-up curriculum**: an arithmetic phase runs first so the policy gets non-zero reward variance before facing full GSM8K — otherwise the group advantages are all ~0 and there's no learning signal.
- **"Informative groups" metric**: fraction of groups with non-zero reward std — a direct read on whether the curriculum is producing learning signal.

## 9. Rollout & log-prob math (shared RL core)

- **Free functions, not bound methods** (`compute_logprobs`, `sequence_logprobs`): the exact same math must run against the trainable policy, the frozen reference, and the old-policy snapshot — `f(model, ...)` composes; a bound method doesn't.
- **Log-probs always fp32** (`logits.float()`) even under bf16 autocast, because DPO/PPO/GRPO *subtract* log-probs and bf16 rounding there is harmful.
- **Sampling vs. recording are two different distributions**: the sampled token comes from the temperature/top-k/top-p *filtered* distribution, but the recorded log-prob is the **full-distribution log-prob at the sampling temperature** — so the importance ratio stays exact.
- **No padding-aware attention mask** → length-bucket prompts to equal lengths, pad only after stop tokens, and use a `response_mask` to zero out padded positions.
- **No KV cache** (kept for clarity); rows stop individually at EOT, and a row is finished *after* (and including) the stop-token step.

## 10. Verifiable rewards (RLVR) — anti-reward-hacking design

- **Correctness-dominant, small bounded format bonus**: `+1.0` if parsed answer matches gold (float tolerance), `+0.2` if exactly one well-formed `<answer>` block, total clipped to `1.2`. The docstring says it explicitly: a tiny model will happily emit empty answer tags or repeat tokens if the format bonus is too large.
- One eval axis across ALL stages: **greedy GSM8K accuracy** — parse the number in `<answer>` tags, compare to gold. Same decoding, same reward, comparable numbers.

## 11. Engineering hygiene worth copying

- **Two config systems, clearly separated**: legacy plain-constants `config/config.py` vs. dataclass + JSON for the modern stages, with a merge chain `defaults < base.json < stage.json < CLI` (`--lr 2e-5` overrides anything).
- **Smoke configs for every stage** (`configs/smoke/*.json`) that shrink the model so a full training run finishes in *seconds on CPU* — plus a CPU smoke test file. This is the ML equivalent of the JVM-verifiable unit tests this project already prizes.
- **Deterministic seeds per rank** (`set_seed(cfg.seed + rank)`), checkpoint resume (`--resume latest`), and per-stage checkpointing with metrics attached.
- **Every loss is a pure function** of tensors (`sft_loss`, `dpo_loss`, `grpo_loss`, `compute_gae`, ...) with the trainer script only doing orchestration — the same "pure core + thin shell" structure this project uses in `core:domain` use cases vs. ViewModels.

---

## 12. How this applies to AiVance

The project already ships **on-device Gemma** (compact 270M variant, resumable downloads). The transferable path:

- **If we ever fine-tune the on-device model**, the smallest useful stage is **SFT with a loss mask** (section 4/6): the interview STAR packs and assistant responses are already structured chat data — encoding them with an assistant-only mask is the direct analog of the repo's `encode_chat` → `sft_loss`.
- **RLVR on a small model** (sections 8/10) is the realistic next stage for the Interview engine: the repo's `reward_gsm8k` design (correctness-dominant + tiny format bonus, clipped) maps directly to scoring interview answers — parse a rubric metric, keep the format bonus small to avoid gaming.
- **k3 KL + group-relative advantages** are the exact machinery needed to keep a 270M model from drifting off its base distribution during alignment — and they're each ~10 lines of pure PyTorch.
- The **warm-up curriculum** lesson applies anywhere sparse rewards are expected (e.g., coach a model on easy behavioral questions before hard technical ones).
- The repo's discipline of **pure-function losses + smoke configs + one eval axis** matches the project's own testing philosophy — if ML training code ever lands in this repo, it should follow the same shape.

---

*Companion source: `/tmp/train-llm-from-scratch` (shallow clone, kept on disk for reference).*
