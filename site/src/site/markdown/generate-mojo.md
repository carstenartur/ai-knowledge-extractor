# ai-knowledge:generate

**Full name:** `org.aiknowledge:ai-knowledge-maven-plugin:generate`

**Phase:** `generate-resources`

**Description:** Scans the repository and generates deterministic AI knowledge index artifacts.

## Output Files

Generated under `outputDirectory` (default: `<project.build.directory>/ai-knowledge`):

- `index.json`
- `modules.json`
- `classes.json`
- `tests.json`
- `docs.json`
- `dependencies.json`
- `capabilities.json`
- `claims.json`

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
mvn org.aiknowledge:ai-knowledge-maven-plugin:VERSION:generate
```

**`pom.xml` binding:**

```xml
<execution>
  <id>ai-knowledge-generate</id>
  <goals>
    <goal>generate</goal>
  </goals>
</execution>
```

---

Back to [Plugin Goals](plugin-info.html)
