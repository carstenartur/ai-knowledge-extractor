#!/usr/bin/env bash
# Check the Portal's own catalog. Its Maven repository can forward to Central.
set -euo pipefail
version=${1:?Release version required}
[[ "$version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]
[[ $# -eq 1 || ( $# -eq 2 && "$2" == --probe ) ]]

plugin=io.github.carstenartur.ai-knowledge-extractor
url="https://plugins.gradle.org/plugin/$plugin/$version"
page=$(mktemp)
trap 'rm -f "$page"' EXIT
status=$(curl -q --silent --show-error --retry 3 --connect-timeout 10 --max-time 30 \
  --output "$page" --write-out '%{http_code}' "$url")
case "$status" in
  200)
    # The version-specific page contains generated instructions for this exact marker.
    if ! grep -Fq "implementation(\"$plugin:$plugin.gradle.plugin:$version\")" "$page"; then
      echo "Portal listing at $url does not identify the requested plugin version" >&2
      exit 1
    fi
    listed=true
    ;;
  400|404)
    # The Portal uses 400 for unknown versions. A proxy error is not a catalog result.
    if ! grep -Fq "Version &quot;$version&quot; of plugin &quot;$plugin&quot; not found" "$page"; then
      echo "Unexpected HTTP $status response from $url" >&2
      exit 1
    fi
    listed=false
    ;;
  *)
    echo "Unexpected HTTP $status response from $url" >&2
    exit 1
    ;;
esac

if [[ ${2:-} == --probe ]]; then
  printf '%s\n' "$listed"
elif [[ "$listed" == true ]]; then
  printf 'Verified Gradle Plugin Portal listing %s\n' "$url"
else
  echo "Version $version is not yet listed at $url" >&2
  exit 1
fi
