# Model profiles

Model profiles describe context-budget assumptions without binding the extractor to a specific vendor. They are used by `complexity.json` and `benchmark.json` to estimate whether the generated repository context fits a practical or hard context budget.

## Default profiles

The extractor includes these default profiles:

| ID | Practical budget | Hard limit | Target compression ratio | Intended use |
| --- | ---: | ---: | ---: | --- |
| `general-128k` | 128,000 | 128,000 | 0.55 | Balanced review context. |
| `large-200k` | 128,000 | 200,000 | 0.65 | Larger architecture context. |
| `very-large-1m` | 256,000 | 1,000,000 | 0.80 | Broad repository exploration. |
| `local-32k` | 16,000 | 32,000 | 0.35 | Local or small-context model. |

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
| `practicalContextBudget` | no | Preferred usable context budget in estimated tokens. |
| `hardContextLimit` | no | Absolute context limit in estimated tokens. |
| `targetCompressionRatio` | no | Intended ratio after compacting the raw extracted context. |
| `compressionPreference` | no | Human-readable guidance for compaction. |

Aliases are accepted for convenience:

- `practicalBudget` → `practicalContextBudget`
- `compressionRatio` → `targetCompressionRatio`

## Generated metrics

For each profile, `complexity.json` contains:

- `estimatedRawTokens`
- `estimatedCompressedTokens`
- `fitsPracticalBudget`
- `fitsHardLimit`
- `compressedFitsPracticalBudget`
- `compressedFitsHardLimit`
- profile-specific `warnings`

Top-level warnings include the first warning per profile so CI can fail on budget pressure when `failOnWarnings` is enabled.
