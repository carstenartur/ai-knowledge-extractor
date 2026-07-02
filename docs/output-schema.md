# Output schema

The extractor writes deterministic JSON and HTML artifacts below the configured output directory, usually `build/ai-knowledge/`.

## AI context artifacts

These are the primary outputs intended for AI-assisted review and architecture work.

| File | Purpose |
|---|---|
| `review-context.md` | Human and AI readable summary: repository overview, module graph, capability status, architecture claims, risk areas and suggested context packs. |
| `context-packs/<id>.json` | One capability-centred context pack per capability, containing matched modules, types, tests, docs, evidence, linked claims and suggested files for AI review. |
| `context-packs/index.json` | Index of all context packs with token estimates and intended use descriptions. |

### How to consume these artifacts

**CI workflow**: Generate and commit `review-context.md` to track capability health over time. Run `generateAiKnowledgeIndex` (Gradle) or `ai-knowledge:generate` (Maven).

**AI assistant**: Load `review-context.md` for a repository overview, then load a specific `context-packs/<capability-id>.json` for targeted review, bug-fixing or feature work. Use `context-packs/index.json` to discover available packs and pick the one with the right token budget.

**Architecture review**: Cross-reference the _Architecture Claims_ table in `review-context.md` with the _Risk Areas_ section to identify gaps that need attention.

### `context-packs/<id>.json` fields

Each capability context pack contains:

- `id`: capability identifier
- `label`: human-readable label
- `status`: capability status (see capability status values below)
- `warnings`: list of warnings (e.g. `no-evidence-found`) when present
- `modules`: matched build modules
- `types`: matched production class names
- `tests`: matched test class names
- `docs`: matched document paths
- `evidence`: matched evidence file paths
- `claims`: list of claim summaries relevant to this capability (each with `id`, optional `category`, `status`, optional `violations`)
- `suggestedFiles`: source file paths derived from matched types and docs — load these when performing AI review

### `context-packs/index.json` fields

- `contextPacks`: array of index entries; each entry has:
  - `id`, `label`, `status`
  - `tokenEstimate`: rough token count estimate for the pack (characters / 4)
  - `file`: relative path to the pack JSON file
  - `intendedUse`: one-line description of the recommended use for this pack

## Stable knowledge artifacts (raw JSON)

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
| `evidence.json` | `evidence` | Project evidence artifacts such as discovery evidence files, benchmark source fixtures and GitHub workflow metadata. |

All list artifacts are wrapped in an object with the primary key shown above. Producers must keep file names and primary keys stable. New fields may be added, but existing fields should not be removed without increasing `schemaVersion`.

## index.json

Current fields:

- `schemaVersion`: integer schema version.
- `repository`: repository root directory name.
- `generationMode`: currently `deterministic-static`.
- `counts`: object with counts for modules, classes, tests, docs, dependencies, capabilities, claims and evidence artifacts.

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
- `lineCount`
- `methodCount`
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
- `lineCount`
- `methodCount`
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
- `ai-knowledge/capabilities.seed.yml`
- `ai-knowledge/capabilities.yaml`
- `ai-knowledge/capabilities.yml`
- `ai-knowledge/capabilities.json`
- `ai-knowledge/claims.seed.yaml`
- `ai-knowledge/claims.seed.yml`
- `ai-knowledge/claims.yaml`
- `ai-knowledge/claims.yml`
- `ai-knowledge/claims.json`

Seed files are optional. Seed entries are merged by `id`. List values are merged additively so generated evidence is not lost when a seed adds curated context.

### Claim verifier

Claims that include at least one rule field are automatically verified against the repository snapshot. The verifier sets `status`, `violations`, `verificationEvidence` and `matchedVerifiedBy` on each claim.

#### Claim rule fields (seed-only)

| Field | Type | Description |
|---|---|---|
| `scopeModules` | string list | Module names whose classes and dependencies are checked. When absent, all modules and classes are in scope. |
| `forbiddenReferences` | string list | Package or class prefixes that must not appear in any import statement of scoped classes. |
| `forbiddenImports` | string list | Alias for `forbiddenReferences`. |
| `forbiddenDependencies` | string list | Substrings that must not appear in external dependency notations of scoped modules. |
| `allowedDependencies` | string list | If present, every external dependency of scoped modules must match at least one entry (substring). An empty list forbids all external dependencies. |
| `allowedTargetModules` | string list | Project dependency targets not in this list are violations. |
| `verifiedBy` | string list | Fully-qualified test class names that must exist in `tests.json` to prove the claim. Matched tests are written to `matchedVerifiedBy`. |
| `requiredTests` | string list | Fully-qualified test class names that must exist (without being recorded as verifiers). |
| `requiredEvidenceTypes` | string list | Evidence `type` values from `evidence.json` that must be present. |
| `requiredDocs` | string list | Glob patterns that must match at least one document path in `docs.json`. |
| `mustBeAcyclic` | boolean | When `true`, import cycles among scoped classes are violations. |
| `severity` | string | `error` (default gate), `warning` or `info`. Claims with `severity=error` that fail will cause `checkAiKnowledgeIndex` to fail the build. |

#### Claim output fields added by the verifier

| Field | Type | Description |
|---|---|---|
| `status` | string | `passed`, `failed`, or `unverified` (no rule fields). Claims set by keyword inference keep their pre-existing status. |
| `violations` | string list | Present when `status=failed`. Each entry describes one violation with its kind, forbidden value, class or module name and source file. |
| `verificationEvidence` | string list | References to the modules, tests and evidence used during verification (e.g. `module:core`, `test:de.example.ArchTest`). |
| `matchedVerifiedBy` | string list | Subset of `verifiedBy` entries that were found in `tests.json`. |

#### Example claim seed (Regelsuche-style)

```yaml
- id: no-infrastructure-in-core
  category: architecture
  description: Core must not reference infrastructure frameworks
  scopeModules: [regelsuche-core]
  forbiddenReferences: [org.hibernate, jakarta.persistence, org.springframework]
  verifiedBy: [de.regelsuche.arch.ArchitectureBoundariesTest]
  severity: error

- id: hibernate-isolated
  category: architecture
  description: Hibernate must only appear in infrastructure modules
  scopeModules: [regelsuche-core, regelsuche-api]
  forbiddenDependencies: [org.hibernate]
  severity: error
```

### Capability selector fields (seed-only)

Seeds may declare optional selector fields to instruct the `CapabilityLinker` which repository facts prove the capability:

| Field | Type | Description |
|---|---|---|
| `modules` | string list | Module names from `modules.json` to associate with this capability. |
| `packages` | string list | Java package names; all types and tests in these packages are matched. |
| `typePatterns` | string list | Glob patterns matched against simple class names (`*Search*`, `*Rewrite*`). |
| `testPatterns` | string list | Glob patterns matched against simple test class names (`*SearchTest`). |
| `docPatterns` | string list | Glob patterns matched against document paths; `**` matches any path segments. |
| `evidenceTypes` | string list | Evidence `type` values from `evidence.json` (e.g. `discovery-evidence`, `benchmark-source`). |

### Linked capability output fields

When seed capabilities with selectors are present the linker adds these computed fields to each capability entry:

| Field | Type | Description |
|---|---|---|
| `matchedModules` | string list | Module names matched from the `modules` selector. |
| `matchedPackages` | string list | Distinct Java packages found in `classes.json` that satisfy the `packages` selector. |
| `matchedTypes` | string list | Fully qualified class names matched via `typePatterns` or `packages`. |
| `matchedTests` | string list | Fully qualified test class names matched via `testPatterns` or `packages`. |
| `matchedDocs` | string list | Document paths matched via `docPatterns`. |
| `matchedEvidence` | string list | Evidence item paths matched via `evidenceTypes`. |
| `status` | string | Computed status (see below). |
| `warnings` | string list | Present when no evidence was found; contains `no-evidence-found`. |

### Capability status values

| Status | Meaning |
|---|---|
| `unknown` | No repository facts linked to this capability. |
| `documented` | Only documentation was matched; no types, tests, or evidence. |
| `partial` | Tests were matched but no implementing types, or vice versa. |
| `implemented` | Implementing types were matched but no corresponding tests or evidence. |
| `implemented-and-tested` | Both types and tests matched; no direct evidence artifacts. |
| `evidence-backed` | Evidence artifacts (e.g. discovery evidence, benchmarks) were matched — strongest proof. |

When no seed capabilities are provided the extractor falls back to keyword-based inference using a fixed set of well-known capability IDs. Provide explicit seed capabilities with selectors to replace the fallback with precise evidence-linked facts.

## evidence.json

Current evidence item types:

- `discovery-evidence`: generated discovery evidence under `docs/generated/discovery/**/evidence.json`, including scenario id, expressions, oracle status, success flags, graph sizes and macro/bridge counts when present.
- `benchmark-source`: benchmark source files under `src/jmh/java`.
- `github-workflow`: GitHub Actions workflow files, including workflow name, dispatch support and a lightweight job count.

The evidence artifact is intended to expose project-specific generated proof, benchmark and CI context that is useful for AI-assisted review but is not captured by Java class scanning alone.

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
- `claimFailureCount`: number of claims with `status=failed` and `severity=error`. Non-zero causes the quality gate to fail.
- `knowledgeQualityGates`: summary of all enabled knowledge quality gates (see below).

### knowledgeQualityGates

The `knowledgeQualityGates` object contains:

- `passed`: `true` only when all enabled gates pass.
- `gates`: array of per-gate results. Each entry has:
  - `gate`: gate identifier (e.g. `requireCapabilityEvidence`)
  - `passed`: whether this specific gate passed
  - `violations`: list of specific violation messages (only present when `passed=false`). Each message names the capability, claim or context pack that caused the failure.

## benchmark.json

Top-level fields:

- `schemaVersion`
- `method`: extraction method used; currently `deterministic-preflight`.
- `results`: array of per-profile budget entries (see below).
- `recommendedProfile`: the profile name recommended for the current codebase size; resolves to `review` for most codebases.
- `empirical`: optional fixture-based empirical layer. Default runs set `"enabled": false` and keep the benchmark fully offline.

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

When empirical benchmark mode is enabled, `empirical` contains:

- `enabled`
- `fixtureFile`
- `fixtureCount`
- `results` (per fixture: `id`, `profile`, `tokenUsage`, `latencyMs`, `reviewQuality`, `duplicateSuggestions`, `missedExistingFeatures`, `taskSuccess`)
- `summary` (`averageTokenUsage`, `averageLatencyMs`, `averageReviewQuality`, `totalDuplicateSuggestions`, `totalMissedExistingFeatures`, `taskSuccessRate`)
