#!/usr/bin/env bash
set -euo pipefail
app_dir="$(cd "$(dirname "$0")" && pwd)"
cd "$app_dir"
mkdir -p data/thumbs data/tmp
if ! java -version 2>&1 | grep -Eq 'version "(2[5-9]|[3-9][0-9])'; then
  echo "DupeLens requires Java 25 or newer." >&2
  exit 1
fi
if [[ -f "$app_dir/dupelens.jar" ]]; then
  exec java -jar "$app_dir/dupelens.jar"
fi
exec ./mvnw spring-boot:run
