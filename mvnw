#!/usr/bin/env bash
set -euo pipefail
VERSION="3.9.16"
BASE="$(cd "$(dirname "$0")" && pwd)"
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/dupelens-maven"
MVN="$CACHE/apache-maven-$VERSION/bin/mvn"
if command -v mvn >/dev/null 2>&1; then exec mvn "$@"; fi
if [[ ! -x "$MVN" ]]; then
  mkdir -p "$CACHE"
  ARCHIVE="$CACHE/apache-maven-$VERSION-bin.tar.gz"
  URL="https://dlcdn.apache.org/maven/maven-3/$VERSION/binaries/apache-maven-$VERSION-bin.tar.gz"
  SUMURL="https://downloads.apache.org/maven/maven-3/$VERSION/binaries/apache-maven-$VERSION-bin.tar.gz.sha512"
  echo "Maven is not installed. Downloading Apache Maven $VERSION from Apache..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL "$URL" -o "$ARCHIVE"
    curl -fL "$SUMURL" -o "$ARCHIVE.sha512"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ARCHIVE" "$URL"
    wget -O "$ARCHIVE.sha512" "$SUMURL"
  else
    echo "Install Maven, curl, or wget first." >&2; exit 1
  fi
  (cd "$CACHE" && sha512sum -c "$(basename "$ARCHIVE").sha512")
  tar -xzf "$ARCHIVE" -C "$CACHE"
fi
exec "$MVN" "$@"
