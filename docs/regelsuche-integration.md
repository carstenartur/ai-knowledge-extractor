# Regelsuche integration

Use the project as a Gradle composite build until a release is published.

In `Regelsuche/settings.gradle` add the included build before the repositories block:

```groovy
pluginManagement {
    includeBuild('../ai-knowledge-extractor')
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Apply the plugin in the Regelsuche root build:

```groovy
plugins {
    id 'io.github.carstenartur.ai-knowledge'
}

aiKnowledge {
    outputDirectory = layout.buildDirectory.dir('ai-knowledge')
    docsOutputDirectory = layout.projectDirectory.dir('docs/ai-knowledge')
    seedDirectory = layout.projectDirectory.dir('ai-knowledge')
    modelProfileDirectory = layout.projectDirectory.dir('ai-knowledge')
    failOnWarnings = false
    maxCognitiveDebt = 100.0
}
```

Run:

```bash
./gradlew generateAiKnowledgeIndex
./gradlew analyzeAiComplexity
./gradlew optimizeAiKnowledge
./gradlew benchmarkAiKnowledge
./gradlew checkAiKnowledgeIndex
./gradlew publishAiKnowledgeIndex
```

The generated files are written to `build/ai-knowledge/`. The publish task copies them to `docs/ai-knowledge/` for committed snapshots.
