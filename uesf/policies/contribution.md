# UESF Contribution Policy

*Version 1.0.0*

How skills and changes enter the framework. Enforced by review and the validator —
not by ceremony.

## 1. Adding a skill

1. Run the intake screen (`uesf-le-continuous-learning`): check taxonomy fit and
   overlap with existing skills. Overlapping candidates go to the merger, not the
   generator.
2. Scaffold (`tools/skill_scaffold.py`) and author the full skill (all 24
   frontmatter fields + 10 body sections).
3. Generate fixtures (`uesf-mk-skill-test-generator`).
4. Validate to zero errors; review for executability
   (`uesf-mk-skill-reviewer`).
5. Version as `0.x`, then promote with a changelog entry.

## 2. Adding a category (taxonomy RFC)

One-page RFC covering: name, two-letter code, scope, at least one candidate skill,
and why existing categories can't hold it. Reviewed by governance for overlap and
5-year fit. Lands as a single commit updating `docs/taxonomy.md`, the validator
`TAXONOMY` table, and the schema enum. Codes are never re-keyed; old codes may be
marked retired.

## 3. Changing the spec

RFC + version bump per `docs/versioning.md` (§4). Breaking changes require the
compat shim period. Validator, schema, template, and migration guide update in one
commit.

## 4. Intake from external sources

- Adopt ideas, synthesize skills, cite sources — never copy files wholesale.
- Rejections are recorded with reasons (rejection is a finding).
- Security-screened: no unvetted third-party skills without review
  (`uesf-se-security-audit` lens).

## 5. Definition of done

Every contribution closes with:

- validator: 0 errors across the whole framework;
- suite: green;
- docs: index + taxonomy + changelog updated;
- review: passed (no blockers).

## 6. Reverting contributions

Drafts (`0.x`) may be deleted freely. Released skills follow the deprecation path
(`uesf-mk-skill-version-manager`). Never edit a released skill in place — new
version, new record.
