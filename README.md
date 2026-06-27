# AI Knowledge Extractor

Deterministic build-integrated knowledge extraction for AI-assisted code understanding.

This repository provides a Java core plus Gradle and Maven entry points. It generates stable files under `build/ai-knowledge/` so tools can inspect modules, classes, tests, docs, dependencies, capabilities, claims, complexity metrics, optimization hints and context-profile benchmark estimates.

## Gradle

Apply the plugin in a consuming root build:

```groovy
plugins {
    id 'io.github.carstenartur.ai-knowledge'
}
```

Available tasks:

```bash
./gradlew generateAiKnowledgeIndex
./gradlew analyzeAiComplexity
./gradlew optimizeAiKnowledge
./gradlew benchmarkAiKnowledge
./gradlew checkAiKnowledgeIndex
./gradlew publishAiKnowledgeIndex
```

For Regelsuche, use the project as a composite build first. See `docs/regelsuche-integration.md`.

## Maven

The current Maven module contains the shared configuration and first goal classes. The Gradle plugin is more complete in this commit; the remaining Maven goal descriptor wiring still needs CI verification.

## Scope

Implemented as deterministic static analysis without external LLM calls:

- repository knowledge extraction
- AI cognitive complexity estimate
- knowledge-smell recommendations
- deterministic extraction-profile benchmark scaffold

License: Apache-2.0
