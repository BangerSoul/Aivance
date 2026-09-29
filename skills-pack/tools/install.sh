#!/usr/bin/env bash
# Install skills from this pack into an agent's skills directory.
#
# Usage (run from the repo root, e.g. ./skills-pack/tools/install.sh ...):
#   ./skills-pack/tools/install.sh <skill-dir>            # install one skill
#   ./skills-pack/tools/install.sh <skill-dir> [dest]     # install into a custom dest
#   ./skills-pack/tools/install.sh --all                  # install every skill in the pack
#   ./skills-pack/tools/install.sh --all [dest]           # install everything into a custom dest
#
# Default destination: <repo-root>/.agents/skills/ (independent of CWD).
set -euo pipefail

PACK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Default to <repo-root>/.agents/skills regardless of invocation directory,
# so installing from skills-pack/ or the repo root behaves identically.
REPO_ROOT="$(dirname "$PACK_DIR")"
DEST="${2:-$REPO_ROOT/.agents/skills}"

install_one() {
  local src="$1"
  local name
  name="$(basename "$src")"
  if [[ ! -f "$src/SKILL.md" ]]; then
    echo "SKIP $name (no SKILL.md)"
    return
  fi
  mkdir -p "$DEST/$name"
  cp -r "$src/." "$DEST/$name/"
  echo "OK   $name -> $DEST/$name"
}

if [[ "${1:-}" == "--all" ]]; then
  mkdir -p "$DEST"
  for dir in "$PACK_DIR"/uesf-core/* "$PACK_DIR"/community/* "$PACK_DIR"/original/* "$PACK_DIR"/learned/*; do
    [[ -d "$dir" ]] && install_one "$dir"
  done
  echo "Installed all skills into $DEST"
else
  local_src="$PACK_DIR/${1:-}"
  if [[ -d "$local_src" ]]; then
    install_one "$local_src"
  elif [[ -n "${1:-}" && -d "$1" ]]; then
    install_one "$1"
  else
    echo "Usage: $0 <skill-dir | --all> [dest]" >&2
    echo "  skill-dir may be a path inside this pack (e.g. original/verify-before-claim) or an absolute path." >&2
    exit 1
  fi
fi
