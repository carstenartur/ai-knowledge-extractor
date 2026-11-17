#!/usr/bin/env bash
# Publish the tag already prepared and qualified by the release workflow.
set -euo pipefail
: "${VERSION:?Set VERSION to the qualified release version}"
: "${RELEASE_SHA:?Set RELEASE_SHA to the qualified release commit}"

root=$(cd "$(dirname "$0")/../.." && pwd)
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ && "$RELEASE_SHA" =~ ^[0-9a-f]{40}$ ]]
tag="v$VERSION"
if [[ $(git -C "$root" rev-parse --verify "refs/tags/${tag}^{commit}") != "$RELEASE_SHA" ]]; then
  echo "Tag $tag does not point to the qualified commit $RELEASE_SHA" >&2
  exit 1
fi

# Maven reads its own release descriptor next to the driver POM. Keep that
# state separate from the tracked Gradle release.properties and from retries.
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
git -C "$root" show "$RELEASE_SHA:release/pom.xml" > "$work/pom.xml"
printf 'scm.tag=%s\n' "$tag" > "$work/release.properties"
scm_url=$(python3 -c 'from pathlib import Path; import sys; print(Path(sys.argv[1]).as_uri())' "$root")
(cd "$work" && mvn -B -ntp org.apache.maven.plugins:maven-release-plugin:3.3.1:perform \
    "-DconnectionUrl=scm:git:$scm_url")
