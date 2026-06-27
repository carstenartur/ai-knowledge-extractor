#!/usr/bin/env bash
set -euo pipefail

VERSION=$(grep -E '^projectVersion=' gradle.properties | head -n 1 | cut -d'=' -f2 | tr -d '[:space:]')

if [[ -z "$VERSION" ]]; then
  echo 'Could not resolve projectVersion from gradle.properties' >&2
  exit 1
fi

echo "Publishing ${VERSION} artifacts to Maven local for consumer fixtures"
gradle publishToMavenLocal

echo 'Verifying Gradle consumer fixture'
gradle -p examples/fixtures/gradle-consumer generateAiKnowledgeIndex checkAiKnowledgeIndex publishAiKnowledgeIndex

test -f examples/fixtures/gradle-consumer/build/ai-knowledge/index.json
test -f examples/fixtures/gradle-consumer/build/ai-knowledge/check.json
test -f examples/fixtures/gradle-consumer/docs/ai-knowledge/index.json

echo 'Verifying Maven consumer fixture'
mvn -B -f examples/fixtures/maven-consumer/pom.xml \
  -DaiKnowledge.version="$VERSION" \
  org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":generate \
  org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":check

test -f examples/fixtures/maven-consumer/target/ai-knowledge/index.json
test -f examples/fixtures/maven-consumer/target/ai-knowledge/check.json

echo 'Consumer fixture verification completed'
