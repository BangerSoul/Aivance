# Skills Pack — Portable Master Export

Complete, portable copy of every skill available in the Aivance workspace, for
training or loading into another model/agent. Generated 2026-08-08.

**41 SKILL.md files (40 unique skills)** across 5 tiers — `skill-creator-guide`
(#24) duplicates the community `skill-creator` (#16), kept in `original/` only
for convenience. Each skill is a directory containing `SKILL.md` (frontmatter
`name` + `description` trigger metadata, then the body).

| # | Skill | Tier | Source | Purpose |
|---|-------|------|--------|---------|
| 1 | `uesf-co-planning` | UESF core | uesf/core (Ultimate Engineering Skills Framework, MIT) | Decompose work into verified, executable plans |
| 2 | `uesf-co-implementation` | UESF core | uesf/core | Implement planned tasks test-first, small increments |
| 3 | `uesf-co-testing` | UESF core | uesf/core | Design + run verification that proves behavior |
| 4 | `uesf-co-debugging` | UESF core | uesf/core | Root-cause via reproduction + hypothesis, never guessing |
| 5 | `uesf-co-review` | UESF core | uesf/core | Review for correctness/design/risk with bounded fix loops |
| 6 | `uesf-co-refactoring` | UESF core | uesf/core | Restructure code behavior-neutrally, step by step |
| 7 | `autofix` | Community | coderabbitai/skills | Apply CodeRabbit PR review feedback with per-change approval |
| 8 | `code-review` | Community | coderabbitai/skills | AI-powered code review (CodeRabbit) |
| 9 | `component-refactoring` | Community | PageAI-Pro/ralph-loop | Refactor high-complexity React components |
| 10 | `e2e-tester` | Community | PageAI-Pro/ralph-loop | Playwright E2E testing patterns |
| 11 | `frontend-code-review` | Community | PageAI-Pro/ralph-loop | Checklist-based review of tsx/ts/js |
| 12 | `frontend-testing` | Community | PageAI-Pro/ralph-loop | Vitest + React Testing Library tests |
| 13 | `mysql` | Community | PageAI-Pro/ralph-loop | MySQL/InnoDB schema, indexing, query tuning |
| 14 | `postgres` | Community | PageAI-Pro/ralph-loop | PostgreSQL best practices, optimization |
| 15 | `prd-creator` | Community | PageAI-Pro/ralph-loop | PRD creation → implementation task lists (JSON) |
| 16 | `skill-creator` | Community | PageAI-Pro/ralph-loop | Guide for creating/updating skills |
| 17 | `vercel-react-best-practices` | Community | PageAI-Pro/ralph-loop | React/Next.js performance (Vercel Engineering) |
| 18 | `vitest-best-practices` | Community | PageAI-Pro/ralph-loop | Vitest patterns: AAA, parametrized, mocking |
| 19 | `web-design-guidelines` | Community | PageAI-Pro/ralph-loop | UI review vs Web Interface Guidelines + accessibility |
| 20 | `gsd-orchestrator` | Community | gsd-build/gsd-2 | GSD headless-mode autonomous build (needs `gsd` CLI) |
| 21 | `public-apis-reference` | Original | distilled from public-apis/public-apis | Curated career-toolkit API catalog (AI/Jobs/Email/Enrichment/NLP/Docs) |
| 22 | `verify-before-claim` | Original | authored (Aivance discipline) | Prove every URL/size/pin/limit before claiming it |
| 23 | `on-device-llm-integration` | Original | authored (Gemma/MediaPipe work) | Keyless offline LLM: SDK contract, engine, downloader, capability gate, compact model, fallback |
| 24 | `skill-creator-guide` | Original | see note | — |
| 25 | `provider-sdk-extension` | Original | authored (Aivance provider platform) | 8-step checklist for adding AI/job/enrichment providers |
| 26 | `coding-discipline` | Learned | inherited from multica-ai/andrej-karpathy-skills + mattpocock/skills (tdd) | Think-before-code, simplicity, surgical changes, goal-driven execution, red-green loop |
| 27 | `plan-driven-implementation` | Learned | inherited from obra/superpowers (writing-plans, executing-plans) | Zero-context bite-sized implementation plans with verification gates |
| 28 | `root-cause-debugging` | Learned | inherited from obra/superpowers (systematic-debugging) | Four-phase debugging: root cause before any fix; iron law |
| 29 | `verification-before-completion` | Learned | inherited from obra/superpowers (verification-before-completion) | No completion claims without fresh verification evidence |
| 30 | `brainstorm-before-build` | Learned | inherited from obra/superpowers (brainstorming) + mattpocock (wait-what) | Design-before-implementation hard gate, clarifying questions, spec approval |
| 31 | `writing-for-agents` | Learned | inherited from mattpocock/skills (writing-for-agents, AGENT-BRIEF) | Context pointers, information hierarchy, progressive disclosure, agent briefs |
| 32 | `skill-lifecycle` | Learned | inherited from anthropics/skills (skill-creator eval machinery) | Draft → eval with/without skill → assertions → rewrite → optimize description |
| 33 | `motion-design` | Learned | inherited from emilkowalski/skills (animate, animation-vocabulary) | Decision-first animation: gate, purpose, curve/duration tables, reverse-lookup vocabulary |
| 34 | `distinctive-design` | Learned | inherited from anthropics/skills (frontend-design) + emilkowalski | Design that avoids AI-template defaults: token system, signature element, restraint |
| 35 | `android-compose-craft` | Learned | inherited from MiniMax-AI/skills (android-native-dev) + google/skills | Kotlin/Compose/Material 3, build troubleshooting, accessibility, testing |
| 36 | `document-generation` | Learned | inherited from anthropics/skills (docx/xlsx/pptx/pdf) + MiniMax | Create/edit Word/Excel/PowerPoint/PDF; XML-safe edits; validate before claiming |
| 37 | `product-validation` | Learned | inherited from slavingia/skills (validate-idea, mvp, processize, pricing) | Validate by selling: manual → processized → productized; four build questions |
| 38 | `issue-triage` | Learned | inherited from mattpocock/skills (triage, AGENT-BRIEF, to-tickets) | Issue/PR triage state machine → agent-ready briefs with acceptance criteria |
| 39 | `skill-discovery` | Learned | inherited from vercel-labs/skills (find-skills) + VoltAgent catalog | Discover/vet/install ecosystem skills; provenance checks before install |
| 40 | `mcp-server-builder` | Learned | inherited from anthropics/skills (mcp-builder) + google/skills | Design/build/test MCP servers; tool naming, context, actionable errors |
| 41 | `web-visual-effects` | Learned | inherited from MengTo/Skills + MiniMax-AI/skills (shader-dev) | Scroll storytelling, staggered reveals, WebGL discipline, shader cheat-sheet |

> Note: `skill-creator-guide` (24) is the `skill-creator` skill from
> PageAI-Pro/ralph-loop referenced under the `skills/` dir below for
> convenience; it duplicates #16.

## Directory layout

```
skills-pack/
├── MANIFEST.md          ← this file
├── uesf-core/           ← #1–6    (6 skills)
├── community/           ← #7–20   (14 skills)
├── original/            ← #21–25  (5 skills incl. skill-creator copy)
├── learned/             ← #26–41  (16 skills — new tier, synthesized from 11 public skill repos)
├── training/            ← JSONL fine-tuning corpora (41 records + 41 chat lines)
│   ├── skills_corpus.jsonl   ← record format: system / instructions / trigger / task
│   ├── skills_chat.jsonl     ← chat format for direct SFT
│   └── README.md             ← usage + regeneration guide
└── tools/
    ├── install.sh            ← copies a skill (or all) into any agent's skills dir
    └── export_training_jsonl.py ← regenerates training/ from every SKILL.md
```

## Training corpus

`skills-pack/training/` contains a training-ready export of all 41 skills:

- `skills_corpus.jsonl` — one record per skill: `system` (frontmatter as a
  system prompt), `instructions` (the SKILL.md body), and a distinct
  `trigger`/`task` pair (WHEN vs WHAT) for behavior-shaping samples.
- `skills_chat.jsonl` — the same content as `{system, user, assistant}`
  messages for direct instruction fine-tuning (SFT).

Regenerate after any skill edit: `python3 skills-pack/tools/export_training_jsonl.py`
(stdlib-only; self-verifies line counts + schema). See `training/README.md`
for how to use the corpus to fine-tune another model.

## How to install into another model/agent

The `.agents/skills/` convention: drop a skill directory under your agent's
skills root (e.g. `.agents/skills/<name>/SKILL.md`), or run:

```bash
./skills-pack/tools/install.sh skills-pack/original/verify-before-claim
# or install everything:
./skills-pack/tools/install.sh --all
```

Any agent that reads `name`/`description` frontmatter to trigger skills will
pick these up. UESF skills also validate against the UESF skill spec (see
`uesf/` in the Aivance repo root for the validator).

## Provenance & honesty notes

- **Community skills** are copies of third-party instructions (MIT/Apache per
  their repos). They are not vetted by Aivance — review before use; they run
  with full agent permissions.
- **Original skills** were authored during Aivance development and encode its
  verified working patterns; they are the safest to train on.
- **Learned skills** (new tier) are original syntheses written for this pack,
  distilled from the public skill repos listed in the Source column
  (anthropics/skills, obra/superpowers, mattpocock/skills, emilkowalski/skills,
  MiniMax-AI/skills, MengTo/Skills, slavingia/skills, google/skills,
  vercel-labs/skills, VoltAgent/awesome-openclaw-skills,
  multica-ai/andrej-karpathy-skills). They are model-agnostic: no vendor API
  keys, no platform-specific tool hooks — plain processes any capable agent can
  follow.
- `gsd-orchestrator` requires the external `gsd` CLI; `autofix`/`code-review`
  require CodeRabbit's GitHub integration.

## How overlapping skills relate

Three clusters look similar but differ — pick by trigger, not by name:

- **Skill authoring** — `skill-creator` (community, PageAI-Pro) is a general
  guide for creating/updating skills; `skill-lifecycle` (learned, from
  anthropics skill-creator) is the *eval-driven* loop: draft → run with/without
  the skill → quantitative assertions → rewrite. Use `skill-lifecycle` when
  you want to prove a skill changes behavior; `skill-creator` for the basics.
- **Debugging** — `uesf-co-debugging` (core) reproduces + hypothesizes;
  `root-cause-debugging` (learned, from obra systematic-debugging) adds the
  explicit four-phase iron law (no fixes before root cause). They compose:
  run root-cause-debugging's phases, then uesf-co-debugging's verification.
- **Verification** — `verify-before-claim` (original) proves *external facts*
  (URLs, sizes, TLS pins) with commands; `verification-before-completion`
  (learned) proves *work status* (tests pass, build succeeds) with fresh
  evidence before claiming done. Use both: facts during research, completion
  evidence before finishing.
