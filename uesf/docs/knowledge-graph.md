# UESF Knowledge Graph

*Version 1.0.0 · Deliverable 3*

The knowledge graph maps the framework's **concepts** (what engineering knowledge
exists), **patterns** (how it is applied), and **skills** (who applies it). It is the
semantic index that taxonomy (the partition) and the dependency graph (the edges)
leave implicit.

## Node types

- **C** — Concept (durable engineering knowledge)
- **P** — Pattern (reusable approach; provenance noted)
- **S** — Skill (the executor; links to its spec record)

## Level 1 — Foundations

```
C: Verification-First ──► P: red-green-refactor (superpowers·mattpocock·karpathy)
C: Evidence Over Claims ─► P: recorded output in every workflow
C: Small Steps ─────────► P: 2–20 min tasks · single-step commits
C: Root Causes ─────────► P: hypothesis loop (superpowers)
C: Reversibility ────────► P: rollback sections in every skill
S: uesf-co-planning · uesf-co-implementation · uesf-co-testing
S: uesf-co-debugging · uesf-co-review · uesf-co-refactoring
```

## Level 2 — Engineering patterns

```
C: Traceability ────────► P: AC→test mapping (testing skill)
C: Scope Discipline ────► P: no drive-by edits (karpathy)
C: Feedback Rate ───────► P: "the rate of feedback is your speed limit" (Pragmatic/MP)
C: Shared Language ─────► P: CONTEXT.md style domain docs (mattpocock)
C: Measured Change ─────► P: profile before optimizing (perf skill)
C: Threat Modeling ─────► P: STRIDE over attack paths (security skill)
C: Proven Rollback ─────► P: rollback tested before go-live (release skill)
S: uesf-ar-solution-architecture · uesf-ra-repository-analysis
S: uesf-se-security-audit · uesf-pf-performance-optimization
S: uesf-ax-accessibility-audit · uesf-do-documentation
S: uesf-re-release-engineering · uesf-de-devops-automation
S: uesf-da-data-modeling · uesf-rs-research-synthesis
S: uesf-ce-certification-audit · uesf-gv-project-governance
S: uesf-le-continuous-learning
```

## Level 3 — AI patterns

```
C: Groundedness ────────► P: retrieval measured · answers cite chunks (rag skill)
C: Eval-Driven Change ──► P: frozen sets · baselines · regression gates (eval skill)
C: Context Isolation ───► P: fresh subagents · artifact hand-offs (superpowers SDD)
C: Capability Interfaces ► P: provider-agnostic adapters (model-integration skill)
C: Degrees of Freedom ──► P: tight for fragile ops · loose for judgment (anthropic)
S: uesf-pe-prompt-engineering · uesf-ai-agent-design
S: uesf-ai-evaluation · uesf-ai-rag-systems · uesf-ai-model-integration
```

## Level 4 — UX/UI patterns

```
C: Design Intent ───────► P: token-based systems (MiniMax) · state coverage
C: Taste As Data ───────► P: mistake checklists (emilkowalski) · animation rules
C: Runtime Truth ───────► P: render, don't just compile (MengTo demos)
S: uesf-ux-ux-audit · uesf-ui-ui-implementation
```

## Level 5 — Meta patterns (the self-improvement loop)

```
P: Analyze (repository-analyzer) ─► P: Synthesize (generator·merger·optimizer)
P: Validate (validator·reviewer) ─► P: Measure (benchmarker)
P: Govern (version-manager·dependency-resolver) ─► P: Prove (certification-engine)
S: all thirteen uesf-mk-* skills
```

## Graph queries (how to use this)

- **"What protects a change from regressing?"** → testing (mutation) → debugging
  (regression test) → review (fix loop) → certification (revocation).
- **"How do I add a capability the framework lacks?"** →
  continuous-learning (intake) → repository-analyzer (study) →
  skill-generator (build) → test-generator → validator → reviewer →
  certification-engine.
- **"How do I keep quality from decaying?"** → optimizer (benchmarked deltas) →
  refactorer (equivalence) → merger (anti-duplication) → version-manager (lifecycle).

The graph is enforced in code by the validator's dependency rules; the semantic
edges above are maintained by `uesf-mk-skill-doc-generator` during doc regeneration.
