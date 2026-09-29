---
name: brainstorm-before-build
description: Design before implementation — explore intent, requirements, and design through dialogue before writing code or creating anything creative. Use before any creative work: creating features, building components, adding functionality, or modifying behavior. Inherited from obra/superpowers (brainstorming) and mattpocock/skills (wait-what, to-spec).
---

# Brainstorm Before Build

Turn ideas into fully formed designs and specs through natural collaborative dialogue,
then get explicit user approval before any implementation.

**Announce at start:** "I'm using the brainstorm-before-build skill to shape this into a
design before we implement."

## The Hard Gate

```
Do NOT write code, scaffold a project, or take any implementation action until you have
presented a design and the user has approved it.
```

**Anti-pattern: "This is too simple to need a design."** Every project goes through this
process — a todo list, a single-function utility, a config change, all of them. "Simple"
projects are where unexamined assumptions cause the most wasted work. The design can be
short (a few sentences for truly simple projects), but you MUST present it and get
approval.

## Process

1. **Explore project context** — check files, docs, recent commits. What exists already?
2. **Ask clarifying questions** — one at a time, not a wall of questions. Understand:
   - Purpose: what is the user actually trying to achieve?
   - Constraints: what must be preserved (stack, APIs, deadlines)?
   - Success criteria: what does "done" look like, verifiably?
3. **Propose 2-3 approaches** — with trade-offs and your recommendation. Don't silently
   pick one.
4. **Present the design** — in sections scaled to their complexity, get approval after
   each section.
5. **Write the design doc** — a short spec capturing decisions. Include what was
   explicitly ruled out and why.
6. **Self-review the spec** — check for placeholders, contradictions, ambiguity,
   out-of-scope scope creep.
7. **User reviews the spec** — ask the user to confirm before implementation.
8. **Transition** — only now move to implementation (e.g. via the
   plan-driven-implementation skill).

## Question Quality

Prefer questions that surface assumptions over questions that request preferences:

| Better | Worse |
|---|---|
| "Who is the primary user of this screen?" | "Do you want blue or green?" |
| "What happens when the network fails?" | "Should I add error handling?" |
| "What does success look like in a week?" | "Is this good?" |

When the user's goal is unclear, ask "what problem are you trying to solve?" before any
"how should I build it?" — a solution to the wrong problem is the most expensive failure.

## Output

- Design doc saved to `docs/` (or a `specs/` dir) with date + topic in the filename.
- A clear statement of scope: in-scope, out-of-scope, and explicitly rejected ideas.
