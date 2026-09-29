---
name: distinctive-design
description: Produce distinctive, intentional visual design that does not read as templated defaults — with a compact token system, a signature element, and restraint. Use when building new UI or reshaping an existing one, when the brief asks for "modern", "premium", or "polished" without specifics, or when a design direction is needed. Inherited from anthropics/skills (frontend-design) and emilkowalski/skills (emil-design-eng).
---

# Distinctive Design

Approach this as the design lead at a studio known for giving every client a visual
identity that could not be mistaken for anyone else's. Make deliberate, opinionated
choices about palette, typography, and layout specific to this brief — and take one real
aesthetic risk you can justify.

## The Three Defaults to Avoid

AI-generated design right now clusters around three looks. All are legitimate for some
briefs, but they are defaults rather than choices:

1. Warm cream background (≈#F4F1EA) + high-contrast serif display + terracotta accent
2. Near-black background + single bright acid-green or vermilion accent
3. Broadsheet layout: hairline rules, zero border-radius, dense newspaper columns

Where the brief pins a direction, follow it exactly — the brief's words win. Where it
leaves an axis free, don't spend that freedom on one of these defaults.

## Ground It in the Subject

Before designing, pin the subject: name one concrete thing (the product, its audience,
the page's single job) and state it. The subject's own world — its materials,
instruments, artifacts, vernacular — is where distinctive choices come from. Build with
the brief's real content throughout.

## Two-Pass Process

### Pass 1: Design plan (before any code)

Create a compact token system with four parts:

- **Color** — 4–6 named hex values. Deliberate palette, not the defaults above.
- **Type** — 2+ roles: a characterful display face used with restraint, a complementary
  body face, and a utility face for captions/data if needed. Deliberate pairings — not
  the same families you reach for on any project.
- **Layout** — a layout concept: one-sentence prose + ASCII wireframes to ideate and
  compare.
- **Signature** — the single unique element this page will be remembered by, embodying
  the brief.

### Pass 2: Review the plan against the brief

If any part reads like the generic default you'd produce for any similar page, revise
that part — say what you changed and why. Only after confirming relative uniqueness
should you write code, deriving every color and type decision from the plan.

## Principles

- **The hero is a thesis.** Open with the most characteristic thing in the subject's
  world: a headline, an image, a live demo, an interactive moment. A big number with a
  small label and a gradient accent is the template answer — only use it if it's truly
  best.
- **Structure is information.** Numbered markers (01/02/03), dividers, and labels should
  encode something true about the content — order only if it's actually a sequence.
- **Typography carries personality.** Make the type treatment itself memorable, not a
  neutral delivery vehicle.
- **Motion deliberately.** One orchestrated moment usually lands harder than scattered
  effects. Extra animation contributes to the "AI-generated" feeling.
- **Match complexity to vision.** Maximalist directions need elaborate execution;
  minimal directions need precision in spacing, type, and detail.
- **Spend boldness in one place.** Let the signature be the one memorable thing; keep
  everything around it quiet and disciplined. Cut any decoration that doesn't serve the
  brief. Chanel's advice: before leaving the house, remove one accessory.

## Writing UI Copy

- Words make it easier to understand and use — design material, not decoration.
- Name things by what people control and recognize, never how the system is built.
- **Active voice as default:** "Save changes", not "Submit". An action keeps the same
  name through the whole flow: the "Publish" button produces a "Published" toast.
- Errors don't apologize, and are never vague about what happened. An empty screen is
  an invitation to act.
- Be specific over clever. Sentence case, plain verbs, no filler.

## Quality Floor (build to it without announcing)

Responsive down to small screens, visible keyboard focus, reduced motion respected,
touch targets adequate, contrast accessible. Critique your own work as you build —
screenshots are worth a thousand tokens.
