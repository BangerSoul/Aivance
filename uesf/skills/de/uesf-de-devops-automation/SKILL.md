---
id: uesf-de-devops-automation
name: DevOps Automation
version: 1.0.0
category: de
kind: engineering
purpose: Build and verify CI/CD pipelines and infrastructure automation that are reproducible, observable, and safe to run.
description: |
  Use when setting up or modifying CI/CD, infrastructure as code, scripts, or deployment
  automation. Produces pipelines and automation verified in a safe environment, with
  observability and rollback. Generalizes across GitHub Actions, GitLab CI, Jenkins,
  Terraform, and scripting.
triggers:
  - condition: "CI/CD, infra-as-code, or deployment automation is being created or changed"
  - condition: "A manual process should be automated with verification"
  - example_prompt: "Add a CI job that runs the security scan and publishes the artifact"
inputs:
  - "The workflow/process to automate and its environment"
  - "Existing CI/infra conventions and tooling"
outputs:
  - "CI/CD pipeline or automation change"
  - "Verification evidence (pipeline ran green in a safe environment)"
  - "Observability (logs, status, alerting) for the automation"
dependencies:
  - "uesf-co-testing"
  - "uesf-se-security-audit"
context_requirements:
  - "Access to the CI environment (or a local runner) and secrets policy"
  - "Ability to run the pipeline in a non-production environment"
quality_gates:
  - "Pipeline runs green in a safe environment before touching production"
  - "Secrets never appear in code, logs, or pipeline output"
  - "Every step has a failure path (fail fast, retry policy, notifications)"
validation:
  - unit
  - integration
  - security
  - regression
rollback: "Automation is versioned code: revert the pipeline/config commit. Infra changes are planned with destroy/rollback steps where applicable."
failure_recovery: "A broken pipeline is a production incident for the team: fix forward on the mainline, root-cause with uesf-co-debugging, and add a regression test for the pipeline itself."
acceptance_criteria:
  - "Pipeline green in a safe environment with recorded logs"
  - "No secrets in code, logs, or artifacts (scanned)"
  - "Failure behavior defined for every step"
  - "Automation reduces a measured manual step or risk"
automation_hooks:
  - "CI status checks blocking merges"
  - "Pipeline self-test job (the pipeline tests the pipeline)"
mcp_tools:
  - "none"
cost:
  input_tokens: "~12k"
  output_tokens: "~5k"
  runtime_minutes: "30–120"
complexity: 4
maintainability_score: 4
scalability_score: 5
production_readiness: 4
related_skills:
  - "uesf-re-release-engineering"
documentation: "docs/skill-spec.md"
---

# DevOps Automation

## Overview
Automation is code, with all of code's obligations: tests, review, versioning, and
rollback. This skill treats pipelines and infra automation as first-class artifacts —
verified in safe environments, secret-safe, observable, and failure-defined. It
synthesizes the CI/automation discipline from the source ecosystem (the Aivance CI
lineage, google/skills' GKE platform skills, OpenClaw's automation category) into a
provider-agnostic process.

## Execution Workflow
1. **Define the outcome** — What manual step or risk does this automation remove, and
   how is that measured? Automation with no measurable outcome is rejected.
2. **Design the pipeline** — Map stages, inputs, artifacts, and environments. Every
   stage has: a failure path (fail fast vs. continue), a retry policy, and a
   notification/alert.
3. **Secrets policy** — Secrets come from the secret store, never literals. The pipeline
   must be scan-clean: no secrets in code, logs, or artifacts.
4. **Build in a safe environment** — Run the pipeline in CI-on-branch, a sandbox, or a
   staging env before anything near production. Record logs as evidence.
5. **Add observability** — Status reporting, artifact links, and alerting for failures;
   the pipeline's health is visible to the team.
6. **Self-test** — Add a pipeline self-test (a job that exercises the pipeline) so
   automation regressions are caught like code regressions.
7. **Review and ship** — Pass the pipeline change through `uesf-co-review`; ship as
   versioned code with a rollback path.

## Quality Gates
- Pipeline green in a safe environment with recorded logs.
- Secret scan clean; secrets only from the store.
- Every step has a defined failure path.
- The automation's outcome is measurable.

## Validation
- **Unit**: pipeline YAML/config linted; script steps unit-tested where feasible.
- **Integration**: full pipeline run in the safe environment.
- **Security**: secrets and dependency scans on the pipeline itself.
- **Regression**: pipeline self-test and re-run of prior jobs.

## Rollback
Automation is versioned code — revert the commit to restore the previous pipeline. For
infra automation, plan changes with reversible steps (state rollback, destroy where
appropriate) and never auto-apply destructive changes without a gate.

## Failure Recovery
A broken pipeline is an incident for the team it serves. Fix forward on the mainline,
root-cause the failure with `uesf-co-debugging`, and add a regression test to the
pipeline so it cannot silently break again.

## Acceptance Criteria
- [ ] Pipeline green in a safe environment, logs recorded.
- [ ] No secrets in code, logs, or artifacts.
- [ ] Failure behavior defined for every step.
- [ ] Measurable outcome achieved.

## Examples
### Example 1 — Security scan CI job
The skill adds a CI job that runs the dependency and secret scans on every push,
fail-fast, with the report uploaded as an artifact and a notification on failure. The
pipeline is run on a branch first (green), secrets confirmed to come from the store,
and a pipeline self-test job added. Merged through review; merges now blocked on
scan-clean.

## Anti-patterns
- **Automation theater**: pipelines that run but gate nothing — every job gates or
  informs a real decision.
- **Secrets in the pipeline**: credentials in YAML, logs, or artifacts — scan-enforced.
- **First-run-in-production**: never testing the pipeline outside the target environment.
- **Silent failures**: jobs that "pass" on skipped steps — fail loudly, alert clearly.

## Testing Strategy
Validated with pipeline fixtures containing planted misconfigurations (missing failure
paths, secret leakage) scored on detection. See `docs/testing-strategy.md`.

## Future Extensions
- Pipeline self-healing (automatic retry classification).
- Infra drift detection as a scheduled verification job.
