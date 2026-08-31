#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

GRADLE=${GRADLE:-gradle}
VERSION=$(grep -E '^projectVersion=' gradle.properties | head -n 1 | cut -d'=' -f2 | tr -d '[:space:]')
PUBLISHED_GRADLE_HOME=''
PUBLISHED_LOG=''

fail() {
  echo "consumer-fixtures: $*" >&2
  exit 1
}

clean_outputs() {
  rm -rf     examples/fixtures/gradle-consumer/build     examples/fixtures/gradle-consumer/docs/ai-knowledge     examples/fixtures/maven-consumer/target
}

cleanup() {
  clean_outputs
  [[ -z "$PUBLISHED_GRADLE_HOME" ]] || rm -rf "$PUBLISHED_GRADLE_HOME"
  [[ -z "$PUBLISHED_LOG" ]] || rm -f "$PUBLISHED_LOG"
}
trap cleanup EXIT

[[ -n "$VERSION" ]] || fail 'Could not resolve projectVersion from gradle.properties'

echo "Publishing ${VERSION} artifacts to Maven local for consumer fixtures"
"$GRADLE" --no-daemon publishToMavenLocal

assert_schema_v2() {
  local directory=$1
  for file in     index.json     source-units.json     symbols.json     relations.json     boundaries.json     warnings.json     boundary-analysis.json     check.json; do
    test -s "$directory/$file" || fail "missing consumer artifact: $directory/$file"
  done
  if grep -RIl --include='*.json' "$ROOT" "$directory" | grep -q .; then
    fail "consumer output exposes the absolute checkout path: $directory"
  fi
}

clean_outputs
echo 'Verifying Gradle consumer fixture through source composite'
"$GRADLE" --no-daemon -p examples/fixtures/gradle-consumer   generateAiKnowledgeIndex checkAiKnowledgeIndex publishAiKnowledgeIndex
assert_schema_v2 examples/fixtures/gradle-consumer/build/ai-knowledge
test -s examples/fixtures/gradle-consumer/docs/ai-knowledge/index.json   || fail 'published Gradle documentation artifact is missing'

clean_outputs
PUBLISHED_GRADLE_HOME=$(mktemp -d)
PUBLISHED_LOG=$(mktemp)
echo 'Verifying the published Gradle plugin marker without a composite build'
GRADLE_USER_HOME="$PUBLISHED_GRADLE_HOME"   "$GRADLE" --no-daemon --refresh-dependencies   -p examples/fixtures/gradle-consumer   -PpublishedPlugin=true   -PaiKnowledgeVersion="$VERSION"   generateAiKnowledgeIndex checkAiKnowledgeIndex publishAiKnowledgeIndex   | tee "$PUBLISHED_LOG"
if grep -Fq ':ai-knowledge-extractor:' "$PUBLISHED_LOG"; then
  fail 'published-plugin verification unexpectedly used the source composite build'
fi
assert_schema_v2 examples/fixtures/gradle-consumer/build/ai-knowledge

echo 'Verifying every Maven plugin goal through Maven-local coordinates'
clean_outputs
mvn -B -f examples/fixtures/maven-consumer/pom.xml   -DaiKnowledge.version="$VERSION"   org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":generate   org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":analyze   org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":optimize   org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":benchmark   org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":check   org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":help
assert_schema_v2 examples/fixtures/maven-consumer/target/ai-knowledge

echo 'Consumer fixture verification completed'
