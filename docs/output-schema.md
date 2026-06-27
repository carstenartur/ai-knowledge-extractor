# Output schema

The extractor writes deterministic JSON and HTML artifacts below the configured output directory, usually `build/ai-knowledge/`.

## Knowledge index

| File | Purpose |
|---|---|
| `index.json` | Repository metadata, generation mode and counts. |
| `modules.json` | Build modules discovered from Gradle and Maven build files. |
| `classes.json` | Production Java classes with package, source file, imports and public/protected API hints. |
| `tests.json` | Test classes and discovered test/API method hints. |
| `docs.json` | Markdown files with title and heading outline. |
| `dependencies.json` | Static dependency notations found in build files. |
| `capabilities.json` | Capability status inferred from code, tests and docs. |
| `claims.json` | Machine-readable claims linked to implementation, tests and documentation evidence. |

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

The schema is intentionally conservative for the first version. New fields may be added, but existing top-level file names and primary list keys should remain stable.
