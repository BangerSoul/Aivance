---
id: uesf-te-fixture-missing-dep
name: Fixture Missing Dependency
version: 1.0.0
category: te
kind: engineering
purpose: An invalid fixture that declares a dependency on a skill that does not exist.
description: |
  Use when the test suite needs a skill whose dependency cannot be resolved against
  the inventory, which must be rejected by the validator.
triggers:
  - condition: "The test suite requests an unresolved-dependency violation"
inputs:
  - "none"
outputs:
  - "A validation failure"
dependencies:
  - "uesf-zz-nonexistent-skill"
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

# Fixture Missing Dependency

## Overview
Declares an unresolvable dependency.

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
- Unresolvable dependencies.

## Testing Strategy
Covered by the suite.

## Future Extensions
- None.
