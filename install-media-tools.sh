#!/usr/bin/env bash
set -euo pipefail
if ! command -v apt >/dev/null 2>&1; then
  echo "This helper is for Debian/Ubuntu/Linux Mint systems." >&2; exit 1
fi
echo "Installing FFmpeg, gio and xdg-open support..."
sudo apt update
sudo apt install -y ffmpeg libglib2.0-bin xdg-utils
