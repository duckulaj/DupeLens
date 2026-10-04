#!/usr/bin/env bash
set -u
printf '%-16s %s\n' "Java" "$(java -version 2>&1 | head -1 || echo MISSING)"
for c in ffmpeg ffprobe gio xdg-open ollama; do
  if command -v "$c" >/dev/null 2>&1; then printf '%-16s %s\n' "$c" "OK ($(command -v "$c"))"; else printf '%-16s %s\n' "$c" "MISSING"; fi
done
if command -v ollama >/dev/null 2>&1; then
  echo; echo "Installed Ollama models:"; ollama list 2>/dev/null || true
fi
