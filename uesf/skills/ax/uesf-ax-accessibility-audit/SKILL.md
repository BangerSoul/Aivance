---
id: uesf-ax-accessibility-audit
name: Accessibility Audit
version: 1.0.0
category: ax
kind: engineering
purpose: Verify and fix interface accessibility against WCAG — automated checks plus human-equivalent reasoning — with regression protection.
description: |
  Use when building or changing any user interface, or before a release gate.
  Produces a conformance assessment against WCAG levels, prioritized fixes, and
  regression tests/guards. Goes beyond automated scanners with manual-equivalent
  reasoning (keyboard, screen reader, color contrast, motion).
triggers:
  - condition: "UI work, a UI change, or a design system component is being built or modified"
  - condition: "A release or certification gate requires accessibility conformance"
  - example_prompt: "Audit the new settings screen for WCAG AA conformance"
inputs:
  - "The UI (code, screens, components) and its interaction flows"
  - "Conformance target (WCAG A/AA/AAA, platform guidelines)"
outputs:
  - "Conformance assessment with evidence per checkpoint"
  - "Prioritized findings (blocker/major/minor)"
  - "Fixes with regression guards"
dependencies:
  - "uesf-co-testing"
  - "uesf-co-review"
context_requirements:
  - "Ability to run the UI or its tests (unit + where available automated a11y scans)"
  - "Access to platform accessibility guidelines"
quality_gates:
  - "Automated scan run and recorded; manual-equivalent pass performed for keyboard and screen reader"
  - "Every WCAG checkpoint relevant to the UI is assessed, not assumed"
  - "Fixes verified by tests that fail without them"
validation:
  - unit
  - integration
  - accessibility
  - regression
rollback: "Fixes are small scoped commits, individually revertible; accessibility changes are behavior-neutral for non-AT users."
failure_recovery: "Findings that block interaction (unusable keyboard flow, no focus) halt release; fix first, re-audit the delta."
acceptance_criteria:
  - "Automated scan green and manual-equivalent passes recorded"
  - "Relevant WCAG checkpoints assessed with evidence"
  - "Blockers and majors fixed with regression tests"
  - "Residual non-conformance documented with an accepted risk decision"
automation_hooks:
  - "Axe/accessibility scanner in CI for every UI change"
  - "Contrast and focus-order checks in the pre-merge gate"
mcp_tools:
  - "none"
cost:
  input_tokens: "~10k"
  output_tokens: "~4k"
  runtime_minutes: "20–60"
complexity: 3
maintainability_score: 4
scalability_score: 4
production_readiness: 5
related_skills:
  - "uesf-ui-ui-implementation"
  - "uesf-ux-ux-audit"
documentation: "docs/skill-spec.md"
---

# Accessibility Audit

## Overview
Automated scanners catch maybe a third of accessibility issues; the rest require
reasoning like a user of assistive technology. This skill pairs automated scans with
manual-equivalent passes (keyboard-only navigation, screen-reader semantics, contrast,
motion) and requires regression tests so fixes persist. It synthesizes the a11y
discipline across the source ecosystem's UI skills (emilkowalski, MengTo) into a
verifiable process.

## Execution Workflow
1. **Scope and target** — Identify the UI surface and its interaction flows; state the
   conformance target (WCAG AA is the default).
2. **Automated pass** — Run the accessibility scanner on the UI; record results.
   Fix nothing yet — automate first, reason second.
3. **Manual-equivalent pass** — Walk the flows as a user of assistive technology:
   - **Keyboard**: every action reachable and operable without a pointer; visible focus;
     no keyboard traps.
   - **Screen reader**: correct semantics (landmarks, headings, labels), meaningful
     order, announced state changes.
   - **Contrast & readability**: text and UI components meet contrast ratios; no
     information conveyed by color alone.
   - **Motion & timing**: no content flashing beyond safe limits; reduce-motion
     respected; no interaction timeouts without warning.
4. **Assess checkpoints** — Map findings to WCAG checkpoints with evidence
   (component, criterion, pass/fail).
5. **Prioritize and fix** — Blockers (unusable flows) and majors first, via
   `uesf-co-implementation`, each with a regression test that fails without the fix.
6. **Verify and guard** — Re-run scans; re-walk keyboard and screen-reader flows; wire
   the scanner into CI for future changes.

## Quality Gates
- Automated scan run and recorded; manual-equivalent passes performed for keyboard and
  screen reader.
- Relevant WCAG checkpoints assessed with evidence, not assumed.
- Fixes verified by tests that fail without them.

## Validation
- **Unit**: component-level tests (labels, roles, contrast) per fixed issue.
- **Integration**: keyboard and screen-reader flow passes end-to-end.
- **Accessibility**: scanner green; contrast checked with real color pairs.
- **Regression**: full suite green.

## Rollback
Fixes are small scoped commits. Accessibility changes are behavior-neutral for
non-assistive-technology users, so rollback is low-risk and individually revertible.

## Failure Recovery
A blocker finding (keyboard trap, unlabeled control, missing focus) halts the change.
Fix it first with a regression test, re-audit the delta, then resume. Non-conformance
that cannot be resolved is documented as residual risk with a governance sign-off —
never silently shipped.

## Acceptance Criteria
- [ ] Automated scan green and manual-equivalent passes recorded.
- [ ] Relevant WCAG checkpoints assessed with evidence.
- [ ] Blockers and majors fixed with regression tests.
- [ ] Residual non-conformance documented with an accepted decision.

## Examples
### Example 1 — Settings screen audit
Automated scan: 2 contrast failures, 1 missing label. Manual pass: the theme picker is
unreachable by keyboard (blocker — no tab order), the sync status change is not announced
(major). Fixes: tab order + regression test, aria-live region + test, contrast fixes +
test. CI scanner wired in. Residual: third-party chart colors out of AA range →
documented, component scheduled.

## Anti-patterns
- **Scan-and-done**: running the scanner and declaring conformance — the manual pass is
  mandatory.
- **Contrast by calculator only**: passing ratio math while adjacent-text grouping still
  fails real readability.
- **Aria soup**: adding ARIA attributes to mask bad semantics instead of fixing the
  underlying element.
- **Fix-without-test**: accessibility fixes without regression tests silently regress.

## Testing Strategy
Validated with planted accessibility defects (missing labels, keyboard traps, contrast
failures) scored on detection and fix quality. See `docs/testing-strategy.md`.

## Future Extensions
- Screen-reader transcript fixtures for automated flow assertions.
- Design-system-level a11y contracts (tokens guarantee contrast).
