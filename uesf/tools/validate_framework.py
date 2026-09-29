#!/usr/bin/env python3
"""
UESF Framework Validator — the framework's immune system.

Scans every SKILL.md under the skill roots, parses its YAML frontmatter with a
stdlib-only subset parser, and validates it against the UESF Skill Specification
(spec/skill-spec.schema.json): required fields, types, taxonomy codes, ID format,
dependency resolution, cycle detection, score ranges, and required body sections.

Usage:
    python3 tools/validate_framework.py [--list] [--graph] [--json FILE] [--quiet]
    python3 -m unittest discover -s tests

Exit codes: 0 = valid (warnings allowed), 1 = validation errors, 2 = internal error.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys

FRAMEWORK_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SKILL_ROOTS = ["core", "skills", "meta-skills", "examples"]

TAXONOMY = {
    "co": "Core", "pl": "Planning", "ar": "Architecture", "ra": "Repository Intelligence",
    "im": "Implementation", "te": "Testing", "dg": "Debugging", "rf": "Refactoring",
    "pf": "Performance", "se": "Security", "ax": "Accessibility", "do": "Documentation",
    "re": "Release Engineering", "de": "DevOps", "cl": "Cloud", "ai": "AI Engineering",
    "pe": "Prompt Engineering", "ux": "UX", "ui": "UI", "da": "Data", "nw": "Networking",
    "au": "Automation", "rs": "Research", "ce": "Certification", "pm": "Product Management",
    "gv": "Project Governance", "le": "Continuous Learning", "mk": "Meta Skills",
    "km": "Knowledge Management", "ac": "Agent Collaboration", "ex": "Example",
}

KINDS = {"core", "engineering", "ai", "ux", "ui", "meta", "example"}

REQUIRED_FIELDS = [
    "id", "name", "version", "category", "kind", "purpose", "description",
    "triggers", "inputs", "outputs", "dependencies", "context_requirements",
    "quality_gates", "validation", "rollback", "failure_recovery",
    "acceptance_criteria", "automation_hooks", "mcp_tools", "cost",
    "complexity", "maintainability_score", "scalability_score",
    "production_readiness", "related_skills",
]

REQUIRED_SECTIONS = [
    "Execution Workflow", "Quality Gates", "Validation", "Rollback",
    "Failure Recovery", "Acceptance Criteria", "Examples", "Anti-patterns",
    "Testing Strategy", "Future Extensions",
]

ID_RE = re.compile(r"^uesf-[a-z]{2}-[a-z0-9-]+$")
SEMVER_RE = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+$")

STR_LIST_FIELDS = [
    "triggers", "inputs", "outputs", "dependencies", "context_requirements",
    "quality_gates", "validation", "acceptance_criteria", "automation_hooks",
    "mcp_tools", "related_skills",
]
NON_EMPTY_LIST_FIELDS = ["triggers", "outputs", "quality_gates", "validation",
                         "acceptance_criteria"]
SCORE_FIELDS = ["complexity", "maintainability_score", "scalability_score",
                "production_readiness"]


# ---------------------------------------------------------------------------
# Minimal YAML-subset frontmatter parser (stdlib only; PyYAML is NOT required).
# Supports: quoted/unquoted scalars, `key: value`, `key:` with nested maps,
# `key: |` block scalars, `- item` lists, `- key: value` list-of-maps, `[]`.
# ---------------------------------------------------------------------------

def _parse_scalar(raw: str):
    raw = raw.strip()
    if raw == "[]":
        return []
    if len(raw) >= 2 and raw[0] == raw[-1] and raw[0] in "\"'":
        return raw[1:-1]
    if raw == "true":
        return True
    if raw == "false":
        return False
    if raw in ("null", "~"):
        return None
    if re.match(r"^-?[0-9]+$", raw):
        return int(raw)
    if re.match(r"^-?[0-9]+\.[0-9]+$", raw):
        return float(raw)
    return raw


class _Parser:
    def __init__(self, lines):
        self.lines = lines
        self.pos = 0
        self.n = len(lines)

    def _skip_blank(self):
        while self.pos < self.n:
            s = self.lines[self.pos].strip()
            if s == "" or s.startswith("#"):
                self.pos += 1
            else:
                break

    def _indent(self, line):
        return len(line) - len(line.lstrip(" "))

    def parse_map(self, min_indent):
        result = {}
        while True:
            self._skip_blank()
            if self.pos >= self.n:
                break
            line = self.lines[self.pos]
            indent = self._indent(line)
            content = line.strip()
            if indent < min_indent:
                break
            if content == "---" or content.startswith("#") or content.startswith("- "):
                break
            m = re.match(r"^([A-Za-z_][A-Za-z0-9_]*):(?:\s+(.*))?$", content)
            if not m:
                raise ValueError("unparseable frontmatter line: %r" % line)
            key, val = m.group(1), (m.group(2) or "")
            self.pos += 1
            if val == "|":
                result[key] = self._parse_block_scalar(indent)
            elif val == "":
                self._skip_blank()
                if self.pos < self.n and self._indent(self.lines[self.pos]) > indent:
                    nxt = self.lines[self.pos].strip()
                    if nxt.startswith("- "):
                        result[key] = self.parse_list(indent + 1)
                    else:
                        result[key] = self.parse_map(indent + 1)
                else:
                    result[key] = None
            else:
                result[key] = _parse_scalar(val)
        return result

    def _parse_block_scalar(self, key_indent):
        parts = []
        while self.pos < self.n:
            line = self.lines[self.pos]
            if not line.strip():
                parts.append("")
                self.pos += 1
                continue
            indent = self._indent(line)
            if indent > key_indent:
                parts.append(line[indent:])
                self.pos += 1
            else:
                break
        return "\n".join(parts).strip()

    def parse_list(self, min_indent):
        result = []
        while True:
            self._skip_blank()
            if self.pos >= self.n:
                break
            line = self.lines[self.pos]
            indent = self._indent(line)
            if indent < min_indent:
                break
            content = line.strip()
            if not content.startswith("- "):
                break
            item_raw = content[2:].strip()
            self.pos += 1
            if item_raw == "":
                result.append(None)
                continue
            m = re.match(r"^([A-Za-z_][A-Za-z0-9_]*):(?:\s+(.*))?$", item_raw)
            if m:
                key, val = m.group(1), (m.group(2) or "")
                item = {key: _parse_scalar(val) if val else None}
                self._skip_blank()
                if self.pos < self.n and self._indent(self.lines[self.pos]) > indent:
                    item.update(self.parse_map(indent + 1))
                result.append(item)
            else:
                result.append(_parse_scalar(item_raw))
        return result


def parse_frontmatter(text: str) -> dict:
    """Extract and parse the YAML frontmatter block of a SKILL.md file."""
    lines = text.split("\n")
    start = None
    for i, line in enumerate(lines):
        if line.strip() == "---":
            start = i
            break
    if start is None:
        raise ValueError("no frontmatter delimiters found")
    end = None
    for i in range(start + 1, len(lines)):
        if lines[i].strip() == "---":
            end = i
            break
    if end is None:
        raise ValueError("unterminated frontmatter block")
    return _Parser(lines[start + 1:end]).parse_map(0)


# ---------------------------------------------------------------------------
# Validation logic
# ---------------------------------------------------------------------------

def _body_sections(body: str) -> list:
    return [m.group(1) for m in re.finditer(r"^##\s+(.+)$", body, re.MULTILINE)]


def validate_skills(skills) -> tuple:
    """Validate a list of skill records. Each record: {path, frontmatter, body}.

    Returns (errors, warnings) as lists of human-readable strings.
    """
    errors, warnings = [], []
    by_id = {}
    for s in skills:
        fm = s["frontmatter"]
        if isinstance(fm, dict) and isinstance(fm.get("id"), str):
            if fm["id"] in by_id:
                errors.append("[%s] duplicate skill id" % fm["id"])
            by_id[fm["id"]] = s

    for s in skills:
        path, fm, body = s["path"], s["frontmatter"], s["body"]
        loc = "%s (%s)" % (fm.get("id", "?"), os.path.relpath(path, FRAMEWORK_ROOT))

        # -- frontmatter parse errors are reported directly, never masked ---------
        if "parse_error" in fm:
            errors.append("[%s] frontmatter parse error: %s" % (loc, fm["parse_error"]))
            continue

        # -- required fields --------------------------------------------------
        missing = [f for f in REQUIRED_FIELDS if f not in fm]
        if missing:
            errors.append("[%s] missing required field(s): %s" % (loc, ", ".join(missing)))
            continue

        # -- id / category / kind --------------------------------------------
        if not ID_RE.match(fm["id"]):
            errors.append("[%s] invalid id (expected uesf-<cc>-<slug>)" % loc)
        m = re.match(r"^uesf-([a-z]{2})-", fm["id"])
        id_cat = m.group(1) if m else None
        if fm["category"] not in TAXONOMY:
            errors.append("[%s] unknown category %r" % (loc, fm["category"]))
        if id_cat and id_cat != fm["category"]:
            errors.append("[%s] id category %r does not match category %r"
                          % (loc, id_cat, fm["category"]))
        if fm["kind"] not in KINDS:
            errors.append("[%s] unknown kind %r" % (loc, fm["kind"]))
        if fm["kind"] == "example" and fm["category"] != "ex":
            errors.append("[%s] kind=example requires category=ex" % loc)
        if fm["category"] == "ex" and fm["kind"] != "example":
            errors.append("[%s] category=ex requires kind=example" % loc)
        if fm["kind"] == "core" and fm["category"] != "co":
            errors.append("[%s] kind=core requires category=co" % loc)
        if fm["kind"] == "meta" and fm["category"] != "mk":
            errors.append("[%s] kind=meta requires category=mk" % loc)

        # -- version -----------------------------------------------------------
        if not SEMVER_RE.match(str(fm["version"])):
            errors.append("[%s] invalid version %r (expected semver x.y.z)" % (loc, fm["version"]))

        # -- string / list / int types -----------------------------------------
        for f in STR_LIST_FIELDS:
            v = fm[f]
            if not isinstance(v, list):
                errors.append("[%s] field %r must be a list" % (loc, f))
            elif f == "triggers":
                bad = [x for x in v
                       if not (isinstance(x, str) or
                               (isinstance(x, dict)
                                and set(x) <= {"condition", "example_prompt"}
                                and all(isinstance(val, str) for val in x.values())))]
                if bad:
                    errors.append("[%s] field 'triggers' entries must be strings or {condition, example_prompt} maps" % loc)
            elif any(not isinstance(x, str) for x in v):
                errors.append("[%s] field %r must contain only strings" % (loc, f))
        for f in NON_EMPTY_LIST_FIELDS:
            if not isinstance(fm[f], list) or len(fm[f]) == 0:
                errors.append("[%s] field %r must be a non-empty list" % (loc, f))
        for f in ("purpose", "description", "rollback", "failure_recovery"):
            if not isinstance(fm[f], str) or len(fm[f]) < 10:
                errors.append("[%s] field %r must be a string of length >= 10" % (loc, f))
        if isinstance(fm.get("description"), str) and len(fm["description"]) > 1024:
            errors.append("[%s] description exceeds 1024 characters" % loc)
        for f in SCORE_FIELDS:
            v = fm[f]
            if not isinstance(v, int) or isinstance(v, bool) or not (1 <= v <= 5):
                errors.append("[%s] score %r must be an integer 1..5" % (loc, f))
        cost = fm.get("cost")
        if not isinstance(cost, dict) or not {"input_tokens", "output_tokens",
                                              "runtime_minutes"} <= set(cost):
            errors.append("[%s] cost must be a map with input_tokens, output_tokens, runtime_minutes" % loc)

        # -- required body sections ---------------------------------------------
        sections = _body_sections(body)
        missing_sec = [sec for sec in REQUIRED_SECTIONS if ("## " + sec) not in body]
        if missing_sec:
            errors.append("[%s] missing body section(s): %s"
                          % (loc, ", ".join(missing_sec)))

        # -- naming ---------------------------------------------------------------
        dir_name = os.path.basename(os.path.dirname(path))
        if dir_name != fm["id"]:
            warnings.append("[%s] directory %r should match id" % (loc, dir_name))

        # -- dependencies / related skills -----------------------------------------
        for dep in fm.get("dependencies", []):
            if dep == fm["id"]:
                errors.append("[%s] self-dependency on %s" % (loc, dep))
            elif dep not in by_id:
                errors.append("[%s] unresolved dependency %r" % (loc, dep))
        for rel in fm.get("related_skills", []):
            if rel not in by_id:
                warnings.append("[%s] related skill %r not found in inventory" % (loc, rel))

        # -- deprecation -----------------------------------------------------------
        if fm.get("deprecated") is True and not fm.get("superseded_by"):
            warnings.append("[%s] deprecated skill has no superseded_by" % loc)

    # -- cycle detection over dependencies ------------------------------------------
    ids = set(by_id)
    graph = {}
    for s in skills:
        fm = s["frontmatter"]
        if isinstance(fm, dict) and isinstance(fm.get("id"), str):
            graph[fm["id"]] = [d for d in fm.get("dependencies", []) if d in ids]
    state = {}

    def visit(node, trail):
        state[node] = 1
        for nxt in graph.get(node, []):
            if nxt == node:
                continue  # self-dependency already reported above
            if state.get(nxt) == 1:
                cycle = trail[trail.index(nxt):] + [nxt]
                errors.append("dependency cycle detected: %s" % " -> ".join(cycle))
            elif state.get(nxt) != 2:
                visit(nxt, trail + [nxt])
        state[node] = 2

    for node in graph:
        if state.get(node) != 2:
            visit(node, [node])

    return errors, warnings


def load_skill(path: str):
    """Load a single SKILL.md into a record {path, frontmatter, body}."""
    with open(path, "r", encoding="utf-8") as fh:
        text = fh.read()
    try:
        fm = parse_frontmatter(text)
    except Exception as exc:  # noqa: BLE001 — report any parse failure
        fm = {"parse_error": str(exc)}
    parts = text.split("\n---\n")
    body = parts[2] if len(parts) >= 3 else text
    return {"path": path, "frontmatter": fm, "body": body}


def scan_skills(root: str, roots=SKILL_ROOTS):
    """Return a list of skill records {path, frontmatter, body} found under roots."""
    skills = []
    for sub in roots:
        base = os.path.join(root, sub)
        if not os.path.isdir(base):
            continue
        for dirpath, dirnames, filenames in os.walk(base):
            if "SKILL.md" in filenames:
                skills.append(load_skill(os.path.join(dirpath, "SKILL.md")))
    return skills


def validate_framework(root: str = FRAMEWORK_ROOT, roots=SKILL_ROOTS):
    """Full validation pass. Returns a report dict."""
    skills = scan_skills(root, roots)
    errors, warnings = validate_skills(skills)
    return {
        "framework": "UESF",
        "validator_version": "1.0.0",
        "skill_roots": list(roots),
        "skills_scanned": len(skills),
        "errors": errors,
        "warnings": warnings,
        "pass": len(errors) == 0,
    }


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def _print_table(skills):
    header = "%-38s %-12s %-12s %-10s %s" % ("id", "category", "kind", "version", "path")
    print(header)
    print("-" * len(header))
    for s in sorted(skills, key=lambda x: x["frontmatter"].get("id", "")):
        fm = s["frontmatter"]
        if not isinstance(fm, dict):
            continue
        print("%-38s %-12s %-12s %-10s %s" % (
            fm.get("id", "?"), fm.get("category", "?"), fm.get("kind", "?"),
            fm.get("version", "?"), os.path.relpath(s["path"], FRAMEWORK_ROOT)))


def _print_graph(skills):
    by_id = {s["frontmatter"].get("id"): s for s in skills
             if isinstance(s["frontmatter"], dict) and s["frontmatter"].get("id")}
    print("Skill dependency graph (%d nodes)" % len(by_id))
    print("-" * 60)
    for sid in sorted(by_id):
        deps = by_id[sid]["frontmatter"].get("dependencies", [])
        if deps:
            for d in deps:
                print("  %s  -->  %s" % (sid, d))
        else:
            print("  %s  (no dependencies)" % sid)


def main(argv=None):
    ap = argparse.ArgumentParser(description="UESF Framework Validator")
    ap.add_argument("--list", action="store_true", help="list all skills and exit")
    ap.add_argument("--graph", action="store_true", help="print the dependency graph and exit")
    ap.add_argument("--json", metavar="FILE", help="write the full report as JSON")
    ap.add_argument("--quiet", action="store_true", help="only print errors/warnings")
    ap.add_argument("--root", default=FRAMEWORK_ROOT, help="framework root (default: repo)")
    args = ap.parse_args(argv)

    skills = scan_skills(args.root, SKILL_ROOTS)
    if args.list:
        _print_table(skills)
        return 0
    if args.graph:
        _print_graph(skills)
        return 0

    report = validate_framework(args.root)
    if args.json:
        if args.json == "-":
            json.dump(report, sys.stdout, indent=2)
            sys.stdout.write("\n")
        else:
            with open(args.json, "w", encoding="utf-8") as fh:
                json.dump(report, fh, indent=2)
                fh.write("\n")

    # When JSON goes to stdout, the human report goes to stderr so stdout stays
    # pure JSON (machine-consumable).
    out = sys.stderr if args.json == "-" else sys.stdout
    if not args.quiet:
        print("UESF Framework Validator v%s" % report["validator_version"], file=out)
        print("Scanned %d skills under: %s" % (report["skills_scanned"],
                                               ", ".join(report["skill_roots"])), file=out)
        print("-" * 60, file=out)
    for e in report["errors"]:
        print("ERROR: %s" % e, file=out)
    for w in report["warnings"]:
        print("WARN : %s" % w, file=out)
    print("-" * 60, file=out)
    if report["pass"]:
        print("PASS — %d skills, %d errors, %d warnings"
              % (report["skills_scanned"], len(report["errors"]), len(report["warnings"])), file=out)
        return 0
    print("FAIL — %d errors, %d warnings" % (len(report["errors"]), len(report["warnings"])), file=out)
    return 1


if __name__ == "__main__":
    sys.exit(main())
