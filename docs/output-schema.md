# Output schema

The extractor writes deterministic JSON and HTML artifacts below the configured output directory, usually `build/ai-knowledge/`.

## Stable knowledge artifacts

| File | Primary key | Purpose |
|---|---|---|
| `index.json` | object | Repository metadata, generation mode and counts. |
| `modules.json` | `modules` | Build modules discovered from Gradle and Maven build files. |
| `classes.json` | `classes` | Production Java classes with package, source file and API hints. |
| `tests.json` | `tests` | Test classes with source file and test methods. |
| `docs.json` | `docs` | Markdown documents with title, heading outline and links. |
| `dependencies.json` | `dependencies` | Static dependency notations found in build files. |
| `capabilities.json` | `capabilities` | Capability status inferred from code, tests, docs and optional seeds. |
| `claims.json` | `claims` | Machine-readable claims linked to capability evidence. |

All list artifacts are wrapped in an object with the primary key shown above. Producers must keep file names and primary keys stable. New fields may be added, but existing fields should not be removed without increasing `schemaVersion`.

## index.json

Current fields:

- `schemaVersion`: integer schema version.
- `repository`: repository root directory name.
- `generationMode`: currently `deterministic-static`.
- `counts`: object with counts for modules, classes, tests, docs, dependencies, capabilities and claims.

## modules.json

Current module fields:

- `name`
- `path`
- `buildFile`
- `buildSystem`
- `sourceSets`
- `mainPackages`
- `projectDependencies`
- `externalDependencies`

## classes.json

Current production Java type fields:

- `class`: fully qualified class/interface/record/enum name when the package can be detected.
- `sourceFile`
- `package`
- `publicApiMethods`
- `kind`
- `imports`
- `superclass`
- `interfaces`
- `referencedProjectClasses`

## tests.json

Current test type fields:

- `testClass`
- `sourceFile`
- `package`
- `testMethods`
- `kind`
- `imports`
- `testedClass`
- `tags`

## docs.json

Current Markdown document fields:

- `path`
- `title`
- `headings`
- `links`

## capabilities.json and claims.json

Capabilities and claims are evidence-based. The generator combines static evidence with optional seed files:

- `ai-knowledge/capabilities.seed.yaml`
- `ai-knowledge/claims.seed.yaml`

Seed files are optional. Seed entries are merged by `id`. List values are merged additively so generated evidence is not lost when a seed adds curated context.

## Analysis reports

| File | Purpose |
|---|---|
| `complexity.json` | Estimated context tokens, concept radius, dependency radius, knowledge density, profile-specific model budget metrics and AI cognitive debt. |
| `complexity.html` | Human-readable complexity report. |
| `metrics-snapshot.json` | Compact current metrics snapshot intended for future trend baselines. |
| `trend.json` | Machine-readable comparison of current metrics with `ai-knowledge/complexity-baseline.json`. |
| `trend.html` | Human-readable trend report. |
| `optimization.json` | Knowledge smells and ranked improvement suggestions. |
| `optimization.html` | Human-readable optimization report. |
| `benchmark.json` | Deterministic extraction-profile comparison for model context budgets. |
| `benchmark.html` | Human-readable benchmark report. |
| `check.json` | Quality-gate result for CI. |

## complexity.json

Current top-level fields:

- `schemaVersion`
- `estimatedContextTokens`
- `conceptRadius`
- `dependencyRadius`
- `knowledgeDensity`
- `contextLocality`
- `compressionRatio`
- `aiCognitiveComplexity`
- `aiCognitiveDebt`
- `warningCount`
- `warnings`
- `modelProfiles`
- `thresholds`

Each `modelProfiles` entry contains:

- `id`
- `practicalContextBudget`
- `hardContextLimit`
- `targetCompressionRatio`
- `compressionPreference`
- `estimatedRawTokens`
- `estimatedCompressedTokens`
- `fitsPracticalBudget`
- `fitsHardLimit`
- `compressedFitsPracticalBudget`
- `compressedFitsHardLimit`
- `warnings`
- `warningCount`

## trend.json

Current top-level fields:

- `schemaVersion`
- `baselinePresent`
- `baselinePath`
- `current`
- `baseline`
- `deltas`
- `thresholds`
- `warnings`
- `violations`
- `violationCount`
- `passed`

Trend deltas include `baseline`, `current`, `absolute` and `percent` for each compared metric.

## check.json

The quality gate output includes:

- `passed`
- `aiCognitiveDebt`
- `maxCognitiveDebt`
- `warningCount`
- `failOnWarnings`
- `trendViolationCount`
- `trendPassed`
- `trendThresholds`

## benchmark.json

Top-level fields:

- `schemaVersion`
- `method`: extraction method used; currently `deterministic-preflight`.
- `results`: array of per-profile budget entries (see below).
- `recommendedProfile`: the profile name recommended for the current codebase size; resolves to `review` for most codebases.

Each `results` entry contains profile-specific budget flags:

- `profile`
- `estimatedTokens`
- `rawTokens`
- `compressionRatio`
- `rawFitsPracticalBudget`
- `rawFitsHardLimit`
- `compressedFitsPracticalBudget`
- `compressedFitsHardLimit`
- `budgetRisk`
- `missingContextRisk`
- `risk`

See `docs/model-profiles.md` for profile configuration and `docs/trend-gates.md` for trend-gate configuration.
