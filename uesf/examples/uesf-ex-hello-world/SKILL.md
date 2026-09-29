---
id: uesf-ex-hello-world
name: Hello World
version: 1.0.0
category: ex
kind: example
purpose: Demonstrate the minimal complete structure of a spec-conformant UESF skill.
description: |
  Use when learning the UESF skill format or as the smallest reference for writing a
  new skill. Produces a greeting with evidence. This example exists to show every
  required frontmatter key and body section in the smallest valid form.
triggers:
  - condition: "A learner needs the smallest valid SKILL.md as a reference"
  - example_prompt: "Show me the minimal valid skill"
inputs:
  - "A name to greet"
outputs:
  - "A verified greeting"
dependencies: []
context_requirements:
  - "Nothing beyond the template"
quality_gates:
  - "The greeting is produced and verified by the Validation section's check"
validation:
  - unit
rollback: "Produces no changes; the example's only side effect is a greeting."
failure_recovery: "If the greeting check fails, the example has been modified — restore it from the template."
acceptance_criteria:
  - "Greeting contains the provided name"
automation_hooks:
  - "Covered by the framework validator like any other skill"
mcp_tools:
  - "none"
cost:
  input_tokens: "~1k"
  output_tokens: "~0.1k"
  runtime_minutes: "<1"
complexity: 1
maintainability_score: 5
scalability_score: 5
production_readiness: 5
related_skills:
  - "uesf-ex-api-design-review"
documentation: "docs/skill-spec.md"
---

# Hello World

## Overview
The smallest complete skill: it takes a name, produces a greeting, and verifies it.
Every line is required by the spec — nothing here is decorative.

## Execution Workflow
1. **Read the input** — Accept the name from the request.
2. **Produce the greeting** — Format "Hello, {name}!".
3. **Verify** — Check the greeting contains the name.

## Quality Gates
- The greeting contains the provided name.

## Validation
- **Unit**: the format check in step 3 is the unit verification.

## Rollback
No files, data, or configuration are modified — the only output is the greeting
itself, so there is nothing to roll back.

## Failure Recovery
If the check fails, the example has been corrupted — restore it from the template and
re-run the validator.

## Acceptance Criteria
- [ ] Greeting contains the provided name.

## Examples
### Example 1 — Greeting "Buffy"
Input: Buffy. Output: "Hello, Buffy!" — verified.

## Anti-patterns
- Removing required frontmatter keys "because they're small" — the validator enforces
  the full spec, and so should every skill.

## Testing Strategy
Covered by the framework test suite's fixture validations and the whole-framework
validator pass.

## Future Extensions
- None intended — this example exists to stay minimal.
