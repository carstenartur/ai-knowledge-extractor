# Maven plugin usage (`ai-knowledge-maven-plugin`)

Canonical Maven plugin coordinates:

```xml
<plugin>
  <groupId>org.aiknowledge</groupId>
  <artifactId>ai-knowledge-maven-plugin</artifactId>
  <version>${ai-knowledge.version}</version>
</plugin>
```

The plugin exposes goals: `generate`, `analyze`, `optimize`, `benchmark`, `check`, and `help`.

For the generated Maven plugin reference with full goal and parameter documentation, see the
[GitHub Pages documentation site](https://carstenartur.github.io/ai-knowledge-extractor/).

## Help goal

Show all goals:

```bash
mvn org.aiknowledge:ai-knowledge-maven-plugin:<version>:help
```

Show one goal with full parameter detail:

```bash
mvn org.aiknowledge:ai-knowledge-maven-plugin:<version>:help -Dgoal=check -Ddetail=true
```

## Shared parameters (all operational goals)

`generate`, `analyze`, `optimize`, `benchmark`, and `check` expose the same parameter set.

| Parameter | Type | Default | Required |
| --- | --- | --- | --- |
| `basedir` | `File` | `${project.basedir}` | yes (read-only) |
| `outputDirectory` | `File` | `${project.build.directory}/ai-knowledge` | no |
| `seedDirectory` | `File` | `${project.basedir}/ai-knowledge` | no |
| `modelProfileDirectory` | `File` | `${project.basedir}/ai-knowledge` | no |
| `failOnWarnings` | `boolean` | `false` | no |
| `maxCognitiveDebt` | `double` | `100.0` | no |
| `maxCognitiveDebtIncrease` | `double` | `1.7976931348623157E308` | no |
| `maxConceptRadiusIncrease` | `double` | `1.7976931348623157E308` | no |
| `maxContextTokenIncrease` | `double` | `1.7976931348623157E308` | no |
| `empiricalBenchmarkEnabled` | `boolean` | `false` | no |
| `empiricalBenchmarkFixtureFile` | `File` | `${project.basedir}/ai-knowledge/benchmark-fixtures.yaml` | no |
| `requireCapabilityEvidence` | `boolean` | `false` | no |
| `requireClaimVerification` | `boolean` | `false` | no |
| `minContextPackCount` | `int` | `0` | no |
| `maxContextPackTokens` | `int` | `2147483647` | no |
| `javaProvider` | `String` | `basic` | no |
| `jdtMode` | `String` | `ast` | no |
| `jdtSearchExecutionMode` | `String` | `forked` | no |
| `jdtSearchFallbackToAst` | `boolean` | `true` | no |
| `jdtWorkspaceMode` | `String` | `create` | no |
| `jdtWorkspaceDirectory` | `File` | `${project.build.directory}/ai-knowledge/jdt-workspace` | no |
| `keepJdtWorkspace` | `boolean` | `false` | no |

Threshold overrides can also be passed as JVM properties:

- `-DaiKnowledge.maxCognitiveDebt=...`
- `-DaiKnowledge.maxCognitiveDebtIncrease=...`
- `-DaiKnowledge.maxConceptRadiusIncrease=...`
- `-DaiKnowledge.maxContextTokenIncrease=...`
- `-Daiknowledge.javaProvider=jdt`
- `-Daiknowledge.jdt.mode=search`
- `-Daiknowledge.jdt.search.execution.mode=forked`
- `-Daiknowledge.jdt.search.fallback.to.ast=true|false`
- `-Daiknowledge.jdt.workspace.mode=create`
- `-Daiknowledge.jdt.workspace.directory=...`
- `-Daiknowledge.jdt.workspace.keep=true|false`

## Goal reference

### `generate`

- Purpose: scan the repository and generate deterministic AI knowledge index artifacts.
- Default lifecycle phase: `generate-resources`.
- Output files:
  - `index.json`, `modules.json`, `classes.json`, `tests.json`, `docs.json`, `dependencies.json`, `capabilities.json`, `claims.json`, `evidence.json`
  - `review-context.md`, `context-packs/index.json`, and one context pack per capability

CLI:

```bash
mvn org.aiknowledge:ai-knowledge-maven-plugin:<version>:generate
```

`pom.xml` execution:

```xml
<execution>
  <id>ai-knowledge-generate</id>
  <goals><goal>generate</goal></goals>
</execution>
```

### `analyze`

- Purpose: compute AI complexity and trend metrics.
- Default lifecycle phase: `verify`.
- Output files:
  - all `generate` outputs
  - `complexity.json`, `complexity.html`, `metrics-snapshot.json`, `trend.json`, `trend.html`

CLI:

```bash
mvn org.aiknowledge:ai-knowledge-maven-plugin:<version>:analyze
```

### `optimize`

- Purpose: detect knowledge smells and rank optimization suggestions.
- Default lifecycle phase: `verify`.
- Output files:
  - all `analyze` outputs
  - `optimization.json`, `optimization.html`

CLI:

```bash
mvn org.aiknowledge:ai-knowledge-maven-plugin:<version>:optimize
```

### `benchmark`

- Purpose: compare model-profile extraction budgets with optional empirical fixtures.
- Default lifecycle phase: `verify`.
- Output files:
  - all `analyze` outputs
  - `benchmark.json`, `benchmark.html`

CLI:

```bash
mvn org.aiknowledge:ai-knowledge-maven-plugin:<version>:benchmark
```

### `check`

`check` is the canonical complete Maven lifecycle. It:

1. generates optimization reports;
2. generates benchmark reports;
3. executes the configured quality gate and writes `check.json`;
4. verifies the complete artifact contract.

The verifier rejects missing or empty required files, malformed JSON, duplicate object fields, trailing JSON tokens, index/envelope count drift, context-pack index drift, missing context packs, inconsistent context-footprint v3 data and disagreement between `check.json` and `complexity.json`.

Project-specific thresholds and evidence requirements remain controlled by the shared configuration parameters; structural verification does not invent consumer policy.

- Default lifecycle phase: `verify`.
- Output files:
  - all `generate` and `analyze` outputs
  - `optimization.json`, `optimization.html`
  - `benchmark.json`, `benchmark.html`
  - `check.json`

CLI:

```bash
mvn org.aiknowledge:ai-knowledge-maven-plugin:<version>:check
```

`pom.xml` execution:

```xml
<execution>
  <id>ai-knowledge-check</id>
  <phase>verify</phase>
  <goals><goal>check</goal></goals>
</execution>
```
