---
name: writing-for-agents
description: Write documents, briefs, and skills that other agents can actually execute predictably — context pointers, information hierarchy, progressive disclosure, completion criteria. Use when creating or editing skills, AGENTS.md/CLAUDE.md files, agent briefs, or any doc an agent consumes. Inherited from mattpocock/skills (writing-for-agents, SKILL-MECHANICS, handoff, AGENT-BRIEF).
---

# Writing For Agents

The same levers make any document an agent consumes predictable — the agent taking the
same *process* every run, not producing the same output.

## Context Pointers

A **context pointer** is a reference held in the agent's context that names some
out-of-context material and encodes the condition for reaching it. A skill's description
is one; a line in AGENTS.md naming a doc is the same object.

The pointer's *wording*, not its target, decides when the agent reaches the material —
and how reliably. A must-have target behind a weakly worded pointer is a variance bug.

A pointer does two jobs:
1. State what the material is.
2. List the **branches** that should trigger reaching it (distinct cases the document
   handles). One trigger per branch — synonyms that rename a single branch are one
   branch written twice; collapse them.

## The Two Loads

- **Context load** — cost of always-loaded material on the agent's window (AGENTS.md
  lines, skill descriptions). Every word of an always-loaded pointer costs on every
  turn; prune harder than the body.
- **Cognitive load** — cost on the human: which documents exist and when to reach for
  each. The human is the index. Not a cost to minimize — it is the price of human
  agency; spend it where human judgment matters.

## Information Hierarchy

Where each piece sits on a ladder ranked by how immediately the agent needs it:

1. **In-file step** — the primary tier: what the agent does, in order.
2. **In-file reference** — consulted on demand; often a legitimately flat peer-set.
3. **Disclosed reference** — pushed into a separate file, reached by a context pointer,
   loaded only when the pointer fires.

**Progressive disclosure** is the move down the ladder — push what only some branches
need behind a pointer; inline what every branch needs. Branching is the cleanest
disclosure test.

**Co-location** — keep a concept's definition, rules, and caveats under one heading
rather than scattered. Grouped material reads like documentation; scattered material
does not.

**Sprawl** is the failure mode: a document too long, even when every line is live.
The cure is the ladder — disclose reference behind pointers, split by branch or
sequence.

## Steps and Completion Criteria

Every step ends on a **completion criterion** — the condition that tells the agent the
work is done. Two properties make it a lever:

- **Clarity** — can the agent tell done from not-done? A vague bound ("understanding
  reached") invites **premature completion**. Sharpen the bound first (local and cheap).
- **Demand** — how much it requires. "Every modified model accounted for" forces
  thorough work where "produce a change list" does not.

## Agent Briefs (durable, behavioral, verifiable)

When writing a brief for another agent (issue/PR triage, handoff):

- **Durability over precision** — the codebase will change. Describe interfaces, types,
  and behavioral contracts; name types and function signatures; DON'T reference file
  paths or line numbers (they go stale).
- **Behavioral, not procedural** — describe *what* the system should do, not *how*.
  Good: "The `SkillConfig` type should accept an optional `schedule` field of type
  `CronExpression`". Bad: "Open src/types/skill.ts and add a schedule field on line 42".
- **Complete acceptance criteria** — each independently verifiable. Bad: "Triage should
  work correctly". Good: "Running `gh issue list --label needs-triage` returns issues
  that have been through initial classification".
- **Explicit scope boundaries** — state what is out of scope to prevent gold-plating.

### Brief template

```markdown
## Agent Brief

**Category:** bug / enhancement
**Summary:** one-line description of what needs to happen

**Current behavior:** what happens now (for bugs: the broken behavior)

**Desired behavior:** what should happen after the work, edge cases included

**Key interfaces:** types/functions/config shapes to look for or modify

**Acceptance criteria:**
- [ ] (each independently verifiable)
- [ ]

**Out of scope:** (prevents gold-plating)
```
