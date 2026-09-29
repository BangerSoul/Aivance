#!/usr/bin/env bash
# ============================================================================
# Aivance — root-level machine setup (Docker + Python tooling)
#
# Everything else (JDK 21, Android SDK, shell env, local.properties) is
# installed in user-space and does NOT need this script.
#
# Usage:
#   sudo ./scripts/setup_sudo.sh
#
# What it does:
#   1. Installs docker + docker-compose + python-pip via pacman
#   2. Enables & starts the Docker systemd service
#   3. Adds your user to the 'docker' group (so `docker` works without sudo)
#   4. Prints a verification summary
#
# After running it: log out and back in (or run `newgrp docker`) so the
# docker group membership takes effect, then re-run the user-space part:
#   ./scripts/build.sh assembleDebug
# ============================================================================
set -euo pipefail

REAL_USER="${SUDO_USER:-$USER}"
if [ "$(id -u)" -ne 0 ]; then
  echo "❌ This script must be run as root:  sudo $0" >&2
  exit 1
fi

echo "🚀 Installing Docker + Compose + Python pip for Aivance..."
pacman -S --needed --noconfirm docker docker-compose python-pip

echo "⚙️  Enabling and starting the Docker service..."
systemctl enable --now docker

if ! id -nG "$REAL_USER" | grep -qw docker; then
  echo "👥 Adding user '$REAL_USER' to the 'docker' group..."
  usermod -aG docker "$REAL_USER"
else
  echo "👥 User '$REAL_USER' is already in the 'docker' group."
fi

echo "📦 Verification:"
docker --version
# Compose is installed as either the v2 plugin ('docker compose') or the
# standalone binary ('docker-compose') depending on the distro package.
docker compose version || docker-compose version
docker info > /dev/null 2>&1 && echo "✅ Docker daemon is working (docker info OK)." || {
  echo "⚠️  Docker daemon did not pass 'docker info'." >&2
  echo "   On Arch/CachyOS this is often iptables/nftables: check:" >&2
  echo "   systemctl status docker   and   docker info" >&2
}

echo ""
echo "🎉 Done! Log out/in (or run: newgrp docker) to pick up the docker group."
echo "Then verify with:  docker run --rm hello-world"
