---
name: verification-before-completion
description: Evidence-before-claims discipline for claiming work is done, including receiving code-review feedback with the same rigor. Use when about to claim work is complete, fixed, or passing, before committing or creating PRs — run verification commands and confirm output before making any success claims. Inherited from obra/superpowers (verification-before-completion, receiving-code-review) and karpathy-guidelines.
---

# Verification Before Completion

**Core principle:** Evidence before claims, always.

**Violating the letter of this rule is violating the spirit of this rule.**

## The Iron Law

```
NO COMPLETION CLAIMS WITHOUT FRESH VERIFICATION EVIDENCE
```

If you haven't run the verification command in this message, you cannot claim it passes.

## The Gate Function

```
BEFORE claiming any status or expressing satisfaction:

1. IDENTIFY: What command proves this claim?
2. RUN: Execute the FULL command (fresh, complete)
3. READ: Full output, check exit code, count failures
4. VERIFY: Does output confirm the claim?
   - If NO: State actual status with evidence
   - If YES: State claim WITH evidence
5. ONLY THEN: Make the claim
```

Skip any step = lying, not verifying.

## Common Failures

| Claim | Requires | Not Sufficient |
|-------|----------|----------------|
| Tests pass | Test command output: 0 failures | Previous run, "should pass" |
| Linter clean | Linter output: 0 errors | Partial check, extrapolation |
| Build succeeds | Build command: exit 0 | Linter passing, logs look good |
| Bug fixed | Test original symptom: passes | Code changed, assumed fixed |
| Regression test works | Red-green cycle verified | Test passes once |
| Agent completed | VCS diff shows changes | Agent reports "success" |
| Requirements met | Line-by-line checklist | Tests passing |

## Red Flags — STOP

- Using "should", "probably", "seems to"
- Expressing satisfaction before verification ("Great!", "Perfect!", "Done!")
- About to commit/push/PR without verification
- Trusting agent success reports
- Relying on partial verification
- Thinking "just this once"
- Tired and wanting work over
- **ANY wording implying success without having run verification**

## Verifying Different Claim Types

- **Tests pass** → run the actual test command, count 0 failures
- **Build succeeds** → run the build, check exit 0
- **Bug fixed** → run the original failing scenario, watch it pass
- **Feature complete** → walk the acceptance criteria one by one against the running app
- **Agent work done** → inspect the diff, run the tests yourself — don't trust the report

## Receiving Code Review (same discipline, other side)

When receiving review feedback: verify before implementing, ask before assuming.
Restate the requirement in your own words, check it against codebase reality, then
implement one item at a time and test each. Never respond with performative agreement
("You're absolutely right!") before verification — technical correctness over social
comfort. If any item is unclear, stop and ask for clarification.
