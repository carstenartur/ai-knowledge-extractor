# ai-knowledge:check

**Full name:** `org.aiknowledge:ai-knowledge-maven-plugin:check`

**Phase:** `verify`

**Description:** Runs AI knowledge quality gates and fails the build on configured threshold violations.

## Output Files

Generated under `outputDirectory` (default: `<project.build.directory>/ai-knowledge`):

- `check.json`

## Parameters

| Name | Type | Required | Editable | Default | Description |
|------|------|----------|----------|---------|-------------|
| `basedir` | `java.io.File` | yes | no | `<project.basedir>` | Root directory of the Maven project (read-only). |
| `outputDirectory` | `java.io.File` | no | yes | `<project.build.directory>/ai-knowledge` | Output directory for generated AI knowledge artifacts. |
| `seedDirectory` | `java.io.File` | no | yes | `<project.basedir>/ai-knowledge` | Seed directory for initial configuration YAML files. |
| `modelProfileDirectory` | `java.io.File` | no | yes | `<project.basedir>/ai-knowledge` | Directory containing model profile configuration. |
| `failOnWarnings` | `boolean` | no | yes | `false` | Fail the build on warnings in addition to errors. |
| `maxCognitiveDebt` | `double` | no | yes | `100.0` | Maximum allowed cognitive debt score; build fails if exceeded. |
| `maxCognitiveDebtIncrease` | `double` | no | yes | `Double.MAX_VALUE` | Maximum allowed increase in cognitive debt between builds. |
| `maxConceptRadiusIncrease` | `double` | no | yes | `Double.MAX_VALUE` | Maximum allowed increase in concept radius between builds. |
| `maxContextTokenIncrease` | `double` | no | yes | `Double.MAX_VALUE` | Maximum allowed increase in context tokens between builds. |
| `empiricalBenchmarkEnabled` | `boolean` | no | yes | `false` | Enable the empirical benchmark fixture layer. |
| `empiricalBenchmarkFixtureFile` | `java.io.File` | no | yes | `<project.basedir>/ai-knowledge/benchmark-fixtures.yaml` | Path to the empirical benchmark fixture YAML file. |

## Example

**Command-line:**

```bash
mvn org.aiknowledge:ai-knowledge-maven-plugin:VERSION:check
```

**`pom.xml` binding:**

```xml
<execution>
  <id>ai-knowledge-check</id>
  <goals>
    <goal>check</goal>
  </goals>
</execution>
```

---

Back to [Plugin Goals](plugin-info.html)
