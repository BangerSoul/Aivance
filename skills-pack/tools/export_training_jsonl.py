#!/usr/bin/env python3
"""Export the Aivance skills pack into training-ready JSONL corpora.

For every SKILL.md in skills-pack/ (all five tiers) this generates:
  1. skills-pack/training/skills_corpus.jsonl  — record format
     { id, name, tier, trigger, task, system, instructions, source }
     - system:       frontmatter (name + description) rendered as a system prompt
     - instructions: the SKILL.md body (minus frontmatter)
     - trigger/task: a derived task/trigger pair for behavior-shaping samples
  2. skills-pack/training/skills_chat.jsonl    — chat format for direct SFT
     { messages: [system, user(task), assistant(instructions)] }

Both are strict JSON Lines (one JSON object per line, UTF-8, no trailing commas).
Pure stdlib — no third-party dependencies.

Usage: python3 skills-pack/tools/export_training_jsonl.py
"""

from __future__ import annotations

import json
import re
from pathlib import Path

PACK_ROOT = Path(__file__).resolve().parent.parent  # skills-pack/
TRAINING_DIR = PACK_ROOT / "training"


def parse_frontmatter(text: str) -> tuple[dict, str]:
    """Return (frontmatter dict, body) for a SKILL.md with `---` delimiters.

    Only *standalone* `---` lines (own line, no other content) delimit the
    frontmatter, so a body that contains a Markdown horizontal rule or a code
    fence with `---` inside never truncates the instructions.
    """
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        return {}, text.strip()
    closing = None
    for idx in range(1, len(lines)):
        if lines[idx].strip() == "---":
            closing = idx
            break
    if closing is None:
        return {}, text.strip()
    fm_text = "\n".join(lines[1:closing])
    body = "\n".join(lines[closing + 1 :]).strip()
    frontmatter: dict = {}
    for line in fm_text.splitlines():
        if ":" in line:
            key, _, value = line.partition(":")
            frontmatter[key.strip()] = value.strip().strip('"').strip("'")
    return frontmatter, body


def derive_task_and_trigger(description: str, name: str) -> tuple[str, str]:
    """Derive a concrete task + trigger pair from the skill description.

    The trigger states WHEN the skill applies (the description's applicability
    clause); the task is an imperative of WHAT to do when invoked. Keeping them
    distinct gives the training corpus a clean input/behavior pairing.
    """
    cleaned = description.strip().rstrip(".")
    if not cleaned:
        return f"Apply the {name} skill to the current work.", f"Use the {name} skill."
    trigger = cleaned
    # Pull the actionable part: cut a trailing "Use when…" applicability clause
    # (the trigger) so the task is the WHAT, not the WHEN.
    task_body = re.split(r"\s*\b(?:use when|when to use|applies when)\b\s*", cleaned, maxsplit=1, flags=re.IGNORECASE)[0]
    # Lead with an action verb when the description starts with a noun phrase,
    # so the "task" is a directive rather than a restated description.
    IMPERATIVE_VERBS = (
        "apply", "build", "create", "design", "fix", "improve", "plan",
        "review", "run", "write", "verify", "debug", "refactor", "triage",
        "export", "install", "discover", "evaluate", "follow", "use",
        "develop", "make", "implement", "test", "check", "maintain",
        "find", "prepare", "generate", "author", "distill", "validate",
    )
    first_word = task_body.split(" ", 1)[0].strip("\"'(),").lower()
    directive = task_body if first_word in IMPERATIVE_VERBS else f"Apply the {name} skill: {task_body}"
    task = f"{directive[0].upper()}{directive[1:]}"
    return task, trigger


def build_system_prompt(frontmatter: dict) -> str:
    name = frontmatter.get("name", "unnamed-skill")
    description = frontmatter.get("description", "")
    parts = [f"You are the '{name}' skill module of an engineering agent."]
    if description:
        parts.append(f"When to use it: {description}")
    parts.append("Follow the instructions below exactly when this skill applies.")
    return "\n".join(parts)


def collect_skills() -> list[dict]:
    """Scan skills-pack/ for SKILL.md files; return enriched records."""
    records: list[dict] = []
    for path in sorted(PACK_ROOT.rglob("SKILL.md")):
        tier = path.parent.parent.name  # e.g. learned/, community/, uesf-core/
        relative = path.relative_to(PACK_ROOT)
        text = path.read_text(encoding="utf-8")
        frontmatter, body = parse_frontmatter(text)
        name = frontmatter.get("name", path.parent.name)
        description = frontmatter.get("description", "")
        task, trigger = derive_task_and_trigger(description, name)
        records.append(
            {
                # id is stable + unique (the directory name, e.g. the slug the
                # skill loads under); name is the human-readable frontmatter
                # title (may differ for UESF core skills).
                "id": path.parent.name,
                "name": name,
                "tier": tier,
                "path": str(relative),
                "trigger": trigger,
                "task": task,
                "system": build_system_prompt(frontmatter),
                "instructions": body,
        "source": extract_source_refs(description),
            }
        )
    return records


# Repo owners this pack's skills actually came from (mirrors MANIFEST.md's
# Source column). A precise allowlist beats a prose-matching heuristic: it
# extracts exactly the provenance refs and can never false-positive on prose
# like 'React/Next.js' or 'and/or'.
KNOWN_REPO_OWNERS = {
    "anthropics", "obra", "mattpocock", "emilkowalski", "MiniMax-AI",
    "MengTo", "slavingia", "google", "vercel-labs", "VoltAgent",
    "multica-ai", "public-apis", "coderabbitai", "PageAI-Pro", "gsd-build",
}


def extract_source_refs(description: str) -> str:
    """Pull known repo/URL provenance from a description."""
    refs: list[str] = []
    for token in re.findall(r"https?://\S+|\S+/\S+", description):
        if token.startswith(("http://", "https://")):
            refs.append(token.rstrip(",.;)"))
            continue
        owner, _, repo = token.partition("/")
        if owner in KNOWN_REPO_OWNERS:
            refs.append(f"{owner}/{repo.rstrip(',.;:!?)')}")
    return " ".join(refs)


def write_jsonl(records: list[dict], path: Path, chat_format: bool) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as fh:
        for record in records:
            if chat_format:
                line = {
                    "messages": [
                        {"role": "system", "content": record["system"]},
                        {"role": "user", "content": record["task"]},
                        {"role": "assistant", "content": record["instructions"]},
                    ],
                    "id": record["id"],
                    "skill": record["name"],
                    "tier": record["tier"],
                }
            else:
                line = record
            fh.write(json.dumps(line, ensure_ascii=False) + "\n")


def main() -> None:
    records = collect_skills()
    write_jsonl(records, TRAINING_DIR / "skills_corpus.jsonl", chat_format=False)
    write_jsonl(records, TRAINING_DIR / "skills_chat.jsonl", chat_format=True)

    # Quick self-check so CI/git-hooks can fail fast on a corrupt export.
    for name in ("skills_corpus.jsonl", "skills_chat.jsonl"):
        target = TRAINING_DIR / name
        with target.open(encoding="utf-8") as fh:
            lines = [json.loads(l) for l in fh if l.strip()]
        assert len(lines) == len(records), f"{name}: {len(lines)} != {len(records)}"
        for line in lines:
            assert "skill" in line or "name" in line
        print(f"OK {name}: {len(lines)} lines")

    print(f"Exported {len(records)} skills to {TRAINING_DIR}/")


if __name__ == "__main__":
    main()
