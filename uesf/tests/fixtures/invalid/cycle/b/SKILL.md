---
id: uesf-te-cycle-b
name: Fixture Cycle B
version: 1.0.0
category: te
kind: engineering
purpose: The second half of a two-skill dependency cycle fixture.
description: |
  Use when the test suite needs a dependency cycle. This skill depends on
  uesf-te-cycle-a, which depends back on it, forming a cycle that the validator must
  detect.
triggers:
  - condition: "The test suite requests a cycle violation"
inputs:
  - "none"
outputs:
  - "A validation failure"
dependencies:
  - "uesf-te-cycle-a"
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

# Fixture Cycle B

## Overview
Part two of a dependency cycle.

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
- Dependency cycles.

## Testing Strategy
Covered by the suite.

## Future Extensions
- None.
