#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

GRADLE=${GRADLE:-gradle}
VERSION=$(grep -E '^projectVersion=' gradle.properties | head -n 1 | cut -d'=' -f2 | tr -d '[:space:]')
REPORT_DIR=${AI_KNOWLEDGE_CONSUMER_REPORT_DIR:-$ROOT/build/reports/consumer-fixtures}
TRACKED_GRADLE_DOCS=examples/fixtures/gradle-consumer/docs/ai-knowledge
PUBLISHED_GRADLE_HOME=''
PUBLISH_LOG="$REPORT_DIR/publish-to-maven-local.log"
COMPOSITE_LOG="$REPORT_DIR/gradle-composite-consumer.log"
PUBLISHED_LOG="$REPORT_DIR/gradle-published-marker-consumer.log"
MAVEN_LOG="$REPORT_DIR/maven-consumer.log"

fail() {
  echo "consumer-fixtures: $*" >&2
  exit 1
}

restore_tracked_gradle_docs() {
  if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    git restore --source=HEAD --worktree -- "$TRACKED_GRADLE_DOCS" 2>/dev/null || true
    git clean -fd -- "$TRACKED_GRADLE_DOCS" >/dev/null 2>&1 || true
  else
    rm -rf "$TRACKED_GRADLE_DOCS"
  fi
}

clean_outputs() {
  rm -rf \
    examples/fixtures/gradle-consumer/build \
    examples/fixtures/maven-consumer/target
  restore_tracked_gradle_docs
}

cleanup() {
  clean_outputs
  [[ -z "$PUBLISHED_GRADLE_HOME" ]] || rm -rf "$PUBLISHED_GRADLE_HOME"
}
trap cleanup EXIT

[[ -n "$VERSION" ]] || fail 'Could not resolve projectVersion from gradle.properties'
mkdir -p "$REPORT_DIR"
: > "$PUBLISH_LOG"
: > "$COMPOSITE_LOG"
: > "$PUBLISHED_LOG"
: > "$MAVEN_LOG"

echo "Publishing ${VERSION} artifacts to Maven local for consumer fixtures"
"$GRADLE" --no-daemon publishToMavenLocal --warning-mode all 2>&1 | tee "$PUBLISH_LOG"

assert_artifacts() {
  local directory=$1
  for file in \
    index.json \
    source-units.json \
    symbols.json \
    relations.json \
    boundaries.json \
    warnings.json \
    boundary-analysis.json \
    complexity.json \
    optimization.json \
    benchmark.json \
    check.json; do
    test -s "$directory/$file" || fail "missing consumer artifact: $directory/$file"
  done
  if grep -RIl --include='*.json' "$ROOT" "$directory" | grep -q .; then
    fail "consumer output exposes the absolute checkout path: $directory"
  fi
}

GRADLE_TASKS=(
  generateAiKnowledgeIndex
  analyzeAiComplexity
  optimizeAiKnowledge
  benchmarkAiKnowledge
  checkAiKnowledgeIndex
  publishAiKnowledgeIndex
)

clean_outputs
echo 'Verifying Gradle consumer fixture through source composite'
"$GRADLE" --no-daemon \
  -p examples/fixtures/gradle-consumer \
  --warning-mode all \
  "${GRADLE_TASKS[@]}" 2>&1 | tee "$COMPOSITE_LOG"
assert_artifacts examples/fixtures/gradle-consumer/build/ai-knowledge
test -s "$TRACKED_GRADLE_DOCS/index.json" \
  || fail 'published Gradle documentation artifact is missing'

clean_outputs
PUBLISHED_GRADLE_HOME=$(mktemp -d)
echo 'Verifying the published Gradle plugin marker without a composite build'
GRADLE_USER_HOME="$PUBLISHED_GRADLE_HOME" \
  "$GRADLE" --no-daemon --refresh-dependencies \
  -p examples/fixtures/gradle-consumer \
  -PpublishedPlugin=true \
  -PaiKnowledgeVersion="$VERSION" \
  --warning-mode all \
  "${GRADLE_TASKS[@]}" 2>&1 | tee "$PUBLISHED_LOG"
if grep -Fq ':ai-knowledge-extractor:' "$PUBLISHED_LOG"; then
  fail 'published-plugin verification unexpectedly used the source composite build'
fi
assert_artifacts examples/fixtures/gradle-consumer/build/ai-knowledge
test -s "$TRACKED_GRADLE_DOCS/index.json" \
  || fail 'published Gradle documentation artifact is missing in marker mode'

echo 'Verifying every Maven plugin goal through Maven-local coordinates'
clean_outputs
{
  mvn -B -e -f examples/fixtures/maven-consumer/pom.xml \
    -DaiKnowledge.version="$VERSION" \
    org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":generate \
    org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":analyze \
    org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":optimize \
    org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":benchmark \
    org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":check
  mvn -B -e -f examples/fixtures/maven-consumer/pom.xml \
    -DaiKnowledge.version="$VERSION" \
    -Ddetail=true \
    org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":help
  mvn -B -e -f examples/fixtures/maven-consumer/pom.xml \
    -DaiKnowledge.version="$VERSION" \
    -Dgoal=benchmark \
    -Ddetail=true \
    org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":help
} 2>&1 | tee "$MAVEN_LOG"
assert_artifacts examples/fixtures/maven-consumer/target/ai-knowledge
grep -F 'AI Knowledge Maven Plugin goals:' "$MAVEN_LOG" >/dev/null \
  || fail 'Maven help goal did not list plugin goals'
grep -F 'benchmark (phase: verify)' "$MAVEN_LOG" >/dev/null \
  || fail 'Maven help goal did not describe benchmark'
grep -F 'empiricalBenchmarkFixtureFile [java.io.File]' "$MAVEN_LOG" >/dev/null \
  || fail 'Maven help goal did not document benchmark parameters'

echo "Consumer fixture verification completed; logs: $REPORT_DIR"
