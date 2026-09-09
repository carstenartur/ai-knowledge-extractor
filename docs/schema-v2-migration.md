# Upgrading retained knowledge from schema v1 to v2

Version 0.2.0 introduced schema v2. The 0.1.x maintenance line continues to emit schema v1;
select the release line deliberately. See [version support](version-support.md).

## What remains available

`index.json`, `modules.json`, `classes.json`, `tests.json`, `docs.json`, `dependencies.json`,
`capabilities.json`, `claims.json`, `evidence.json`, context packs and the existing complexity,
optimization, benchmark, check and trend reports remain available. Java-oriented facts are retained.
Existing field names are not a promise of unchanged numeric values: mixed-language extraction
changes the working set, dependency surface, complexity and context-footprint inputs.

Representative abbreviated index fragments (other fields omitted):

Schema v1:

```json
{"schemaVersion": 1, "counts": {"modules": 1, "classes": 2}}
```

Schema v2:

```json
{"schemaVersion": 2, "counts": {"modules": 2, "classes": 2, "sourceUnits": 3, "symbols": 4, "relations": 5, "boundaries": 2, "warnings": 0}}
```

The new required artifact families are:

| File | Contents |
|---|---|
| `source-units.json` | `{"sourceUnits": [...]}` |
| `symbols.json` | `{"symbols": [...]}` |
| `relations.json` | `{"relations": [...]}` |
| `boundaries.json` | `{"boundaries": [...]}` |
| `warnings.json` | `{"warnings": [...]}` |
| `boundary-analysis.json` | Boundary score, dimensions, links, confidence and raw evidence |
| `boundary-analysis.html` | Human-readable boundary report |

Every complete v2 output contains these files, including Java-only repositories. Empty facts use
empty arrays. `complexity.json.boundaryAnalysis` and `check.json.boundaryAnalysis` must equal the
entire standalone `boundary-analysis.json` object. A representative abbreviated analysis is:

```json
{"score": 12, "clientCallCount": 2, "versionControlHistoryUsed": false, "changeCouplingIncluded": false}
```

The enclosing index schema, each report's schema, the provider fact contract and the scoring-model
version are separate concepts. See the [fact contract](schema-v2-contract.md) and
[provider SPI](provider-spi.md). Unknown additive fields and relation kinds must remain readable.

## Consumer upgrade checklist

1. Archive retained v1 output and its baseline with the producing plugin version. **Regenerate the
   entire output directory** from the same checkout using v2; do not add empty files to an old output
   or mix outputs produced by different versions. The v2 verifier rejects incomplete v1 directories.
2. Update both the selected plugin version and repository/credentials configuration. For Gradle,
   apply `org.aiknowledge.extractor` through the [published marker](gradle-plugin.md). For Maven,
   update `org.aiknowledge:ai-knowledge-maven-plugin` in [plugin configuration](maven-plugin.md).
3. Run Gradle `aiKnowledgeCheck`; with Maven run `generate`, `analyze`, `optimize`, `benchmark`,
   and `check`. Keep existing thresholds disabled or permissive while inspecting the first v2 run.
4. Extend artifact allowlists, uploads and retained-output storage to include every file above.
   Inspect frontend calls, endpoint links, dynamic unresolved URLs and runtime versus development
   package evidence; JavaScript/TypeScript structural extraction remains experimental.
5. Verify retained outputs independently. Gradle's `verifyAiKnowledgeArtifacts` does not regenerate
   them. Core/Maven integrations may call `AiKnowledgeArtifactVerifier.verifyCompleteLifecycle(path)`
   on a retained directory. The shared [consumer check](../.github/scripts/verify-consumer-fixtures.sh)
   exercises both paths and rejects changed or missing artifacts.
6. Recalculate baselines and thresholds as described below before enforcing them in CI.

## Baselines and thresholds

Compare v1 and v2 on the **same source revision**, inspect the changed raw evidence, and distinguish
new coverage from an architecture regression. Do not compare scores across the schema transition
as if the source code alone had changed. Replace `ai-knowledge/complexity-baseline.json` with a
reviewed v2 `metrics-snapshot.json`, retaining the previous baseline in the archived v1 output.
Recalibrate context, method-complexity and trend limits from representative repositories. Do not
copy a v1 numeric cutoff blindly. Boundary scores remain experimental structural proxies; begin
with report-only operation and choose any opt-in limits from observed evidence and review capacity.

No Git-history traversal, co-change analysis or commit-derived change coupling is added by this
migration. Source revision pinning for reproducibility is not a scoring input.
