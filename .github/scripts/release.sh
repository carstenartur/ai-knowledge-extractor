#!/usr/bin/env bash
set -euo pipefail

: "${RELEASE_VERSION:?RELEASE_VERSION is required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
: "${METADATA_HELPER:?METADATA_HELPER is required}"

NEXT_VERSION_INPUT=${NEXT_VERSION_INPUT:-}
SKIP_TESTS=${SKIP_TESTS:-false}
DRY_RUN=${DRY_RUN:-false}
SOURCE_BRANCH=${SOURCE_BRANCH:-main}

TAG_NAME="v${RELEASE_VERSION}"
RELEASE_BRANCH="release/${TAG_NAME}"
MAVEN_PLUGIN_DESCRIPTOR="maven/src/main/resources/META-INF/maven/plugin.xml"

if ! [[ "$RELEASE_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "::error::release_version must use X.Y.Z without a leading v"
  exit 1
fi

if [[ "$SOURCE_BRANCH" != "main" && "$DRY_RUN" != "true" ]]; then
  echo "::error::Real releases must be dispatched from main, not ${SOURCE_BRANCH}"
  exit 1
fi

current_project_version() {
  grep -E '^projectVersion=' gradle.properties | head -n 1 | cut -d'=' -f2 | tr -d '[:space:]'
}

set_project_version() {
  local version=$1
  python3 - "$version" <<'PY'
from pathlib import Path
import sys

version = sys.argv[1]
path = Path("gradle.properties")
lines = path.read_text(encoding="utf-8").splitlines()
updated = False
for index, line in enumerate(lines):
    if line.startswith("projectVersion="):
        lines[index] = f"projectVersion={version}"
        updated = True
        break
if not updated:
    lines.append(f"projectVersion={version}")
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
}

set_maven_plugin_descriptor_version() {
  local version=$1
  python3 - "$version" "$MAVEN_PLUGIN_DESCRIPTOR" <<'PY'
from pathlib import Path
import re
import sys

version = sys.argv[1]
path = Path(sys.argv[2])
text = path.read_text(encoding="utf-8")
text, count = re.subn(r"(<version>)[^<]+(</version>)", rf"\g<1>{version}\g<2>", text, count=1)
if count != 1:
    raise SystemExit(f"Could not update version in {path}")
path.write_text(text, encoding="utf-8")
PY
}

set_next_release_version() {
  local next_release=$1
  python3 - "$next_release" <<'PY'
from pathlib import Path
import sys

next_release = sys.argv[1]
path = Path("release.properties")
lines = path.read_text(encoding="utf-8").splitlines() if path.exists() else []
updated = False
for index, line in enumerate(lines):
    if line.startswith("next.release.version="):
        lines[index] = f"next.release.version={next_release}"
        updated = True
        break
if not updated:
    lines.append(f"next.release.version={next_release}")
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
}

verify_metadata() {
  local expected=$1
  local release_mode=$2
  local project_version
  project_version=$(current_project_version)

  if [[ "$project_version" != "$expected" ]]; then
    echo "::error::gradle.properties projectVersion '${project_version}' does not match expected '${expected}'"
    exit 1
  fi

  grep -q "^version: \"${expected}\"$" CITATION.cff || {
    echo "::error::CITATION.cff version does not match ${expected}"
    exit 1
  }

  grep -q "<version>${expected}</version>" "$MAVEN_PLUGIN_DESCRIPTOR" || {
    echo "::error::Maven plugin descriptor version does not match ${expected}"
    exit 1
  }

  EXPECTED_VERSION="$expected" RELEASE_MODE="$release_mode" python3 - <<'PY'
import json
import os

with open('.zenodo.json', encoding='utf-8') as handle:
    data = json.load(handle)
expected = os.environ['EXPECTED_VERSION']
if data.get('version') != expected:
    raise SystemExit(f'.zenodo.json version {data.get("version")!r} != {expected!r}')
if os.environ['RELEASE_MODE'] == 'true':
    if not data.get('publication_date'):
        raise SystemExit('.zenodo.json publication_date is missing')
else:
    if 'publication_date' in data:
        raise SystemExit('.zenodo.json still contains publication_date')
PY
}

CURRENT_VERSION=$(current_project_version)
if [[ "$CURRENT_VERSION" != *-SNAPSHOT ]]; then
  echo "::error::Current project version must be a SNAPSHOT, but was ${CURRENT_VERSION}"
  exit 1
fi
if [[ "${CURRENT_VERSION%-SNAPSHOT}" != "$RELEASE_VERSION" ]]; then
  echo "::error::Release ${RELEASE_VERSION} does not match current project version ${CURRENT_VERSION}"
  exit 1
fi

EXPECTED_RELEASE=$(grep -E '^next.release.version=' release.properties | head -n 1 | cut -d'=' -f2 | tr -d '[:space:]' || true)
if [[ -n "$EXPECTED_RELEASE" && "$EXPECTED_RELEASE" != "$RELEASE_VERSION" ]]; then
  echo "::warning::release_version ${RELEASE_VERSION} differs from release.properties suggestion ${EXPECTED_RELEASE}"
fi

if [[ -n "$NEXT_VERSION_INPUT" ]]; then
  NEXT_VERSION=$NEXT_VERSION_INPUT
else
  IFS='.' read -r MAJOR MINOR PATCH <<< "$RELEASE_VERSION"
  NEXT_VERSION="${MAJOR}.${MINOR}.$((PATCH + 1))-SNAPSHOT"
fi
if ! [[ "$NEXT_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]]; then
  echo "::error::next_development_version must use X.Y.Z-SNAPSHOT"
  exit 1
fi

verify_metadata "$CURRENT_VERSION" false
gradle help

git fetch origin --tags --force
TAG_EXISTS=false
BRANCH_EXISTS=false
if git rev-parse "${TAG_NAME}^{commit}" >/dev/null 2>&1; then
  TAG_EXISTS=true
fi
if git rev-parse "origin/${RELEASE_BRANCH}^{commit}" >/dev/null 2>&1; then
  BRANCH_EXISTS=true
fi

RELEASE_STATE=$(gh api "repos/${GITHUB_REPOSITORY}/releases?per_page=100" \
  --jq ".[] | select(.tag_name == \"${TAG_NAME}\") | if .draft then \"draft\" else \"published\" end" \
  | head -n 1 || true)

if [[ -n "$RELEASE_STATE" && "$TAG_EXISTS" != "true" ]]; then
  echo "::error::A GitHub release exists for ${TAG_NAME}, but its tag is missing"
  exit 1
fi
if [[ "$TAG_EXISTS" == "false" && "$BRANCH_EXISTS" == "true" ]]; then
  echo "::error::Release branch ${RELEASE_BRANCH} exists without tag ${TAG_NAME}"
  exit 1
fi
if [[ "$TAG_EXISTS" == "true" && "$BRANCH_EXISTS" == "true" ]]; then
  TAG_COMMIT=$(git rev-parse "${TAG_NAME}^{commit}")
  BRANCH_COMMIT=$(git rev-parse "origin/${RELEASE_BRANCH}^{commit}")
  if [[ "$TAG_COMMIT" != "$BRANCH_COMMIT" ]]; then
    echo "::error::${TAG_NAME} and ${RELEASE_BRANCH} point to different commits"
    exit 1
  fi
fi

if [[ -n "$RELEASE_STATE" ]]; then
  STATE=$RELEASE_STATE
elif [[ "$TAG_EXISTS" == "true" ]]; then
  STATE=tagged
else
  STATE=new
fi

echo "Release state: ${STATE}"
echo "Release version: ${RELEASE_VERSION}"
echo "Next development version: ${NEXT_VERSION}"

if [[ "$STATE" == "new" ]]; then
  set_project_version "$RELEASE_VERSION"
  set_maven_plugin_descriptor_version "$RELEASE_VERSION"
  python3 "$METADATA_HELPER" "$RELEASE_VERSION" --release
  verify_metadata "$RELEASE_VERSION" true
  git add gradle.properties CITATION.cff .zenodo.json "$MAVEN_PLUGIN_DESCRIPTOR"
  git commit -m "Release version ${RELEASE_VERSION}"
else
  git checkout --detach "$TAG_NAME"
  verify_metadata "$RELEASE_VERSION" true
fi

if [[ "$SKIP_TESTS" == "true" ]]; then
  gradle clean build -x test
else
  gradle clean build
fi

rm -rf build/release-artifacts
mkdir -p build/release-artifacts
find core gradle-plugin maven -path '*/build/libs/*.jar' -type f -exec cp {} build/release-artifacts/ \;
ls -la build/release-artifacts

if [[ "$DRY_RUN" != "true" && "$STATE" == "new" ]]; then
  RELEASE_COMMIT=$(git rev-parse HEAD)
  git push origin "HEAD:refs/heads/${RELEASE_BRANCH}"
  TAG_SHA=$(gh api "repos/${GITHUB_REPOSITORY}/git/tags" \
    --method POST \
    -f tag="$TAG_NAME" \
    -f message="Release version ${RELEASE_VERSION}" \
    -f object="$RELEASE_COMMIT" \
    -f type="commit" \
    --jq '.sha')
  gh api "repos/${GITHUB_REPOSITORY}/git/refs" \
    --method POST \
    -f ref="refs/tags/${TAG_NAME}" \
    -f sha="$TAG_SHA"
  STATE=tagged
fi

if [[ "$DRY_RUN" != "true" && "$STATE" != "published" ]]; then
  gradle publish "-PreleaseVersion=${RELEASE_VERSION}"
fi

if [[ "$DRY_RUN" != "true" && "$STATE" == "tagged" ]]; then
  gh release create "$TAG_NAME" \
    --verify-tag \
    --draft \
    --title "AI Knowledge Extractor ${RELEASE_VERSION}" \
    --generate-notes
  STATE=draft
fi

if [[ "$DRY_RUN" != "true" && "$STATE" == "draft" ]]; then
  mapfile -d '' ARTIFACTS < <(find build/release-artifacts -type f -print0)
  if [[ ${#ARTIFACTS[@]} -gt 0 ]]; then
    gh release upload "$TAG_NAME" "${ARTIFACTS[@]}" --clobber
  fi
  gh release edit "$TAG_NAME" --draft=false --latest
  STATE=published
fi

if [[ "$DRY_RUN" != "true" ]]; then
  IS_DRAFT=$(gh release view "$TAG_NAME" --json isDraft --jq '.isDraft')
  test "$IS_DRAFT" = false
fi

set_project_version "$NEXT_VERSION"
set_maven_plugin_descriptor_version "$NEXT_VERSION"
python3 "$METADATA_HELPER" "$NEXT_VERSION"
set_next_release_version "${NEXT_VERSION%-SNAPSHOT}"
verify_metadata "$NEXT_VERSION" false

NEXT_BRANCH="release/prepare-next-${NEXT_VERSION}"
git switch -C "$NEXT_BRANCH"
git add gradle.properties CITATION.cff .zenodo.json release.properties "$MAVEN_PLUGIN_DESCRIPTOR"
git commit -m "Prepare next development version ${NEXT_VERSION}"

if [[ "$DRY_RUN" != "true" ]]; then
  REMOTE_SHA=$(git ls-remote --heads origin "refs/heads/${NEXT_BRANCH}" | awk '{print $1}')
  if [[ -n "$REMOTE_SHA" ]]; then
    git push \
      --force-with-lease="refs/heads/${NEXT_BRANCH}:${REMOTE_SHA}" \
      origin "HEAD:refs/heads/${NEXT_BRANCH}"
  else
    git push origin "HEAD:refs/heads/${NEXT_BRANCH}"
  fi

  cat > /tmp/next-development-pr.md <<EOF
Automated follow-up after release ${RELEASE_VERSION}.

## Changes
- Bump Gradle projectVersion to ${NEXT_VERSION}
- Update Maven plugin descriptor to ${NEXT_VERSION}
- Update CITATION.cff to ${NEXT_VERSION}
- Update .zenodo.json to ${NEXT_VERSION}
- Remove release-only date metadata from the development snapshot
- Update release.properties for the next release cycle
EOF

  EXISTING_PR=$(gh pr list --base main --head "$NEXT_BRANCH" \
    --state open --json number --jq '.[0].number // empty')
  if [[ -n "$EXISTING_PR" ]]; then
    gh pr edit "$EXISTING_PR" \
      --title "Prepare next development version ${NEXT_VERSION}" \
      --body-file /tmp/next-development-pr.md
  else
    gh pr create \
      --title "Prepare next development version ${NEXT_VERSION}" \
      --body-file /tmp/next-development-pr.md \
      --base main \
      --head "$NEXT_BRANCH"
  fi
else
  echo 'Dry run completed; no remote refs, release or PR were changed.'
fi
