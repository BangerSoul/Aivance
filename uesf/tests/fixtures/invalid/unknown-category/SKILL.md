---
id: uesf-zz-fixture-unknown-category
name: Fixture Unknown Category
version: 1.0.0
category: zz
kind: engineering
purpose: An invalid fixture that uses a category code not present in the taxonomy.
description: |
  Use when the test suite needs a skill whose category code is not registered in the
  taxonomy, which must be rejected by the validator.
triggers:
  - condition: "The test suite requests an unknown-category violation"
inputs:
  - "none"
outputs:
  - "A validation failure"
dependencies: []
context_requirements:
  - "The validator is installed"
quality_gates:
  - "The fixture fails validation"
validation:
  - unit
rollback: "Nothing to roll back."
failure_recovery: "Restore the fixture from the repository."
acceptance_criteria:
  - "The fixture fails validation"
automation_hooks: []
mcp_tools: []
cost:
  input_tokens: "~1k"
  output_tokens: "~1k"
  runtime_minutes: "<1"
complexity: 1
maintainability_score: 5
scalability_score: 5
production_readiness: 5
related_skills: []
---

# Fixture Unknown Category

## Overview
Unknown category code.

## Execution Workflow
1. **Step** — run validation.

## Quality Gates
- Fails validation.

## Validation
- **Unit**: covered by the suite.

## Rollback
Nothing to roll back.

## Failure Recovery
Restore from the repository.

## Acceptance Criteria
- [ ] Fails validation.

## Examples
### Example 1
Fails.

## Anti-patterns
- Unknown categories.

## Testing Strategy
Covered by the suite.

## Future Extensions
- None.
