#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")" && pwd)"
install_dir="$HOME/DupeLens"

cd "$project_dir"
./mvnw clean test package

mkdir -p "$install_dir"
install -m 0644 "$project_dir/target/dupelens-0.1.0.jar" "$install_dir/dupelens.jar"
install -m 0755 "$project_dir/run.sh" "$install_dir/run.sh"

printf '\nRunnable JAR deployed to: %s/dupelens.jar\n' "$install_dir"
printf 'Start DupeLens with: %s/run.sh\n' "$install_dir"
