#!/usr/bin/env python3
"""
UESF Skill Scaffolder — create a new spec-conformant skill skeleton.

Usage:
    python3 tools/skill_scaffold.py new <category-code> <skill-slug> [--name "Skill Name"] [--kind KIND] [--force]

Copies templates/skill-template/SKILL.md into
    <root>/<kind-dir>/<category>/uesf-<category>-<slug>/SKILL.md
and fills the placeholders (id, category, kind, name).

The skeleton is a *draft* (version 0.1.0) — run the validator after editing:
    python3 tools/validate_framework.py
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import shutil

FRAMEWORK_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

VALID_CATEGORIES = {
    "co", "pl", "ar", "ra", "im", "te", "dg", "rf", "pf", "se", "ax", "do",
    "re", "de", "cl", "ai", "pe", "ux", "ui", "da", "nw", "au", "rs", "ce",
    "pm", "gv", "le", "mk", "km", "ac", "ex",
}

KIND_DIRS = {
    "core": "core",
    "meta": "meta-skills",
    "example": "examples",
    "engineering": "skills",
    "ai": "skills",
    "ux": "skills",
    "ui": "skills",
}

KIND_TO_CATEGORY = {
    "core": "co", "meta": "mk", "example": "ex",
}


def main(argv=None):
    ap = argparse.ArgumentParser(description="UESF Skill Scaffolder")
    sub = ap.add_subparsers(dest="command", required=True)

    new = sub.add_parser("new", help="create a new skill skeleton")
    new.add_argument("category", help="two-letter taxonomy code (e.g. pf)")
    new.add_argument("slug", help="skill slug (kebab-case, e.g. android-startup-profiling)")
    new.add_argument("--name", default=None, help="human-readable skill name")
    new.add_argument("--kind", default=None,
                     help="kind: core|engineering|ai|ux|ui|meta|example "
                          "(default from category)")
    new.add_argument("--force", action="store_true", help="overwrite existing skeleton")
    args = ap.parse_args(argv)

    if args.command != "new":
        ap.error("unknown command %r" % args.command)

    category = args.category
    if category not in VALID_CATEGORIES:
        sys.exit("unknown category %r — see docs/taxonomy.md" % category)

    slug = args.slug
    if not re.match(r"^[a-z0-9]+(-[a-z0-9]+)*$", slug):
        sys.exit("invalid slug %r — use kebab-case (lowercase letters, numbers, hyphens)" % slug)

    kind = args.kind or (KIND_TO_CATEGORY.get(category, "engineering"))
    if kind not in KIND_DIRS:
        sys.exit("unknown kind %r" % kind)
    if kind in KIND_TO_CATEGORY and KIND_TO_CATEGORY[kind] != category:
        sys.exit("kind=%r requires category=%r" % (kind, KIND_TO_CATEGORY[kind]))

    skill_id = "uesf-%s-%s" % (category, slug)
    name = args.name or (" ".join(w.capitalize() for w in slug.split("-")))

    kind_root = KIND_DIRS[kind]
    if kind in ("engineering", "ai", "ux", "ui"):
        dest_dir = os.path.join(FRAMEWORK_ROOT, "skills", category, skill_id)
    else:
        dest_dir = os.path.join(FRAMEWORK_ROOT, kind_root, skill_id)

    template = os.path.join(FRAMEWORK_ROOT, "templates", "skill-template", "SKILL.md")
    if not os.path.isfile(template):
        sys.exit("template not found at %s" % template)

    os.makedirs(dest_dir, exist_ok=True)
    dest = os.path.join(dest_dir, "SKILL.md")
    if os.path.exists(dest) and not args.force:
        sys.exit("%s already exists (use --force to overwrite)" % dest)

    with open(template, "r", encoding="utf-8") as fh:
        content = fh.read()

    content = content.replace("uesf-{{CATEGORY_CODE}}-{{skill-slug}}", skill_id)
    content = content.replace("{{Skill Name}}", name)
    content = content.replace("{{category_code}}", category)
    content = content.replace("{{core|engineering|ai|ux|ui|meta|example}}", kind)

    with open(dest, "w", encoding="utf-8") as fh:
        fh.write(content)

    print("Scaffolded: %s" % dest)
    print("Next: fill in every {{PLACEHOLDER}}, then run")
    print("    python3 tools/validate_framework.py")
    return 0


if __name__ == "__main__":
    sys.exit(main())
