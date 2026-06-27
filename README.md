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

After publishing or installing locally, the Maven plugin exposes matching goals:

```bash
mvn io.github.carstenartur:ai-knowledge-maven-plugin:0.1.0-SNAPSHOT:generate
mvn io.github.carstenartur:ai-knowledge-maven-plugin:0.1.0-SNAPSHOT:analyze
mvn io.github.carstenartur:ai-knowledge-maven-plugin:0.1.0-SNAPSHOT:optimize
mvn io.github.carstenartur:ai-knowledge-maven-plugin:0.1.0-SNAPSHOT:benchmark
mvn io.github.carstenartur:ai-knowledge-maven-plugin:0.1.0-SNAPSHOT:check
```

## Scope

Implemented as deterministic static analysis without external LLM calls:

- repository knowledge extraction
- AI cognitive complexity estimate
- knowledge-smell recommendations
- deterministic extraction-profile benchmark scaffold

License: Apache-2.0
