#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

fail() {
  echo "release-readiness: $*" >&2
  exit 1
}

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

grep -Fq 'aiknowledge.source.enabledProviders' docs/integrator-quickstart.md \
  || fail 'shared source configuration is not documented'
grep -Fq "id 'org.aiknowledge.extractor'" docs/integrator-quickstart.md \
  || fail 'the integrator quickstart does not use the published Gradle plugin id'
! grep -Fq "id 'org.aiknowledge' version" docs/integrator-quickstart.md \
  || fail 'the integrator quickstart still contains the obsolete Gradle plugin id'
grep -Fq 'versionControlHistoryUsed' docs/schema-v2-contract.md \
  || fail 'Git-history exclusion is not part of the schema contract'
grep -Fq 'Maven Central' docs/releases/0.2.0.md \
  || fail 'publication status is not documented'
! grep -Fq 'plugin-info.html' site/src/site/site.xml \
  || fail 'site navigation still points to the removed plugin report'

git diff --check
python3 .github/scripts/verify-version-consistency.py

if [[ "${1:-}" == "--metadata-only" ]]; then
  echo 'release-readiness metadata checks passed'
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

VERSION=$(sed -n 's/^projectVersion=//p' gradle.properties | head -n1 | tr -d '[:space:]')
SITE_LOG="$REPORT/maven-site.log"
mvn -B -f site/pom.xml -Drevision="$VERSION" process-resources site 2>&1 | tee "$SITE_LOG"
if grep -F '[WARNING]' "$SITE_LOG"; then
  fail 'Maven site generation emitted warnings'
fi
if grep -E 'NoSuchMethodError|LinkageError|An issue has occurred|old pre-version' "$SITE_LOG"; then
  fail 'Maven site generation skipped or failed a report'
fi
for page in index generate-mojo analyze-mojo optimize-mojo benchmark-mojo check-mojo help-mojo; do
  test -s "site/target/site/${page}.html" || fail "missing Maven site page: ${page}.html"
done
test ! -e site/target/site/plugin-info.html \
  || fail 'obsolete plugin-info.html was generated'
if grep -R '/home/runner/' site/target/site/; then
  fail 'Maven site exposes an absolute Actions checkout path'
fi

find core gradle-plugin maven -path '*/build/libs/*' -type f -print0 \
  | sort -z \
  | xargs -0 sha256sum > "$REPORT/artifacts.sha256"
[[ -s "$REPORT/artifacts.sha256" ]] || fail 'no release artifacts were found for checksums'

echo "release-readiness checks passed; checksums: $REPORT/artifacts.sha256"
