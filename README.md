# AI Knowledge Extractor

Deterministic build-integrated knowledge extraction for AI-assisted code understanding.

This repository provides a Java core plus Gradle and Maven entry points. It generates stable files under `build/ai-knowledge/` for modules, classes, tests, docs, dependencies, capabilities, claims, complexity metrics, optimization hints and context-profile benchmark estimates.

## Gradle

Plugin id: `org.aiknowledge.extractor`

Tasks: `generateAiKnowledgeIndex`, `analyzeAiComplexity`, `optimizeAiKnowledge`, `benchmarkAiKnowledge`, `checkAiKnowledgeIndex`, `publishAiKnowledgeIndex`.

Optional empirical benchmark layer (disabled by default):

- `aiKnowledge.empiricalBenchmarkEnabled = true`
- `aiKnowledge.empiricalBenchmarkFixtureFile = file("ai-knowledge/benchmark-fixtures.yaml")`

For local plugin development, consumers may use a Gradle composite build with `includeBuild('../ai-knowledge-extractor')`.

Canonical Gradle plugin usage (plugin id, tasks, extension defaults, CI/local examples): [`docs/gradle-plugin.md`](docs/gradle-plugin.md).

## Maven

Maven plugin coordinates: `org.aiknowledge:ai-knowledge-maven-plugin:0.1.0-SNAPSHOT`.

Goals: `generate`, `analyze`, `optimize`, `benchmark`, `check`, `help`.

Maven help goal:

```bash
mvn org.aiknowledge:ai-knowledge-maven-plugin:<version>:help -Ddetail=true
```

Optional empirical benchmark layer parameters:

- `empiricalBenchmarkEnabled` (default `false`)
- `empiricalBenchmarkFixtureFile` (default `${project.basedir}/ai-knowledge/benchmark-fixtures.yaml`)

Canonical Maven goal, parameter and help usage reference: [`docs/maven-plugin.md`](docs/maven-plugin.md).

## Scope

Implemented as deterministic static analysis without external model calls:

- repository knowledge extraction
- AI cognitive complexity estimate
- trend reports and CI quality gates
- knowledge-smell recommendations
- deterministic extraction-profile benchmark scaffold with optional empirical fixture layer
- configurable model-profile budget metrics

## Model profiles

Default model profiles and project-specific `ai-knowledge/model-profiles.yaml` configuration are documented in [`docs/model-profiles.md`](docs/model-profiles.md).

## Trend gates

Trend baselines, `metrics-snapshot.json`, `trend.json` and CI threshold configuration are documented in [`docs/trend-gates.md`](docs/trend-gates.md).

## Publishing and consumption

Artifact IDs, local development consumption, GitHub Packages repository configuration and release-version handling are documented in [`docs/publishing.md`](docs/publishing.md). Plugin-specific canonical usage docs: [`docs/gradle-plugin.md`](docs/gradle-plugin.md) and [`docs/maven-plugin.md`](docs/maven-plugin.md).

## Citation

Citation metadata is maintained in `CITATION.cff`. Release metadata is maintained in `.zenodo.json`. See `docs/release.md` for the release checklist.

License: Apache-2.0
