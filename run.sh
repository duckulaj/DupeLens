#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p data/thumbs data/tmp
if ! java -version 2>&1 | grep -Eq 'version "(2[1-9]|[3-9][0-9])'; then
  echo "DupeLens requires Java 21 or newer." >&2
  exit 1
fi
exec ./mvnw spring-boot:run
