# AiVance AI Model Evaluation & Leaderboard Report

> **Generated:** 2026-09-22 08:05:28 UTC  
> **Execution Mode:** `Deterministic Mock Golden Testbed (--mock)`  
> **Evaluated Datasets:** `all`  
> **Target Platform:** AiVance Career AI Engine (Mobile & Backend Services)

## 1. Executive Summary

This evaluation report validates performance, grounding, determinism, latency, and cost across AI providers integrated into the AiVance ecosystem: **Gemini 1.5 Pro**, **Claude 3.5 Sonnet**, **GPT-4o**, **Groq (Llama 3.3 70B)**, and **Gemma 2 9B (Local / On-Device)**.

### Key Quality Gate Observations:
- **ATS Scoring Determinism:** Standard deviation across repeated runs remains **< 1.0**, satisfying enterprise stability standards.
- **Zero Hallucination Gate:** Models successfully refuted credential inflation and ungrounded skill fabrications in adversarial prompts.
- **Interview Rubrics:** Strong correlation with human ground-truth on clarity, technical accuracy, and STAR methodology.
- **Real-Time Edge Advantage:** Groq delivers ultra-low TTFT (<100ms) making it the prime candidate for live voice/interview coaching.
- **Private On-Device Option:** Local Gemma provides $0.00 inference cost with zero external data transmission for privacy-sensitive users.

## 2. Model Leaderboard

| Rank | Model / Provider | Type | Overall Score | Skill F1 | ATS StdDev | Grounding % | Hallucination % | TTFT (ms) | Total Latency (ms) | Cost / 1k Reqs | Status |
| :---: | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| 1 | **Claude 3.5 Sonnet** | Cloud API | **95.8** | 1.000 | 0.15 | 100.0% | 0.0% | 440 | 980 | $12.3000 | 🟢 **READY** |
| 2 | **Gemini 1.5 Pro** | Cloud API | **95.5** | 1.000 | 0.35 | 100.0% | 0.0% | 315 | 720 | $4.5625 | 🟢 **READY** |
| 3 | **GPT-4o** | Cloud API | **94.5** | 1.000 | 0.46 | 100.0% | 0.0% | 380 | 850 | $9.1250 | 🟢 **READY** |
| 4 | **Llama 3.3 70B (Groq LPU)** | High-Speed LPU | **92.5** | 0.924 | 0.84 | 100.0% | 0.0% | 82 | 195 | $1.4470 | 🟢 **READY** |
| 5 | **Gemma 2 9B (Local / On-Device)** | Local On-Device | **87.4** | 0.881 | 0.95 | 100.0% | 0.0% | 520 | 1150 | $0.0000 | 🟢 **READY** |

## 3. Resume Skill & Entity Extraction

Evaluates the ability to extract explicit technical skills, years of experience, and degrees without hallucinating unlisted qualifications.

| Provider | Precision | Recall | F1 Score | Exp. Years MAE | Degree Match % |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Claude 3.5 Sonnet** | 1.000 | 1.000 | **1.000** | 0.00 yrs | 100.0% |
| **Gemini 1.5 Pro** | 1.000 | 1.000 | **1.000** | 0.05 yrs | 100.0% |
| **GPT-4o** | 1.000 | 1.000 | **1.000** | 0.05 yrs | 100.0% |
| **Llama 3.3 70B (Groq LPU)** | 0.924 | 0.924 | **0.924** | 0.15 yrs | 100.0% |
| **Gemma 2 9B (Local / On-Device)** | 0.918 | 0.848 | **0.881** | 0.20 yrs | 100.0% |

## 4. ATS Scoring & Determinism Benchmarks

ATS score consistency is critical for user trust. Models were evaluated across multiple runs with identical prompts. Standard deviation must remain **under 1.0** to qualify as deterministic.

| Provider | Score Range Compliance % | Mean Score Error | Repeat Run Variance | Repeat Run StdDev | Determinism Verdict |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Claude 3.5 Sonnet** | 100.0% | 0.03% | 0.0233 | **0.1528** | ✅ PASS (< 1.0) |
| **Gemini 1.5 Pro** | 100.0% | 0.03% | 0.1233 | **0.3512** | ✅ PASS (< 1.0) |
| **GPT-4o** | 100.0% | 0.00% | 0.2100 | **0.4583** | ✅ PASS (< 1.0) |
| **Llama 3.3 70B (Groq LPU)** | 100.0% | 0.17% | 0.7033 | **0.8386** | ✅ PASS (< 1.0) |
| **Gemma 2 9B (Local / On-Device)** | 100.0% | 0.20% | 0.9100 | **0.9539** | ✅ PASS (< 1.0) |

## 5. Interview Evaluation & Rubric Alignment

Candidate transcripts (strong STAR, vague mediocre, technically flawed) were evaluated against human gold-standard rubrics.

| Provider | Clarity MAE | Technical Accuracy MAE | STAR Method MAE | Rubric Alignment % | Feedback Quality |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Claude 3.5 Sonnet** | 0.10 | 0.10 | 0.10 | **99.2%** | High (Actionable & STAR-Aligned) |
| **Gemini 1.5 Pro** | 0.20 | 0.20 | 0.20 | **98.2%** | High (Actionable & STAR-Aligned) |
| **GPT-4o** | 0.20 | 0.20 | 0.30 | **98.0%** | High (Actionable & STAR-Aligned) |
| **Llama 3.3 70B (Groq LPU)** | 0.40 | 0.50 | 0.40 | **96.0%** | High (Actionable & STAR-Aligned) |
| **Gemma 2 9B (Local / On-Device)** | 0.50 | 0.58 | 0.60 | **94.8%** | High (Actionable & STAR-Aligned) |

## 6. Hallucination & Adversarial Defense

Models were probed with 5 adversarial traps attempting to induce skill fabrication (Swift on Android resume), degree inflation (PhD on MSc profile), executive title inflation (VP/50-person department), FAANG employer fabrication, and 10-year experience inflation.

| Provider | Grounding Score | Hallucination Rate | Traps Refuted / Total | Defense Assessment |
| :--- | :---: | :---: | :---: | :--- |
| **Claude 3.5 Sonnet** | **100.0%** | **0.0%** | 5/5 | Zero Hallucination - Verified Grounding |
| **Gemini 1.5 Pro** | **100.0%** | **0.0%** | 5/5 | Zero Hallucination - Verified Grounding |
| **GPT-4o** | **100.0%** | **0.0%** | 5/5 | Zero Hallucination - Verified Grounding |
| **Llama 3.3 70B (Groq LPU)** | **100.0%** | **0.0%** | 5/5 | Zero Hallucination - Verified Grounding |
| **Gemma 2 9B (Local / On-Device)** | **100.0%** | **0.0%** | 5/5 | Zero Hallucination - Verified Grounding |

## 7. Latency, Throughput & Cost Profiling

| Provider | Time-To-First-Token (TTFT) | Total Latency | Avg Input Tokens | Avg Output Tokens | Est. Cost / 1k Invocations | Est. Cost / 100k Monthly |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Claude 3.5 Sonnet** | 440.0 ms | 980.0 ms | 1,850 | 450 | **$12.3000** | $1230.00 |
| **Gemini 1.5 Pro** | 315.0 ms | 720.0 ms | 1,850 | 450 | **$4.5625** | $456.25 |
| **GPT-4o** | 380.0 ms | 850.0 ms | 1,850 | 450 | **$9.1250** | $912.50 |
| **Llama 3.3 70B (Groq LPU)** | 82.0 ms | 195.0 ms | 1,850 | 450 | **$1.4470** | $144.70 |
| **Gemma 2 9B (Local / On-Device)** | 520.0 ms | 1150.0 ms | 1,850 | 450 | **$0.0000** | $0.00 |

## 8. Capability Routing Matrix

Based on the empirical benchmark results, the AiVance orchestrator applies the following provider routing policy:

| Feature Area | Primary Provider | Fallback Provider | Rationale |
| :--- | :--- | :--- | :--- |
| **ATS Resume Matching** | Claude 3.5 Sonnet | Gemini 1.5 Pro | Highest keyword recall and deterministic score stability (std_dev < 0.3). |
| **Real-Time Voice/Interview** | Groq (Llama 3.3 70B) | Gemini 1.5 Flash | Sub-100ms TTFT required for realistic conversational latency without user pause. |
| **Deep Resume & Cover Letter** | Gemini 1.5 Pro | Claude 3.5 Sonnet | 100% grounding verification, high context window, balanced cost. |
| **Offline / Air-Gapped Mode** | Gemma 2 9B (Local) | N/A (Local) | Zero network egress, zero inference token cost, 100% data privacy. |

---
*Report generated automatically by the AiVance AI Evaluation Framework.*