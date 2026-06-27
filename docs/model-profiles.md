# Model profiles

Model profiles describe context-budget assumptions without binding the extractor to a specific vendor. They are used by `complexity.json` and `benchmark.json` to estimate whether the generated repository context fits a practical or hard context budget.

## Default profiles

The extractor includes these default profiles:

| ID | Practical budget | Hard limit | Target compression ratio | Intended use |
| --- | ---: | ---: | ---: | --- |
| `raw` | 128,000 | 256,000 | 1.00 | Minimal compaction for maximal context retention. |
| `compact` | 32,000 | 64,000 | 0.35 | Aggressive compaction for constrained model budgets. |
| `review` | 128,000 | 256,000 | 0.60 | Balanced context for code review tasks (default recommendation). |
| `architecture` | 192,000 | 384,000 | 0.75 | Broader architecture-level context. |
| `deep-research` | 256,000 | 1,000,000 | 0.85 | Maximum breadth for deep repository exploration. |

## Custom profiles

A repository can provide additional or overriding profiles in:

```text
ai-knowledge/model-profiles.yaml
```

The file uses a deliberately small YAML subset: a list of flat key/value entries.

```yaml
- id: architecture-review
  practicalContextBudget: 64000
  hardContextLimit: 128000
  targetCompressionRatio: 0.45
  compressionPreference: prioritize API surfaces and architectural relations

- id: tiny-ci-profile
  practicalContextBudget: 8000
  hardContextLimit: 16000
  targetCompressionRatio: 0.30
  compressionPreference: strict CI budget
```

If a custom profile uses the same `id` as a default profile, the custom fields override the default fields. This allows projects to keep the default profile names while adapting budgets to their own review workflow.

Supported fields:

| Field | Required | Meaning |
| --- | --- | --- |
| `id` | yes | Stable profile identifier. |
| `practicalContextBudget` | no | Preferred usable context budget in estimated tokens. Values below 1 are normalized to 1. |
| `hardContextLimit` | no | Absolute context limit in estimated tokens. Values below 1 are normalized, and values below the practical budget are raised to the practical budget. |
| `targetCompressionRatio` | no | Intended ratio after compacting the raw extracted context. Values are clamped to the range 0..1. |
| `compressionPreference` | no | Human-readable guidance for compaction. |

Aliases are accepted for convenience:

- `practicalBudget` → `practicalContextBudget`
- `compressionRatio` → `targetCompressionRatio`

Invalid numeric values are normalized and reported as profile warnings in `complexity.json`. This keeps CI output deterministic and prevents negative budgets or out-of-range compression ratios from producing misleading token estimates.

## Generated metrics

For each profile, `complexity.json` contains:

- `estimatedRawTokens`
- `estimatedCompressedTokens`
- `fitsPracticalBudget`
- `fitsHardLimit`
- `compressedFitsPracticalBudget`
- `compressedFitsHardLimit`
- profile-specific `warnings`

`benchmark.json` uses the same configured profiles and emits explicit profile budget fields:

- `rawFitsPracticalBudget`
- `rawFitsHardLimit`
- `compressedFitsPracticalBudget`
- `compressedFitsHardLimit`
- `budgetRisk`
- `missingContextRisk`
- `risk` (combined indicator)

Top-level warnings include the first warning per profile so CI can fail on budget pressure when `failOnWarnings` is enabled.
