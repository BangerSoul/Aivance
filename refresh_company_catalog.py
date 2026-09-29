#!/usr/bin/env python3
"""
Regenerates the bundled remote-company catalog snapshot used by R-02.

Source: remoteintech/remote-jobs (https://github.com/remoteintech/remote-jobs),
ISC-licensed. The dataset lives as per-company Markdown files with YAML
frontmatter under `src/companies/*.md` on the `main` branch. This script:

  1. Fetches the repo tree via the GitHub API and selects `src/companies/*.md`.
  2. Downloads each file and parses the YAML frontmatter (flat key: value
     scalars plus `key:\n  - item` lists — the subset the dataset uses).
  3. Writes a compact JSON array to `core/data/src/main/assets/company_catalog.json`,
     sorted by company name, with the exact fields the app's
     `CompanyCatalogEntry` serializer expects:

       name, website, careers_url, region, remote_policy,
       company_size, technologies

Usage:
    python3 refresh_company_catalog.py            # fetch + regenerate snapshot
    python3 refresh_company_catalog.py --dry-run  # report stats without writing

Run it whenever the upstream dataset needs refreshing. The generated file is a
static snapshot on purpose: the app bundles it so discovery filtering and
company enrichment work fully offline with zero network calls.
"""

import argparse
import json
import re
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed

REPO = "remoteintech/remote-jobs"
BRANCH = "main"
TREE_URL = f"https://api.github.com/repos/{REPO}/git/trees/{BRANCH}?recursive=1"
RAW_BASE = f"https://raw.githubusercontent.com/{REPO}/{BRANCH}/"
OUT_PATH = "core/data/src/main/assets/company_catalog.json"

# Frontmatter fields surfaced to the app (dataset key -> snapshot key).
FIELD_MAP = {
    "title": "name",
    "website": "website",
    "careers_url": "careers_url",
    "region": "region",
    "remote_policy": "remote_policy",
    "company_size": "company_size",
    "technologies": "technologies",
}

SCALAR_KEYS = {"title", "website", "careers_url", "region", "remote_policy", "company_size"}


def fetch(url: str, timeout: int = 20) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "aivance-catalog-refresh"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()


def parse_frontmatter(md: str) -> dict:
    """Parses the flat YAML frontmatter subset used by remoteintech entries."""
    match = re.match(r"^---\n(.*?)\n---", md, re.DOTALL)
    if not match:
        return {}
    result = {}
    current_list_key = None
    for raw_line in match.group(1).splitlines():
        line = raw_line.rstrip()
        if not line.strip():
            continue
        list_item = re.match(r"^\s*-\s+(.+)$", line)
        if list_item:
            if current_list_key is not None:
                result.setdefault(current_list_key, []).append(
                    list_item.group(1).strip().strip('"\'')
                )
            continue
        kv = re.match(r"^([A-Za-z_]+):\s*(.*)$", line)
        if not kv:
            continue
        key, value = kv.group(1), kv.group(2).strip()
        current_list_key = key if key not in SCALAR_KEYS else None
        if key in SCALAR_KEYS:
            result[key] = value.strip('"\'') if value else None
    return result


def to_snapshot_entry(frontmatter: dict) -> dict | None:
    name = frontmatter.get("title")
    if not name:
        return None
    entry = {}
    for src_key, dst_key in FIELD_MAP.items():
        value = frontmatter.get(src_key)
        if value in (None, "", []):
            continue
        if src_key == "technologies":
            # Normalize to lowercase slugs, dedup, keep order.
            seen, cleaned = set(), []
            for tech in value:
                t = tech.strip().lower()
                if t and t not in seen:
                    seen.add(t)
                    cleaned.append(t)
            if not cleaned:
                continue
            value = cleaned
        entry[dst_key] = value
    return entry


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="report stats without writing")
    args = parser.parse_args()

    print(f"Fetching repo tree: {REPO}@{BRANCH} ...")
    tree = json.loads(fetch(TREE_URL).decode())
    paths = [
        e["path"]
        for e in tree.get("tree", [])
        if e["path"].startswith("src/companies/") and e["path"].endswith(".md")
    ]
    print(f"Found {len(paths)} company files")

    def load(path: str):
        try:
            md = fetch(RAW_BASE + path, timeout=15).decode("utf-8", errors="replace")
        except Exception as exc:  # noqa: BLE001
            return None, f"{path}: {exc}"
        entry = to_snapshot_entry(parse_frontmatter(md))
        return (entry, None) if entry is not None else (None, f"{path}: no entry")

    entries = []
    skipped = 0
    done = 0
    with ThreadPoolExecutor(max_workers=12) as pool:
        futures = [pool.submit(load, p) for p in paths]
        for future in as_completed(futures):
            entry, err = future.result()
            if entry is not None:
                entries.append(entry)
            else:
                if err is not None:
                    print(f"  ! {err}", file=sys.stderr)
                skipped += 1
            done += 1
            if done % 200 == 0:
                print(f"  ... {done}/{len(paths)}")

    entries.sort(key=lambda e: e["name"].lower())
    print(f"Parsed {len(entries)} entries, skipped {skipped}")

    if args.dry_run:
        print("Dry run — no files written")
    else:
        import os
        os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
        with open(OUT_PATH, "w", encoding="utf-8") as fh:
            json.dump(entries, fh, separators=(",", ":"), ensure_ascii=False)
        print(f"Wrote {OUT_PATH} ({os.path.getsize(OUT_PATH) / 1024:.0f} KB)")

    from collections import Counter
    policies = Counter(e.get("remote_policy") for e in entries)
    print("remote_policy distribution:", dict(policies))
    return 0


if __name__ == "__main__":
    sys.exit(main())
