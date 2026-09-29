<!--
  UESF SKILL TEMPLATE v1.0.0
  Copy this file to: skills/<category-code>/uesf-<category-code>-<skill-slug>/SKILL.md
  then replace every {{PLACEHOLDER}}. Delete this comment block.
  Contract: keep ALL frontmatter keys (required per spec/skill-spec.schema.json)
  and keep ALL body section headings. Frontmatter must stay parseable by the
  stdlib-only parser in tools/validate_framework.py: use plain scalars, quoted
  strings, "- item" lists, and the nested cost: map. No inline YAML comments.
-->
---
id: uesf-{{CATEGORY_CODE}}-{{skill-slug}}
name: {{Skill Name}}
version: 0.1.0
category: {{category_code}}
kind: {{core|engineering|ai|ux|ui|meta|example}}
purpose: One sentence stating the single outcome this skill produces.
description: |
  Use when {{triggering situation}}. Produces {{deliverable}}. Works for
  {{agent/tool scope}}. Two to four sentences, discovery-optimized: describe
  WHEN to use, never summarize the workflow.
triggers:
  - condition: "{{Symptom or user request that should activate this skill}}"
  - example_prompt: "{{A realistic invocation prompt}}"
inputs:
  - "{{Required input 1}}"
  - "{{Required input 2}}"
outputs:
  - "{{Deliverable 1}}"
dependencies:
  - "uesf-co-planning"
context_requirements:
  - "{{Environment / context the skill needs to start}}"
quality_gates:
  - "{{Gate 1, objectively checkable}}"
  - "{{Gate 2}}"
validation:
  - unit
  - integration
rollback: "{{How to undo this skill's effects safely and cheaply}}"
failure_recovery: "{{What to do when a quality gate fails — never hide the failure}}"
acceptance_criteria:
  - "{{AC 1 — measurable, testable}}"
  - "{{AC 2}}"
automation_hooks:
  - "{{CI job / pre-commit hook / validator rule that automates a gate}}"
mcp_tools:
  - "none"
cost:
  input_tokens: "~{{N}}k"
  output_tokens: "~{{N}}k"
  runtime_minutes: "{{N}}–{{M}}"
complexity: 2
maintainability_score: 4
scalability_score: 4
production_readiness: 3
related_skills:
  - "uesf-{{...}}"
documentation: "docs/skill-spec.md"
---

# {{Skill Name}}

## Overview
One short paragraph: the core principle, when it applies, and when it does not.

## Execution Workflow
1. **{{Phase 1}}** — {{what and why}}.
   - {{step}}
   - {{step}}
2. **{{Phase 2}}** — {{what and why}}.
3. **{{Phase 3}}** — {{what and why}}.

## Quality Gates
- {{Gate 1}}: {{how to check it objectively}}.
- {{Gate 2}}: {{how to check it objectively}}.

## Validation
- **Unit**: {{how to validate the smallest units of this skill's output}}.
- **Integration**: {{how to validate against the real environment}}.
- **Regression**: {{what must keep working}}.
- **Performance / Security / Accessibility** (where applicable): {{how}}.
- **Documentation**: {{check that docs/output are complete and accurate}}.

## Rollback
{{What to revert and how, including the cheapest safe undo path.}}

## Failure Recovery
{{Exact protocol when a gate fails: stop, report evidence, shrink scope or escalate.}}

## Acceptance Criteria
- [ ] {{AC 1}}
- [ ] {{AC 2}}

## Examples
### Example 1 — {{name}}
{{Brief realistic scenario and the expected behavior.}}

## Anti-patterns
- {{Anti-pattern}}: {{why it fails and what to do instead}}.

## Testing Strategy
{{How this skill itself is tested (fixtures, dry-runs, eval prompts) — see docs/testing-strategy.md.}}

## Future Extensions
- {{Planned evolution of this skill}}.
