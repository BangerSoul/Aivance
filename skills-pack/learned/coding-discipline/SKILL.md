---
name: coding-discipline
description: Behavioral guardrails that reduce the most common agent coding mistakes — thinking before coding, simplicity first, surgical changes, and goal-driven execution with verifiable success criteria. Use when writing, reviewing, or refactoring code, or when a task feels open-ended enough that overcomplication or scope-creep could creep in. Inherited from multica-ai/andrej-karpathy-skills (karpathy-guidelines) and mattpocock/skills (tdd).
---

# Coding Discipline

Behavioral guidelines to reduce common agent coding mistakes, distilled from Andrej
Karpathy's observations on LLM coding pitfalls and Matt Pocock's engineering skills.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use
judgment. The goal is fewer, better commits — not slower ones.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

Silent assumption is the single most expensive mistake: it produces a whole
implementation built on a wrong premise, discovered only at review.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.
YAGNI is a tiebreaker, not a slogan — when a simple version and a clever version both
work, the simple one wins.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it — don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan with verification checkpoints:

```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work")
require constant clarification.

## 5. Red-Green Loop (TDD)

When implementing a feature or fixing a bug, work in vertical slices:

1. Write the failing test first (red).
2. Implement the minimal code to pass it (green).
3. Refactor only if needed — refactoring belongs to review, not the loop.

Rules of the loop:
- **Red before green.** Never write implementation before its failing test.
- **One slice at a time.** One seam, one test, one minimal implementation per cycle.
- **Test at seams, not internals.** Test behavior through public interfaces; a good
  test reads like a specification and survives refactors.
- **Independent expected values.** Assertions must come from a known-good literal or
  the spec — never recomputed the same way the code computes them (tautological tests).

## 6. Finish With Verification

Before claiming done: run the check, read the output, confirm the exit code. Evidence
before assertions — a test that passed last week is not evidence, a fresh run is.

## Checklist (run before every substantial change)

- [ ] Assumptions stated, ambiguities resolved
- [ ] Simplest approach that meets the request
- [ ] Every changed line traces to the request
- [ ] No speculative features/abstractions
- [ ] Failing test written before implementation (for features/bugs)
- [ ] Fresh verification evidence for every success claim
