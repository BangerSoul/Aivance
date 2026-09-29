---
name: motion-design
description: Build animations with deliberate decisions in the order that determines whether they feel right — should it animate at all, what purpose, which tool, which properties, which curve and duration, how it interrupts, how it exits. Also serves as a reverse-lookup vocabulary for naming motion effects. Use when asked to animate something, add motion, make a component feel alive, build a transition, or when the user asks "what's it called when…". Inherited from emilkowalski/skills (animate, animation-vocabulary, apple-design).
---

# Motion Design

A construction skill: turn a request for motion into an implementation that would
survive strict review. **Never present motion options as a menu — make the call, state
the reasoning in one line, write the code.**

## Two Failure Modes (the first is worse)

1. **Animating something that shouldn't animate.** The gate below exists to produce
   zero lines of code sometimes. That's a success, not a dodge.
2. **Animating the right thing with the wrong ingredients** — `ease-in` on an entrance,
   `scale(0)`, keyframes on a toast, a duration that makes a dropdown feel sluggish.

## Hard Rules

1. **Run the sequence in order.** Steps 1 and 2 gate everything.
2. **No approximated values.** Every curve, duration, and spring config comes from the
   tables below. Never invent `cubic-bezier(0.4, 0, 0.2, 1)` because it looks familiar.
3. **Extend the codebase's tokens, don't fork them.** If `--ease-out` or a duration
   scale already exists, use it. Adding a parallel system is a defect.
4. **Reduced motion and hover gating ship with the animation**, not as a follow-up.
5. **Cheapest tool that works.** Don't install a motion library for a fade.

## The Build Sequence

### 1. Should this animate at all?

| Frequency | Decision |
| --- | --- |
| 100+ times/day (keyboard shortcuts, command palette toggle) | **No animation. Ever.** Stop here. |
| Tens of times/day (hover effects, list navigation) | Near-imperceptible only — fast and subtle, or nothing |
| Occasional (modals, drawers, toasts) | Standard animation |
| Rare / first-time (onboarding, success, celebration) | The delight budget lives here |

**Keyboard-initiated actions are a disqualifier, not a judgment call.** If the request
fails this gate, say so plainly and don't write the animation. Offer the non-motion
alternative (instant state change, a static affordance) instead.

### 2. What is the purpose?

Name it in one of these words before continuing:
- **Feedback** — confirming the interface heard the user
- **Spatial consistency** — showing where something came from or went
- **State indication** — making a state change legible
- **Preventing a jarring change** — bridging content that would otherwise teleport
- **Explanation** — demonstrating how something works (marketing/onboarding only)
- **Delight** — allowed *only* at the rare/first-time tier

Can't name it? Don't build it. "It looks cool" on a frequently-seen element is a reason
to stop.

### 3. Which tool?

Cheapest tool that works, in this order: CSS transitions → CSS animations → a small
library (only if the interaction genuinely needs it, e.g. complex springs or shared
element transitions). Don't install a motion library for a fade.

### 4. Which properties?

Animate only transform and opacity. **Animating width/height/top/left causes layout
thrash** — it repaints and reflows every frame. If you need layout animation, use
transform-based techniques (scale, translate) or FLIP.

### 5. Which curve and duration?

**Entrances:** fade + rise (translateY), `ease-out`, duration 150–250ms. Never ease-in
an entrance — it drags at the start.
**Exits:** slightly faster than entrances, `ease-in` is acceptable for exits (things
leaving should accelerate), 100–150ms.
**Layout/shared elements:** springs or `cubic-bezier(0.32, 0.72, 0, 1)` — a "good" curve
that stays responsive; duration scales with distance.
**Overlay/backdrop:** 300–400ms, `ease-out`.
**Micro-interactions (hover/active):** 100–150ms. Hover in, instant-ish out.

**Stagger:** 30–80ms between items. Beyond ~100ms feels like a queue.

### 6. How does it interrupt and exit?

- **Interruptible:** a running animation must be cancelable and restartable. Springs
  (inertia-aware) handle interruption best.
- **Exit animations:** things leaving are usually best instant or faster than entry —
  users want to get on with it.
- **Reduced motion:** respect the platform's reduced-motion setting: replace motion
  with opacity-only or an instant state change.

## Reverse-Lookup Vocabulary (naming motion)

When the user describes an effect without its name, map the sensation to the exact term:

| Vague description | Exact term |
| --- | --- |
| "the bouncy thing when a popover opens" | **Pop in** — scales up from 0.9 with an overshoot spring |
| "the iOS rubber-band scroll" | **Rubber-banding** — scroll resistance at the edge with elastic return |
| "grows out of the button you clicked" | **Origin-aware animation** — animates from its trigger's position |
| "slides off the side" | **Slide transition** — translateX exit |
| "one image turns into another" | **Crossfade** (or **shared element transition** if positioned) |
| "draws itself in" | **Mask/stroke reveal** — clip-path or stroke-dashoffset |
| "items appear one after another" | **Stagger** — cascade with small delays |
| "feels springy" | **Spring animation** — mass/velocity/damping-based motion |
| "the thing keeps going after I let go" | **Momentum/inertia** — velocity-driven continuation |

Stay within the vocabulary; if a term genuinely isn't here, say so rather than inventing
one. Lead with the term; expand only if asked.
