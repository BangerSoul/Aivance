---
name: plan-driven-implementation
description: Write comprehensive, bite-sized implementation plans and execute them task-by-task with verification gates. Use when you have a spec or requirements for a multi-step task before touching code, or when delegating implementation to another agent/session. Inherited from obra/superpowers (writing-plans, executing-plans, subagent-driven-development) and mattpocock/skills (to-spec).
---

# Plan-Driven Implementation

## Overview

Write comprehensive implementation plans assuming the engineer has **zero context** for
the codebase and questionable taste. Document everything they need to know: which files
to touch for each task, code, testing, docs to check, how to test it. Give the whole
plan as bite-sized tasks.

**Announce at start:** "I'm using the plan-driven-implementation skill to create the
implementation plan."

## Scope Check

If the spec covers multiple independent subsystems, break it into separate plans — one
per subsystem. Each plan should produce working, testable software on its own.

## File Structure First

Before defining tasks, map out which files will be created or modified and what each is
responsible for. This is where decomposition decisions get locked in:

- Design units with clear boundaries and well-defined interfaces. Each file has one
  clear responsibility.
- Files that change together should live together. Split by responsibility, not by
  technical layer.
- In existing codebases, follow established patterns. Don't unilaterally restructure
  large files — but if a file you're modifying has grown unwieldy, a split is reasonable.

## Task Right-Sizing

A task is the smallest unit that carries its own test cycle and is worth a fresh
reviewer's gate. Fold setup, configuration, scaffolding, and docs into the task whose
deliverable needs them; split only where a reviewer could meaningfully reject one task
while approving its neighbor. **Each task ends with an independently testable deliverable.**

## Bite-Sized Task Granularity

**Each step is one action (2–5 minutes):**
- "Write the failing test" — step
- "Run it to make sure it fails" — step
- "Implement the minimal code to make the test pass" — step
- "Run the tests and make sure they pass" — step
- "Commit" — step

## Plan Document Header

Every plan MUST start with this header:

```markdown
# [Feature Name] Implementation Plan

> **For agentic workers:** execute this plan task-by-task using checkbox (`- [ ]`)
> syntax for tracking. Complete each task fully (including its tests) before moving on.

**Goal:** [One sentence describing what this builds]

**Architecture:** [2-3 sentences about approach]

**Tech Stack:** [Key technologies/libraries]

## Global Constraints

[Project-wide requirements — version floors, dependency limits, naming and copy rules,
platform requirements — one line each, with exact values copied verbatim from the spec.
Every task's requirements implicitly include this section.]
```

## Task Structure

Each task contains:

1. **Context** — 1-2 sentences connecting this task to the rest of the plan.
2. **Steps** — the 2-5 minute actions, each starting with `- [ ]`.
3. **Test** — how to verify this task in isolation.
4. **Definition of done** — the deliverable and its acceptance criteria.

## Executing the Plan

- Work one task at a time, in order. Do not jump ahead.
- After each task, run its tests and mark the checkbox.
- If reality diverges from the plan (an API changed, a test reveals a design flaw),
  update the plan BEFORE improvising — a plan that lies is worse than no plan.
- Use parallel agents only for tasks with no shared state and no sequential
  dependencies; have each report back and integrate their results.

## Verification

When the last checkbox is marked: run the full test suite, the typecheck, and the
build. Only then claim the plan is complete — evidence before assertions.
