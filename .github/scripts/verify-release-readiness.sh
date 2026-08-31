#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

fail() {
  echo "release-readiness: $*" >&2
  exit 1
}

for temporary in   .github/workflows/export-release-hardening-bootstrap.yml   .github/workflows/apply-release-hardening.yml   .github/workflows/finalize-release-hardening.yml   .github/workflows/override-finalize-release-hardening.yml   .github/scripts/apply-release-hardening.py   .github/scripts/finalize-020-preparation.py   .release-transfer; do
  [[ ! -e "$temporary" ]] || fail "temporary integration file remains: $temporary"
done

grep -q 'aiknowledge.source.enabledProviders' docs/integrator-quickstart.md   || fail 'shared source configuration is not documented'
grep -q 'versionControlHistoryUsed' docs/schema-v2-contract.md   || fail 'Git-history exclusion is not part of the schema contract'
grep -q 'Maven Central' docs/releases/0.2.0.md   || fail 'publication status is not documented'
! grep -q 'plugin-info.html' site/src/site/site.xml   || fail 'site navigation still points to the removed plugin report'

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

LC_ALL=C LANG=C JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8'   "$GRADLE" --no-daemon   :core:javadoc :gradle-plugin:javadoc :maven:javadoc --warning-mode all

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

find core gradle-plugin maven -path '*/build/libs/*' -type f -print0   | sort -z   | xargs -0 sha256sum > "$REPORT/artifacts.sha256"
[[ -s "$REPORT/artifacts.sha256" ]] || fail 'no release artifacts were found for checksums'

echo "release-readiness checks passed; checksums: $REPORT/artifacts.sha256"
