#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./mvnw clean test package
printf '\nBuilt: target/dupelens-0.1.0.jar\n'
