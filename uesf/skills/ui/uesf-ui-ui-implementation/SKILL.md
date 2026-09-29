---
id: uesf-ui-ui-implementation
name: UI Implementation
version: 1.0.0
category: ui
kind: ui
purpose: Build interfaces that match the design intent — tokens, states, motion, and accessibility — verified in the real runtime.
description: |
  Use when implementing or fixing UI: components, screens, animations, responsive
  layouts, or design-system work. Produces UI that follows the design system,
  covers all states, and is verified in the runtime (rendered, not just compiled).
  Applies to web, native, and cross-platform.
triggers:
  - condition: "UI implementation or UI fixes are being done"
  - condition: "Design-system tokens or components are created or changed"
  - example_prompt: "Implement the settings screen per the design spec with all states"
inputs:
  - "Design spec or intent (tokens, layout, states, motion)"
  - "The component/screen to build and its runtime"
outputs:
  - "UI implementation matching the design intent"
  - "All states and edge cases covered (empty, loading, error, disabled)"
  - "Runtime verification (rendered + interaction-tested)"
dependencies:
  - "uesf-co-implementation"
  - "uesf-ax-accessibility-audit"
context_requirements:
  - "Access to the design tokens/spec and the runtime environment"
  - "Ability to run the UI (dev server, emulator, browser)"
quality_gates:
  - "Design tokens used — no hardcoded colors/spacing (unless intentionally local)"
  - "All states covered: empty, loading, error, disabled, reduced-motion"
  - "Verified in the runtime, not just compiled"
validation:
  - unit
  - integration
  - accessibility
  - performance
rollback: "UI changes are small scoped commits; revert individually. Design-token refactors are separate from component work."
failure_recovery: "A rendered-state mismatch (layout, contrast, interaction) is fixed against the spec, re-verified in the runtime; never shipped from a static screenshot."
acceptance_criteria:
  - "UI matches the design intent (layout, spacing, type, color from tokens)"
  - "All states implemented and rendered"
  - "Keyboard/AT flows verified (a11y gate passed)"
  - "No regressions in existing UI tests"
automation_hooks:
  - "Visual/component tests in CI"
  - "A11y scanner and reduced-motion checks in the pre-merge gate"
mcp_tools:
  - "none"
cost:
  input_tokens: "~12k"
  output_tokens: "~7k"
  runtime_minutes: "30–120"
complexity: 4
maintainability_score: 4
scalability_score: 4
production_readiness: 4
related_skills:
  - "uesf-ux-ux-audit"
  - "uesf-ax-accessibility-audit"
documentation: "docs/skill-spec.md"
---

# UI Implementation

## Overview
Agents ship interfaces that compile but don't render — wrong spacing, missing states,
animations that feel off. The source ecosystem's lesson is that great UI is
*encoded judgment*: tokens, states, motion rules, and verification in the real runtime
(emilkowalski's animation rules and "agents don't have taste", MengTo's visual
feedback loops, MiniMax's token-based design systems). This skill makes those
judgments explicit and verifiable.

## Execution Workflow
1. **Load the design intent** — Design spec, tokens, and motion rules. If tokens
   don't exist, create them first (as their own task) — UI without tokens is
   drift-by-default.
2. **Plan the component surface** — List every state: default, hover/focus/active,
   empty, loading, error, disabled, reduced-motion. Missing states are the most
   common UI defect.
3. **Implement from tokens** — Build using the design system: colors, spacing, type,
   radii, shadows from tokens — never hardcoded (unless intentionally local with a
   reason).
4. **Motion with rules** — Motion uses correct curves and durations per the design
   system (e.g., ease-out for enter, ease-in for exit), respects `prefers-reduced-
   motion`, and never animates purely decorative elements into annoyance.
5. **Verify in the runtime** — Render the component in the real runtime: layout at
   target breakpoints, all states toggled, keyboard navigation, and AT semantics.
   A rendered check beats a compiled one.
6. **Cover edge cases** — Long text, tiny viewports, RTL where supported, offline,
   and data-missing scenarios.
7. **Ship through review** — `uesf-co-review` with the a11y lens; wire visual tests
   into CI.

## Quality Gates
- Tokens used; no unexplained hardcoded values.
- All states implemented and rendered.
- Verified in the runtime (rendered + interaction), not just compiled.
- Reduced-motion respected; motion follows the design rules.

## Validation
- **Unit**: component tests per state.
- **Integration**: full-screen render at target breakpoints.
- **Accessibility**: keyboard + AT flows via `uesf-ax-accessibility-audit`.
- **Performance**: no layout thrash or render regressions on hot paths.

## Rollback
UI changes are small scoped commits. Token refactors are separate commits from
component work so each is independently revertible.

## Failure Recovery
A rendered mismatch is fixed against the spec and re-verified in the runtime — never
accepted from a static screenshot or an impression. When the spec and reality conflict,
the discrepancy goes back to the spec owner as a finding, not silently resolved.

## Acceptance Criteria
- [ ] UI matches the design intent using tokens.
- [ ] All states implemented and rendered.
- [ ] Keyboard/AT flows verified.
- [ ] No regressions in existing UI tests.

## Examples
### Example 1 — Settings screen
Spec with tokens loaded. Implementation covers: default, focus, disabled, empty (no
alerts), loading, error states; the theme picker works by keyboard; motion (sheet
entrance) uses the system curve with reduced-motion fallback. Rendered at 360px and
1280px; a11y scanner green; visual tests added. One finding: the spec's error state
had no contrast-AA pairing — escalated to the spec owner.

## Anti-patterns
- **Compile-and-done**: shipping UI never rendered — the runtime check is mandatory.
- **Hardcoded everything**: hex colors and magic spacing — tokens or explicit local
  decisions.
- **State amnesia**: only the happy path implemented — all states are planned.
- **Animation for its own sake**: motion with no rules or reduced-motion support.

## Testing Strategy
Validated with UI fixtures containing planted state omissions and token violations;
scored on detection. See `docs/testing-strategy.md`.

## Future Extensions
- Visual regression baselines per component.
- Design-token → code contract generation.
