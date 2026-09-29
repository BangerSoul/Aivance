---
name: web-visual-effects
description: Build cinematic web UI moments — scroll-driven storytelling, staggered text reveals, WebGL/Three.js scenes, shader effects, and premium micro-interactions — with progressive enhancement, performance discipline, and reduced-motion support. Use when a page needs visual drama: scroll worlds, hero effects, animated landing pages, 3D moments, or "make it feel alive". Inherited from MengTo/Skills (web-design skills) and MiniMax-AI/skills (shader-dev).
---

# Web Visual Effects

Build the cinematic moments that make a web page memorable — without turning it into a
slideshow, a performance disaster, or an accessibility hazard. The bar: each effect
earns its place (see motion-design for the *should it animate at all* gate), enhances
the message, and degrades gracefully.

## 1. Decide the Effect to Match the Message

| Story/message | Effect family |
|---|---|
| Long-form narrative, case study, product journey | **Scroll-driven storytelling** (sections scrub through a visual world) |
| Headlines, quotes, manifesto copy | **Staggered word/line reveals** (fade + rise on viewport entry) |
| Product hero, 3D object, "real thing" feel | **WebGL/Three.js scene** (only when real 3D depth beats CSS tricks) |
| Ambient background atmosphere | **Background effects** (particle fields, gradients, Vanta-style WebGL) |
| Editorial/data storytelling | **Scrub-scrubbed sequences** (video or DOM tied to scroll progress) |
| Premium interactivity | **Cursor/hover micro-effects** (ripples, glows, reveals) |

**Never** combine more than one or two effect families per page — one signature moment
plus quiet surroundings (same discipline as distinctive-design).

## 2. The Scroll-Driven Pattern

Scroll storytelling has three renderer options, cheapest first:

1. **Semantic HTML/SVG + CSS** — data, typography, and diagrams revealed as scroll
   advances. Most accessible, fastest, always works.
2. **Scrubbed video/sequence** — a video or image sequence driven by scroll position.
   Use only with a real reason (photographic or captured motion).
3. **Real-time Three.js world** — a 3D scene that reacts to scroll. Highest cost; only
   for scenes that genuinely need 3D.

Common requirements across all three:

- **Native scroll, not hijacked scroll.** Don't capture wheel/touch events to fake
  scroll — use IntersectionObserver, `position: sticky`, or scroll-linked transforms.
- **Respect reduced motion:** `prefers-reduced-motion: reduce` → show the final state
  or a static summary instead of the scrub sequence.
- **Content accessibility:** the same information must be readable without JavaScript
  or motion — a scroll world is an enhancement over real content, not a replacement
  for it.

## 3. Staggered Text Reveals

The workhorse of premium-feeling editorial UI:

- **Word-by-word or line-by-line**, never character-by-character (typing feels cheap
  unless the subject is typewriting).
- **Fade + rise** (opacity 0→1, translateY 8–16px), 30–80ms stagger (see
  motion-design), ease-out, ~400–600ms total.
- Trigger on viewport entry via IntersectionObserver; **run once**, don't re-trigger.
- Preserve semantic inline elements: links, emphasis, and line wrapping must survive
  the reveal (don't break words or links).
- Reveal content **above the fold immediately** (no lazy-loading the hero).

## 4. WebGL / Three.js Discipline

- **Cheapest renderer that achieves the look:** CSS 3D transforms for simple
  parallax/cubes; Canvas 2D for particles; WebGL/Three.js only for real depth, complex
  geometry, or custom shaders.
- **Performance budget:** requestAnimationFrame, pause when off-screen
  (IntersectionObserver + visibilitychange), cap device pixel ratio at 2, dispose
  geometries/materials on cleanup.
- **Fallbacks:** no WebGL → static image or CSS approximation; no JS → content intact.
- **Lighting:** a few deliberate lights (key + fill + ambient), not a dozen.
- Small helper libs (Vanta.js, Shaders, Unicorn Studio) are fine when they fit the
  need and ship a fallback — don't build custom shaders when a library does it.

## 5. Shader Techniques Cheat-Sheet

For custom GLSL work (inherited from MiniMax shader-dev):

- **Start from noise** (value → Perlin/Simplex → domain-warped/fractal) — most organic
  effects are layered noise.
- **SDFs for shapes** — ray-marched or 2D signed distance fields for smooth geometry.
- **Post-processing last** — apply bloom/vignette/grain after the scene, cheaply.
- **Performance:** fragment-shader-heavy effects need LOD, `precision mediump` where
  acceptable, and avoidance of per-pixel branching.
- Ship a **static fallback** for low-end devices — shader code is the first thing
  mobile GPUs choke on.

## 6. Quality Gate

Before claiming done:

- [ ] Page works with JS disabled (content readable, no blank canvas)
- [ ] `prefers-reduced-motion: reduce` respected (static final state)
- [ ] Effect visible on mobile + desktop; touch-friendly (no hover-only interactions)
- [ ] Performance: no layout thrash (transform/opacity only), pauses off-screen
- [ ] Only 1–2 effect families; one signature moment
- [ ] Semantic content preserved (links, headings, text selectable)
