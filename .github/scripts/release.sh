#!/usr/bin/env bash
set -euo pipefail

: "${RELEASE_VERSION:?RELEASE_VERSION is required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
: "${METADATA_HELPER:?METADATA_HELPER is required}"

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
RELEASE_LINES_FILE=${RELEASE_LINES_FILE:-.github/release-lines.json}
SOURCE_BRANCH=${SOURCE_BRANCH:-main}
NEXT_VERSION_INPUT=${NEXT_VERSION_INPUT:-}
SKIP_TESTS=${SKIP_TESTS:-false}
DRY_RUN=${DRY_RUN:-false}

trim() {
  local value=${1-}
  printf '%s' "$value" | tr -d '\r' | sed -E 's/^[[:space:]]+//;s/[[:space:]]+$//'
}

RELEASE_VERSION=$(trim "$RELEASE_VERSION")
SOURCE_BRANCH=$(trim "$SOURCE_BRANCH")
NEXT_VERSION_INPUT=$(trim "$NEXT_VERSION_INPUT")
SKIP_TESTS=$(trim "$SKIP_TESTS")
DRY_RUN=$(trim "$DRY_RUN")

if ! [[ "$RELEASE_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "::error::release_version must use X.Y.Z without a leading v; got '${RELEASE_VERSION}'" >&2
  exit 1
fi
for pair in "SKIP_TESTS:$SKIP_TESTS" "DRY_RUN:$DRY_RUN"; do
  name=${pair%%:*}
  value=${pair#*:}
  if [[ "$value" != "true" && "$value" != "false" ]]; then
    echo "::error::${name} must be true or false; got '${value}'" >&2
    exit 1
  fi
done

POLICY_ENV=$(mktemp)
trap 'rm -f "$POLICY_ENV"' EXIT
python3 "$SCRIPT_DIR/release_line_policy.py" \
  --policy "$RELEASE_LINES_FILE" \
  resolve \
  --branch "$SOURCE_BRANCH" \
  --release-version "$RELEASE_VERSION" \
  --dry-run "$DRY_RUN" \
  --env-file "$POLICY_ENV"
# The file contains only shlex-quoted values emitted by the validated policy parser.
# shellcheck disable=SC1090
source "$POLICY_ENV"

TAG_NAME="v${RELEASE_VERSION}"
RELEASE_BRANCH="release/${TAG_NAME}"
MAVEN_PLUGIN_DESCRIPTOR="maven/src/main/resources/META-INF/maven/plugin.xml"
SITE_POM="site/pom.xml"
MAVEN_EXAMPLE_POMS=(
  "examples/maven-consumer/pom.xml"
  "examples/fixtures/maven-consumer/pom.xml"
)
VERSIONED_METADATA_FILES=(
  "gradle.properties"
  "release.properties"
  "CITATION.cff"
  ".zenodo.json"
  "$MAVEN_PLUGIN_DESCRIPTOR"
  "$SITE_POM"
  "${MAVEN_EXAMPLE_POMS[@]}"
)

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
for index, line in enumerate(lines):
    if line.startswith("projectVersion="):
        lines[index] = f"projectVersion={version}"
        break
else:
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
text, count = re.subn(
    r"(<version>)[^<]+(</version>)", rf"\g<1>{version}\g<2>", text, count=1
)
if count != 1:
    raise SystemExit(f"Could not update version in {path}")
path.write_text(text, encoding="utf-8")
PY
}

set_next_release_version() {
  local version=$1
  python3 - "$version" <<'PY'
from pathlib import Path
import sys

version = sys.argv[1]
path = Path("release.properties")
lines = path.read_text(encoding="utf-8").splitlines() if path.exists() else []
for index, line in enumerate(lines):
    if line.startswith("next.release.version="):
        lines[index] = f"next.release.version={version}"
        break
else:
    lines.append(f"next.release.version={version}")
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
}

verify_metadata() {
  local expected=$1
  local release_mode=$2
  local project_version
  project_version=$(current_project_version)

  [[ "$project_version" == "$expected" ]] || {
    echo "::error::gradle.properties projectVersion '${project_version}' does not match '${expected}'" >&2
    exit 1
  }
  grep -q "^version: \"${expected}\"$" CITATION.cff || {
    echo "::error::CITATION.cff version does not match ${expected}" >&2
    exit 1
  }
  grep -q "<version>${expected}</version>" "$MAVEN_PLUGIN_DESCRIPTOR" || {
    echo "::error::Maven plugin descriptor version does not match ${expected}" >&2
    exit 1
  }
  grep -q "<revision>${expected}</revision>" "$SITE_POM" || {
    echo "::error::Maven site revision does not match ${expected}" >&2
    exit 1
  }
  for pom in "${MAVEN_EXAMPLE_POMS[@]}"; do
    grep -q "<aiKnowledge.version>${expected}</aiKnowledge.version>" "$pom" || {
      echo "::error::${pom} aiKnowledge.version does not match ${expected}" >&2
      exit 1
    }
  done

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
  echo "::error::Current project version must be a SNAPSHOT, but was ${CURRENT_VERSION}" >&2
  exit 1
fi
if [[ "${CURRENT_VERSION%-SNAPSHOT}" != "$RELEASE_VERSION" ]]; then
  echo "::error::Release ${RELEASE_VERSION} does not match current version ${CURRENT_VERSION}" >&2
  exit 1
fi

EXPECTED_RELEASE=$(grep -E '^next\.release\.version=' release.properties | head -n 1 | cut -d'=' -f2 | tr -d '[:space:]' || true)
if [[ -n "$EXPECTED_RELEASE" && "$EXPECTED_RELEASE" != "$RELEASE_VERSION" ]]; then
  echo "::error::release.properties suggests ${EXPECTED_RELEASE}, but this line contains ${CURRENT_VERSION}" >&2
  exit 1
fi

if [[ -n "$NEXT_VERSION_INPUT" ]]; then
  NEXT_VERSION=$NEXT_VERSION_INPUT
else
  IFS='.' read -r MAJOR MINOR PATCH <<< "$RELEASE_VERSION"
  NEXT_VERSION="${MAJOR}.${MINOR}.$((PATCH + 1))-SNAPSHOT"
fi
NEXT_VERSION=$(trim "$NEXT_VERSION")
python3 "$SCRIPT_DIR/release_line_policy.py" \
  --policy "$RELEASE_LINES_FILE" \
  validate-next \
  --branch "$SOURCE_BRANCH" \
  --release-version "$RELEASE_VERSION" \
  --next-version "$NEXT_VERSION" \
  --dry-run "$DRY_RUN"

verify_metadata "$CURRENT_VERSION" false
if [[ "$RELEASE_SOURCE_STATUS" == "dry-run" ]]; then
  python3 "$SCRIPT_DIR/verify-version-consistency.py"
else
  AI_KNOWLEDGE_TARGET_BRANCH="$SOURCE_BRANCH" \
    python3 "$SCRIPT_DIR/verify-version-consistency.py"
fi
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
  echo "::error::A GitHub release exists for ${TAG_NAME}, but its tag is missing" >&2
  exit 1
fi
if [[ "$TAG_EXISTS" == "false" && "$BRANCH_EXISTS" == "true" ]]; then
  echo "::error::Release branch ${RELEASE_BRANCH} exists without tag ${TAG_NAME}" >&2
  exit 1
fi
if [[ "$TAG_EXISTS" == "true" && "$BRANCH_EXISTS" == "true" ]]; then
  TAG_COMMIT=$(git rev-parse "${TAG_NAME}^{commit}")
  BRANCH_COMMIT=$(git rev-parse "origin/${RELEASE_BRANCH}^{commit}")
  [[ "$TAG_COMMIT" == "$BRANCH_COMMIT" ]] || {
    echo "::error::${TAG_NAME} and ${RELEASE_BRANCH} point to different commits" >&2
    exit 1
  }
fi

if [[ -n "$RELEASE_STATE" ]]; then
  STATE=$RELEASE_STATE
elif [[ "$TAG_EXISTS" == "true" ]]; then
  STATE=tagged
else
  STATE=new
fi

printf 'Release state: %s\n' "$STATE"
printf 'Release line: %s (%s)\n' "$RELEASE_SOURCE_LINE" "$RELEASE_SOURCE_STATUS"
printf 'Release branch: %s\n' "$SOURCE_BRANCH"
printf 'Release version: %s\n' "$RELEASE_VERSION"
printf 'Artifact contract: %s\n' "$RELEASE_ARTIFACT_CONTRACT"
printf 'Next development version: %s\n' "$NEXT_VERSION"
printf 'Next development PR base: %s\n' "$RELEASE_NEXT_PR_BASE"

if [[ "$STATE" == "new" ]]; then
  set_project_version "$RELEASE_VERSION"
  set_maven_plugin_descriptor_version "$RELEASE_VERSION"
  python3 "$METADATA_HELPER" "$RELEASE_VERSION" --release
  verify_metadata "$RELEASE_VERSION" true
  git add "${VERSIONED_METADATA_FILES[@]}"
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
  gh release edit "$TAG_NAME" --draft=false "$RELEASE_LATEST_ARGUMENT"
  STATE=published
fi

if [[ "$DRY_RUN" != "true" ]]; then
  IS_DRAFT=$(gh release view "$TAG_NAME" --json isDraft --jq '.isDraft')
  [[ "$IS_DRAFT" == "false" ]]
fi

set_project_version "$NEXT_VERSION"
set_maven_plugin_descriptor_version "$NEXT_VERSION"
python3 "$METADATA_HELPER" "$NEXT_VERSION"
set_next_release_version "${NEXT_VERSION%-SNAPSHOT}"
verify_metadata "$NEXT_VERSION" false
if [[ "$RELEASE_SOURCE_STATUS" == "dry-run" ]]; then
  python3 "$SCRIPT_DIR/verify-version-consistency.py"
else
  AI_KNOWLEDGE_TARGET_BRANCH="$RELEASE_NEXT_PR_BASE" \
    python3 "$SCRIPT_DIR/verify-version-consistency.py"
fi

SAFE_LINE=${RELEASE_SOURCE_LINE//[^A-Za-z0-9._-]/-}
NEXT_BRANCH="release/${SAFE_LINE}/prepare-next-${NEXT_VERSION}"
git switch -C "$NEXT_BRANCH"
git add "${VERSIONED_METADATA_FILES[@]}"
git commit -m "Prepare ${RELEASE_NEXT_PR_BASE} for ${NEXT_VERSION}"

if [[ "$DRY_RUN" == "true" ]]; then
  echo 'Dry run completed; no remote refs, release or PR were changed.'
  exit 0
fi

REMOTE_SHA=$(git ls-remote --heads origin "refs/heads/${NEXT_BRANCH}" | awk '{print $1}')
if [[ -n "$REMOTE_SHA" ]]; then
  git push \
    --force-with-lease="refs/heads/${NEXT_BRANCH}:${REMOTE_SHA}" \
    origin "HEAD:refs/heads/${NEXT_BRANCH}"
else
  git push origin "HEAD:refs/heads/${NEXT_BRANCH}"
fi

cat > /tmp/next-development-pr.md <<EOF
Automated follow-up after release ${RELEASE_VERSION} from the supported **${RELEASE_SOURCE_LINE}** line.

## Scope

- Target branch: \`${RELEASE_NEXT_PR_BASE}\`
- Next development version: \`${NEXT_VERSION}\`
- Support status: \`${RELEASE_SOURCE_STATUS}\`
- Artifact contract: \`${RELEASE_ARTIFACT_CONTRACT}\`

This changes only the ${RELEASE_SOURCE_LINE} release line. In particular, a maintenance-line
transition does not downgrade or modify \`main\`.

## Changes

- align all versioned Gradle, Maven, citation and Zenodo metadata;
- remove release-only date metadata from the development snapshot;
- update \`release.properties\` for the next release in this line;
- keep the next development version inside the same explicitly supported X.Y series.

The repository workflow \`Verify release follow-up PR\` validates the exact head, runs CI by
explicit workflow dispatch, and merges this metadata-only PR only after success.

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
    --title "Prepare ${RELEASE_NEXT_PR_BASE} for ${NEXT_VERSION}" \
    --body-file /tmp/next-development-pr.md
else
  gh pr create \
    --draft \
    --title "Prepare ${RELEASE_NEXT_PR_BASE} for ${NEXT_VERSION}" \
    --body-file /tmp/next-development-pr.md \
    --base "$RELEASE_NEXT_PR_BASE" \
    --head "$NEXT_BRANCH"
fi
