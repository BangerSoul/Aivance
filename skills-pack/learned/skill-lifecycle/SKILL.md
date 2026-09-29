---
name: skill-lifecycle
description: Create, evaluate, and iteratively improve skills — draft, write evals, run with/without the skill, assert quantitatively, rewrite, and optimize the description for triggering. Use when creating a new skill, modifying an existing one, or measuring whether a skill actually changes behavior. Inherited from anthropics/skills (skill-creator eval machinery) and obra/superpowers (writing-skills).
---

# Skill Lifecycle

A skill for creating new skills and iteratively improving them until they measurably
work. The loop: **draft → eval → assert → rewrite → re-eval**.

## The Loop

1. **Decide** what the skill should do and roughly how.
2. **Write a draft** SKILL.md: `name` + `description` frontmatter, then the body.
3. **Create a few test prompts** — realistic user requests the skill should trigger on.
4. **Run the same prompts WITH and WITHOUT the skill** (two contexts). This is the only
   way to prove the skill changes behavior.
5. **Evaluate** both qualitatively and quantitatively.
6. **Rewrite** based on the evidence; fix glaring flaws.
7. **Repeat** until satisfied; then expand the test set and run larger.

## Writing the Description (Triggering Accuracy)

The description is the **context pointer** — its wording decides when the skill fires.
Follow the pointer rules from writing-for-agents:

- **Front-load the leading word** — the description is where it does its triggering work.
- **One trigger per branch.** Synonyms that rename a single branch are one branch
  written twice; collapse them.
- **List the branches** — the distinct cases the skill handles — so different runs take
  different paths through it.
- **Cut identity the body already carries.** Don't repeat the skill's name in the
  description.
- **Include explicit skip conditions** for large, trigger-heavy skills: when NOT to use
  this skill (overrides all triggers).

## Writing Evals

Save test cases to `evals/evals.json`. Don't write assertions yet — just the prompts:

```json
{
  "evals": [
    {
      "id": 0,
      "name": "descriptive-name-here",
      "prompt": "The user request to test",
      "input_files": [],
      "expect": "what correct behavior looks like"
    }
  ]
}
```

Each eval gets a **descriptive name** based on what it tests — not "eval-0".

## Running and Evaluating

- Organize results by iteration: `<skill-name>-workspace/iteration-1/eval-0/outputs/`,
  `iteration-2/...`, etc. Create directories as you go.
- For each eval, run the prompt in a context **with the skill** and one **without**.
- While runs happen, **draft quantitative assertions**: measurable checks (output
  contains X, follows Y format, length ≥ N, invokes the right tool). Explain them to
  the user.
- **Assertions must be checkable by a grader** — a sub-agent or script that reads the
  output and scores pass/fail per assertion. Qualitative review is the human's; the
  quantitative pass is the machine's.
- **Variance analysis**: run the same eval several times. If a skill's outputs swing
  wildly, the skill text is ambiguous — sharpen wording, add constraints, or inline
  material that some branches miss.

## Rewriting

- Fix any assertion that fails consistently.
- For inconsistent (variance) evals, tighten the skill's steps and completion criteria
  (clarity + demand, per writing-for-agents).
- After the skill is stable, **optimize the description** for triggering: test whether
  real user phrasings fire it, and whether near-miss phrasings don't.
- If the skill is only for explicit invocation (not auto-triggering), set
  `disable-model-invocation: true` in frontmatter.

## Checklist

- [ ] Draft with sharp, branch-listing description
- [ ] 3+ realistic test prompts
- [ ] Evals run with AND without the skill
- [ ] Quantitative assertions drafted and checked by a grader
- [ ] Variance analyzed (multiple runs)
- [ ] Rewritten from evidence; re-evaled
- [ ] Description triggering tested; skip conditions set
