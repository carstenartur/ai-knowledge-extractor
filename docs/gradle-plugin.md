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

## Automatic JavaScript/TypeScript and boundary analysis

Supported JavaScript and TypeScript files are analyzed automatically. `package.json` files participate in module and dependency discovery, and no Node.js installation is required by the built-in provider.

The provider composes with the selected Java mode. For example, `javaProvider = "jdt"` strengthens Java structural facts while JavaScript/TypeScript client extraction and JDT-backed Spring/JAX-RS endpoint extraction continue to run.

`generateAiKnowledgeIndex` writes the language-neutral raw facts (`source-units.json`, `symbols.json`, `relations.json`, `boundaries.json`, `warnings.json`). Analysis and check lifecycles additionally write `boundary-analysis.json` and `boundary-analysis.html`.

See [`language-providers-and-boundary-analysis.md`](language-providers-and-boundary-analysis.md) for provider extension, scoring and limitations.

## Tasks

| Task | Group | Purpose |
| --- | --- | --- |
| `generateAiKnowledgeIndex` | `documentation` | Generate deterministic knowledge index, review context and context packs in `build/ai-knowledge`. |
| `analyzeAiComplexity` | `verification` | Generate complexity metrics and trend reports. |
| `optimizeAiKnowledge` | `verification` | Generate optimization recommendations. |
| `benchmarkAiKnowledge` | `verification` | Generate model-profile benchmark reports. |
| `checkAiKnowledgeIndex` | `verification` | Run configured quality gates, write `check.json` and verify the quality-gate artifact set. |
| `verifyAiKnowledgeArtifacts` | `verification` | Verify an existing complete artifact set without regenerating it. |
| `aiKnowledgeCheck` | `verification` | Canonical one-command lifecycle: scan once, generate all reports, run the quality gate and verify the complete artifact set. |
| `publishAiKnowledgeIndex` | `documentation` | Copy generated artifacts to `docs/ai-knowledge` (depends on `generateAiKnowledgeIndex`). |

For CI and release verification, prefer:

```bash
./gradlew aiKnowledgeCheck
```

`aiKnowledgeCheck` is a single orchestrated core lifecycle rather than a dependency chain over the five focused tasks. This avoids rescanning a repository and rewriting shared outputs several times. The focused tasks remain available when only one report family is needed.

To verify already generated files without modifying them, run:

```bash
./gradlew verifyAiKnowledgeArtifacts
```

The lifecycle rejects missing or empty required files, malformed JSON, duplicate object fields, trailing JSON tokens, index/envelope count drift, context-pack index drift, missing context packs, inconsistent context-footprint v3 data, disagreement between `check.json` and `complexity.json`, and divergence between the embedded and standalone boundary analysis.

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
