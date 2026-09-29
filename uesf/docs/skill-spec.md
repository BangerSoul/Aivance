# UESF Skill Specification Standard

*Version 1.0.0 · Deliverable 8*

This is the normative specification every UESF skill must conform to. Machine-readable
form: [`spec/skill-spec.schema.json`](../spec/skill-spec.schema.json). Enforcement:
[`tools/validate_framework.py`](../tools/validate_framework.py).

## 1. Physical form

A skill is a directory:

```
uesf-<cc>-<slug>/
├── SKILL.md          (required — the skill, frontmatter + body)
├── references/       (optional — deep reference material, 1 level deep)
├── scripts/          (optional — deterministic executables, stdlib-preferred)
├── examples/         (optional — worked scenarios)
└── assets/           (optional — templates, fonts, fixtures)
```

- The directory name **equals the skill id**.
- `SKILL.md` must stay **under 500 lines**; heavier content goes to `references/`.

## 2. Identity

| Field | Rule |
|-------|------|
| `id` | `uesf-<cc>-<slug>`; `<cc>` is a registered taxonomy code; `<slug>` is kebab-case. Immutable once published. |
| `name` | Human-readable, ≥3 chars. |
| `version` | Semantic version `x.y.z`. Bumps per `docs/versioning.md`. |
| `category` | One registered taxonomy code (see `docs/taxonomy.md`). |
| `kind` | `core` \| `engineering` \| `ai` \| `ux` \| `ui` \| `meta` \| `example`. Constraints: `core`→`co`, `meta`→`mk`, `example`→`ex`. |

## 3. Required frontmatter (24 fields)

| Field | Type | Notes |
|-------|------|-------|
| `id` | string | pattern `uesf-[a-z]{2}-[a-z0-9-]+` |
| `name` | string | ≥3 chars |
| `version` | string | semver |
| `category` | string | taxonomy enum |
| `kind` | string | kind enum |
| `purpose` | string | one sentence; the single outcome |
| `description` | string | 20–1024 chars; **when to use**, never the workflow |
| `triggers` | array≥1 | strings or `{condition, example_prompt}` maps |
| `inputs` | array | what the skill consumes |
| `outputs` | array≥1 | what the skill produces |
| `dependencies` | array | existing skill ids; must resolve, no cycles |
| `context_requirements` | array | environment/context to start |
| `quality_gates` | array≥1 | objectively checkable gates |
| `validation` | array≥1 | strategies: unit/integration/regression/performance/security/accessibility/documentation/certification |
| `rollback` | string | how to undo safely; ≥10 chars |
| `failure_recovery` | string | what to do when a gate fails; ≥10 chars |
| `acceptance_criteria` | array≥1 | measurable ACs |
| `automation_hooks` | array | CI/hook integrations |
| `mcp_tools` | array | required MCP tools (or `none`) |
| `cost` | map | `input_tokens`, `output_tokens`, `runtime_minutes` (estimate strings) |
| `complexity` | int 1–5 | |
| `maintainability_score` | int 1–5 | |
| `scalability_score` | int 1–5 | |
| `production_readiness` | int 1–5 | |
| `related_skills` | array | lateral links (warned if unresolved) |
| `documentation` | string (optional) | doc reference |
| `deprecated` / `superseded_by` | bool / string (optional) | lifecycle record |

## 4. Required body sections (10)

`## Execution Workflow` · `## Quality Gates` · `## Validation` · `## Rollback` ·
`## Failure Recovery` · `## Acceptance Criteria` · `## Examples` ·
`## Anti-patterns` · `## Testing Strategy` · `## Future Extensions`

Section purpose:

- **Execution Workflow** — numbered phases a fresh agent can follow to the stated
  outcome.
- **Quality Gates** — gates that stop progress when unmet; objectively checkable.
- **Validation** — how the skill's output is verified (maps to `validation` field).
- **Rollback** — the cheapest safe undo.
- **Failure Recovery** — exact protocol when a gate fails.
- **Acceptance Criteria** — checkbox-form measurable completion.
- **Examples** — at least one concrete worked scenario.
- **Anti-patterns** — the mistakes this skill exists to prevent (mistake-checklist
  pattern).
- **Testing Strategy** — how the skill itself is tested.
- **Future Extensions** — planned evolution.

## 5. Authoring rules

1. **Description hygiene:** describe *when* to use. Never summarize the workflow —
   agents shortcut to summaries (empirically established by superpowers).
2. **Degrees of freedom:** exact commands/scripts where correctness matters;
   instructions where judgment is needed.
3. **No time-sensitive content:** dates and version pins live outside the skill.
4. **Externalize:** heavy references to `references/` (1 level deep); deterministic
   work to `scripts/`.
5. **Cite provenance** (optional): a `Sources:` note in the body for patterns adopted
   from the ecosystem.
6. **Draft until validated:** new skills are `0.x` until validator + reviewer pass,
   then promoted per `docs/versioning.md`.

## 6. Validation (what "conforming" means)

The validator enforces, per skill:

- all 24 required fields present with correct types;
- id format + id↔category agreement + taxonomy membership;
- kind↔category constraints;
- semver version;
- dependencies resolve and the graph is acyclic;
- required body sections present;
- score ranges (1–5);
- non-empty lists where required; description length bounds.

Warnings (non-blocking): unresolved `related_skills`, directory/id mismatch,
deprecated-without-supersession.

## 7. Evolution of the spec itself

The spec is versioned like any skill. Changes require: a governance RFC
(`policies/contribution.md`), validator + schema + template updated in one commit,
and a migration note (`docs/migration-guide.md`). Backward-incompatible changes bump
the spec major version and add a compat shim period.
