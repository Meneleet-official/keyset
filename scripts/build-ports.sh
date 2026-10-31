#!/usr/bin/env bash
# Builds the per-version ports and collects their JARs under build/mods/<minecraft-version>/.
# Usage: scripts/build-ports.sh [minecraft-version ...]   (default: every port)
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
versions=("$@")
if [ "${#versions[@]}" -eq 0 ]; then versions=(26.2 1.21.11 1.21.1 1.20.1); fi

for version in "${versions[@]}"; do
    project="$root/ports/$version"
    [ -d "$project" ] || { echo "Unknown port: $version" >&2; exit 1; }
    # collectMods builds only the Fabric and NeoForge artifacts (no test task).
    bash "$root/gradlew" -p "$project" collectMods --console=plain
    destination="$root/build/mods/$version"
    mkdir -p "$destination"
    count=0
    for jar in "$project"/build/mods/*.jar; do
        [ -f "$jar" ] || continue
        cp -f "$jar" "$destination/"
        count=$((count + 1))
    done
    [ "$count" -ge 2 ] || { echo "Missing Fabric or NeoForge JAR for Minecraft $version" >&2; exit 1; }
done

python3 "$root/scripts/verify-port-jars.py" "${versions[@]}"
