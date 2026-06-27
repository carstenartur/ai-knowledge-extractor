# Trend analysis and CI quality gates

Trend analysis compares the current deterministic complexity metrics with a committed baseline snapshot. It is fully offline and does not require external model calls.

## Generated files

Running `analyzeAiComplexity`, `benchmarkAiKnowledge`, `optimizeAiKnowledge`, `checkAiKnowledgeIndex`, or the equivalent Maven goals writes:

| File | Purpose |
| --- | --- |
| `metrics-snapshot.json` | Current compact snapshot of trend-relevant metrics. Commit this as a future baseline when desired. |
| `trend.json` | Machine-readable comparison between the current metrics and the baseline. |
| `trend.html` | Human-readable trend report. |

## Baseline file

The trend analyzer looks for this file:

```text
ai-knowledge/complexity-baseline.json
```

The simplest workflow is:

```bash
gradle analyzeAiComplexity
cp build/ai-knowledge/metrics-snapshot.json ai-knowledge/complexity-baseline.json
```

Commit `ai-knowledge/complexity-baseline.json` when the current metrics are accepted as the project baseline.

## Metrics compared

The trend report compares:

- `estimatedContextTokens`
- `conceptRadius`
- `dependencyRadius`
- `knowledgeDensity`
- `contextLocality`
- `compressionRatio`
- `aiCognitiveComplexity`
- `aiCognitiveDebt`

The quality gate currently supports hard thresholds for increases in:

- AI cognitive debt
- concept radius
- estimated context tokens

## Gradle configuration

```groovy
aiKnowledge {
    maxCognitiveDebt = 100.0d
    failOnWarnings = false

    // Disabled by default. Set finite values to fail checkAiKnowledgeIndex on growth.
    maxCognitiveDebtIncrease = 0.5d
    maxConceptRadiusIncrease = 2.0d
    maxContextTokenIncrease = 1000.0d
}
```

`checkAiKnowledgeIndex` fails when:

- current `aiCognitiveDebt` exceeds `maxCognitiveDebt`,
- a trend increase exceeds one of the configured trend thresholds,
- or `failOnWarnings = true` and warnings are present.

## Maven configuration

The Maven check goal supports the same thresholds through system properties:

```bash
mvn org.aiknowledge:ai-knowledge-maven-plugin:0.1.0-SNAPSHOT:check \
  -DaiKnowledge.maxCognitiveDebt=100.0 \
  -DaiKnowledge.maxCognitiveDebtIncrease=0.5 \
  -DaiKnowledge.maxConceptRadiusIncrease=2.0 \
  -DaiKnowledge.maxContextTokenIncrease=1000.0
```

The thresholds are disabled by default. Use finite values to enforce trend gates in CI.
