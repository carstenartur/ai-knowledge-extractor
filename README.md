# AI Knowledge Extractor

Deterministic build-integrated knowledge extraction for AI-assisted code understanding.

This repository provides a Java core plus Gradle and Maven entry points. It generates stable files under `build/ai-knowledge/` for modules, classes, tests, docs, dependencies, capabilities, claims, complexity metrics, optimization hints and context-profile benchmark estimates.

## Gradle

Plugin id: `org.aiknowledge.extractor`

Tasks: `generateAiKnowledgeIndex`, `analyzeAiComplexity`, `optimizeAiKnowledge`, `benchmarkAiKnowledge`, `checkAiKnowledgeIndex`, `publishAiKnowledgeIndex`.

For local plugin development, consumers may use a Gradle composite build with `includeBuild('../ai-knowledge-extractor')`.

## Maven

Maven plugin coordinates: `org.aiknowledge:ai-knowledge-maven-plugin:0.1.0-SNAPSHOT`.

Goals: `generate`, `analyze`, `optimize`, `benchmark`, `check`.

## Scope

Implemented as deterministic static analysis without external model calls:

- repository knowledge extraction
- AI cognitive complexity estimate
- knowledge-smell recommendations
- deterministic extraction-profile benchmark scaffold
- configurable model-profile budget metrics

## Model profiles

Default model profiles and project-specific `ai-knowledge/model-profiles.yaml` configuration are documented in [`docs/model-profiles.md`](docs/model-profiles.md).

## Publishing and consumption

Artifact IDs, local development consumption, GitHub Packages repository configuration and release-version handling are documented in [`docs/publishing.md`](docs/publishing.md).

## Citation

Citation metadata is maintained in `CITATION.cff`. Release metadata is maintained in `.zenodo.json`. See `docs/release.md` for the release checklist.

License: Apache-2.0
