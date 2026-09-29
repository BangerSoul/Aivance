# AiVance Career AI Evaluation Framework

The **AiVance AI Evaluation Framework** provides automated regression testing, hallucination detection, scoring determinism validation, and cost-latency profiling across all supported AI providers (**Gemini**, **Claude**, **OpenAI**, **Groq**, and **local Gemma**).

---

## 1. Core Objectives & Quality Gates

| Objective | Target Gate | Description |
| :--- | :---: | :--- |
| **Zero Hallucination** | **100% Grounding / 0% Hallucination** | Prohibits models from fabricating skills, credentials, past employers, or executive titles. |
| **ATS Score Determinism** | **StdDev < 1.0** | Ensures repeated ATS score evaluations on identical inputs remain stable and reproducible. |
| **Resume Entity Extraction** | **F1 Score > 0.90** | Accurately extracts technical skills, years of experience, and degrees without omitting or adding items. |
| **Interview Rubric Alignment** | **MAE < 0.5** | Aligns model evaluations with human expert rubrics for Clarity, Technical Accuracy, and STAR methodology. |
| **Conversational Latency** | **TTFT < 100ms (Voice)** | Monitors Time-To-First-Token to ensure responsive interactive coaching experiences. |

---

## 2. Directory Structure

```
evaluation/
├── README.md                                          # Framework specification, runner guide & results
├── benchmarks/
│   └── latest_results.md                             # Automatically generated benchmark report & leaderboard
├── datasets/
│   ├── resume_extraction/
│   │   └── synthetic_resumes.json                    # 5 synthetic candidate resumes with labeled ground truth
│   ├── ats_scoring/
│   │   └── paired_jd_resume_benchmarks.json          # 5 paired resume-job combinations with target match scores
│   ├── interview_evaluation/
│   │   └── interview_transcripts.json                # Candidate interview responses with rubric ratings (1-10)
│   └── hallucination/
│       └── adversarial_prompts.json                  # 5 adversarial prompts testing false claim resistance
└── harness/
    ├── metrics.py                                    # Pure Python calculators (F1, determinism, grounding, cost)
    ├── report_generator.py                           # Markdown table and report formatter
    └── eval_runner.py                                # CLI test harness supporting mock and live provider runs
```

---

## 3. Golden Benchmark Datasets

### A. Resume Extraction (`datasets/resume_extraction/synthetic_resumes.json`)
Contains 5 realistic synthetic candidate profiles spanning different engineering archetypes and seniority levels:
1. **Maya Lin (Senior Android Engineer, 7.5 yrs exp)**: Kotlin, Jetpack Compose, Coroutines, Flow, Dagger Hilt, Retrofit, Room, Clean Architecture, JUnit. (B.S. CS, Univ. of Washington 2017).
2. **Marcus Vance (Staff Backend Engineer, 12.0 yrs exp)**: Java, Kotlin, Spring Boot, Microservices, Distributed Systems, Kafka, PostgreSQL, Redis, Kubernetes, Docker, gRPC, AWS, System Design, Terraform. (M.S. CS Georgia Tech 2014, B.S. Purdue 2012).
3. **Chloe Chen (Junior Frontend Developer, 1.5 yrs exp)**: TypeScript, JavaScript, React, Next.js, HTML5, CSS3, Tailwind CSS, Redux Toolkit, REST APIs, Git, Jest, Responsive Design. (B.S. IT San Jose State Univ. 2023).
4. **Tariq Al-Mansoor (Mobile Architect, 14.0 yrs exp)**: Kotlin, Swift, Android NDK, iOS SDK, Cross-Platform Architecture, Modularization, Jetpack Compose, SwiftUI, Performance Profiling, Memory Optimization, App Security, CI/CD Automation, SDK Design. (M.S. CE Univ. of Michigan 2010, B.S. UT Austin 2008).
5. **Elena Rostova (DevOps / Platform Engineer, 6.0 yrs exp)**: Kubernetes, Docker, Terraform, AWS, Linux, Python, Bash, CI/CD, ArgoCD, Prometheus, Grafana, Helm, Ansible, GitOps. (B.S. CE CU Boulder 2018, CKA, AWS Solutions Architect).

### B. ATS Scoring & Determinism (`datasets/ats_scoring/paired_jd_resume_benchmarks.json`)
Contains 5 paired resume-job combinations with ground-truth matched keywords, missing keywords, and expected match percentage scores:
- **Case 1 (High Match - 92.0%, Range 85-95%)**: Maya Lin (Senior Android) vs. FinTech Senior Android Engineer.
- **Case 2 (High Match - 89.0%, Range 85-95%)**: Elena Rostova (Platform Engineer) vs. Senior Cloud Platform Engineer.
- **Case 3 (Moderate Match - 68.0%, Range 60-75%)**: Marcus Vance (Staff Backend) vs. Senior Go Distributed Systems Engineer.
- **Case 4 (Moderate Match - 64.0%, Range 60-75%)**: Chloe Chen (Junior Frontend) vs. Full-Stack Web Developer.
- **Case 5 (Mismatch - 18.0%, Range 0-35%)**: Chloe Chen (Junior Frontend) vs. Principal Embedded Firmware Engineer.

### C. Interview Evaluation Rubrics (`datasets/interview_evaluation/interview_transcripts.json`)
Contains behavioral and technical responses with ground truth rubric ratings (1-10) and expected constructive feedback:
- **Interview 01 (Strong STAR Behavioral)**: Black Friday production outage triage and long-term fix (Clarity: 9.5, Tech Accuracy: 9.0, STAR Score: 9.5).
- **Interview 02 (Mediocre Vague Behavioral)**: Conflict with PM over technical debt (Clarity: 5.0, Tech Accuracy: 5.5, STAR Score: 3.5).
- **Interview 03 (Technically Flawed Technical)**: RecyclerView optimization recommending AsyncTask in view inflation, 10,000-item caching, and disabling view recycling (Clarity: 7.0, Tech Accuracy: 2.0, STAR Score: 3.0).
- **Interview 04 (Strong STAR Technical)**: High-throughput Kafka event streaming architecture (Clarity: 9.0, Tech Accuracy: 9.5, STAR Score: 8.5).
- **Interview 05 (Mediocre Vague Technical)**: Coroutines vs Threads incorrectly recommending `GlobalScope` (Clarity: 6.0, Tech Accuracy: 5.0, STAR Score: 4.5).

### D. Adversarial Hallucination Defense (`datasets/hallucination/adversarial_prompts.json`)
Contains 5 adversarial prompts designed to bait models into ungrounded claims:
- **Case 01 (iOS / Swift Skill Trap)**: Prompt claims candidate Maya Lin is an expert iOS/Swift/SwiftUI developer.
- **Case 02 (Doctoral Credential Inflation)**: Prompt refers to Marcus Vance as "Dr. Vance" and requests his PhD dissertation topic and advisor.
- **Case 03 (Executive Title & Budget Inflation)**: Prompt claims Elena Rostova directed a 50+ person cloud org as VP with a $10M budget.
- **Case 04 (FAANG Employer Fabrication)**: Prompt attributes YouTube rendering optimizations at Google to Chloe Chen.
- **Case 05 (Experience Timeline Inflation)**: Prompt describes Chloe Chen (2023 graduate, 1.5 yrs exp) as having 10+ years of enterprise architecture experience since 2014.

---

## 4. Test Harness Tools (`evaluation/harness/`)

- **`metrics.py`**: Pure Python module with zero third-party dependencies computing:
  - Skill Precision, Recall, and F1 Score with alias normalization.
  - ATS Determinism across repeat runs (variance, sample standard deviation, pass/fail against threshold std_dev < 1.0).
  - Grounding Score (%) and Hallucination Rate (%) with negation and refutation parsing.
  - Mean Absolute Error (MAE) on multi-criterion interview rubrics.
  - Token consumption, TTFT, total latency, and cost estimation per 1k and 100k requests.
- **`eval_runner.py`**: CLI test runner orchestrating evaluations across providers with argument parsing.
- **`report_generator.py`**: Generates GitHub-flavored Markdown reports with comparison tables, KPIs, and routing recommendations.

---

## 5. Execution Instructions

### Running with Deterministic Mocks (No API Keys Required)
To validate the full pipeline deterministically in CI/CD or local testing:

```bash
python evaluation/harness/eval_runner.py --mock --dataset all
```

### Running Specific Subsets
Evaluate a single dataset:
```bash
python evaluation/harness/eval_runner.py --mock --dataset resume_extraction
python evaluation/harness/eval_runner.py --mock --dataset ats_scoring
python evaluation/harness/eval_runner.py --mock --dataset interview_evaluation
python evaluation/harness/eval_runner.py --mock --dataset hallucination
```

Evaluate specific providers:
```bash
python evaluation/harness/eval_runner.py --mock --providers gemini,claude
```

Custom repeat runs and output file:
```bash
python evaluation/harness/eval_runner.py --mock --runs 5 --output evaluation/benchmarks/custom_run.md
```

---

## 6. Latest Benchmark Results Summary

*Source: `evaluation/benchmarks/latest_results.md`*

### Model Leaderboard

| Rank | Provider / Model | Type | Overall Score | Skill F1 | ATS StdDev | Grounding % | Hallucination % | TTFT (ms) | Total Latency (ms) | Cost / 1k Reqs | Status |
| :---: | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| 1 | **Claude 3.5 Sonnet** | Cloud API | **95.8** | 1.000 | 0.15 | 100.0% | 0.0% | 440 | 980 | $12.3000 | 🟢 **READY** |
| 2 | **Gemini 1.5 Pro** | Cloud API | **95.5** | 1.000 | 0.35 | 100.0% | 0.0% | 315 | 720 | $4.5625 | 🟢 **READY** |
| 3 | **GPT-4o** | Cloud API | **94.5** | 1.000 | 0.46 | 100.0% | 0.0% | 380 | 850 | $9.1250 | 🟢 **READY** |
| 4 | **Llama 3.3 70B (Groq LPU)** | High-Speed LPU | **92.5** | 0.924 | 0.84 | 100.0% | 0.0% | 82 | 195 | $1.4470 | 🟢 **READY** |
| 5 | **Gemma 2 9B (Local / On-Device)** | Local On-Device | **87.4** | 0.881 | 0.95 | 100.0% | 0.0% | 520 | 1150 | $0.0000 | 🟢 **READY** |

---

## 7. Production Routing Recommendations

Based on the empirical benchmark findings, AiVance applies dynamic capability-based routing:

1. **ATS Keyword Matching & Scoring**: Route to **Claude 3.5 Sonnet** (fallback: **Gemini 1.5 Pro**). Delivers the lowest variance across repeated runs (StdDev: 0.15) and 100% range compliance.
2. **Interactive Voice & Mock Interview Coaching**: Route to **Groq Llama 3.3 70B** (fallback: **Gemini 1.5 Flash**). Delivers sub-100ms TTFT (82ms) essential for conversational audio without unnatural pauses.
3. **Deep Career Goal Planning & Resume Parsing**: Route to **Gemini 1.5 Pro** (fallback: **Claude 3.5 Sonnet**). Exceptional 100% grounding verification with cost efficiency ($4.56 / 1k requests).
4. **Privacy-Preserving Offline Mode**: Route to **local Gemma 2 9B**. Delivers 100% local processing with zero egress and $0 token cost.
