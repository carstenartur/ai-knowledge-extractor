# Gradle plugin usage (`org.aiknowledge.extractor`)

This is the canonical user guide for applying and configuring the Gradle plugin.

## Plugin id and versioned application

Plugin id:

```text
org.aiknowledge.extractor
```

Consumer build:

```groovy
plugins {
    id 'org.aiknowledge.extractor' version '<version>'
}
```

## Plugin resolution and repositories

### GitHub Packages (released artifacts)

`settings.gradle`:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            url = uri('https://maven.pkg.github.com/carstenartur/ai-knowledge-extractor')
            credentials {
                username = findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR')
                password = findProperty('gpr.key') ?: System.getenv('GITHUB_TOKEN')
            }
        }
    }
}
```

### Local composite build (development only)

Use this only while developing plugin changes locally:

```groovy
pluginManagement {
    includeBuild('../ai-knowledge-extractor')
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

### Future Gradle Plugin Portal distribution

Plugin Portal publication is not part of the current release flow. Until that is added, use GitHub Packages for released versions or composite builds for local development.

## Tasks

| Task | Group | Purpose |
| --- | --- | --- |
| `generateAiKnowledgeIndex` | `documentation` | Generate deterministic knowledge index files in `build/ai-knowledge`. |
| `analyzeAiComplexity` | `verification` | Generate complexity metrics and trend reports. |
| `optimizeAiKnowledge` | `verification` | Generate optimization recommendations. |
| `benchmarkAiKnowledge` | `verification` | Generate model-profile benchmark reports. |
| `checkAiKnowledgeIndex` | `verification` | Run AI quality gates and fail build on violations. |
| `publishAiKnowledgeIndex` | `documentation` | Copy generated artifacts to `docs/ai-knowledge` (depends on `generateAiKnowledgeIndex`). |

## Extension configuration

```groovy
aiKnowledge {
    outputDirectory = layout.buildDirectory.dir('ai-knowledge')
    docsOutputDirectory = layout.projectDirectory.dir('docs/ai-knowledge')
    seedDirectory = layout.projectDirectory.dir('ai-knowledge')
    modelProfileDirectory = layout.projectDirectory.dir('ai-knowledge')
    failOnWarnings = false
    maxCognitiveDebt = 100.0d
    maxCognitiveDebtIncrease = Double.MAX_VALUE
    maxConceptRadiusIncrease = Double.MAX_VALUE
    maxContextTokenIncrease = Double.MAX_VALUE
    empiricalBenchmarkEnabled = false
    empiricalBenchmarkFixtureFile = layout.projectDirectory.file('ai-knowledge/benchmark-fixtures.yaml')
    requireCapabilityEvidence = false
    requireClaimVerification = false
    minContextPackCount = 0
    maxContextPackTokens = Integer.MAX_VALUE
}
```

| Property | Type | Default |
| --- | --- | --- |
| `outputDirectory` | `DirectoryProperty` | `build/ai-knowledge` |
| `docsOutputDirectory` | `DirectoryProperty` | `docs/ai-knowledge` |
| `seedDirectory` | `DirectoryProperty` | `ai-knowledge` |
| `modelProfileDirectory` | `DirectoryProperty` | `ai-knowledge` |
| `failOnWarnings` | `Property<Boolean>` | `false` |
| `maxCognitiveDebt` | `Property<Double>` | `100.0` |
| `maxCognitiveDebtIncrease` | `Property<Double>` | `Double.MAX_VALUE` |
| `maxConceptRadiusIncrease` | `Property<Double>` | `Double.MAX_VALUE` |
| `maxContextTokenIncrease` | `Property<Double>` | `Double.MAX_VALUE` |
| `empiricalBenchmarkEnabled` | `Property<Boolean>` | `false` |
| `empiricalBenchmarkFixtureFile` | `RegularFileProperty` | `ai-knowledge/benchmark-fixtures.yaml` |
| `requireCapabilityEvidence` | `Property<Boolean>` | `false` |
| `requireClaimVerification` | `Property<Boolean>` | `false` |
| `minContextPackCount` | `Property<Integer>` | `0` |
| `maxContextPackTokens` | `Property<Integer>` | `Integer.MAX_VALUE` |

## Example configurations

### CI-oriented configuration

```groovy
plugins {
    id 'org.aiknowledge.extractor' version '<version>'
}

tasks.named('check') {
    dependsOn('checkAiKnowledgeIndex')
}

aiKnowledge {
    failOnWarnings = true
    maxCognitiveDebt = 80.0d
    maxCognitiveDebtIncrease = 5.0d
    maxConceptRadiusIncrease = 0.5d
    maxContextTokenIncrease = 500.0d
    requireCapabilityEvidence = true
    requireClaimVerification = true
    minContextPackCount = 3
}
```

To use the JDT Java provider for stronger type/reference extraction, run Gradle with:

```bash
./gradlew generateAiKnowledgeIndex \
  -Daiknowledge.javaProvider=jdt \
  -Daiknowledge.jdt.mode=search \
  -Daiknowledge.jdt.search.execution.mode=forked \
  -Daiknowledge.jdt.workspace.mode=create
```

### Local development configuration

```groovy
plugins {
    id 'org.aiknowledge.extractor'
}

aiKnowledge {
    empiricalBenchmarkEnabled = true
    empiricalBenchmarkFixtureFile = layout.projectDirectory.file('ai-knowledge/benchmark-fixtures.yaml')
}
```
