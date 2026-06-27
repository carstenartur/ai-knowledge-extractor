# Output schema

The extractor writes deterministic JSON and HTML artifacts below the configured output directory, usually `build/ai-knowledge/`.

## Stable knowledge artifacts

| File | Primary key | Purpose |
|---|---|---|
| `index.json` | object | Repository metadata, generation mode, artifact directory and counts. |
| `modules.json` | `modules` | Build modules discovered from Gradle and Maven build files. |
| `classes.json` | `classes` | Production Java classes with package, source file, kind, imports and API hints. |
| `tests.json` | `tests` | Test classes with source file, test methods and inferred tested class where possible. |
| `docs.json` | `docs` | Markdown documents with title, heading outline and links. |
| `dependencies.json` | `dependencies` | Static dependency notations found in build files. |
| `capabilities.json` | `capabilities` | Capability status inferred from code, tests, docs and optional seeds. |
| `claims.json` | `claims` | Machine-readable claims linked to implementation, test and documentation evidence. |

All list artifacts are wrapped in an object with the primary key shown above. Producers must keep file names and primary keys stable. New fields may be added, but existing fields should not be removed without increasing `schemaVersion`.

## index.json

Required fields:

- `schemaVersion`: integer schema version.
- `repository`: repository root directory name.
- `generationMode`: currently `deterministic-static`.
- `artifactDirectory`: conventional output directory.
- `counts`: object with counts for modules, classes, tests, docs, dependencies, capabilities and claims.

## modules.json

Each module should include:

- `name`
- `path`
- `buildFile`
- `buildSystem`
- `sourceSets`
- `mainPackages`
- `projectDependencies`
- `externalDependencies`

## classes.json

Each production Java type should include:

- `class`: fully qualified class/interface/record/enum name.
- `sourceFile`
- `package`
- `kind`
- `imports`
- `publicApiMethods`
- `superclass`
- `interfaces`
- `referencedProjectClasses`

## tests.json

Each test type should include:

- `testClass`
- `sourceFile`
- `package`
- `kind`
- `imports`
- `testMethods`
- `testedClass`
- `tags`

## docs.json

Each Markdown document should include:

- `path`
- `title`
- `headings`
- `links`

## capabilities.json and claims.json

Capabilities and claims are evidence-based. The generator may combine static evidence with optional seed files:

- `ai-knowledge/capabilities.seed.yaml`
- `ai-knowledge/claims.seed.yaml`

Seed files are optional. Seed entries are merged by `id`. List values are merged additively so generated evidence is not lost when a seed adds curated context.

## Analysis reports

| File | Purpose |
|---|---|
| `complexity.json` | Estimated context tokens, concept radius, dependency radius, knowledge density and AI cognitive debt. |
| `complexity.html` | Human-readable complexity report. |
| `optimization.json` | Knowledge smells and ranked improvement suggestions. |
| `optimization.html` | Human-readable optimization report. |
| `benchmark.json` | Deterministic extraction-profile comparison for model context budgets. |
| `benchmark.html` | Human-readable benchmark report. |
| `check.json` | Quality-gate result for CI. |
