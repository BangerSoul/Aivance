# AiVance CLI Command Contract Specification

**Target Binary:** `aivance`  
**Standard Version:** 2.0.0  
**Status:** Canonical Standard Definition  

---

## 1. Global Flags & Architecture

All commands accept the following global flags:

```bash
aivance [command] [subcommand] [flags]
  --format [text|json|yaml]     Output serialization format (default: text)
  --offline                     Force local execution; suppress network calls
  --provider [providerId]       Explicitly select inference or search provider
  --profile [profilePath]       Custom path to local encrypted career database
  --verbose                     Print internal Career Operation trace IDs & spans
  --quiet                       Suppress banner and non-essential progress output
```

Exit Codes:
* `0`: Success
* `1`: General execution failure / unexpected error
* `2`: Invalid argument or schema validation failure
* `3`: AI evaluation quality regression detected
* `4`: Operation rejected by Human Approval Gate / Safety Policy
* `5`: Network required but client in offline mode

---

## 2. Command Specifications

### 2.1 Career Knowledge Graph
```bash
# Inspect high-level Career Graph metrics, node counts, and lifecycle stage
aivance career inspect [--nodes] [--edges] [--gaps]

# Run skill gap analysis against target jobs
aivance career gaps [--role <target_role>]
```

### 2.2 Resume Intelligence
```bash
# Parse and analyze local resume file
aivance resume analyze --file <path_to_pdf_or_docx> [--job <job_url_or_id>]

# Export canonical structured resume JSON
aivance resume export [--version <version_id>] --output <path_to_json>
```

### 2.3 ATS Optimization
```bash
# Calculate keyword match percentage and missing skills
aivance ats score --resume <resume_id_or_file> --job <job_id_or_file>

# Generate optimization tips
aivance ats optimize --resume <resume_id> --job <job_id>
```

### 2.4 Job Discovery
```bash
# Search jobs across connected providers
aivance jobs search --query <keywords> [--location <city>] [--remote] [--min-salary <num>]

# Save discovered job to pipeline
aivance jobs save --id <job_id>
```

### 2.5 Application Pipeline
```bash
# List tracked applications by stage
aivance application list [--stage <stage_name>] [--active]

# Advance application stage
aivance application move --id <application_id> --to <SAVED|APPLIED|SCREENING|INTERVIEW|OFFER>
```

### 2.6 Interview Coach
```bash
# Run interactive mock interview session in terminal
aivance interview practice --role <target_role> [--type <behavioral|system_design|coding>]

# Evaluate past interview transcript
aivance interview evaluate --file <transcript_txt>
```

### 2.7 Autonomous Agent Runtime
```bash
# Formulate autonomous multi-step plan for a career objective
aivance agent plan --goal "Secure Staff Android role within 60 days"

# Advance current active plan (prompts human confirmation on HIGH-risk actions)
aivance agent advance [--auto-approve-low-risk]
```

### 2.8 AI Evaluation Testbed
```bash
# Execute benchmark evaluation suite against providers
aivance evaluation run [--dataset all|resume|ats|interview|hallucination] [--providers gemini,claude,groq,gemma]
```
