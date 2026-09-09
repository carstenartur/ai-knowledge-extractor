#!/usr/bin/env bash
set -euo pipefail

: "${VERSION:?Set VERSION to the stable release to verify}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || exit 1
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
REPORT_DIR=${AI_KNOWLEDGE_PUBLIC_REPORT_DIR:-$ROOT/build/reports/public-consumers}
GRADLE=${GRADLE:-gradle}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$REPORT_DIR" "$WORK/gradle" "$WORK/maven" "$WORK/core"

# --repository is exclusively the CI rehearsal. No override is read from the
# environment; the public path always uses Central and the Plugin Portal.
PUBLIC=true
REPOSITORY=''
if [[ $# -gt 0 ]]; then
  [[ $# -eq 2 && "$1" == --repository && -d "$2" ]] || exit 1
  REPOSITORY=$(python3 -c 'import pathlib,sys; print(pathlib.Path(sys.argv[1]).resolve().as_uri())' "$2")
  PUBLIC=false
fi

python3 - "$WORK" "$VERSION" "$REPOSITORY" <<'PY'
import sys
from pathlib import Path
from xml.sax.saxutils import escape
work, version, repository = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
for name in ['gradle', 'maven', 'core']:
    root = work / name
    (root / 'web').mkdir()
    (root / 'backend').mkdir()
    (root / 'web/client.ts').write_text("export async function load() { return await fetch('/api/items'); }\n")
    (root / 'backend/Items.java').write_text('''package fixture;
import org.springframework.web.bind.annotation.GetMapping;
class Items { @GetMapping("/api/items") Object get() { return null; } }
''')
local = f"maven {{ url = uri('{repository}') }}" if repository else ''
# Force our marker through the Portal in public mode, even if also on Central.
portal = """exclusiveContent {
  forRepository { gradlePluginPortal() }
  filter { includeGroup('org.aiknowledge.extractor') }
}""" if not repository else ''
(work / 'gradle/settings.gradle').write_text(f'''pluginManagement {{
    repositories {{ {local} {portal} mavenCentral() }}
}}
rootProject.name = 'anonymous-public-consumer'
''')
(work / 'gradle/build.gradle').write_text(f'''plugins {{
    id 'java'
    id 'org.aiknowledge.extractor' version '{version}'
}}
aiKnowledge {{ failOnWarnings = false; maxCognitiveDebt = 1000.0d }}
''')
repos = f'''<repositories><repository><id>rehearsal</id><url>{escape(repository)}</url>
</repository></repositories><pluginRepositories><pluginRepository><id>rehearsal</id>
<url>{escape(repository)}</url></pluginRepository></pluginRepositories>''' if repository else ''
pom = f'''<project xmlns="http://maven.apache.org/POM/4.0.0">
<modelVersion>4.0.0</modelVersion><groupId>fixture</groupId>
<artifactId>anonymous-public-consumer</artifactId><version>1.0</version>{repos}
<dependencies><dependency><groupId>org.aiknowledge</groupId>
<artifactId>ai-knowledge-core</artifactId><version>{version}</version></dependency></dependencies>
<build><plugins><plugin><groupId>org.aiknowledge</groupId>
<artifactId>ai-knowledge-maven-plugin</artifactId><version>{version}</version>
<configuration><failOnWarnings>false</failOnWarnings><maxCognitiveDebt>1000.0</maxCognitiveDebt>
</configuration></plugin></plugins></build></project>'''
(work / 'maven/pom.xml').write_text(pom)
(work / 'settings.xml').write_text('<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>')
PY

# An allowlisted environment plus empty user/global settings prevents credentials,
# init scripts, Maven-local installs and authenticated mirror fallbacks.
CLEAN_ENV=(env -i "PATH=$PATH" "JAVA_HOME=${JAVA_HOME:?JDK required}" LANG=C.UTF-8)
"${CLEAN_ENV[@]}" GRADLE_USER_HOME="$WORK/gradle-home" \
  "$GRADLE" --no-daemon --refresh-dependencies -p "$WORK/gradle" \
  aiKnowledgeCheck 2>&1 | tee "$REPORT_DIR/gradle.log"
"${CLEAN_ENV[@]}" mvn -B -U -s "$WORK/settings.xml" -gs "$WORK/settings.xml" \
  -Dmaven.repo.local="$WORK/maven-cache" -f "$WORK/maven/pom.xml" \
  org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":check \
  org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":help \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
  -Dmdep.outputFile="$WORK/classpath.txt" 2>&1 | tee "$REPORT_DIR/maven.log"

cat > "$WORK/PublicCoreConsumer.java" <<'JAVA'
import java.nio.file.Path;
import org.aiknowledge.core.AiKnowledgeRunner;
import org.aiknowledge.core.ExtractionOptions;
class PublicCoreConsumer {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        new AiKnowledgeRunner().check(ExtractionOptions.defaults(root, root.resolve("output")));
    }
}
JAVA
"${CLEAN_ENV[@]}" java -cp "$(cat "$WORK/classpath.txt")" \
  "$WORK/PublicCoreConsumer.java" "$WORK/core" 2>&1 | tee "$REPORT_DIR/core.log"
CORE_JAR="$WORK/maven-cache/org/aiknowledge/ai-knowledge-core/$VERSION/ai-knowledge-core-$VERSION.jar"
for output in "$WORK/gradle/build/ai-knowledge" "$WORK/maven/target/ai-knowledge" "$WORK/core/output"; do
  "${CLEAN_ENV[@]}" java -cp "$CORE_JAR" "$ROOT/.github/fixtures/VerifyRetainedArtifacts.java" "$output"
  python3 - "$output" <<'PY'
import json, sys
from pathlib import Path
root = Path(sys.argv[1])
data = json.loads((root / 'boundary-analysis.json').read_text())
assert any(x['status'] == 'linked' and x['operation'] == 'GET /api/items' for x in data['links']), data
assert data['versionControlHistoryUsed'] is False
assert data['changeCouplingIncluded'] is False
assert (root / 'check.html').is_file()
PY
done
cp -R "$WORK/gradle/build/ai-knowledge" "$REPORT_DIR/gradle-output"
cp -R "$WORK/maven/target/ai-knowledge" "$REPORT_DIR/maven-output"
cp -R "$WORK/core/output" "$REPORT_DIR/core-output"
python3 - "$REPORT_DIR" "$VERSION" "$PUBLIC" <<'PY'
import json, sys
from pathlib import Path
data = {'version': sys.argv[2], 'publicAvailabilityVerified': sys.argv[3] == 'true',
        'credentialsUsed': False, 'sourceCompositeUsed': False, 'mavenLocalUsed': False,
        'verifiedConsumers': ['gradle-plugin-marker', 'maven-plugin', 'core-api']}
(Path(sys.argv[1]) / 'summary.json').write_text(json.dumps(data, indent=2) + '\n')
PY
