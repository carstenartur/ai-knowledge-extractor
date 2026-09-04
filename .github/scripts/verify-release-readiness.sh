#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

fail() {
  echo "release-readiness: $*" >&2
  exit 1
}

current_branch() {
  if [[ -n "${AI_KNOWLEDGE_TARGET_BRANCH:-}" ]]; then
    printf '%s\n' "$AI_KNOWLEDGE_TARGET_BRANCH"
  elif [[ -n "${GITHUB_BASE_REF:-}" ]]; then
    printf '%s\n' "$GITHUB_BASE_REF"
  elif [[ -n "${GITHUB_REF_NAME:-}" ]]; then
    printf '%s\n' "$GITHUB_REF_NAME"
  else
    git symbolic-ref --quiet --short HEAD 2>/dev/null || printf '%s\n' main
  fi
}

PROJECT_VERSION=$(sed -n 's/^projectVersion=//p' gradle.properties | head -n1 | tr -d '[:space:]')
[[ "$PROJECT_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]] \
  || fail "development version must use X.Y.Z-SNAPSHOT, found: $PROJECT_VERSION"
RELEASE_VERSION=${PROJECT_VERSION%-SNAPSHOT}
TARGET_BRANCH=$(current_branch)
export AI_KNOWLEDGE_TARGET_BRANCH=$TARGET_BRANCH

POLICY_ENV=$(mktemp)
trap 'rm -f "$POLICY_ENV"' EXIT
python3 .github/scripts/release_line_policy.py resolve \
  --branch "$TARGET_BRANCH" \
  --release-version "$RELEASE_VERSION" \
  --dry-run false \
  --env-file "$POLICY_ENV"
# shellcheck disable=SC1090
source "$POLICY_ENV"

TEMPORARY_PATHS=(
  .github/workflows/export-release-hardening-bootstrap.yml
  .github/workflows/apply-release-hardening.yml
  .github/workflows/finalize-release-hardening.yml
  .github/workflows/override-finalize-release-hardening.yml
  .github/workflows/export-preparation-source.yml
  .github/workflows/finalize-020-preparation-v2-v3.yml
  .github/workflows/verify-020-hardening-main.yml
  .github/workflows/post-verify-020-cleanup.yml
  .github/workflows/post-verify-020-final-cleanup.yml
  .github/scripts/apply-release-hardening.py
  .github/scripts/finalize-020-preparation.py
  .release-transfer
)
for temporary in "${TEMPORARY_PATHS[@]}"; do
  [[ ! -e "$temporary" ]] || fail "temporary integration file remains: $temporary"
done

for script in \
  .github/scripts/release.sh \
  .github/scripts/prepare-next.sh \
  .github/scripts/verify-consumer-fixtures.sh \
  .github/scripts/verify-release-readiness.sh; do
  test -s "$script" || fail "required release script is missing: $script"
  bash -n "$script"
done

for script in \
  .github/scripts/release_line_policy.py \
  .github/scripts/test_release_line_policy.py \
  .github/scripts/verify-version-consistency.py; do
  test -s "$script" || fail "required Python release script is missing: $script"
  python3 -m py_compile "$script"
done

for workflow in \
  .github/workflows/ci.yml \
  .github/workflows/publish.yml \
  .github/workflows/prepare-next.yml; do
  test -s "$workflow" || fail "required release workflow is missing: $workflow"
done

test -s .github/release-lines.json || fail 'release-line policy is missing'
test -s docs/version-support.md || fail 'version support documentation is missing'
python3 .github/scripts/test_release_line_policy.py

grep -Fq 'SHA256SUMS' .github/workflows/publish.yml \
  || fail 'the Release workflow does not publish a checksum manifest'
! grep -Fq 'next_version_increment' .github/workflows/publish.yml \
  || fail 'release workflow still permits an implicit cross-series increment'
! grep -Fq 'next_version_increment' .github/workflows/prepare-next.yml \
  || fail 'prepare-next workflow still permits an implicit cross-series increment'
grep -Fq '.github/release-request.json' .github/workflows/publish.yml \
  || fail 'reviewed release requests are not wired into the Release workflow'
grep -Fq 'resolve-request' .github/workflows/publish.yml \
  || fail 'the Release workflow does not use the strict request parser'
if [[ -e docs/release.md ]]; then
  grep -Fq '.github/release-request.json' docs/release.md \
    || fail 'reviewed release requests are not documented'
fi

case "$RELEASE_ARTIFACT_CONTRACT" in
  schema-v2)
    for script in .github/scripts/verify-published-release.sh; do
      test -s "$script" || fail "required schema-v2 release script is missing: $script"
      bash -n "$script"
    done
    for workflow in \
      .github/workflows/verify-published-release.yml \
      .github/workflows/verify-release-follow-up.yml; do
      test -s "$workflow" || fail "required schema-v2 release workflow is missing: $workflow"
    done

    grep -Fq 'aiknowledge.source.enabledProviders' docs/integrator-quickstart.md \
      || fail 'shared source configuration is not documented'
    grep -Fq "id 'org.aiknowledge.extractor'" docs/integrator-quickstart.md \
      || fail 'the integrator quickstart does not use the published Gradle plugin id'
    ! grep -Fq "id 'org.aiknowledge' version" docs/integrator-quickstart.md \
      || fail 'the integrator quickstart still contains the obsolete Gradle plugin id'
    grep -Fq 'versionControlHistoryUsed' docs/schema-v2-contract.md \
      || fail 'Git-history exclusion is not part of the schema contract'
    grep -Fq '# 0.2.0 release notes' docs/releases/0.2.0.md \
      || fail 'final 0.2.0 release notes are missing'
    ! grep -Fq '(target)' docs/releases/0.2.0.md \
      || fail '0.2.0 release notes are still marked as a target'
    grep -Fq 'GitHub Packages' docs/releases/0.2.0.md \
      || fail 'the real 0.2.0 package channel is not documented'
    grep -Fq 'Version 0.2.0 is **not** published to Maven Central or the Gradle Plugin Portal.' \
      docs/releases/0.2.0.md \
      || fail 'the exact Maven Central and Gradle Plugin Portal non-availability statement is missing'
    grep -Fq '## 0.2.0 – 2026-08-31' CHANGELOG.md \
      || fail 'the 0.2.0 changelog entry is not finalized'
    ! grep -Fq 'Unreleased – target 0.2.0' CHANGELOG.md \
      || fail 'the changelog still presents 0.2.0 as unreleased'
    ! grep -Fq 'Maven Central staging closes' docs/release-checklist.md \
      || fail 'the release checklist still requires an unused Maven Central channel'
    ! grep -Fq 'plugin-info.html' site/src/site/site.xml \
      || fail 'site navigation still points to the removed plugin report'
    grep -Fq 'maintenance/0.1.x' README.md \
      || fail 'README does not explain the maintenance release line'
    grep -Fq 'docs/version-support.md' README.md \
      || fail 'README does not link to the version-support policy'
    grep -Fq 'release/follow-up-ci' .github/workflows/verify-release-follow-up.yml \
      || fail 'generated follow-up PRs do not record exact-head CI status'
    grep -Fq 'workflow_run:' .github/workflows/verify-release-follow-up.yml \
      || fail 'release follow-up verification is not triggered from completed workflows'
    grep -Fq -- '--match-head-commit' .github/workflows/verify-release-follow-up.yml \
      || fail 'release follow-up merge does not pin the verified head commit'
    grep -Fq 'machine-readable policy for simultaneously supported' CHANGELOG.md \
      || fail 'the multi-version support change is missing from CHANGELOG.md'
    ;;
  schema-v1)
    for obsolete in \
      .github/release-requests/0.1.x.json \
      .github/scripts/release-source-policy.sh \
      .github/scripts/resolve-maintenance-release-request.py \
      .github/scripts/test-maintenance-release-request.sh \
      .github/scripts/test-maintenance-release-workflow.sh \
      .github/scripts/test-release-source-policy.sh; do
      [[ ! -e "$obsolete" ]] || fail "obsolete branch-specific release helper remains: $obsolete"
    done
    ;;
  *)
    fail "unsupported artifact contract: $RELEASE_ARTIFACT_CONTRACT"
    ;;
esac

git diff --check
python3 .github/scripts/verify-version-consistency.py

if [[ "${1:-}" == "--metadata-only" ]]; then
  echo "release-readiness metadata checks passed for $TARGET_BRANCH ($RELEASE_ARTIFACT_CONTRACT)"
  exit 0
fi

GRADLE=${GRADLE:-gradle}
REPORT=build/reports/release-readiness
rm -rf "$REPORT"
mkdir -p "$REPORT"

"$GRADLE" --no-daemon clean check publishToMavenLocal --warning-mode all

LC_ALL=C LANG=C JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8' \
  "$GRADLE" --no-daemon --warning-mode all \
  :core:javadoc \
  :gradle-plugin:javadoc \
  :maven:javadoc

bash .github/scripts/verify-consumer-fixtures.sh

SITE_LOG="$REPORT/maven-site.log"
mvn -B -f site/pom.xml -Drevision="$PROJECT_VERSION" process-resources site 2>&1 | tee "$SITE_LOG"
if [[ "$RELEASE_ARTIFACT_CONTRACT" == "schema-v2" ]]; then
  if grep -F '[WARNING]' "$SITE_LOG"; then
    fail 'Maven site generation emitted warnings'
  fi
  if grep -E 'NoSuchMethodError|LinkageError|An issue has occurred|old pre-version' "$SITE_LOG"; then
    fail 'Maven site generation skipped or failed a report'
  fi
fi
for page in index generate-mojo analyze-mojo optimize-mojo benchmark-mojo check-mojo help-mojo; do
  test -s "site/target/site/${page}.html" || fail "missing Maven site page: ${page}.html"
done
if [[ "$RELEASE_ARTIFACT_CONTRACT" == "schema-v2" ]]; then
  test ! -e site/target/site/plugin-info.html \
    || fail 'obsolete plugin-info.html was generated'
else
  test -s site/target/site/plugin-info.html \
    || fail 'schema-v1 Maven site is missing plugin-info.html'
fi
if grep -R '/home/runner/' site/target/site/; then
  fail 'Maven site exposes an absolute Actions checkout path'
fi

find core gradle-plugin maven -path '*/build/libs/*' -type f -print0 \
  | sort -z \
  | xargs -0 sha256sum > "$REPORT/artifacts.sha256"
[[ -s "$REPORT/artifacts.sha256" ]] || fail 'no release artifacts were found for checksums'

echo "release-readiness checks passed for $TARGET_BRANCH ($RELEASE_ARTIFACT_CONTRACT); checksums: $REPORT/artifacts.sha256"
