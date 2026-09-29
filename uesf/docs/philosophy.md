# UESF Design Philosophy

*Version 1.0.0 · Core design document*

This document states *why* UESF is built the way it is. Every structural decision in
the framework traces back to one of the principles below. If a future change
contradicts a principle, the change is wrong — or the principle must be explicitly
amended through governance.

## The twelve axioms

1. **Evidence over claims.** Nothing is "done" because it was asserted. Verification
   output, test results, and measurements are the only currency. (Borrowed and
   hardened from superpowers' empirical-evidence rule and the entire test-first
   lineage.)
2. **Verification before completion.** A change is complete only when its verification
   passes. This is encoded in every skill's Acceptance Criteria and enforced by the
   validator's required sections.
3. **Planning is not optional for non-trivial work.** Ambiguity is decomposed into
   verifiable tasks before implementation. (Superpowers' brainstorm→plan→execute,
   mattpocock's grilling sessions — generalized.)
4. **Small steps, fast feedback.** "The rate of feedback is your speed limit."
   (Pragmatic Programmer, echoed by mattpocock.) Every core skill enforces
   increment-sized, individually verifiable steps.
5. **Root causes, never symptoms.** Debugging and security both fix causes with
   regression protection. Symptom-patching is listed as an anti-pattern in every
   relevant skill.
6. **Minimal surface, maximal clarity.** Skills describe *when to use* in discovery
   metadata, never workflow summaries — agents shortcut to summaries. (Superpowers'
   tested trigger-description hygiene; Anthropic's 1024-char description rule.)
7. **Model-agnosticism is a requirement, not a preference.** No vendor prompt
   recipes, no harness assumptions. The framework must outlive any single model.
8. **The framework validates itself.** No skill — including meta-skills — is exempt
   from the spec. The validator has no exceptions for the framework.
9. **Anti-duplication is enforced, not encouraged.** Overlap is merged
   (`uesf-mk-skill-merger`) or rejected with recorded reasons at intake
   (`uesf-le-continuous-learning`).
10. **Everything is versioned and reversible.** Skills, prompts, and even
    certifications carry versions, deprecation, and rollback paths. A reversible
    system can be improved safely.
11. **The framework improves itself.** Meta-skills generate, optimize, and certify
    new skills. The intake loop (analyze → synthesize → validate) is scheduled, not
    ad-hoc.
12. **Humility about scope.** Skills declare their coverage limits. Research briefs
    time-box and freeze. Audits list evidence gaps. "Comprehensive-looking fiction"
    is worse than honest partial coverage.

## Engineering philosophy

UESF treats skills as **products** with a build pipeline:

```
gap → generate (scaffold + body) → test (fixtures) → document → validate → review
    → benchmark → version → certify → publish → maintain → deprecate → migrate
```

Every stage is a skill. The pipeline applies to the framework itself — the meta layer
is the framework's own CI/CD.

## Agent philosophy

Research across the eleven source repositories converged on a set of agent failure
modes that UESF is explicitly designed to counter:

| Agent failure mode | UESF countermeasure |
|--------------------|---------------------|
| Guessing instead of planning | `uesf-co-planning` gate before implementation |
| Code before tests | `uesf-co-testing`'s red-green discipline; `uesf-co-implementation`'s mandatory RED |
| Symptom patching | `uesf-co-debugging`'s hypothesis loop; anti-pattern lists |
| Shotgun / drive-by edits | Scoped-diff gates and "no unrelated changes" acceptance criteria |
| Rubber-stamp review | `uesf-co-review`'s evidence tracing + prioritized findings + circuit breaker |
| Context pollution | Agent-architecture skill's context budgets and artifact hand-offs |
| Fabricated evidence | Every skill's "recorded output" requirement |
| Overcomplication | The "senior engineer" simplification check (karpathy-guidelines lineage) |

## Naming

**UESF — Ultimate Engineering Skills Framework** is the official name (v1.0.0).
Alternatives considered and rejected: *FORGE* (already crowded), *PRISM*
(pronounceable but vague), *LOOM* (nice metaphor, confusing with existing products).
The tagline — *"the open engineering skills framework"* — emphasizes the two
properties that matter: openness and engineering discipline.

## Relationship to the source repositories

UESF is not a fork or a superset of any single repository; it is a **synthesis**.
See `docs/repository-analysis.md` for the per-repository reverse engineering and
`benchmarks/matrices.md` for the feature-by-feature comparison. The standing rule for
intake: *adopt the idea, synthesize the skill, cite the source, never copy the file.*
