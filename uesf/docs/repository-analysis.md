# Repository Analysis Report

*Version 1.0.0 · Deliverable 1*
*Method: primary-source reading (READMEs, repo trees, skill files) via direct fetch
and targeted research, with claims cited to observed artifacts. This report is the
output of the `uesf-mk-repository-analyzer` discipline applied to the eleven sources.*

---

## 1. anthropics/skills

- **Purpose:** Canonical demonstration of the Agent Skills standard for Claude: folders
  of instructions, scripts, and resources that an agent loads dynamically for
  specialized tasks. Includes the document skills (docx/pdf/pptx/xlsx) that power
  Claude's document capabilities (source-available, not open source).
- **Architecture:** Flat skill folders + `spec/` (the Agent Skills specification) +
  `template/` (skill template). Distributed via Claude Code plugin marketplace and
  skills.sh.
- **Skill format:** `SKILL.md` with **exactly two required frontmatter fields**
  (`name`, `description`). Name: `[a-z0-9-]`, ≤64 chars, gerund recommended.
  Description: ≤1024 chars, third person, must cover what the skill does *and* when to
  select it.
- **Progressive disclosure:** metadata loaded at startup (~100 tokens), body only when
  triggered (<500 lines recommended), resources/scripts loaded on demand. Scripts run
  via bash; only stdout enters context.
- **Hidden patterns:** `skill-creator` (authoring with subagent-based evaluation),
  `mcp-skills-adder`, artifacts-builder's `output/` directory convention, reference
  files kept 1 level deep to prevent token degradation.
- **Strengths:** Spec clarity, minimal friction, scripts-as-executables (determinism),
  evaluation-driven authoring guidance.
- **Weaknesses:** No automated validation/versioning; two-field frontmatter carries no
  dependency or quality metadata; Claude-centric origin (now broadly adopted).
- **What UESF adopts:** the SKILL.md + progressive-disclosure model, the ≤1024-char
  description rule, the "scripts do the fragile work" principle, and the
  evaluation-driven authoring loop.

## 2. obra/superpowers

- **Purpose:** A disciplined software-engineering methodology for coding agents —
  "brainstorm, plan, execute, verify" — with heavy anti-rationalization machinery.
- **Architecture:** Flat `skills/` + `references/` + harness hooks. A bootstrap skill
  (`using-superpowers`) is injected at session start and forces skill lookup before
  any response.
- **Naming:** lowercase-hyphenated, verb-first/gerund (`writing-plans`,
  `systematic-debugging`).
- **Metadata:** `name` + `description`; descriptions must start with "Use when…" —
  describing the workflow in the description is *banned* (tested: agents shortcut to
  the summary).
- **Workflow design:** brainstorming (Socratic, one question at a time) → writing-plans
  (2–5-minute tasks with exact files/interfaces) → subagent-driven development (fresh
  implementer per task, plan-scoped workspace, `progress.md` ledger) → review with a
  bounded fix loop (circuit breaker, re-review of delta only).
- **Verification strategy:** The Iron Law — no production code without a failing test
  first (RED observed); empirical evidence; "verification-before-completion."
- **Hidden patterns:** meta-skills (`writing-skills` — TDD applied to documentation),
  plan-scoped SDD workspaces, re-review prompts scoped to fix deltas, rationalization
  tables + red flags.
- **Strengths:** The strongest anti-shortcut machinery in the ecosystem; context
  isolation; multi-harness portability.
- **Weaknesses:** Overhead for trivial tasks; brittle when agents rationalize;
  git-ignored scratch state can be wiped by destructive git commands.
- **What UESF adopts:** trigger-description hygiene ("use when", no workflow
  summaries), red-green-refactor as a hard gate, bounded review loops, context
  isolation for subagents, meta-skills as first-class.

## 3. mattpocock/skills

- **Purpose:** "Skills for real engineers" — small, composable skills fixing four
  observed agent failure modes: misalignment (grilling sessions), verbosity (shared
  domain language), broken code (TDD + diagnosing-bugs), and "ball of mud"
  (architecture survey).
- **Architecture:** `skills/engineering/` + `skills/productivity/`; **two invocation
  tiers**: user-invoked (orchestrators: `/grill-me`, `/triage`, `/to-spec`,
  `/to-tickets`, `/implement`, `/wayfinder`) and model-invoked (disciplines: `/tdd`,
  `/diagnosing-bugs`, `/prototype`). User-invoked may call model-invoked, never vice
  versa. ADRs live in `.agents/adr/`.
- **Hidden patterns:** `CONTEXT.md` shared-language doc (concise naming that pays off
  every session); `/improve-codebase-architecture` as a *survey, not a rescue*;
  red-green-refactor guidance; "the rate of feedback is your speed limit."
- **Strengths:** Composability and ownership philosophy (skills are files you can edit);
  model-agnostic; honest about scope.
- **Weaknesses:** No validation/versioning machinery; two-tier routing is manual.
- **What UESD adopts:** invocation-tier separation (orchestrators vs. disciplines),
  shared-language documentation, survey-not-rescue humility, feedback-rate principle.

## 4. emilkowalski/skills

- **Purpose:** Encode design/animation expertise so agents stop shipping "slop":
  "Agents don't have great taste."
- **Inventory:** `emil-design-eng`, `animate`, `review-animations`,
  `improve-animations`, `find-animation-opportunities`, `animation-vocabulary`,
  `apple-design`, `pick-ui-library`, `prototype`.
- **Hidden patterns:** mistake-checklists as the skill's core (list the errors agents
  make and how to fix them); animation *vocabulary* to bridge the language gap;
  strict review skills for creative work; "know what *not* to animate."
- **Strengths:** Domain-expertise-as-data; opinionated and therefore effective.
- **Weaknesses:** Opinionated aesthetics don't generalize; no verification machinery.
- **What UESF adopts:** the mistake-checklist pattern (folded into
  `uesf-ui-ui-implementation` and `uesf-ux-ux-audit` anti-pattern lists), vocabulary
  as shared language.

## 5. vercel-labs/skills

- **Purpose:** The `skills` CLI + skills.sh registry — the *plumbing* of the open
  agent-skills ecosystem (77+ supported agents, install/update/remove/find/use).
- **Hidden patterns:** symlink vs. copy install modes; `.agents/skills/` as the
  cross-agent convergence point; source-format abstraction (GitHub/GitLab/local/direct
  URLs); archive size limits (10 MiB / 25 MiB / 1000 files).
- **Strengths:** The de-facto distribution and interop layer.
- **Weaknesses:** Registry ≠ quality: no validation, versioning, or security gate on
  what gets distributed (the ecosystem's biggest gap, per the curation research).
- **What UESF adopts:** the installation/distribution model (`integrations/`), the
  `.agents/skills/` convention, and a *hardened* view of what a skill package must
  carry (spec metadata, versions, dependencies) — precisely what the registry lacks.

## 6. google/skills

- **Purpose:** Production-grade Agent Skills for Google Cloud — authoritative
  "infrastructure-as-guidance" so agents stop hallucinating CLI flags and deprecated
  SDKs.
- **Inventory (excerpt):** `gemini-api`, `agent-platform-*`, `gke-*` (25+),
  `bigquery-*`, `spanner-basics`, `cloud-run-basics`, `google-ads-api-*`,
  well-architected reviews (`google-cloud-waf-*`).
- **Format:** `SKILL.md` with frontmatter (`name`, `metadata.category`,
  `description`, `compatibility`) + `references/` subdirs; multi-language code
  snippets (Python/TS/Go/Java/C#).
- **Hidden patterns:** proactive deprecation bans (explicitly forbidding legacy SDKs),
  conditional auth-state handling, defensive parameter enforcement.
- **Strengths:** Authoritative depth; version-sensitive accuracy.
- **Weaknesses:** Vendor lock-in; high drift risk as APIs evolve.
- **What UESF adopts:** the references/ modular-deep-dive pattern, deprecation
  explicitness, well-architected-lens structure (permeates `uesf-ar-solution-architecture`).

## 7. MiniMax-AI/skills

- **Purpose:** Full-stack dev skills fused with MiniMax multimodal APIs and office
  document pipelines (17 skills: `frontend-dev`, `minimax-pdf`, `pptx-generator`,
  `vision-analysis`, `minimax-music-gen`, …).
- **Format:** `SKILL.md` + `scripts/` (Python wrappers) + `references/` +
  `templates/`; frontmatter includes `license` and `metadata` (version, category,
  sources).
- **Hidden patterns:** token-based design systems (semantic doc types drive
  palettes/typography); two-step asset generation with explicit user confirmation;
  rigid negative constraints (anti-emoji, no external placeholder URLs).
- **Strengths:** Real executable pipelines; strict quality constraints.
- **Weaknesses:** API-key dependence; heavier setup surface.
- **What UESF adopts:** scripts-as-verification (deterministic work out of context),
  token-based design systems (in `uesf-ui-ui-implementation`), explicit confirmation
  gates for irreversible generation.

## 8. slavingia/skills

- **Purpose:** Turn *The Minimalist Entrepreneur* into 10 Claude Code slash-command
  skills (`find-community`, `validate-idea`, `mvp`, `pricing`, …).
- **Hidden pattern:** *behavioral gating* — skills that add friction on purpose,
  forcing the agent to question *whether* to build before building.
- **Strengths:** Cohesive domain philosophy; trivial installation.
- **Weaknesses:** Ultra-narrow scope; no engineering relevance.
- **What UESF adopts:** the behavioral-gating insight (see `uesf-gv-project-governance`
  and planning gates) and the "skill as opinionated advisor" posture.

## 9. VoltAgent/awesome-openclaw-skills

- **Purpose:** A curated index of 5,300+ community OpenClaw skills across 25+
  categories, with aggressive filtering (4,065 spam, 1,040 duplicates, 851 low-quality,
  886 crypto, 373 malicious entries rejected).
- **Hidden patterns:** curation as a quality gate; ecosystem-partner integrations
  woven into docs; security guidance (VirusTotal, Snyk, Agent Trust Hub).
- **Strengths:** Unmatched breadth and discovery.
- **Weaknesses:** Curation ≠ line-by-line audit; post-approval drift risk.
- **What UESF adopts:** the intake-screening categories (spam/duplicate/quality/
  security) — generalized into `uesf-le-continuous-learning`'s screen step.

## 10. multica-ai/andrej-karpathy-skills

- **Purpose:** Karpathy's observed LLM coding failure modes, packaged as
  `CLAUDE.md` + a `karpathy-guidelines` skill + a Cursor rule.
- **Hidden patterns:** imperative-to-declarative transformation (turn "fix the bug"
  into "write the failing test, then make it pass"); the senior-engineer mental model
  check ("would a senior engineer call this overcomplicated?"); surgical-change
  discipline.
- **Strengths:** Universal, zero-dependency, directly targets root LLM weaknesses.
- **Weaknesses:** Pure text guidance — no tooling; can feel bureaucratic on trivia.
- **What UESF adopts:** the simplification check (in `uesf-co-implementation` and
  `uesf-ar-solution-architecture`), drive-by-edit prohibition, test-loop discipline.

## 11. MengTo/Skills

- **Purpose:** 118+ portable design/UI/game skills for Codex, Claude, Cursor:
  "prompts are assets," "specs beat vibes," "references beat paragraphs."
- **Format:** `agent-skills/<category>/<skill>/` with `SKILL.md` (frontmatter:
  name + description) + optional `REFERENCES.md`, `ARTICLE.md`, `scripts/`,
  `demo/index.html`.
- **Hidden patterns:** *visual feedback loops* — self-contained `demo/` folders so the
  agent can self-inspect and hand off exact reproductions; versioned prompt bundles.
- **Strengths:** High craft bar; reproducible demos; deep graphics-framework depth.
- **Weaknesses:** Overwhelming volume; opinionated aesthetics.
- **What UESF adopts:** the demo/self-inspection loop (in `uesf-ui-ui-implementation`
  runtime verification) and "assets over paragraphs."

---

## Cross-repository synthesis

### Shared strengths (adopted, generalized)

1. **SKILL.md + YAML frontmatter** as the universal format.
2. **Progressive disclosure** (metadata → body → resources/scripts).
3. **"Use when" trigger descriptions**; never workflow summaries.
4. **Test-first / evidence-based verification** as the core discipline.
5. **Small composable skills** over monolithic playbooks.
6. **Meta-skills** (skills that improve the framework).
7. **References/scripts externalized** from the instruction body.

### Shared weaknesses (fixed in UESF)

| Weakness | Where it shows | UESF fix |
|----------|----------------|----------|
| No automated validation | nearly all | `tools/validate_framework.py` + spec schema |
| No versioning/deprecation | nearly all | `uesf-mk-skill-version-manager` |
| No dependency model | all | `dependencies` + resolver + cycle detection |
| No test requirement | all | `uesf-mk-skill-test-generator` + suite |
| No certification | all | `uesf-mk-skill-certification-engine` |
| No intake/curation process | all | `uesf-le-continuous-learning` + analyzer |
| Duplication across repos | ecosystem-wide | merger + overlap gates |
| Vendor lock-in | google, MiniMax, anthropic | model-agnosticism as a spec requirement |

### Hidden patterns preserved (with provenance)

- Trigger hygiene (superpowers) → `uesf-pe-prompt-engineering`
- Circuit-breaker review loops (superpowers) → `uesf-co-review`
- Red-green-refactor as a hard gate (superpowers, mattpocock, karpathy) →
  `uesf-co-testing`, `uesf-co-implementation`
- Survey-not-rescue (mattpocock) → `uesf-ra-repository-analysis`, `uesf-ar-solution-architecture`
- Mistake checklists (emilkowalski) → anti-pattern sections everywhere
- Scripts out of context (anthropic, MiniMax) → the `mcp_tools`/script guidance
- Shared language docs (mattpocock) → `uesf-do-documentation`
- Behavior gating (slavingia) → governance gates
- Curation as quality gate (awesome-openclaw) → `uesf-le-continuous-learning` screen
- Evaluation-driven authoring (anthropic, google) → `uesf-ai-evaluation`,
  `uesf-mk-skill-benchmarker`
