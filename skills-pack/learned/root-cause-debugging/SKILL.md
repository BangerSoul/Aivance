---
name: root-cause-debugging
description: Systematic debugging that finds root cause before proposing any fix. Use when encountering any bug, test failure, or unexpected behavior — especially under time pressure, when "one quick fix" seems obvious, when a previous fix didn't work, or when the issue seems simple (simple bugs have root causes too). Inherited from obra/superpowers (systematic-debugging); complements the uesf-co-debugging skill with an explicit four-phase discipline.
---

# Root-Cause Debugging

**Core principle:** ALWAYS find root cause before attempting fixes. Symptom fixes are
failure. Violating the letter of this process is violating the spirit of debugging.

## The Iron Law

```
NO FIXES WITHOUT ROOT CAUSE INVESTIGATION FIRST
```

If you haven't completed Phase 1, you cannot propose fixes.

## When to Use

Use for ANY technical issue: test failures, production bugs, unexpected behavior,
performance problems, build failures, integration issues.

**Use ESPECIALLY when:**
- Under time pressure (emergencies make guessing tempting)
- "Just one quick fix" seems obvious
- You've already tried multiple fixes
- The previous fix didn't work
- You don't fully understand the issue

**Don't skip when:**
- The issue seems simple (simple bugs have root causes too)
- You're in a hurry (rushing guarantees rework)

## The Four Phases

You MUST complete each phase before proceeding to the next.

### Phase 1: Root Cause Investigation

**BEFORE attempting ANY fix:**

1. **Read error messages carefully.** Don't skip past errors or warnings. They often
   contain the exact solution. Read stack traces completely. Note line numbers, file
   paths, error codes.
2. **Reproduce consistently.** Can you trigger it reliably? What are the exact steps?
   Reproduce it before you change anything.
3. **Gather evidence.** What changed recently? What are the relevant inputs and states?
   Check logs, git history, and recent commits.
4. **Form a hypothesis, not a guess.** A hypothesis is testable and specific: "The
   parser fails when the input contains a BOM because byte 0 is treated as content."

### Phase 2: Isolate the Cause

- **Binary search** the change: bisect git history, comment out halves, reduce the
  input until it's minimal.
- **Confirm the mechanism.** The cause must explain ALL observed symptoms, not just one.
  If your explanation doesn't cover every symptom, it's not the root cause.
- **Prove it.** A hypothesis that you cannot demonstrate (a minimal repro, a targeted
  assertion, a log line) is not yet a cause.

### Phase 3: Fix the Root Cause

- Fix the cause, not the symptom. If the fix removes the symptom but the mechanism
  remains, you've applied a band-aid.
- Fix it at the right layer: don't patch the UI when the data layer is wrong.

### Phase 4: Verify and Prevent

- **Verify** the original symptom is gone, with a fresh run — not by reasoning.
- **Add a regression test** that reproduces the original bug and would fail if the
  root cause returned.
- **Ask "where else?"** Does this same pattern exist elsewhere? Fix the class of bug,
  not the instance.

## Anti-Patterns

| Anti-pattern | Why it fails |
|---|---|
| Symptom fix (patching output) | Root cause resurfaces elsewhere |
| Shotgun debugging (try random fixes) | No learning; can introduce new bugs |
| Blaming the tool/framework | Rarely the cause; check your code first |
| Fixing without a repro | Can't verify the fix |
| Fixing when tired/frustrated | Rushing guarantees rework |

## Checklist

- [ ] Bug reproduced consistently with minimal steps
- [ ] Hypothesis explains ALL symptoms
- [ ] Mechanism demonstrated (repro/assertion/log)
- [ ] Fix targets root cause, not symptom
- [ ] Original symptom verified gone (fresh run)
- [ ] Regression test added
- [ ] "Where else?" sweep done
