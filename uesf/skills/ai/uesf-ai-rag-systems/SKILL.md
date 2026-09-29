---
id: uesf-ai-rag-systems
name: RAG Systems
version: 1.0.0
category: ai
kind: ai
purpose: Design, build, and evaluate retrieval-augmented generation systems with measured retrieval quality and grounded answers.
description: |
  Use when building or improving retrieval-augmented features: document Q&A, repository
  chat, enterprise search, or knowledge assistants. Produces a RAG design (indexing,
  retrieval, generation), a retrieval evaluation, and grounded-answer verification.
  Retrieval quality is measured; answers are required to be grounded.
triggers:
  - condition: "A feature needs to answer questions from a document/codebase corpus"
  - condition: "An existing RAG feature has weak answers and needs measurement"
  - example_prompt: "Design a RAG system for answering questions from our engineering docs"
inputs:
  - "The corpus, the question domain, and expected answer behavior"
  - "Access to the retrieval/generation stack or the freedom to choose"
outputs:
  - "RAG design: chunking, indexing, retrieval, generation, grounding"
  - "Retrieval evaluation (recall@k, hit rate) on a labeled query set"
  - "Groundedness check and failure-mode documentation"
dependencies:
  - "uesf-ai-evaluation"
  - "uesf-da-data-modeling"
context_requirements:
  - "Access to the corpus and the ability to index/query it"
  - "A labeled query set (or the ability to build one)"
quality_gates:
  - "Retrieval quality measured on a labeled query set (no retrieval without evaluation)"
  - "Answers must cite retrieved context; ungrounded answers are a defect"
  - "Chunking/indexing decisions recorded with evidence"
validation:
  - unit
  - integration
  - performance
  - security
rollback: "RAG components are versioned code/pipelines; revert the indexing or retrieval change independently of the generation layer."
failure_recovery: "Low retrieval quality is a measurement problem first: inspect recall@k per query class, fix indexing/retrieval, re-measure — never patch the prompt to hide retrieval gaps."
acceptance_criteria:
  - "Labeled query set with measured retrieval metrics"
  - "Groundedness check in place (citations traceable to retrieved chunks)"
  - "Chunking and index choices recorded with evidence"
  - "Known failure modes documented (e.g., multi-hop questions)"
automation_hooks:
  - "Retrieval eval run in CI on indexing changes"
  - "Groundedness spot-check job on production answers"
mcp_tools:
  - "none"
cost:
  input_tokens: "~15k"
  output_tokens: "~6k"
  runtime_minutes: "60–180"
complexity: 5
maintainability_score: 3
scalability_score: 4
production_readiness: 4
related_skills:
  - "uesf-ai-evaluation"
  - "uesf-ai-model-integration"
documentation: "docs/skill-spec.md"
---

# RAG Systems

## Overview
Most RAG failures are retrieval failures wearing a generation costume: the model
answers from memory because the right chunk wasn't retrieved. This skill makes
retrieval a measured quantity (recall@k, hit rate on a labeled set) and makes grounding
a hard requirement (answers cite retrieved context, or they are defective). It
generalizes the RAG patterns from the ecosystem (google's RAG enterprise recipes,
anthropics' claude-code-docs-rag) into a stack-agnostic process.

## Execution Workflow
1. **Define the corpus and questions** — The corpus, the question domain, and the
   expected answer behavior. Build a labeled query set with the chunk(s) each query
   should retrieve and a model answer.
2. **Design indexing** — Choose chunking (by structure, semantic units, with overlap
   strategy), metadata (source, section, date), and the index (vector, keyword,
   hybrid). Record the choices and why — they are decisions, not defaults.
3. **Build retrieval** — Implement retrieval with the query path: embedding or
   keyword retrieval, optional reranking, and a k/score policy.
4. **Measure retrieval** — Run the labeled set: recall@k, hit rate, and per-class
   breakdowns (simple lookups vs. multi-hop vs. negations). This number is the
   system's health.
5. **Design generation + grounding** — The generator receives the retrieved context
   with source pointers and is instructed to answer only from it. Groundedness is
   enforced: answers cite chunks; ungrounded answers are flagged.
6. **Evaluate end-to-end** — Grounded-answer quality on the labeled set (with the
   eval methodology from `uesf-ai-evaluation`), plus a hallucination check.
7. **Document failure modes** — Multi-hop questions, stale corpus, out-of-domain
   queries, chunk boundary issues — each with its mitigation or explicit acceptance.

## Quality Gates
- Retrieval measured on a labeled set — no retrieval without evaluation.
- Groundedness enforced; ungrounded answers are defects.
- Chunking/index decisions recorded with evidence.
- Eval set frozen; iterations measured.

## Validation
- **Unit**: per-query retrieval checks on the labeled set.
- **Integration**: end-to-end answer quality with grounding.
- **Performance**: index latency and query cost measured.
- **Security**: injection and data-leak review of the retrieval boundary.

## Rollback
RAG components are independently versioned: revert the indexing, retrieval, or
generation change on its own. Because retrieval is measured, a rollback decision is
objective.

## Failure Recovery
Low retrieval quality → measurement first: break recall@k down by query class, fix the
weakest class (chunking, metadata, hybrid search), re-measure. Patching the prompt to
"answer anyway" hides the retrieval gap and is forbidden — that's how RAG turns into
fancy autocomplete.

## Acceptance Criteria
- [ ] Labeled query set with measured retrieval metrics.
- [ ] Groundedness check in place; citations traceable.
- [ ] Chunking/index choices recorded with evidence.
- [ ] Failure modes documented.

## Examples
### Example 1 — Engineering-docs assistant
Corpus: 2k markdown docs. Labeled set: 40 queries across lookup/multi-hop/negation.
Hybrid retrieval (BM25 + embeddings) with reranking: hit@5 = 0.88. Generation layer
instructed to cite doc sections; groundedness check flags 3 ungrounded answers from
stale docs (fixed by re-indexing + freshness metadata). Eval frozen in CI; a chunking
change that drops hit@5 below 0.85 fails the build.

## Anti-patterns
- **Prompt-masking retrieval gaps**: letting the model answer from memory when
  retrieval misses — grounding is enforced.
- **Chunking by default**: arbitrary fixed-size chunks with no structural reasoning —
  decisions are recorded with evidence.
- **No labeled set**: "retrieval seems good" — measurement is the gate.
- **Stale corpus**: answering from an index that drifted from the source — freshness
  metadata + re-indexing.

## Testing Strategy
Validated on fixture corpora with planted retrieval regressions (chunk boundary shifts,
index staleness) scored on detection. See `docs/testing-strategy.md`.

## Future Extensions
- Query-class routing (lookup vs. synthesis vs. multi-hop).
- Continuous re-indexing pipelines with freshness gates.
