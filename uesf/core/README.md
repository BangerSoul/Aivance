# UESF Core Skills

The Core layer holds the six language- and domain-agnostic primitives that every other
skill composes. They form the UESF execution loop:

```
Plan ──► Implement ──► Verify ──► Review ──► (merge/release)
 ▲                                        │
 └────────── Debug / Refactor ◄───────────┘
```

| Skill | What it does | Composes |
|-------|-------------|----------|
| `uesf-co-planning` | Decomposes any request into a verified, executable plan. | entry point |
| `uesf-co-implementation` | Implements planned tasks test-first, in small verified increments. | planning, testing |
| `uesf-co-testing` | Designs and runs the verification that proves behavior. | planning |
| `uesf-co-debugging` | Finds and fixes root causes through reproduction and hypothesis. | testing |
| `uesf-co-review` | Reviews changes for correctness, design, and risk with bounded fix loops. | testing |
| `uesf-co-refactoring` | Restructures code behavior-neutrally, step by step. | testing, implementation |

## Why these six

- They are **model-agnostic**: no prompt recipes tied to one vendor; they describe
  verifiable process.
- They are **repository-independent**: usable in any language and any codebase.
- They **cover the observed failure modes** of AI agents (from the source-repository
  research): guessing instead of planning, code before tests, symptom patching, shotgun
  edits, and rubber-stamp reviews.
- They are the **composition substrate**: engineering, AI, and meta skills call these
  primitives rather than reimplementing them.

## Usage

Each skill is a `SKILL.md` with machine-readable frontmatter (see
`docs/skill-spec.md`) and a body with the required sections: Execution Workflow, Quality
Gates, Validation, Rollback, Failure Recovery, Acceptance Criteria, Examples,
Anti-patterns, Testing Strategy, Future Extensions.

Validate the whole framework at any time:

```bash
python3 tools/validate_framework.py
```
