#!/usr/bin/env bash
set -euo pipefail

: "${RELEASED_VERSION:?RELEASED_VERSION is required}"
: "${NEXT_DEVELOPMENT_VERSION:?NEXT_DEVELOPMENT_VERSION is required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
RELEASE_LINES_FILE=${RELEASE_LINES_FILE:-.github/release-lines.json}
SOURCE_BRANCH=${SOURCE_BRANCH:-main}
METADATA_HELPER=${METADATA_HELPER:-.github/scripts/update-release-metadata.py}
DRY_RUN=${DRY_RUN:-false}

trim() {
  local value=${1-}
  printf '%s' "$value" | tr -d '\r' | sed -E 's/^[[:space:]]+//;s/[[:space:]]+$//'
}

RELEASED_VERSION=$(trim "$RELEASED_VERSION")
NEXT_DEVELOPMENT_VERSION=$(trim "$NEXT_DEVELOPMENT_VERSION")
SOURCE_BRANCH=$(trim "$SOURCE_BRANCH")
DRY_RUN=$(trim "$DRY_RUN")

if ! [[ "$RELEASED_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "::error::released_version must use X.Y.Z without a leading v; got '${RELEASED_VERSION}'" >&2
  exit 1
fi
if ! [[ "$NEXT_DEVELOPMENT_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]]; then
  echo "::error::next_development_version must use X.Y.Z-SNAPSHOT; got '${NEXT_DEVELOPMENT_VERSION}'" >&2
  exit 1
fi
if [[ "$DRY_RUN" != "true" && "$DRY_RUN" != "false" ]]; then
  echo "::error::DRY_RUN must be true or false" >&2
  exit 1
fi

POLICY_ENV=$(mktemp)
trap 'rm -f "$POLICY_ENV"' EXIT
python3 "$SCRIPT_DIR/release_line_policy.py" \
  --policy "$RELEASE_LINES_FILE" \
  resolve \
  --branch "$SOURCE_BRANCH" \
  --release-version "$RELEASED_VERSION" \
  --dry-run "$DRY_RUN" \
  --env-file "$POLICY_ENV"
# shellcheck disable=SC1090
source "$POLICY_ENV"
python3 "$SCRIPT_DIR/release_line_policy.py" \
  --policy "$RELEASE_LINES_FILE" \
  validate-next \
  --branch "$SOURCE_BRANCH" \
  --release-version "$RELEASED_VERSION" \
  --next-version "$NEXT_DEVELOPMENT_VERSION" \
  --dry-run "$DRY_RUN"

CURRENT_VERSION=$(grep -E '^projectVersion=' gradle.properties | head -n 1 | cut -d'=' -f2 | tr -d '[:space:]')
EXPECTED_CURRENT="${RELEASED_VERSION}-SNAPSHOT"
if [[ "$CURRENT_VERSION" != "$EXPECTED_CURRENT" ]]; then
  echo "::error::${SOURCE_BRANCH} must still be on ${EXPECTED_CURRENT}, but contains ${CURRENT_VERSION}" >&2
  exit 1
fi

TAG_NAME="v${RELEASED_VERSION}"
if ! gh release view "$TAG_NAME" --json isDraft --jq '.isDraft == false' | grep -q true; then
  echo "::error::GitHub release ${TAG_NAME} must exist and be published" >&2
  exit 1
fi

set_project_version() {
  local version=$1
  python3 - "$version" <<'PY'
from pathlib import Path
import re
import sys

version = sys.argv[1]
path = Path("gradle.properties")
text = path.read_text(encoding="utf-8")
text, count = re.subn(r"^projectVersion=.*$", f"projectVersion={version}", text, count=1, flags=re.MULTILINE)
if count != 1:
    raise SystemExit("Could not update projectVersion")
path.write_text(text, encoding="utf-8")
PY
}

set_release_property() {
  local version=$1
  python3 - "$version" <<'PY'
from pathlib import Path
import re
import sys

version = sys.argv[1]
path = Path("release.properties")
text = path.read_text(encoding="utf-8")
text, count = re.subn(r"^next\.release\.version=.*$", f"next.release.version={version}", text, count=1, flags=re.MULTILINE)
if count != 1:
    raise SystemExit("Could not update next.release.version")
path.write_text(text, encoding="utf-8")
PY
}

set_maven_plugin_descriptor_version() {
  local version=$1
  python3 - "$version" <<'PY'
from pathlib import Path
import re
import sys

version = sys.argv[1]
path = Path("maven/src/main/resources/META-INF/maven/plugin.xml")
text = path.read_text(encoding="utf-8")
text, count = re.subn(r"(<version>)[^<]+(</version>)", rf"\g<1>{version}\g<2>", text, count=1)
if count != 1:
    raise SystemExit("Could not update Maven plugin descriptor version")
path.write_text(text, encoding="utf-8")
PY
}

set_build_fallback_version() {
  local version=$1
  python3 - "$version" <<'PY'
from pathlib import Path
import re
import sys

version = sys.argv[1]
path = Path("build.gradle")
text = path.read_text(encoding="utf-8")
updated, count = re.subn(
    r"(findProperty\('projectVersion'\) \?: ')[^']+(')",
    rf"\g<1>{version}\g<2>",
    text,
    count=1,
)
if count == 1:
    path.write_text(updated, encoding="utf-8")
PY
}

NEXT_RELEASE_VERSION=${NEXT_DEVELOPMENT_VERSION%-SNAPSHOT}
set_project_version "$NEXT_DEVELOPMENT_VERSION"
set_release_property "$NEXT_RELEASE_VERSION"
set_maven_plugin_descriptor_version "$NEXT_DEVELOPMENT_VERSION"
set_build_fallback_version "$NEXT_DEVELOPMENT_VERSION"
python3 "$METADATA_HELPER" "$NEXT_DEVELOPMENT_VERSION"

if [[ "$RELEASE_SOURCE_STATUS" == "dry-run" ]]; then
  python3 "$SCRIPT_DIR/verify-version-consistency.py"
else
  AI_KNOWLEDGE_TARGET_BRANCH="$RELEASE_NEXT_PR_BASE" \
    python3 "$SCRIPT_DIR/verify-version-consistency.py"
fi

SAFE_LINE=${RELEASE_SOURCE_LINE//[^A-Za-z0-9._-]/-}
NEXT_BRANCH="release/${SAFE_LINE}/prepare-next-${NEXT_DEVELOPMENT_VERSION}"
git switch -C "$NEXT_BRANCH"
git add \
  gradle.properties \
  release.properties \
  build.gradle \
  CITATION.cff \
  .zenodo.json \
  maven/src/main/resources/META-INF/maven/plugin.xml \
  site/pom.xml \
  examples/maven-consumer/pom.xml \
  examples/fixtures/maven-consumer/pom.xml

git commit -m "Prepare ${RELEASE_NEXT_PR_BASE} for ${NEXT_DEVELOPMENT_VERSION}"

if [[ "$DRY_RUN" == "true" ]]; then
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
Manually requested next-development transition for the supported **${RELEASE_SOURCE_LINE}** line.

- Target branch: \`${RELEASE_NEXT_PR_BASE}\`
- Released version: \`${RELEASED_VERSION}\`
- Next development version: \`${NEXT_DEVELOPMENT_VERSION}\`
- Support status: \`${RELEASE_SOURCE_STATUS}\`

This transition affects only \`${RELEASE_NEXT_PR_BASE}\`; it cannot downgrade another supported
line. The generated PR is verified and merged by the same release-follow-up workflow used after a
normal release.

<!-- release-follow-up-run: ${GITHUB_RUN_ID:-local} -->
EOF

EXISTING_PR=$(gh pr list \
  --base "$RELEASE_NEXT_PR_BASE" \
  --head "$NEXT_BRANCH" \
  --state open \
  --json number \
  --jq '.[0].number // empty')
if [[ -n "$EXISTING_PR" ]]; then
  gh pr edit "$EXISTING_PR" \
    --title "Prepare ${RELEASE_NEXT_PR_BASE} for ${NEXT_DEVELOPMENT_VERSION}" \
    --body-file /tmp/prepare-next-pr.md
else
  gh pr create \
    --draft \
    --title "Prepare ${RELEASE_NEXT_PR_BASE} for ${NEXT_DEVELOPMENT_VERSION}" \
    --body-file /tmp/prepare-next-pr.md \
    --base "$RELEASE_NEXT_PR_BASE" \
    --head "$NEXT_BRANCH"
fi
