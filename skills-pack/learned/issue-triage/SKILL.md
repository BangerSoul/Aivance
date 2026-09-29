---
name: issue-triage
description: Move issues and external PRs through a triage state machine — categorize, verify, gather info, and write agent-ready briefs so work can proceed without the reporter. Use when a repo has an issue tracker and issues/PRs need classification and preparation for implementation. Inherited from mattpocock/skills (triage, AGENT-BRIEF).
---

# Issue Triage

Move issues (and external PRs) through a small state machine of triage roles. A PR is
an issue with attached code — same roles, same states, with a few deltas.

## Roles

Two **category** roles:
- `bug` — something is broken
- `enhancement` — new feature or improvement

Five **state** roles:
- `needs-triage` — maintainer needs to evaluate
- `needs-info` — waiting on reporter for more information
- `ready-for-agent` — fully specified, ready for an agent
- `ready-for-human` — needs human implementation
- `wontfix` — will not be actioned

Every triaged issue carries exactly one category role and one state role. If state
roles conflict, flag it and ask the maintainer before doing anything else.

## State Transitions

- An unlabeled issue normally goes to `needs-triage` first.
- From there: `needs-info` (reporter must answer), `ready-for-agent`,
  `ready-for-human`, or `wontfix`.
- `needs-info` returns to `needs-triage` once the reporter replies.
- The maintainer can override at any time — flag transitions that look unusual and ask
  before proceeding.

## Triage Steps (per issue)

1. **Read the full issue** — body, comments, linked items. Never classify from the title.
2. **Verify it's real** — can you reproduce the bug? Is the enhancement already
   implemented? Duplicate? Out of scope?
3. **Classify** — category role + state role.
4. **Gather missing info** — if the issue can't proceed (no repro steps, vague goal),
   move to `needs-info` and ask precise questions, one at a time.
5. **Write the brief** — when it's `ready-for-agent`, write a durable agent brief
   (see writing-for-agents): behavioral not procedural, no file paths or line numbers,
   concrete acceptance criteria, explicit out-of-scope.
6. **Post** — with a clear disclaimer that the triage was AI-generated, so humans know
   the classification is a first pass, not a decision.

## Triage Disciplines

- **Durability over precision:** the issue may sit for days or weeks; the codebase will
  change. Describe interfaces, types, and behavioral contracts — not file paths.
- **Complete acceptance criteria:** each independently verifiable. Bad: "Triage should
  work correctly". Good: "Running `gh issue list --label needs-triage` returns issues
  that have been through initial classification".
- **Explicit scope boundaries:** state what is out of scope to prevent gold-plating.
- **Don't implement during triage.** Triage prepares work; it doesn't do it.

## Checklist

- [ ] Full issue read (body + comments), not just title
- [ ] Reproduced / verified / de-duplicated
- [ ] Exactly one category + one state role
- [ ] Missing info → needs-info with precise questions
- [ ] ready-for-agent → durable brief with acceptance criteria
- [ ] AI-generated disclaimer posted
