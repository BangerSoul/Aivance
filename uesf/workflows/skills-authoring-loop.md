# Workflow: Skills Authoring Loop

*How new skills enter UESF. This is the framework's own CI/CD, driven by the meta
layer.*

## When to use

A gap is identified (intake sweep, taxonomy gap, repeated pattern, user request).

## Flow

```
GAP
 │
 ▼
[1] uesf-le-continuous-learning ─── screen: fit / overlap / quality
 │
 ▼
[2] uesf-mk-repository-analyzer ─── (external sources) citable analysis + proposal
 │
 ▼
[3] tools/skill_scaffold.py ──────── skeleton (spec-conformant, 0.x)
 │
 ▼
[4] author the body (template sections)
 │
 ▼
[5] uesf-mk-skill-test-generator ── fixtures (happy / failure / edge)
 │
 ▼
[6] uesf-mk-skill-validator ──────── zero errors (or iterate)
 │
 ▼
[7] uesf-mk-skill-reviewer ───────── executability + compliance (bounded loop)
 │
 ▼
[8] uesf-mk-skill-version-manager ── promote to 1.0.0 + changelog
 │
 ▼
[9] uesf-mk-skill-certification-engine ── L1 certificate
 │
 ▼
[10] uesf-mk-skill-doc-generator ─── index + docs refreshed
```

## Non-negotiables

- No skill skips validation (step 6) — the validator has no exceptions.
- No skill is promoted unreviewed (step 7).
- Merged/overlapping skills go through `uesf-mk-skill-merger` instead of steps 3–5.
- Every merged skill re-validates the whole framework (regression).

## Time-boxing

- Authoring: 20–60 min per skill. Iterate in place; do not let drafts accumulate
  past one release cycle without promotion or rejection (recorded).
