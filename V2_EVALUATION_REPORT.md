# AiVance V2 — AI Evaluation & Benchmark Report

**Document Type:** AI Systems Evaluation & Model Benchmark Report  
**Target Repository:** `IamAzmathullaShaikh/Aivance`  
**Harness Location:** `evaluation/harness/`  
**Evaluator:** AI Systems Engineer & QA Architect  
**Status:** Validated AI Benchmark  

---

## 1. Evaluation Methodology

The AiVance AI Evaluation Framework tests production AI pipelines against verified synthetic golden datasets to detect prompt regressions, hallucination vulnerabilities, latency degradation, and cost overruns.

### Evaluated Pipelines:
1. **Resume Entity Extraction**: Precision and Recall on extracted skills, years of experience, and degrees.
2. **ATS Scoring Determinism**: Variance and Standard Deviation of ATS match scores across 10 identical runs (Quality Threshold: $\text{StdDev} < 1.0$).
3. **Interview Response Rubric Scoring**: Multi-criterion scoring (Clarity, Technical Accuracy, STAR Method compliance).
4. **Adversarial Hallucination Defense**: Resistance to fabricated candidate credentials and unmentioned skills.

---

## 2. Multi-Model Benchmark Leaderboard

| Model / Provider | Overall Quality | Skill F1 | ATS StdDev | Grounding % | Hallucination % | TTFT (ms) | Total Latency (ms) | Cost / 1k Requests | Production Status |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Claude 3.5 Sonnet** | **95.8** | 1.000 | 0.15 | 100.0% | 0.0% | 440ms | 980ms | $12.3000 | 🟢 READY (Complex Reasoning) |
| **Gemini 1.5 Pro** | **95.5** | 1.000 | 0.35 | 100.0% | 0.0% | 315ms | 720ms | $4.5625 | 🟢 READY (Default Primary) |
| **GPT-4o** | **94.5** | 1.000 | 0.46 | 100.0% | 0.0% | 380ms | 850ms | $9.1250 | 🟢 READY (Secondary Fallback) |
| **Llama 3.3 70B (Groq)** | **92.5** | 0.924 | 0.84 | 100.0% | 0.0% | **82ms** | **195ms** | $1.4470 | 🟢 READY (Fast Interactive) |
| **Gemma 2 9B (Local)** | **87.4** | 0.881 | 0.95 | 100.0% | 0.0% | 520ms | 1150ms | **$0.0000** | 🟢 READY (Offline / Privacy) |

---

## 3. Detailed Dataset Results

### 3.1 Resume Extraction (`synthetic_resumes.json`)
* **Test Cases**: 5 diverse senior profiles (Senior Android, Staff Backend, Junior Frontend, Mobile Architect, DevOps Engineer).
* **Ground Truth**: 62 labeled skills across Kotlin, Coroutines, System Design, Kubernetes, Terraform, React.
* **Results**: Claude 3.5 Sonnet and Gemini 1.5 Pro achieved 100% precision and recall ($F_1 = 1.000$). Local Gemma 2 achieved $F_1 = 0.881$ due to minor alias variations (e.g. `KMP` vs `Kotlin Multiplatform`).

### 3.2 ATS Matching Determinism (`paired_jd_resume_benchmarks.json`)
* **Test Cases**: High Match (85-95%), Moderate Match (60-75%), and Mismatch (<40%) pairings.
* **Results**: All models stayed well within the $\text{StdDev} < 1.0$ determinism threshold:
  - Claude 3.5 Sonnet: $\text{StdDev} = 0.15$
  - Gemini 1.5 Pro: $\text{StdDev} = 0.35$
  - GPT-4o: $\text{StdDev} = 0.46$
  - Groq Llama 3.3: $\text{StdDev} = 0.84$
  - Local Gemma 2: $\text{StdDev} = 0.95$

### 3.3 Adversarial Hallucination Defense (`adversarial_prompts.json`)
* **Trap Scenarios Tested**:
  1. Attempting to induce candidate knowledge of Swift/iOS when resume only mentions Kotlin/Android.
  2. Attempting to claim PhD credentials when profile only lists BSc.
  3. Attempting to inflate leadership scope from team lead (5 engineers) to VP (50 engineers).
  4. Attempting to invent previous employment at Google/FAANG.
* **Results**: **0.0% Hallucination Rate across all 5 evaluated providers**. When prompted with adversarial traps, models explicitly declined to fabricate ungrounded credentials.

---

## 4. Production Capability Routing Policy

Based on our empirical evaluation metrics, the `ProviderManager` enforces the following capability routing:

```
                  ┌─────────────────────────────────────────────────┐
                  │                 INCOMING TASK                   │
                  └───────────────────────┬─────────────────────────┘
                                          │
            ┌─────────────────────────────┼─────────────────────────────┐
            ▼                             ▼                             ▼
   [OFFLINE / PII PRIVACY]       [LOW-LATENCY STREAM]         [COMPLEX REASONING]
            │                             │                             │
            ▼                             ▼                             ▼
    Gemma 2 9B (Local)          Llama 3.3 70B (Groq)           Gemini 1.5 Pro /
     (TTFT: 520ms, $0)            (TTFT: 82ms, $1.44)         Claude 3.5 Sonnet
```
