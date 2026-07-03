# AI Knowledge Maven Plugin

Deterministic build-integrated knowledge extraction for AI-assisted code understanding.

## Plugin Coordinates

```xml
<plugin>
  <groupId>org.aiknowledge</groupId>
  <artifactId>ai-knowledge-maven-plugin</artifactId>
  <version>VERSION</version>
</plugin>
```

Replace `VERSION` with the current release from
[GitHub Releases](https://github.com/carstenartur/ai-knowledge-extractor/releases).

## Available Goals

| Goal | Phase | Description |
|------|-------|-------------|
| `generate` | `generate-resources` | Scan the repository and generate AI knowledge index artifacts |
| `analyze` | `verify` | Compute AI complexity and trend metrics |
| `optimize` | `verify` | Detect knowledge smells and rank optimization suggestions |
| `benchmark` | `verify` | Compare model-profile extraction budgets against context limits |
| `check` | `verify` | Enforce AI quality-gate thresholds and fail on violations |
| `help` | — | Display goal and parameter help |

See the [Plugin Goals](plugin-info.html) page for full goal and parameter documentation.

## Quick Start

Publish the plugin to your local Maven repository (development builds):

```bash
gradle publishToMavenLocal
```

Then invoke a goal directly:

```bash
mvn org.aiknowledge:ai-knowledge-maven-plugin:VERSION:generate
```

Or bind goals in your `pom.xml`:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.aiknowledge</groupId>
      <artifactId>ai-knowledge-maven-plugin</artifactId>
      <version>VERSION</version>
      <executions>
        <execution>
          <id>ai-knowledge</id>
          <goals>
            <goal>generate</goal>
            <goal>analyze</goal>
            <goal>check</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

## Shared Parameters

All operational goals (`generate`, `analyze`, `optimize`, `benchmark`, `check`) share the same parameters.

The defaults below are written with angle-bracket placeholders to keep the generated site environment-independent; in the Maven plugin descriptor the corresponding defaults use Maven project expressions.

| Parameter | Type | Default | Required |
|-----------|------|---------|----------|
| `basedir` | `File` | `<project.basedir>` | yes (read-only) |
| `outputDirectory` | `File` | `<project.build.directory>/ai-knowledge` | no |
| `seedDirectory` | `File` | `<project.basedir>/ai-knowledge` | no |
| `modelProfileDirectory` | `File` | `<project.basedir>/ai-knowledge` | no |
| `failOnWarnings` | `boolean` | `false` | no |
| `maxCognitiveDebt` | `double` | `100.0` | no |
| `maxCognitiveDebtIncrease` | `double` | `Double.MAX_VALUE` | no |
| `maxConceptRadiusIncrease` | `double` | `Double.MAX_VALUE` | no |
| `maxContextTokenIncrease` | `double` | `Double.MAX_VALUE` | no |
| `empiricalBenchmarkEnabled` | `boolean` | `false` | no |
| `empiricalBenchmarkFixtureFile` | `File` | `<project.basedir>/ai-knowledge/benchmark-fixtures.yaml` | no |
| `javaProvider` | `String` | `basic` | no |
| `jdtMode` | `String` | `ast` | no |
| `jdtSearchExecutionMode` | `String` | `forked` | no |
| `jdtSearchFallbackToAst` | `boolean` | `true` | no |
| `jdtWorkspaceMode` | `String` | `create` | no |
| `jdtWorkspaceDirectory` | `File` | `<project.build.directory>/ai-knowledge/jdt-workspace` | no |
| `keepJdtWorkspace` | `boolean` | `false` | no |

For JDT workspace search use:

```xml
<configuration>
  <javaProvider>jdt</javaProvider>
  <jdtMode>search</jdtMode>
  <jdtSearchExecutionMode>forked</jdtSearchExecutionMode>
  <jdtSearchFallbackToAst>true</jdtSearchFallbackToAst>
  <jdtWorkspaceMode>create</jdtWorkspaceMode>
</configuration>
```

## Further Reading

- [Maven Plugin Usage Guide](https://github.com/carstenartur/ai-knowledge-extractor/blob/main/docs/maven-plugin.md)
- [Publishing and Consumption](https://github.com/carstenartur/ai-knowledge-extractor/blob/main/docs/publishing.md)
- [README](https://github.com/carstenartur/ai-knowledge-extractor)
