#!/usr/bin/env bash
set -euo pipefail

: "${VERSION:?VERSION is required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
: "${GITHUB_ACTOR:?GITHUB_ACTOR is required}"
: "${GITHUB_TOKEN:?GITHUB_TOKEN is required}"

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "published-release-smoke: VERSION must use X.Y.Z, found '$VERSION'" >&2
  exit 1
fi

ROOT=$(pwd)
REPORT_DIR=${AI_KNOWLEDGE_PUBLISHED_REPORT_DIR:-$ROOT/build/reports/published-release-smoke}
WORK=$(mktemp -d)
PACKAGE_URL="https://maven.pkg.github.com/${GITHUB_REPOSITORY}"
GRADLE=${GRADLE:-gradle}

cleanup() {
  rm -rf "$WORK"
}
trap cleanup EXIT

fail() {
  echo "published-release-smoke: $*" >&2
  exit 1
}

mkdir -p "$REPORT_DIR/release-assets"

assert_output() {
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
    test -s "$directory/$file" || fail "missing published-consumer artifact: $directory/$file"
  done

  python3 - "$directory/boundary-analysis.json" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
data = json.loads(path.read_text(encoding='utf-8'))
if int(data.get('linkedCallCount', 0)) < 1:
    raise SystemExit(f'No frontend/backend operation was linked in {path}')
if data.get('versionControlHistoryUsed') is not False:
    raise SystemExit(f'Git history unexpectedly used in {path}')
if data.get('changeCouplingIncluded') is not False:
    raise SystemExit(f'Commit-derived change coupling unexpectedly included in {path}')
links = data.get('links') or []
if not any(link.get('status') == 'linked' and link.get('operation') == 'GET /api/users/{}'
           for link in links):
    raise SystemExit(f'Expected GET /api/users/{{}} link is missing in {path}')
PY
}

write_mixed_sources() {
  local project=$1
  mkdir -p \
    "$project/web/src" \
    "$project/backend/src/architecture/java/example"

  cat > "$project/web/package.json" <<'JSON'
{
  "name": "ai-knowledge-published-smoke-web",
  "private": true,
  "dependencies": {
    "axios": "1.7.9"
  }
}
JSON

  cat > "$project/web/src/users.ts" <<'TS'
export interface UserView {
  displayName: string;
}

export async function loadUser(id: string): Promise<UserView> {
  const response = await fetch(`/api/users/${id}`);
  if (!response.ok) {
    throw new Error(`backend status ${response.status}`);
  }
  const dto = await response.json();
  return { displayName: dto.name };
}
TS

  cat > "$project/backend/src/architecture/java/example/UserController.java" <<'JAVA'
package example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
class UserController {
    @GetMapping("/{id}")
    Object get() {
        return null;
    }
}
JAVA
}

echo "Verifying GitHub Release v${VERSION} and its checksum manifest"
GH_TOKEN="$GITHUB_TOKEN" gh release view "v${VERSION}" \
  --repo "$GITHUB_REPOSITORY" \
  --json isDraft,tagName,assets \
  --jq 'select(.isDraft == false and .tagName == "v'"$VERSION"'") | .tagName' \
  | grep -Fx "v${VERSION}"
GH_TOKEN="$GITHUB_TOKEN" gh release download "v${VERSION}" \
  --repo "$GITHUB_REPOSITORY" \
  --dir "$REPORT_DIR/release-assets" \
  --clobber
(
  cd "$REPORT_DIR/release-assets"
  test -s SHA256SUMS || fail 'GitHub Release does not contain SHA256SUMS'
  sha256sum -c SHA256SUMS | tee "$REPORT_DIR/release-assets-checksums.log"
)

echo 'Verifying Gradle plugin marker from an empty Gradle user home'
GRADLE_PROJECT="$WORK/gradle-consumer"
mkdir -p "$GRADLE_PROJECT"
write_mixed_sources "$GRADLE_PROJECT"

cat > "$GRADLE_PROJECT/settings.gradle" <<EOF
pluginManagement {
    repositories {
        maven {
            url = uri('${PACKAGE_URL}')
            credentials {
                username = System.getenv('GITHUB_ACTOR')
                password = System.getenv('GITHUB_TOKEN')
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven {
            url = uri('${PACKAGE_URL}')
            credentials {
                username = System.getenv('GITHUB_ACTOR')
                password = System.getenv('GITHUB_TOKEN')
            }
        }
    }
}

rootProject.name = 'ai-knowledge-published-gradle-smoke'
EOF

cat > "$GRADLE_PROJECT/build.gradle" <<EOF
plugins {
    id 'java'
    id 'org.aiknowledge.extractor' version '${VERSION}'
}

group = 'example.consumer'
version = '1.0.0'

aiKnowledge {
    failOnWarnings = false
    maxCognitiveDebt = 1000.0d
}
EOF

EMPTY_GRADLE_HOME="$WORK/gradle-home"
mkdir -p "$EMPTY_GRADLE_HOME"
GITHUB_ACTOR="$GITHUB_ACTOR" GITHUB_TOKEN="$GITHUB_TOKEN" \
GRADLE_USER_HOME="$EMPTY_GRADLE_HOME" \
  "$GRADLE" --no-daemon --refresh-dependencies \
  -p "$GRADLE_PROJECT" \
  --warning-mode all \
  aiKnowledgeCheck 2>&1 | tee "$REPORT_DIR/gradle-published-release.log"
assert_output "$GRADLE_PROJECT/build/ai-knowledge"
cp -R "$GRADLE_PROJECT/build/ai-knowledge" "$REPORT_DIR/gradle-output"

echo 'Verifying Maven plugin from an empty Maven local repository'
MAVEN_PROJECT="$WORK/maven-consumer"
mkdir -p "$MAVEN_PROJECT"
write_mixed_sources "$MAVEN_PROJECT"

cat > "$MAVEN_PROJECT/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example.consumer</groupId>
  <artifactId>ai-knowledge-published-maven-smoke</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.release>17</maven.compiler.release>
  </properties>
  <build>
    <plugins>
      <plugin>
        <groupId>org.aiknowledge</groupId>
        <artifactId>ai-knowledge-maven-plugin</artifactId>
        <version>${VERSION}</version>
        <configuration>
          <outputDirectory>\${project.build.directory}/ai-knowledge</outputDirectory>
          <failOnWarnings>false</failOnWarnings>
          <maxCognitiveDebt>1000.0</maxCognitiveDebt>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
EOF

cat > "$WORK/settings.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <servers>
    <server>
      <id>github-ai-knowledge-extractor</id>
      <username>${GITHUB_ACTOR}</username>
      <password>${GITHUB_TOKEN}</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>github-ai-knowledge-extractor</id>
      <repositories>
        <repository>
          <id>github-ai-knowledge-extractor</id>
          <url>${PACKAGE_URL}</url>
        </repository>
      </repositories>
      <pluginRepositories>
        <pluginRepository>
          <id>github-ai-knowledge-extractor</id>
          <url>${PACKAGE_URL}</url>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>github-ai-knowledge-extractor</activeProfile>
  </activeProfiles>
</settings>
EOF

EMPTY_MAVEN_REPOSITORY="$WORK/maven-repository"
mkdir -p "$EMPTY_MAVEN_REPOSITORY"
mvn -B -e \
  -s "$WORK/settings.xml" \
  -Dmaven.repo.local="$EMPTY_MAVEN_REPOSITORY" \
  -f "$MAVEN_PROJECT/pom.xml" \
  org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":check \
  org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":help \
  -Ddetail=true 2>&1 | tee "$REPORT_DIR/maven-published-release.log"
assert_output "$MAVEN_PROJECT/target/ai-knowledge"
cp -R "$MAVEN_PROJECT/target/ai-knowledge" "$REPORT_DIR/maven-output"

cat > "$REPORT_DIR/summary.json" <<EOF
{
  "version": "${VERSION}",
  "repository": "${GITHUB_REPOSITORY}",
  "distribution": "GitHub Packages",
  "gradleCompositeBuildUsed": false,
  "mavenLocalUsed": false,
  "versionControlHistoryUsed": false,
  "changeCouplingIncluded": false
}
EOF

echo "Published release ${VERSION} verified from clean Gradle and Maven consumer environments."
