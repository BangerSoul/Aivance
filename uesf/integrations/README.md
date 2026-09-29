# Installing UESF Skills into Your Agent

UESF skills are standard `SKILL.md` packages — they install into any agent that
consumes the Agent Skills format. The ecosystem has converged on
`.agents/skills/` as the cross-agent directory; per-agent paths are listed below.

## Fast path — skills.sh

```bash
# Install selected skills into selected agents
npx skills add ./uesf --skill uesf-co-planning -a claude-code -a codex

# Copy (editable) instead of symlinking
npx skills add ./uesf --skill '*' --copy
```

## Per-agent paths

| Agent | Project path | Global path |
|-------|--------------|-------------|
| Claude Code | `.claude/skills/` | `~/.claude/skills/` |
| OpenAI Codex | `.agents/skills/` | `~/.codex/skills/` |
| Cursor | `.agents/skills/` | `~/.cursor/skills/` |
| Gemini CLI | `.agents/skills/` | `~/.gemini/skills/` |
| Cline | `.cline/skills/` | `~/.cline/skills/` |
| Windsurf | `.windsurf/skills/` | `~/.codeium/windsurf/skills/` |
| Continue | `.continue/skills/` | `~/.continue/skills/` |
| Antigravity | `.agents/skills/` | `~/.gemini/antigravity/skills/` |
| OpenHands | `.openhands/skills/` | `~/.openhands/skills/` |
| Aider | `.aider/skills/` (via npx) | `~/.aider/skills/` |
| OpenClaw | `skills/` | `~/.openclaw/skills/` |
| GitHub Copilot | `.agents/skills/` | `~/.copilot/skills/` |

*All paths are the standard Agent Skills conventions (per vercel-labs/skills
supported-agents table).*

## Recommended bundles

- **Core loop (start here):** `uesf-co-planning`, `uesf-co-implementation`,
  `uesf-co-testing`, `uesf-co-review`.
- **Quality:** `uesf-co-debugging`, `uesf-se-security-audit`,
  `uesf-pf-performance-optimization`.
- **Meta (framework maintainers):** the full `uesf-mk-*` set.

## Rules-based agents (Cursor, etc.)

For agents that prefer persistent rules over on-demand skills, point them at the
skill bodies or use the checklists from `uesf-co-review` and
`uesf-ax-accessibility-audit` as `.cursor/rules` content. The skills remain the
source of truth; rules are a projection.

## Verifying the install

```bash
npx skills list           # skills.sh-managed installs
python3 tools/validate_framework.py   # the framework itself always validates
```

## Model-agnosticism guarantee

No UESF skill references a vendor model, SDK, or harness by name in its
instructions (frontmatter `mcp_tools` may name optional tools). If you find a
vendor leak, it is a bug — file it against the skill (see
`policies/contribution.md`).
