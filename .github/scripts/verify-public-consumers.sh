#!/usr/bin/env bash
set -euo pipefail

: "${VERSION:?Set VERSION to the stable release to verify}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || exit 1
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
GRADLE=${GRADLE:-gradle}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

# Public modes always resolve against the named registries with empty caches.
MODE=public
REPOSITORY=''
if [[ $# -eq 1 && "$1" == --central-only ]]; then
  MODE=central
elif [[ $# -eq 2 && "$1" == --repository && -d "$2" ]]; then
  REPOSITORY=$(python3 -c 'import pathlib,sys; print(pathlib.Path(sys.argv[1]).resolve().as_uri())' "$2")
  MODE=rehearsal
elif [[ $# -ne 0 ]]; then
  echo "Usage: $0 [--central-only | --repository DIRECTORY]" >&2
  exit 1
fi
report_name=public-consumers
if [[ "$MODE" == central ]]; then
  report_name=central-consumers
fi
REPORT_DIR=${AI_KNOWLEDGE_PUBLIC_REPORT_DIR:-$ROOT/build/reports/$report_name}
mkdir -p "$REPORT_DIR" "$WORK/gradle" "$WORK/maven" "$WORK/core"
rm -f "$REPORT_DIR/summary.json"
if [[ "$MODE" == public ]]; then
  bash "$ROOT/.github/scripts/verify-portal-listing.sh" "$VERSION" | tee "$REPORT_DIR/portal-listing.log"
fi

python3 - "$WORK" "$VERSION" "$REPOSITORY" "$MODE" <<'PYTHON'
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
  filter { includeGroup('io.github.carstenartur.ai-knowledge-extractor') }
}""" if sys.argv[4] == 'public' else ''
(work / 'gradle/settings.gradle').write_text(f'''pluginManagement {{
    repositories {{
        {local}
        {portal}
        mavenCentral()
    }}
}}
rootProject.name = 'anonymous-public-consumer'
''')
(work / 'gradle/build.gradle').write_text(f'''plugins {{
    id 'java'
    id 'io.github.carstenartur.ai-knowledge-extractor' version '{version}'
}}
aiKnowledge {{ failOnWarnings = false; maxCognitiveDebt = 1000.0d }}
''')
repos = f'''<repositories><repository><id>rehearsal</id><url>{escape(repository)}</url>
</repository></repositories><pluginRepositories><pluginRepository><id>rehearsal</id>
<url>{escape(repository)}</url></pluginRepository></pluginRepositories>''' if repository else ''
pom = f'''<project xmlns="http://maven.apache.org/POM/4.0.0">
<modelVersion>4.0.0</modelVersion><groupId>fixture</groupId>
<artifactId>anonymous-public-consumer</artifactId><version>1.0</version>{repos}
<dependencies><dependency><groupId>io.github.carstenartur</groupId>
<artifactId>ai-knowledge-core</artifactId><version>{version}</version></dependency>
<dependency><groupId>io.github.carstenartur</groupId><artifactId>ai-knowledge-gradle-plugin</artifactId>
<version>{version}</version><scope>runtime</scope></dependency></dependencies>
<build><plugins><plugin><groupId>io.github.carstenartur</groupId>
<artifactId>ai-knowledge-maven-plugin</artifactId><version>{version}</version>
<configuration><failOnWarnings>false</failOnWarnings><maxCognitiveDebt>1000.0</maxCognitiveDebt>
</configuration></plugin></plugins></build></project>'''
(work / 'maven/pom.xml').write_text(pom)
(work / 'settings.xml').write_text('<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>')
PYTHON

# An allowlisted environment plus empty user/global settings prevents credentials,
# init scripts, Maven-local installs and authenticated mirror fallbacks.
CLEAN_ENV=(env -i "PATH=$PATH" "JAVA_HOME=${JAVA_HOME:?JDK required}" LANG=C.UTF-8)
"${CLEAN_ENV[@]}" GRADLE_USER_HOME="$WORK/gradle-home" \
  "$GRADLE" --no-daemon --refresh-dependencies -p "$WORK/gradle" \
  optimizeAiKnowledge benchmarkAiKnowledge aiKnowledgeCheck \
  2>&1 | tee "$REPORT_DIR/gradle.log"
"${CLEAN_ENV[@]}" mvn -B -U -s "$WORK/settings.xml" -gs "$WORK/settings.xml" \
  -Dmaven.repo.local="$WORK/maven-cache" -f "$WORK/maven/pom.xml" \
  io.github.carstenartur:ai-knowledge-maven-plugin:"$VERSION":optimize \
  io.github.carstenartur:ai-knowledge-maven-plugin:"$VERSION":benchmark \
  io.github.carstenartur:ai-knowledge-maven-plugin:"$VERSION":check \
  io.github.carstenartur:ai-knowledge-maven-plugin:"$VERSION":help \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
  -Dmdep.outputFile="$WORK/classpath.txt" 2>&1 | tee "$REPORT_DIR/maven.log"

cat > "$WORK/PublicCoreConsumer.java" <<'JAVA'
import java.nio.file.Path;
import org.aiknowledge.core.AiKnowledgeRunner;
import org.aiknowledge.core.ExtractionOptions;
class PublicCoreConsumer {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        var options = ExtractionOptions.defaults(root, root.resolve("build/ai-knowledge"));
        var runner = new AiKnowledgeRunner();
        runner.optimize(options);
        runner.benchmark(options);
        runner.check(options);
    }
}
JAVA
"${CLEAN_ENV[@]}" java -cp "$(cat "$WORK/classpath.txt")" \
  "$WORK/PublicCoreConsumer.java" "$WORK/core" 2>&1 | tee "$REPORT_DIR/core.log"
CORE_JAR="$WORK/maven-cache/io/github/carstenartur/ai-knowledge-core/$VERSION/ai-knowledge-core-$VERSION.jar"
for output in "$WORK/gradle/build/ai-knowledge" "$WORK/maven/target/ai-knowledge" "$WORK/core/build/ai-knowledge"; do
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
mkdir -p "$REPORT_DIR/gradle-output" "$REPORT_DIR/maven-output" "$REPORT_DIR/core-output"
cp -R "$WORK/gradle/build/ai-knowledge/." "$REPORT_DIR/gradle-output"
cp -R "$WORK/maven/target/ai-knowledge/." "$REPORT_DIR/maven-output"
cp -R "$WORK/core/build/ai-knowledge/." "$REPORT_DIR/core-output"
python3 - "$REPORT_DIR" "$VERSION" "$MODE" <<'PY'
import json, sys
from pathlib import Path
mode = sys.argv[3]
data = {'version': sys.argv[2], 'centralAvailabilityVerified': mode in {'central', 'public'},
        'publicAvailabilityVerified': mode == 'public', 'portalListingVerified': mode == 'public',
        'gradleMarkerSource': {'public': 'Gradle Plugin Portal', 'central': 'Maven Central',
                               'rehearsal': 'rehearsal repository'}[mode],
        'credentialsUsed': False, 'sourceCompositeUsed': False, 'mavenLocalUsed': False,
        'verifiedConsumers': ['gradle-plugin-marker', 'maven-plugin', 'core-api'],
        'explicitGradleImplementationAliasResolved': True}
(Path(sys.argv[1]) / 'summary.json').write_text(json.dumps(data, indent=2) + '\n')
PY
