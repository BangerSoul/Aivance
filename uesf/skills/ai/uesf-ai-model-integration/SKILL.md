---
id: uesf-ai-model-integration
name: Model-Agnostic Integration
version: 1.0.0
category: ai
kind: ai
purpose: Integrate AI models into products behind a stable interface — swappable providers, measured behavior, and cost control.
description: |
  Use when wiring an LLM/multimodal provider into an application, or when a product
  must not be locked to one model vendor. Produces a provider-agnostic integration
  with a defined interface, provider adapters, behavior contracts, and cost
  instrumentation. The product code never talks to a vendor SDK directly.
triggers:
  - condition: "A model provider is being integrated or swapped"
  - condition: "A feature must work across multiple providers or models"
  - example_prompt: "Integrate chat completions behind an interface that works with Claude, GPT, and Gemini"
inputs:
  - "The feature requirements (capabilities, latency, cost budget)"
  - "Target providers/models and their SDKs/APIs"
outputs:
  - "Provider-agnostic interface (capability surface, not SDK surface)"
  - "Provider adapters with behavior contracts"
  - "Cost/latency instrumentation and evaluation wiring"
dependencies:
  - "uesf-ai-evaluation"
  - "uesf-pe-prompt-engineering"
context_requirements:
  - "Access to provider credentials/endpoints for at least one adapter"
  - "A feature whose behavior can be specified and evaluated"
quality_gates:
  - "Product code depends on the interface, never a vendor SDK"
  - "Every adapter passes the behavior contract tests"
  - "Cost and latency are instrumented per request"
validation:
  - unit
  - integration
  - performance
  - security
rollback: "Providers are behind an interface: switching back to a previous provider is a config/flag change, not a code rewrite."
failure_recovery: "A provider outage or degradation is handled by the integration's fallback/failover path (defined per capability), then post-incident review."
acceptance_criteria:
  - "Interface defined by capabilities, not SDK methods"
  - "Adapters pass contract tests; at least one adapter verified end-to-end"
  - "Cost/latency instrumentation live"
  - "Fallback/failover behavior defined and tested"
automation_hooks:
  - "Contract tests in CI for every adapter"
  - "Cost/latency telemetry wired to dashboards"
mcp_tools:
  - "none"
cost:
  input_tokens: "~15k"
  output_tokens: "~6k"
  runtime_minutes: "60–180"
complexity: 5
maintainability_score: 4
scalability_score: 5
production_readiness: 4
related_skills:
  - "uesf-ai-agent-design"
  - "uesf-ai-rag-systems"
documentation: "docs/skill-spec.md"
---

# Model-Agnostic Integration

## Overview
Vendor lock-in is the quiet killer of AI products: the SDK's shapes leak into every
call site, and swapping providers becomes a rewrite. This skill isolates the provider
behind a capability-defined interface, wraps adapters with behavior contracts, and
instruments cost and latency — so the product owns its AI dependency instead of the
reverse. It is the integration discipline behind UESF's own model-agnosticism.

## Execution Workflow
1. **Specify capabilities** — Define what the product needs from a model: completion,
   chat with tools, structured output, embeddings, streaming, latency/quality floors.
   The interface is capability-shaped, never SDK-shaped.
2. **Define the interface** — A stable interface (types + contract) that expresses the
   capabilities with provider-neutral semantics (e.g., normalized messages, tool
   schemas, structured-output requests).
3. **Implement adapters** — One adapter per provider implementing the contract,
   translating normalized ↔ provider-native. Each adapter carries its SDK version
   pinned and its model/compatibility notes.
4. **Behavior contracts** — Contract tests each adapter must pass: semantics,
   structured output validity, tool-call correctness, error normalization, timeout
   behavior. An adapter that fails contracts is not integrated.
5. **Instrument cost and latency** — Per-request token counts, cost, and latency
   captured through the interface, exposed to telemetry.
6. **Design failover** — Fallback policy per capability (secondary provider, cached
   responses, graceful degradation) with a tested trigger path.
7. **Evaluate** — Run the feature's eval set (`uesf-ai-evaluation`) through the
   interface across providers; the eval is provider-agnostic by construction.

## Quality Gates
- Product code depends on the interface, never a vendor SDK.
- Every adapter passes the contract tests.
- Cost and latency instrumented per request.
- Fallback behavior defined and tested.

## Validation
- **Unit**: contract tests per adapter.
- **Integration**: end-to-end feature through the interface.
- **Performance**: latency/cost per provider measured against floors.
- **Security**: credential handling, prompt-injection surface, and data-boundary
  review of the adapters.

## Rollback
Providers sit behind the interface — switching back is a flag/config change with
zero product-code changes. This is the integration's core safety property.

## Failure Recovery
Provider degradation follows the designed failover path, then post-incident review
feeds the eval set with the failure scenario. A single provider's outage never
becomes a product outage by design.

## Acceptance Criteria
- [ ] Interface defined by capabilities.
- [ ] Adapters pass contract tests; at least one verified end-to-end.
- [ ] Cost/latency instrumentation live.
- [ ] Fallback/failover defined and tested.

## Examples
### Example 1 — Chat completions service
Capability interface: `complete(req: Message[] & ToolSchema, opts: {stream, structured})`.
Adapters for Claude, GPT, and Gemini each pass contract tests (streaming works,
structured output parses, errors normalize). Eval set (30 cases) runs through the
interface: quality parity confirmed with cost noted (GPT cheapest on this workload →
default; Claude failover). Outage drill: kill the default provider, fallback engages,
p95 +400ms, no downtime.

## Anti-patterns
- **SDK-shaped interfaces**: `ChatCompletionRequest` leaking into product code —
  capability-shaped only.
- **Adapter theater**: adapters that compile but fail contract semantics.
- **No cost instrumentation**: discovering the bill after the fact.
- **Provider monoculture**: "we use one model, why abstract?" — the abstraction is
  what makes failover and negotiation possible.

## Testing Strategy
Validated with adapter fixtures containing contract violations (wrong semantics,
broken structured output) scored on detection. See `docs/testing-strategy.md`.

## Future Extensions
- Cost-aware routing (per-request provider selection by capability/cost).
- Contract-test generators from the interface definition.
