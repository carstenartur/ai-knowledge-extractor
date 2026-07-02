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

## Knowledge quality gates

In addition to trend thresholds, `checkAiKnowledgeIndex` supports knowledge quality gates that verify the generated knowledge is actually useful — not just that the pipeline ran. Results are recorded in `check.json` under the `knowledgeQualityGates` key.

### Available gates

| Gate | Description | Default |
| --- | --- | --- |
| `requireCapabilityEvidence` | Fails if any capability has no matched module, type, doc or evidence (status `unknown`). Names the specific capability ID in the violation message. | disabled |
| `requireClaimVerification` | Fails if any claim has `status=unverified`. Claims without structural rule fields are unverified by default. | disabled |
| `minContextPackCount` | Fails if the number of generated context packs is below this value. | `0` (disabled) |
| `maxContextPackTokens` | Fails if any context pack's token estimate exceeds this value. Names the pack ID and actual token count in the violation message. | `Integer.MAX_VALUE` (disabled) |

### Gradle configuration

```groovy
aiKnowledge {
    requireCapabilityEvidence = true
    requireClaimVerification = true
    minContextPackCount = 3
    maxContextPackTokens = 8000
}
```

### Maven configuration

```xml
<configuration>
    <requireCapabilityEvidence>true</requireCapabilityEvidence>
    <requireClaimVerification>true</requireClaimVerification>
    <minContextPackCount>3</minContextPackCount>
    <maxContextPackTokens>8000</maxContextPackTokens>
</configuration>
```

### Recommended CI usage

For most projects, start with the following setup and tighten gates incrementally:

```groovy
tasks.named('check') {
    dependsOn('checkAiKnowledgeIndex')
}

aiKnowledge {
    // Fail on unlinked capabilities so decorative seeds do not accumulate.
    requireCapabilityEvidence = true
    // Fail on unverified claims once verifiable rules are added.
    requireClaimVerification = true
    // Ensure a minimum number of context packs are generated.
    minContextPackCount = 3
}
```

Violation messages in `check.json` point to specific capability IDs, claim IDs and context pack IDs, making it straightforward to identify and fix failures in CI.
