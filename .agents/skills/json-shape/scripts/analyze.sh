#!/usr/bin/env bash
# Thin wrapper around the json-schema-explorer CLI. Resolves a jar — preferring a local
# Maven build, falling back to the latest published GitHub release — then forwards every
# argument through untouched. Deliberately has no opinion about which flags to pass; see
# SKILL.md for that.
set -euo pipefail

REPO_SLUG="ssullivan/json-schema-analyzer"
CACHE_DIR="${JSON_SCHEMA_EXPLORER_CACHE:-$HOME/.cache/json-schema-explorer}"

# Walk up from the current directory looking for a built jar, so this still works when the
# agent has cd'd into a subdirectory of a clone. Mirrors the justfile's `run` recipe.
find_local_jar() {
    local dir jar
    dir=$(pwd -P)
    while [ "$dir" != "/" ]; do
        if [ -d "$dir/cli/target" ]; then
            jar=$(ls "$dir"/cli/target/json-schema-explorer-*.jar 2>/dev/null \
                  | grep -v -e sources -e javadoc -e original | head -1 || true)
            if [ -n "$jar" ]; then
                printf '%s\n' "$jar"
                return 0
            fi
        fi
        dir=$(dirname "$dir")
    done
    return 1
}

jar=$(find_local_jar || true)

if [ -z "$jar" ]; then
    for cmd in curl java; do
        command -v "$cmd" >/dev/null 2>&1 \
            || { echo "analyze.sh: '$cmd' is required but was not found on PATH" >&2; exit 1; }
    done

    mkdir -p "$CACHE_DIR"

    # Always resolve the latest release (a cheap API call) rather than trusting whatever is
    # already cached — otherwise the first jar ever fetched would be pinned forever, even
    # after newer releases add flags this skill depends on.
    api_json=$(curl -fsSL "https://api.github.com/repos/$REPO_SLUG/releases/latest") \
        || { echo "analyze.sh: could not reach GitHub to resolve the latest release" >&2; exit 1; }

    url=$(printf '%s' "$api_json" \
          | grep -o '"browser_download_url":[[:space:]]*"[^"]*\.jar"' \
          | head -1 | cut -d'"' -f4)
    [ -n "$url" ] \
        || { echo "analyze.sh: the latest release of $REPO_SLUG has no .jar asset" >&2; exit 1; }

    jar_name=$(basename "$url")
    jar="$CACHE_DIR/$jar_name"

    if [ ! -f "$jar" ]; then
        tmp="$jar.part.$$"
        sums="$tmp.sha256"
        trap 'rm -f "$tmp" "$sums"' EXIT
        curl -fsSL "$url" -o "$tmp" \
            || { echo "analyze.sh: failed to download $url" >&2; exit 1; }
        # Guard against a truncated download or an error page served with a 200.
        head -c 4 "$tmp" | grep -q $'PK\x03\x04' \
            || { echo "analyze.sh: downloaded file is not a valid jar" >&2; exit 1; }

        # Verify against the release's published checksum when both the .sha256 asset and a
        # sha256 tool are available. Releases before 1.1.0 have no checksum asset, so a
        # missing one is skipped rather than treated as a failure; a checksum that is present
        # but does not match is always fatal.
        if sha_cmd=$(command -v sha256sum || command -v shasum); then
            if curl -fsSL "$url.sha256" -o "$sums" 2>/dev/null; then
                expected=$(awk '{print $1}' "$sums")
                actual=$("$sha_cmd" "$tmp" | awk '{print $1}')
                if [ "$expected" != "$actual" ]; then
                    echo "analyze.sh: checksum mismatch for $jar_name" >&2
                    echo "  expected $expected" >&2
                    echo "  actual   $actual" >&2
                    exit 1
                fi
            else
                echo "analyze.sh: note: no published checksum for $jar_name; skipping verification" >&2
            fi
        fi

        mv "$tmp" "$jar"
        rm -f "$sums"
        trap - EXIT
    fi
fi

exec java -jar "$jar" "$@"
