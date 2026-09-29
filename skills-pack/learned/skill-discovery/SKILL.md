---
name: skill-discovery
description: Discover, evaluate, and install skills from the open agent-skills ecosystem — search by task, vet provenance and quality, and install into the right skills directory for the current agent. Use when the user asks "how do I do X", "find a skill for X", "is there a skill that…", or wants to extend agent capabilities. Inherited from vercel-labs/skills (find-skills) and VoltAgent/awesome-openclaw-skills (curated catalog).
---

# Skill Discovery

Find and install skills from the open agent-skills ecosystem. Skills are modular
packages of specialized knowledge and workflows that extend an agent's capabilities.

## Step 1: Understand the Need

When the user asks "how do I do X" or "find a skill for X", identify:

1. The **domain** (e.g. Android, PDF processing, testing, design).
2. The **specific task** (e.g. "generate a resume docx", "audit animations").
3. Whether the user wants a **one-off answer** (answer directly) or a **capability**
   (find/install a skill).

Answer the question directly if it's simple and self-contained; only search for a
skill when the task is a recurring capability.

## Step 2: Search the Ecosystem

Primary tool — the Skills CLI:

```bash
npx skills find <query>            # search the ecosystem
npx skills find <query> --owner <owner>  # scoped to an org
npx skills add <owner/repo> --list # preview a repo's skills before adding
npx skills add <owner/repo> --skill <name> --yes  # install one skill
npx skills update                  # update installed skills
```

Browse curated indexes: `https://skills.sh/` and the VoltAgent awesome-openclaw-skills
catalog (categories: ai-and-llms, pdf-and-documents, coding-agents-and-ides,
web-and-frontend-development, devops-and-cloud, browser-and-automation, and more —
each listing hundreds of community skills with one-line descriptions).

## Step 3: Vet Before Installing

Community skills are **not vetted** — they run with full agent permissions. Before
installing:

1. **Provenance:** who wrote it? Is it a known org (google, anthropics, vercel) or a
   stranger?
2. **Read the SKILL.md** before installing — frontmatter + body. Does it do what the
   description claims?
3. **Watch for red flags:** skills that exfiltrate data, run arbitrary network calls,
   ask for secrets, or claim to bypass security. Skills that request credentials
   should only receive them via the user's own secret management.
4. **License:** check LICENSE before using for training or redistribution.
5. **Quality signal:** tests included? maintained? stars/downloads?
6. **Confirm with the user** which skill(s) to install before running `npx skills add`.

## Step 4: Install

Install into the agent's skills root:

- Claude Code / Codex: `~/.claude/skills/<name>/SKILL.md` or the project's
  `.claude/skills/`
- Generic agents: `.agents/skills/<name>/SKILL.md` (the convention used by this repo)
- Cursor: `.cursor/skills/`
- Or use the `npx skills add` package manager for managed installs + updates.

The skill loads by its frontmatter `name`; the `description` decides when it
auto-triggers. Skills with `disable-model-invocation: true` only run when explicitly
invoked.

## Step 5: Verify It Loads and Works

After installing, load the skill and run a minimal task to confirm it behaves as
described. If it doesn't, uninstall or fix it — an unverified skill is a liability.

## Checklist

- [ ] Need understood: one-off answer vs recurring capability
- [ ] Searched ecosystem (`npx skills find` + curated catalogs)
- [ ] SKILL.md read and vetted; provenance + license checked
- [ ] User confirmed before install
- [ ] Installed to the right skills dir for the agent
- [ ] Loaded and smoke-tested
