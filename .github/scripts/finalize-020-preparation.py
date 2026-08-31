#!/usr/bin/env python3
"""Materialize and adapt the 0.2.0 release-hardening changes on the preparation branch."""

from __future__ import annotations

from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
MATERIALIZER = Path("/tmp/apply-release-hardening.py")
VERSION = "0.2.0-SNAPSHOT"


def read(path: str | Path) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str | Path, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        if new in text:
            return text
        raise SystemExit(f"{label} marker missing")
    return text.replace(old, new, 1)


def run(*args: str) -> None:
    subprocess.run(args, cwd=ROOT, check=True)


def adapt_materializer() -> None:
    source = MATERIALIZER.read_text(encoding="utf-8")
    replacements = {
        'update("core/src/main/java/org/aiknowledge/core/KnowledgeExtractionPipeline.java", patch_pipeline)':
            'print("deferred KnowledgeExtractionPipeline patch for current source model")',
        'update(".github/workflows/ci.yml", patch_ci)':
            'print("deferred CI workflow patch; applied separately through the GitHub API")',
        "JavaScriptKnowledgeProvider": "JavaScriptTypeScriptKnowledgeProvider",
        ".callableFacts()": ".symbolFacts()",
    }
    for old, new in replacements.items():
        if old not in source:
            raise SystemExit(f"materializer compatibility marker missing: {old}")
        source = source.replace(old, new)
    MATERIALIZER.write_text(source, encoding="utf-8")


def write_result_contract() -> None:
    write(
        "core/src/main/java/org/aiknowledge/core/sourcespi/SourceKnowledgeResult.java",
        """package org.aiknowledge.core.sourcespi;

import java.util.List;
import java.util.Map;

/**
 * Language-neutral JSON-compatible facts emitted by a source provider.
 *
 * <p>Callables are symbols with {@code kind=callable}; parser-specific AST objects never cross
 * this contract. Empty categories are represented by empty lists. Recoverable limitations are
 * emitted through {@code warningFacts}; fatal I/O failures follow the shared source error policy.</p>
 */
public record SourceKnowledgeResult(
        List<Map<String, Object>> sourceUnitFacts,
        List<Map<String, Object>> symbolFacts,
        List<Map<String, Object>> relationFacts,
        List<Map<String, Object>> boundaryFacts,
        List<Map<String, Object>> warningFacts) {

    public SourceKnowledgeResult {
        sourceUnitFacts = immutable(sourceUnitFacts);
        symbolFacts = immutable(symbolFacts);
        relationFacts = immutable(relationFacts);
        boundaryFacts = immutable(boundaryFacts);
        warningFacts = immutable(warningFacts);
    }

    public static SourceKnowledgeResult empty() {
        return new SourceKnowledgeResult(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static List<Map<String, Object>> immutable(List<Map<String, Object>> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
""",
    )


def patch_pipeline() -> None:
    path = "core/src/main/java/org/aiknowledge/core/KnowledgeExtractionPipeline.java"
    pipeline = read(path)

    import_line = "import org.aiknowledge.core.sourcespi.SourceAnalysisConfiguration;"
    if import_line not in pipeline:
        marker = "import org.aiknowledge.core.sourcespi.SourceKnowledgeProvider;"
        pipeline = replace_once(
            pipeline,
            marker,
            import_line + "\n" + marker,
            "source-provider import",
        )

    field = (
        "    private final SourceAnalysisConfiguration sourceAnalysisConfiguration = "
        "SourceAnalysisConfiguration.fromSystemProperties();\n"
    )
    if "private final SourceAnalysisConfiguration sourceAnalysisConfiguration" not in pipeline:
        marker = "    private final List<SourceKnowledgeProvider> sourceKnowledgeProviders;\n"
        pipeline = replace_once(pipeline, marker, marker + field, "source-provider field")

    old_loop = """                if (!provider.supports(path)) continue;
                SourceKnowledgeResult result = provider.extract(new SourceKnowledgeRequest(
                        root,
                        file,
                        path,
                        snapshot.modules,
                        buildMetadata,
                        Map.of()));
                recordSourceFacts(snapshot, provider, path, result);"""
    new_loop = """                if (!sourceAnalysisConfiguration.providerEnabled(provider.id())
                        || !provider.supports(path)
                        || !sourceAnalysisConfiguration.acceptsSource(file, path)) {
                    continue;
                }
                try {
                    SourceKnowledgeResult result = provider.extract(new SourceKnowledgeRequest(
                            root,
                            file,
                            path,
                            snapshot.modules,
                            buildMetadata,
                            Map.of()));
                    recordSourceFacts(snapshot, provider, path, result);
                } catch (Exception exception) {
                    handleSourceProviderFailure(snapshot, provider, path, exception);
                }"""
    if old_loop in pipeline:
        pipeline = pipeline.replace(old_loop, new_loop, 1)
    elif new_loop not in pipeline:
        raise SystemExit("current provider loop marker missing")

    if "private void handleSourceProviderFailure(" not in pipeline:
        marker = "    private static void recordJavaFacts(RepositorySnapshot snapshot) {"
        handler = """    private void handleSourceProviderFailure(
            RepositorySnapshot snapshot,
            SourceKnowledgeProvider provider,
            String sourcePath,
            Exception exception) throws IOException {
        if (sourceAnalysisConfiguration.errorPolicy() == SourceAnalysisConfiguration.ErrorPolicy.FAIL) {
            if (exception instanceof IOException ioException) throw ioException;
            throw new IOException("Source provider " + provider.id() + " failed for " + sourcePath, exception);
        }
        if (sourceAnalysisConfiguration.errorPolicy() == SourceAnalysisConfiguration.ErrorPolicy.WARN) {
            Map<String, Object> warning = new LinkedHashMap<>();
            warning.put("provider", provider.id());
            warning.put("sourceFile", sourcePath);
            warning.put("code", "source-provider-failure");
            warning.put("message", exception.getClass().getSimpleName() + ": "
                    + String.valueOf(exception.getMessage()));
            snapshot.warnings.add(warning);
        }
    }

"""
        pipeline = replace_once(pipeline, marker, handler + marker, "recordJavaFacts")

    evidence = "        snapshot.evidence.add(sourceAnalysisConfiguration.asEvidence());\n"
    if evidence not in pipeline:
        marker = "        RepositoryFacts.populateIndex(root, snapshot);"
        pipeline = replace_once(pipeline, marker, evidence + marker, "repository index")

    write(path, pipeline)


def patch_provider_documentation() -> None:
    path = "docs/provider-spi.md"
    docs = read(path)
    old = (
        "- `symbolFacts`: types, fields, values and declarations;\n"
        "- `callableFacts`: functions, methods, hooks or equivalent executable symbols;"
    )
    new = (
        "- `symbolFacts`: types, fields, values, declarations and executable symbols with "
        "`kind=callable`;"
    )
    if old in docs:
        docs = docs.replace(old, new, 1)
    write(path, docs)


def set_candidate_version() -> None:
    gradle = ROOT / "gradle.properties"
    lines = [
        line
        for line in gradle.read_text(encoding="utf-8").splitlines()
        if not line.startswith("projectVersion=")
    ]
    lines.append(f"projectVersion={VERSION}")
    gradle.write_text("\n".join(lines) + "\n", encoding="utf-8")

    write(
        "release.properties",
        "# Managed by the audited release workflow.\nnext.release.version=0.2.0\n",
    )

    plugin_path = "maven/src/main/resources/META-INF/maven/plugin.xml"
    plugin = read(plugin_path)
    plugin, count = re.subn(
        r"(<version>)[^<]+(</version>)",
        rf"\g<1>{VERSION}\g<2>",
        plugin,
        count=1,
    )
    if count != 1:
        raise SystemExit("Maven plugin descriptor version marker missing")
    write(plugin_path, plugin)

    run("python3", ".github/scripts/update-release-metadata.py", VERSION)


def patch_gradle_build() -> None:
    path = "build.gradle"
    source = read(path)
    if "tasks.withType(Javadoc).configureEach" not in source:
        marker = """    tasks.withType(JavaCompile).configureEach {
        options.encoding = 'UTF-8'
        options.release = 17
    }
"""
        addition = """
    tasks.withType(Javadoc).configureEach {
        options.encoding = 'UTF-8'
        options.charSet = 'UTF-8'
        options.docEncoding = 'UTF-8'
    }
"""
        source = replace_once(source, marker, marker + addition, "JavaCompile configuration")
    write(path, source)


def patch_site() -> None:
    write(
        "site/pom.xml",
        f"""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>org.aiknowledge</groupId>
  <artifactId>ai-knowledge-maven-plugin</artifactId>
  <version>${{revision}}</version>
  <packaging>jar</packaging>

  <name>AI Knowledge Maven Plugin</name>
  <description>Deterministic build-integrated knowledge extraction for AI-assisted code understanding.</description>
  <url>https://carstenartur.github.io/ai-knowledge-extractor/</url>
  <inceptionYear>2024</inceptionYear>

  <developers>
    <developer>
      <id>carstenartur</id>
      <name>Carsten Hammer</name>
      <roles>
        <role>developer</role>
        <role>maintainer</role>
      </roles>
    </developer>
  </developers>

  <licenses>
    <license>
      <name>Apache License, Version 2.0</name>
      <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
      <distribution>repo</distribution>
    </license>
  </licenses>

  <scm>
    <connection>scm:git:https://github.com/carstenartur/ai-knowledge-extractor.git</connection>
    <developerConnection>scm:git:https://github.com/carstenartur/ai-knowledge-extractor.git</developerConnection>
    <url>https://github.com/carstenartur/ai-knowledge-extractor</url>
  </scm>

  <issueManagement>
    <system>GitHub Issues</system>
    <url>https://github.com/carstenartur/ai-knowledge-extractor/issues</url>
  </issueManagement>

  <properties>
    <revision>{VERSION}</revision>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <build>
    <resources>
      <resource>
        <directory>../maven/src/main/resources</directory>
        <includes>
          <include>META-INF/maven/plugin.xml</include>
        </includes>
      </resource>
    </resources>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-site-plugin</artifactId>
        <version>3.21.0</version>
      </plugin>
    </plugins>
  </build>

  <reporting>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-project-info-reports-plugin</artifactId>
        <version>3.9.0</version>
      </plugin>
    </plugins>
  </reporting>
</project>
""",
    )

    write(
        "site/src/site/site.xml",
        """<?xml version="1.0" encoding="UTF-8"?>
<site xmlns="http://maven.apache.org/SITE/2.0.0"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://maven.apache.org/SITE/2.0.0
                          https://maven.apache.org/xsd/site-2.0.0.xsd"
      name="AI Knowledge Maven Plugin">
  <skin>
    <groupId>org.apache.maven.skins</groupId>
    <artifactId>maven-fluido-skin</artifactId>
    <version>2.0.0-M11</version>
  </skin>
  <bannerLeft name="AI Knowledge Extractor"
              href="https://github.com/carstenartur/ai-knowledge-extractor"/>
  <body>
    <links>
      <item name="GitHub" href="https://github.com/carstenartur/ai-knowledge-extractor"/>
      <item name="Issues" href="https://github.com/carstenartur/ai-knowledge-extractor/issues"/>
    </links>
    <menu name="Documentation">
      <item name="About" href="index.html"/>
      <item name="generate" href="generate-mojo.html"/>
      <item name="analyze" href="analyze-mojo.html"/>
      <item name="optimize" href="optimize-mojo.html"/>
      <item name="benchmark" href="benchmark-mojo.html"/>
      <item name="check" href="check-mojo.html"/>
      <item name="help" href="help-mojo.html"/>
    </menu>
    <menu ref="reports"/>
  </body>
</site>
""",
    )


def patch_consumer_verification() -> None:
    write(
        ".github/scripts/verify-consumer-fixtures.sh",
        """#!/usr/bin/env bash
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
  rm -rf \
    examples/fixtures/gradle-consumer/build \
    examples/fixtures/gradle-consumer/docs/ai-knowledge \
    examples/fixtures/maven-consumer/target
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
  for file in \
    index.json \
    source-units.json \
    symbols.json \
    relations.json \
    boundaries.json \
    warnings.json \
    boundary-analysis.json \
    check.json; do
    test -s "$directory/$file" || fail "missing consumer artifact: $directory/$file"
  done
  if grep -RIl --include='*.json' "$ROOT" "$directory" | grep -q .; then
    fail "consumer output exposes the absolute checkout path: $directory"
  fi
}

clean_outputs
echo 'Verifying Gradle consumer fixture through source composite'
"$GRADLE" --no-daemon -p examples/fixtures/gradle-consumer \
  generateAiKnowledgeIndex checkAiKnowledgeIndex publishAiKnowledgeIndex
assert_schema_v2 examples/fixtures/gradle-consumer/build/ai-knowledge
test -s examples/fixtures/gradle-consumer/docs/ai-knowledge/index.json \
  || fail 'published Gradle documentation artifact is missing'

clean_outputs
PUBLISHED_GRADLE_HOME=$(mktemp -d)
PUBLISHED_LOG=$(mktemp)
echo 'Verifying the published Gradle plugin marker without a composite build'
GRADLE_USER_HOME="$PUBLISHED_GRADLE_HOME" \
  "$GRADLE" --no-daemon --refresh-dependencies \
  -p examples/fixtures/gradle-consumer \
  -PpublishedPlugin=true \
  -PaiKnowledgeVersion="$VERSION" \
  generateAiKnowledgeIndex checkAiKnowledgeIndex publishAiKnowledgeIndex \
  | tee "$PUBLISHED_LOG"
if grep -Fq ':ai-knowledge-extractor:' "$PUBLISHED_LOG"; then
  fail 'published-plugin verification unexpectedly used the source composite build'
fi
assert_schema_v2 examples/fixtures/gradle-consumer/build/ai-knowledge

echo 'Verifying every Maven plugin goal through Maven-local coordinates'
clean_outputs
mvn -B -f examples/fixtures/maven-consumer/pom.xml \
  -DaiKnowledge.version="$VERSION" \
  org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":generate \
  org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":analyze \
  org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":optimize \
  org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":benchmark \
  org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":check \
  org.aiknowledge:ai-knowledge-maven-plugin:"$VERSION":help
assert_schema_v2 examples/fixtures/maven-consumer/target/ai-knowledge

echo 'Consumer fixture verification completed'
""",
    )


def patch_release_readiness() -> None:
    write(
        ".github/scripts/verify-release-readiness.sh",
        """#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

fail() {
  echo "release-readiness: $*" >&2
  exit 1
}

for temporary in \
  .github/workflows/export-release-hardening-bootstrap.yml \
  .github/workflows/apply-release-hardening.yml \
  .github/workflows/finalize-release-hardening.yml \
  .github/workflows/override-finalize-release-hardening.yml \
  .github/scripts/apply-release-hardening.py \
  .github/scripts/finalize-020-preparation.py \
  .release-transfer; do
  [[ ! -e "$temporary" ]] || fail "temporary integration file remains: $temporary"
done

grep -q 'aiknowledge.source.enabledProviders' docs/integrator-quickstart.md \
  || fail 'shared source configuration is not documented'
grep -q 'versionControlHistoryUsed' docs/schema-v2-contract.md \
  || fail 'Git-history exclusion is not part of the schema contract'
grep -q 'Maven Central' docs/releases/0.2.0.md \
  || fail 'publication status is not documented'
! grep -q 'plugin-info.html' site/src/site/site.xml \
  || fail 'site navigation still points to the removed plugin report'

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
  "$GRADLE" --no-daemon \
  :core:javadoc :gradle-plugin:javadoc :maven:javadoc --warning-mode all

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

find core gradle-plugin maven -path '*/build/libs/*' -type f -print0 \
  | sort -z \
  | xargs -0 sha256sum > "$REPORT/artifacts.sha256"
[[ -s "$REPORT/artifacts.sha256" ]] || fail 'no release artifacts were found for checksums'

echo "release-readiness checks passed; checksums: $REPORT/artifacts.sha256"
""",
    )


def main() -> None:
    if not MATERIALIZER.is_file():
        raise SystemExit(f"materializer not found: {MATERIALIZER}")
    adapt_materializer()
    run("python3", str(MATERIALIZER))
    write_result_contract()
    patch_pipeline()
    patch_provider_documentation()
    set_candidate_version()
    patch_gradle_build()
    patch_site()
    patch_consumer_verification()
    patch_release_readiness()


if __name__ == "__main__":
    main()
