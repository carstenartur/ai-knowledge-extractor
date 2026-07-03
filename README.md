# AI Knowledge Extractor

[![CI](https://github.com/carstenartur/ai-knowledge-extractor/actions/workflows/ci.yml/badge.svg)](https://github.com/carstenartur/ai-knowledge-extractor/actions/workflows/ci.yml)
[![Release](https://github.com/carstenartur/ai-knowledge-extractor/actions/workflows/publish.yml/badge.svg)](https://github.com/carstenartur/ai-knowledge-extractor/actions/workflows/publish.yml)
[![License](https://img.shields.io/github/license/carstenartur/ai-knowledge-extractor)](LICENSE)
![Java 17](https://img.shields.io/badge/Java-17-blue)
[![Release version](https://img.shields.io/github/v/release/carstenartur/ai-knowledge-extractor?sort=semver)](https://github.com/carstenartur/ai-knowledge-extractor/releases)
[![GitHub Packages](https://img.shields.io/badge/packages-GitHub%20Packages-blue)](docs/publishing.md)
[![Citation](https://img.shields.io/badge/citation-CFF-informational)](CITATION.cff)
[![Documentation](https://img.shields.io/badge/docs-GitHub%20Pages-blue)](https://carstenartur.github.io/ai-knowledge-extractor/)

Deterministic build-integrated knowledge extraction for AI-assisted code understanding.

This repository provides a Java core plus Gradle and Maven entry points. It generates stable files under `build/ai-knowledge/` for modules, classes, tests, docs, dependencies, capabilities, claims, complexity metrics, optimization hints and context-profile benchmark estimates.

## Quick start

- Gradle: apply plugin `org.aiknowledge.extractor` and run `./gradlew generateAiKnowledgeIndex`.
- Maven: invoke `mvn org.aiknowledge:ai-knowledge-maven-plugin:<version>:generate`.
- Consumer setup, tasks, extension parameters, goals and repository configuration are documented in [`docs/gradle-plugin.md`](docs/gradle-plugin.md), [`docs/maven-plugin.md`](docs/maven-plugin.md) and [`docs/publishing.md`](docs/publishing.md).
- Releases are published through the GitHub Actions [`Release` workflow](https://github.com/carstenartur/ai-knowledge-extractor/actions/workflows/publish.yml); use the documented `dry_run` mode before the first real release.

## Gradle

Plugin id: `org.aiknowledge.extractor`

Tasks: `generateAiKnowledgeIndex`, `analyzeAiComplexity`, `optimizeAiKnowledge`, `benchmarkAiKnowledge`, `checkAiKnowledgeIndex`, `publishAiKnowledgeIndex`.

Optional empirical benchmark layer (disabled by default):

- `aiKnowledge.empiricalBenchmarkEnabled = true`
- `aiKnowledge.empiricalBenchmarkFixtureFile = file("ai-knowledge/benchmark-fixtures.yaml")`

For local plugin development, consumers may use a Gradle composite build with `includeBuild('../ai-knowledge-extractor')`.

Canonical Gradle plugin usage (plugin id, tasks, extension defaults, CI/local examples): [`docs/gradle-plugin.md`](docs/gradle-plugin.md).

## Maven

Maven plugin coordinates: `org.aiknowledge:ai-knowledge-maven-plugin:<version>`.

Goals: `generate`, `analyze`, `optimize`, `benchmark`, `check`, `help`.

Maven help goal:

```bash
mvn org.aiknowledge:ai-knowledge-maven-plugin:<version>:help -Ddetail=true
```

Optional empirical benchmark layer parameters:

- `empiricalBenchmarkEnabled` (default `false`)
- `empiricalBenchmarkFixtureFile` (default `${project.basedir}/ai-knowledge/benchmark-fixtures.yaml`)

Canonical Maven goal, parameter and help usage reference: [`docs/maven-plugin.md`](docs/maven-plugin.md). For the generated Maven plugin reference with full parameter tables for all goals, see the [GitHub Pages documentation site](https://carstenartur.github.io/ai-knowledge-extractor/).

## Scope

Implemented as deterministic static analysis without external model calls:

- repository knowledge extraction
- AI cognitive complexity estimate
- trend reports and CI quality gates
- knowledge-smell recommendations
- deterministic extraction-profile benchmark scaffold with optional empirical fixture layer
- configurable model-profile budget metrics

## Extraction architecture packages

The core extractor is organized as package-level modules (within `core`) so responsibilities stay separated while preserving the current artifacts:

- `org.aiknowledge.core.model`: stable repository fact model helpers (`RepositoryFacts` index/count assembly).
- `org.aiknowledge.core`: orchestration and outputs (`AiKnowledgeRunner`, `KnowledgeExtractionPipeline`).
- `org.aiknowledge.core.repositoryscan`: repository inventory and non-Java scanners (build files, docs, workflows, discovery evidence, benchmark sources).
- `org.aiknowledge.core.javaspi`: Java provider interface (`JavaKnowledgeProvider`).
- `org.aiknowledge.core.javabasic`: default heuristic Java provider implementation.
- `org.aiknowledge.core.linker`: capability/claim linking from extracted evidence.
- `org.aiknowledge.core.context`: seed/context merge step before output writing.

### Java provider selection and limitations

- Java extraction is resolved via Java `ServiceLoader` (`JavaKnowledgeProvider` SPI) and selected through `-Daiknowledge.javaProvider=<basic|jdt|providerClassName>`.
- The built-in fallback is `org.aiknowledge.core.javabasic.BasicJavaKnowledgeProvider`.
- The optional JDT provider is `org.aiknowledge.core.javajdt.JdtJavaKnowledgeProvider` and enriches type/interface/reference facts (`classes.json` and `tests.json`) using Eclipse JDT model/search APIs.
- Use the JDT provider when you need stronger interface implementation mapping, type reference discovery and test-to-production links; keep `basic` for lightweight heuristic extraction.
- JDT runs best with complete compile classpaths. If classpath data is incomplete, the provider records warnings in extracted facts (for example `jdt-classpath-incomplete` / `jdt-bindings-incomplete`).
- `BasicJavaKnowledgeProvider` preserves current `classes.json` and `tests.json` fields and adds structured Java facts, but it is still heuristic line-based parsing and may miss complex Java syntax.

### JDT provider: fieldFacts and relationFacts

When `-Daiknowledge.javaProvider=jdt` is active, the provider emits two additional fact collections per source file:

**`fieldFacts`** — one entry per field declaration, including:

| Field | Description |
|---|---|
| `declaringType` | Fully qualified name of the declaring class |
| `name` | Field name |
| `fieldType` | Declared type (syntactic name) |
| `modifiers` | Modifier keywords (e.g. `private`, `final`) |
| `sourceFile` | Repository-relative path |
| `offset` / `length` | Character position in source file |
| `line` | 1-based line number |
| `provider` | Always `jdt-ast` |
| `confidence` | `syntactic` (AST) or `binding` (with full classpath) |

**`relationFacts`** — one entry per structural relationship, with `kind` values:

| Kind | Description |
|---|---|
| `PACKAGE_CONTAINS_TYPE` | Package → type |
| `TYPE_EXTENDS_TYPE` | Class superclass |
| `TYPE_IMPLEMENTS_TYPE` | Interface implementation |
| `FIELD_HAS_TYPE` | Field → declared type |
| `METHOD_RETURNS_TYPE` | Method → return type |
| `METHOD_PARAMETER_HAS_TYPE` | Method parameter → type |
| `TYPE_REFERENCES_TYPE` | Any resolved cross-file reference |
| `TEST_REFERENCES_PRODUCTION_TYPE` | Test class → referenced production type |

Each relation fact includes `source`, `target`, `sourceFile`, `offset`/`length` (when available), `line`, `provider`, and `confidence`.

### Classpath configuration for the JDT provider

The Gradle plugin automatically resolves the `compileClasspath` configuration and passes it to the JDT provider. You can also set `javaProvider` and `jdtMode` in the extension block:

```groovy
aiKnowledge {
    javaProvider = "jdt"   // basic | jdt
    jdtMode      = "ast"   // ast (headless default; workspace-backed search planned)
}
```

Or via system properties:

```bash
./gradlew generateAiKnowledgeIndex \
  -Daiknowledge.javaProvider=jdt \
  -Daiknowledge.jdt.mode=ast
```

The Maven plugin accepts the same parameters:

```xml
<configuration>
    <javaProvider>jdt</javaProvider>
    <jdtMode>ast</jdtMode>
</configuration>
```

The Maven plugin also automatically injects `${project.compileClasspathElements}` so bindings resolve correctly when the project has been compiled.

## Model profiles

Default model profiles and project-specific `ai-knowledge/model-profiles.yaml` configuration are documented in [`docs/model-profiles.md`](docs/model-profiles.md).

## Trend gates

Trend baselines, `metrics-snapshot.json`, `trend.json` and CI threshold configuration are documented in [`docs/trend-gates.md`](docs/trend-gates.md).

## Publishing and consumption

Artifact IDs, local development consumption, GitHub Packages repository configuration and release-version handling are documented in [`docs/publishing.md`](docs/publishing.md). Plugin-specific canonical usage docs: [`docs/gradle-plugin.md`](docs/gradle-plugin.md) and [`docs/maven-plugin.md`](docs/maven-plugin.md).

## Release process

Release execution, `dry_run`, metadata updates, tagging and the follow-up PR flow are documented in [`docs/release.md`](docs/release.md).

## Citation

Citation metadata is maintained in `CITATION.cff`. Release metadata is maintained in `.zenodo.json`. See `docs/release.md` for the release checklist.

License: Apache-2.0
