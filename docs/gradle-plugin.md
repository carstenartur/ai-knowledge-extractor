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
| `generateAiKnowledgeIndex` | `documentation` | Generate deterministic knowledge index, review context and context packs in `build/ai-knowledge`. |
| `analyzeAiComplexity` | `verification` | Generate complexity metrics and trend reports. |
| `optimizeAiKnowledge` | `verification` | Generate optimization recommendations. |
| `benchmarkAiKnowledge` | `verification` | Generate model-profile benchmark reports. |
| `checkAiKnowledgeIndex` | `verification` | Run configured quality gates, write `check.json` and verify the quality-gate artifact set. |
| `verifyAiKnowledgeArtifacts` | `verification` | Verify the complete generated artifact contract, including optimization and benchmark outputs. |
| `aiKnowledgeCheck` | `verification` | Canonical one-command lifecycle: generation, analysis, optimization, benchmark, quality gate and artifact verification. |
| `publishAiKnowledgeIndex` | `documentation` | Copy generated artifacts to `docs/ai-knowledge` (depends on `generateAiKnowledgeIndex`). |

For CI and release verification, prefer:

```bash
./gradlew aiKnowledgeCheck
```

The lifecycle rejects missing or empty required files, malformed JSON, duplicate object fields, trailing JSON tokens, index/envelope count drift, context-pack index drift, missing context packs, inconsistent context-footprint v3 data and disagreement between `check.json` and `complexity.json`.

Artifact verification is intentionally different from project policy. The verifier establishes structural and cross-document integrity. Thresholds such as maximum context debt, required capability evidence or acceptable unresolved selectors remain controlled by the configured quality gate.

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
| `javaProvider` | `Property<String>` | `basic` |
| `jdtMode` | `Property<String>` | `ast` |
| `jdtWorkspaceMode` | `Property<String>` | `create` |
| `jdtSearchExecutionMode` | `Property<String>` | `forked` |
| `jdtSearchFallbackToAst` | `Property<Boolean>` | `true` |
| `jdtWorkspaceDirectory` | `Property<String>` | `` |
| `keepJdtWorkspace` | `Property<Boolean>` | `false` |

## Example configurations

### CI-oriented configuration

```groovy
plugins {
    id 'org.aiknowledge.extractor' version '<version>'
}

tasks.named('check') {
    dependsOn('aiKnowledgeCheck')
}

aiKnowledge {
    javaProvider = "jdt"
    jdtMode = "search"
    jdtSearchExecutionMode = "forked"
    jdtSearchFallbackToAst = true
    jdtWorkspaceMode = "create"
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
./gradlew aiKnowledgeCheck \
  -Daiknowledge.javaProvider=jdt \
  -Daiknowledge.jdt.mode=search \
  -Daiknowledge.jdt.search.execution.mode=forked \
  -Daiknowledge.jdt.search.fallback.to.ast=true \
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
