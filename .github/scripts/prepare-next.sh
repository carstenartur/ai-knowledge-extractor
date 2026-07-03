#!/usr/bin/env bash
set -euo pipefail

: "${RELEASED_VERSION:?RELEASED_VERSION is required}"
: "${NEXT_DEVELOPMENT_VERSION:?NEXT_DEVELOPMENT_VERSION is required}"

trim() {
  local value=${1-}
  printf '%s' "$value" | tr -d '\r' | sed -E 's/^[[:space:]]+//;s/[[:space:]]+$//'
}

RELEASED_VERSION=$(trim "$RELEASED_VERSION")
NEXT_DEVELOPMENT_VERSION=$(trim "$NEXT_DEVELOPMENT_VERSION")
TAG_NAME="v${RELEASED_VERSION}"
NEXT_RELEASE_VERSION="${NEXT_DEVELOPMENT_VERSION%-SNAPSHOT}"
NEXT_BRANCH="release/prepare-next-${NEXT_DEVELOPMENT_VERSION}"

if ! [[ "$RELEASED_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "::error::released_version must use X.Y.Z without a leading v; got '${RELEASED_VERSION}'"
  exit 1
fi
if ! [[ "$NEXT_DEVELOPMENT_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]]; then
  echo "::error::next_development_version must use X.Y.Z-SNAPSHOT; got '${NEXT_DEVELOPMENT_VERSION}'"
  exit 1
fi
IFS='.' read -r NEXT_MAJOR NEXT_MINOR NEXT_PATCH <<< "$NEXT_RELEASE_VERSION"
AFTER_NEXT_VERSION="${NEXT_MAJOR}.${NEXT_MINOR}.$((NEXT_PATCH + 1))-SNAPSHOT"

CURRENT_VERSION=$(grep -E '^projectVersion=' gradle.properties | head -n 1 | cut -d'=' -f2 | tr -d '[:space:]')
EXPECTED_CURRENT="${RELEASED_VERSION}-SNAPSHOT"
if [[ "$CURRENT_VERSION" != "$EXPECTED_CURRENT" ]]; then
  echo "::error::main must still be on ${EXPECTED_CURRENT}, but gradle.properties contains ${CURRENT_VERSION}"
  exit 1
fi

if ! gh release view "$TAG_NAME" --json isDraft --jq '.isDraft == false' | grep -q true; then
  echo "::error::GitHub release ${TAG_NAME} must exist and be published"
  exit 1
fi

python3 - "$NEXT_DEVELOPMENT_VERSION" "$NEXT_RELEASE_VERSION" "$AFTER_NEXT_VERSION" <<'PY'
from pathlib import Path
import json
import re
import sys

next_snapshot = sys.argv[1]
next_release = sys.argv[2]
after_next = sys.argv[3]


def rewrite(path, callback):
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    new_text = callback(text)
    if new_text != text:
        p.write_text(new_text, encoding="utf-8")


rewrite("gradle.properties", lambda text: re.sub(r"^projectVersion=.*$", f"projectVersion={next_snapshot}", text, count=1, flags=re.MULTILINE))
rewrite("release.properties", lambda text: re.sub(r"^next\.release\.version=.*$", f"next.release.version={next_release}", text, count=1, flags=re.MULTILINE))
rewrite("build.gradle", lambda text: re.sub(r"(findProperty\('projectVersion'\) \?: ')[^']+(')", rf"\g<1>{next_snapshot}\g<2>", text, count=1))
rewrite("CITATION.cff", lambda text: re.sub(r'^version:\s*"[^"]+"$', f'version: "{next_snapshot}"', text, count=1, flags=re.MULTILINE))

zenodo_path = Path(".zenodo.json")
zenodo = json.loads(zenodo_path.read_text(encoding="utf-8"))
zenodo["version"] = next_snapshot
zenodo.pop("publication_date", None)
zenodo_path.write_text(json.dumps(zenodo, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

rewrite("maven/src/main/resources/META-INF/maven/plugin.xml", lambda text: re.sub(r"(<version>)[^<]+(</version>)", rf"\g<1>{next_snapshot}\g<2>", text, count=1))
rewrite("site/pom.xml", lambda text: re.sub(r"(<revision>)[^<]+(</revision>)", rf"\g<1>{next_snapshot}\g<2>", text, count=1))
for path in ("examples/maven-consumer/pom.xml", "examples/fixtures/maven-consumer/pom.xml"):
    rewrite(path, lambda text: re.sub(r"(<aiKnowledge\.version>)[^<]+(</aiKnowledge\.version>)", rf"\g<1>{next_snapshot}\g<2>", text, count=1))

def update_publish(text):
    text = re.sub(r"Release version to publish, without leading v, e\.g\. [0-9]+\.[0-9]+\.[0-9]+", f"Release version to publish, without leading v, e.g. {next_release}", text, count=1)
    text = re.sub(r"default: [0-9]+\.[0-9]+\.[0-9]+", f"default: {next_release}", text, count=1)
    text = re.sub(r"Optional next development version, e\.g\. [0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT", f"Optional next development version, e.g. {after_next}", text, count=1)
    return text
rewrite(".github/workflows/publish.yml", update_publish)
PY

python3 .github/scripts/verify-version-consistency.py

git switch -C "$NEXT_BRANCH"
git add gradle.properties release.properties build.gradle CITATION.cff .zenodo.json \
  maven/src/main/resources/META-INF/maven/plugin.xml site/pom.xml \
  examples/maven-consumer/pom.xml examples/fixtures/maven-consumer/pom.xml \
  .github/workflows/publish.yml

git commit -m "Prepare next development version ${NEXT_DEVELOPMENT_VERSION}"

if [[ "${DRY_RUN:-false}" == "true" ]]; then
  echo "Dry run completed; no branch or PR was pushed."
  exit 0
fi

REMOTE_SHA=$(git ls-remote --heads origin "refs/heads/${NEXT_BRANCH}" | awk '{print $1}')
if [[ -n "$REMOTE_SHA" ]]; then
  git push --force-with-lease="refs/heads/${NEXT_BRANCH}:${REMOTE_SHA}" origin "HEAD:refs/heads/${NEXT_BRANCH}"
else
  git push origin "HEAD:refs/heads/${NEXT_BRANCH}"
fi

cat > /tmp/prepare-next-pr.md <<EOF
Automated follow-up after release ${RELEASED_VERSION}.

## Changes
- Bump development metadata to ${NEXT_DEVELOPMENT_VERSION}
- Set release.properties to ${NEXT_RELEASE_VERSION}
- Remove release-only publication date metadata from .zenodo.json
EOF

EXISTING_PR=$(gh pr list --base main --head "$NEXT_BRANCH" --state open --json number --jq '.[0].number // empty')
if [[ -n "$EXISTING_PR" ]]; then
  gh pr edit "$EXISTING_PR" --title "Prepare next development version ${NEXT_DEVELOPMENT_VERSION}" --body-file /tmp/prepare-next-pr.md
else
  gh pr create --title "Prepare next development version ${NEXT_DEVELOPMENT_VERSION}" --body-file /tmp/prepare-next-pr.md --base main --head "$NEXT_BRANCH"
fi
