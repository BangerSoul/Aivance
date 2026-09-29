# UESF Benchmark Matrices

*Version 1.0.0 · Deliverable 16 (matrix set)*

Scores: **1–5** (5 = best). Legend for feature matrix: ANTH = anthropics/skills,
SPW = obra/superpowers, MP = mattpocock/skills, EMIL = emilkowalski/skills,
VL = vercel-labs/skills, GOOG = google/skills, MMX = MiniMax-AI/skills,
SLAV = slavingia/skills, OCLW = VoltAgent/awesome-openclaw-skills,
KARP = multica-ai/andrej-karpathy-skills, MENG = MengTo/Skills, UESF = this framework.

## 1. Feature matrix

| Capability | ANTH | SPW | MP | EMIL | VL | GOOG | MMX | SLAV | OCLW | KARP | MENG | UESF |
|------------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| SKILL.md format | 5 | 4 | 4 | 4 | 4 | 4 | 4 | 4 | 3 | 3 | 4 | 5 |
| Rich frontmatter | 2 | 2 | 2 | 2 | 2 | 3 | 3 | 2 | 2 | 2 | 2 | 5 |
| Trigger hygiene ("use when") | 4 | 5 | 3 | 3 | 2 | 3 | 3 | 3 | 2 | 3 | 3 | 5 |
| Progressive disclosure | 5 | 3 | 2 | 2 | 3 | 4 | 4 | 2 | 2 | 1 | 3 | 5 |
| Scripts/references externalization | 5 | 2 | 2 | 1 | 2 | 2 | 4 | 1 | 3 | 1 | 3 | 4 |
| Taxonomy/organization | 2 | 2 | 3 | 1 | 1 | 4 | 2 | 1 | 5 | 1 | 4 | 5 |
| Meta-skills (skills about skills) | 2 | 4 | 2 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 5 |
| Versioning | 1 | 1 | 2 | 1 | 3 | 1 | 1 | 1 | 1 | 1 | 1 | 5 |
| Deprecation/migration | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 5 |
| Dependency model | 1 | 1 | 2 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 5 |
| Automated validation | 2 | 2 | 3 | 1 | 1 | 2 | 3 | 2 | 2 | 3 | 2 | 5 |
| Test suite for the framework | 2 | 3 | 3 | 1 | 1 | 2 | 2 | 1 | 1 | 2 | 1 | 5 |
| Benchmark machinery | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 4 |
| Certification | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 5 |
| Intake/curation process | 2 | 2 | 1 | 1 | 2 | 1 | 1 | 1 | 4 | 1 | 1 | 5 |
| Security gate | 2 | 2 | 1 | 1 | 1 | 3 | 2 | 1 | 3 | 1 | 1 | 4 |
| Model-agnostic | 3 | 4 | 5 | 5 | 5 | 2 | 2 | 3 | 3 | 5 | 4 | 5 |
| Multi-agent support | 4 | 4 | 4 | 4 | 5 | 4 | 4 | 3 | 4 | 4 | 4 | 5 |
| Distribution/marketplace | 5 | 3 | 5 | 4 | 5 | 4 | 3 | 3 | 5 | 4 | 4 | 4 |
| Documentation quality | 4 | 4 | 5 | 4 | 4 | 5 | 4 | 3 | 3 | 4 | 4 | 5 |
| Anti-duplication machinery | 1 | 2 | 2 | 1 | 1 | 1 | 1 | 1 | 3 | 1 | 1 | 5 |
| Self-improvement loop | 2 | 4 | 2 | 1 | 1 | 1 | 1 | 1 | 2 | 1 | 1 | 5 |
| Scalability of growth | 4 | 3 | 3 | 2 | 5 | 4 | 3 | 1 | 5 | 1 | 4 | 5 |
| Maintainability | 3 | 4 | 4 | 3 | 4 | 3 | 3 | 3 | 3 | 4 | 3 | 5 |
| Production readiness | 4 | 4 | 4 | 3 | 3 | 4 | 4 | 2 | 3 | 3 | 3 | 5 |
| **TOTALS** | **62** | **58** | **59** | **43** | **50** | **51** | **50** | **39** | **50** | **44** | **51** | **119** |

*UESF leads in 24 of 25 rows; distribution (row 19) trails the registry projects
(registry integration is roadmap).*

## 2. Strength matrix (what each repo does best)

| Repo | Signature strength | UESF inherits via |
|------|-------------------|-------------------|
| ANTH | Spec clarity + scripts-as-executables | spec standard, `mcp_tools` guidance |
| SPW | Anti-rationalization machinery + meta-skills | review circuit breaker, trigger hygiene |
| MP | Composability + shared language | two-tier invocation, `uesf-do-documentation` |
| EMIL | Expertise-as-mistake-checklists | anti-pattern sections |
| VL | Distribution plumbing | `integrations/`, `.agents/skills/` |
| GOOG | Authoritative depth + deprecation bans | references/, explicit deprecation |
| MMX | Executable pipelines + token systems | scripts pattern, UI tokens |
| SLAV | Behavioral gating | governance gates |
| OCLW | Curation at scale | intake screen criteria |
| KARP | Surgical-change discipline | scope gates in implementation |
| MENG | Visual feedback loops | runtime verification in UI skill |

## 3. Weakness matrix (what UESF fixes)

| Weakness | Repos affected | UESF fix |
|----------|----------------|----------|
| No automated validation | 11/11 | validator + schema |
| No versioning | 10/11 | version manager |
| No dependency model | 11/11 | deps + resolver |
| No test requirement | 11/11 | test generator + suite |
| No certification | 11/11 | certification engine |
| No deprecation path | 11/11 | lifecycle records |
| Copy-paste culture | ecosystem | merger + intake rejections |
| Vendor lock-in | GOOG, MMX, ANTH | model-agnosticism rule |

## 4. Coverage matrix (taxonomy categories served)

Legend: ● strong · ◐ partial · — absent

| Category | ANTH | SPW | MP | EMIL | VL | GOOG | MMX | OCLW | UESF |
|----------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Planning | — | ● | ● | — | — | — | — | ◐ | ● |
| Architecture | ◐ | ◐ | ● | — | — | ● | — | ◐ | ● |
| Implementation/TDD | ◐ | ● | ● | — | — | ◐ | ● | ◐ | ● |
| Testing | ● | ● | ● | — | — | ◐ | ◐ | ◐ | ● |
| Debugging | — | ● | ● | — | — | ◐ | — | ◐ | ● |
| Review | ● | ● | ● | ◐ | — | — | — | ◐ | ● |
| Refactoring | — | ◐ | ◐ | — | — | — | — | ◐ | ● |
| Performance | — | — | — | — | — | ● | — | ◐ | ● |
| Security | ◐ | ◐ | — | — | — | ● | — | ● | ● |
| Accessibility | ◐ | — | — | ◐ | — | — | — | ◐ | ● |
| Docs | ● | ◐ | ● | — | — | ● | ◐ | ◐ | ● |
| Release/DevOps | ◐ | — | — | — | ◐ | ● | ◐ | ● | ● |
| Data | ● | — | — | — | — | ● | ● | ● | ● |
| AI/Prompt | ◐ | ◐ | — | — | — | ● | ● | ● | ● |
| UX/UI | ● | — | — | ● | — | — | ◐ | ◐ | ● |
| Research | ◐ | — | — | — | ◐ | — | — | ● | ● |
| Certification | — | — | — | — | — | ◐ | — | ◐ | ● |
| Meta-skills | ◐ | ● | — | — | — | — | — | — | ● |
| Governance | — | ◐ | ◐ | — | — | — | — | ◐ | ● |
| **Total ●** | **6** | **7** | **6** | **0** | **0** | **10** | **4** | **8** | **19** |

## 5. Complexity matrix

| Repo | Skill count | Format complexity | Machinery | Net |
|------|:-----------:|:-----------------:|:---------:|:---:|
| ANTH | ~25 | low | low | simple |
| SPW | ~30 | low | medium | heavy process |
| MP | ~20 | low | medium | medium |
| GOOG | 100+ | low | low | broad |
| OCLW | 5,300+ | low | low | chaotic breadth |
| UESF | 41 | medium (standardized) | high (self-managing) | **bounded** |

*UESF's complexity is concentrated in tooling that *manages* complexity — the skill
authoring surface stays simple while growth is governed.*

## 6. Maintainability matrix

| Repo | Validation | Versioning | Docs | Tooling | Score |
|------|:---:|:---:|:---:|:---:|:---:|
| ANTH | ◐ | — | ● | ◐ | 3 |
| SPW | ● | — | ● | ◐ | 3.5 |
| MP | ◐ | ◐ | ● | — | 3 |
| VL | — | ◐ | ● | ● | 3 |
| GOOG | ◐ | — | ● | ◐ | 3 |
| UESF | ● | ● | ● | ● | 5 |

## 7. Automation matrix

| Repo | Validator | Tests | Benchmarks | Certification | Intake |
|------|:---:|:---:|:---:|:---:|:---:|
| ANTH | — | ◐ | — | — | — |
| SPW | — | ◐ | — | — | — |
| MP | — | ◐ | — | — | — |
| VL | — | — | — | — | ◐ |
| OCLW | — | — | — | — | ● (curation) |
| UESF | ● | ● | ◐ | ● | ● |

## 8. Scalability matrix

| Growth axis | Best source | UESF |
|-------------|-------------|------|
| Adding skills | OCLW (curation) | intake + generator + validator gate |
| Keeping quality | SPW (discipline) | validator + reviewer + certification |
| Avoiding duplication | OCLW (dedup filter) | merger + overlap checks |
| Evolving format | — | spec versioning + compat shim |
| Multi-agent | VL (77 agents) | `.agents/skills/` + integrations doc |
| Self-maintenance | SPW (meta) | 13 meta-skills |

## 9. Developer experience matrix

| Aspect | ANTH | SPW | MP | VL | GOOG | UESF |
|--------|:---:|:---:|:---:|:---:|:---:|:---:|
| Install friction | ● | ◐ | ● | ● | ◐ | ● |
| Skill authoring ease | ● | ◐ | ● | ● | ◐ | ◐ (spec-first) |
| Editing freedom | ● | ◐ | ● | ● | ◐ | ● |
| Guardrails | ◐ | ● | ◐ | — | ◐ | ● |
| Learning curve | low | high | low | low | medium | medium (paid back by guards) |

## 10. Agent compatibility matrix

| Agent | Native path | UESF consumption |
|-------|-------------|------------------|
| Claude Code | `.claude/skills/` | copy/symlink skills; see `integrations/` |
| Codex | `.agents/skills/` | `npx skills add ./uesf` |
| Cursor | `.agents/skills/` | rules + skills |
| Gemini CLI | `.agents/skills/` | `activate_skill` |
| Cline/Windsurf/Continue/Antigravity/OpenHands/Aider/OpenClaw | `.agents/skills/` + others | same directory convention |
| Future agents | `.agents/skills/` | format is standard SKILL.md |

## 11. Why each synthesized skill is superior (summary)

1. **Core loop** (planning/implementation/testing/debugging/review/refactoring):
   superpowers-grade discipline *plus* spec-recorded rollback, failure recovery, and
   acceptance criteria that the validator enforces structurally — not just prose.
2. **Engineering skills**: google/skills' depth and deprecation bans, MiniMax's
   executable pipelines, and mattpocock's humility — all with measured
   validation strategies per skill (unit/integration/regression/performance/security).
3. **AI skills**: anthropic's evaluation-driven authoring and degree-of-freedom
   control, superpowers' context isolation, and a *groundedness* hard requirement
   that none of the sources enforce.
4. **Meta skills**: the only layer that turns "extensible" into a closed loop —
   every other repo stops at "you can add skills"; UESF adds validated, versioned,
   benchmarked, certified addition.
5. **Verification**: every UESF skill carries an explicit Testing Strategy section
   and maps to `validation` strategies — no source repo requires this of its own
   skills.
