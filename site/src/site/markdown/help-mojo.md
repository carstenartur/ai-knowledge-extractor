# ai-knowledge:help

**Full name:** `io.github.carstenartur:ai-knowledge-maven-plugin:help`

**Phase:** `(none)`

**Description:** Displays plugin goal and parameter help for the AI Knowledge Maven Plugin.

## Parameters

| Name | Type | Required | Editable | Default | Description |
|------|------|----------|----------|---------|-------------|
| `goal` | `java.lang.String` | no | yes | `(none)` | Name of the goal to display help for. If omitted, all goals are listed. |
| `detail` | `boolean` | no | yes | `false` | When true, include parameter details in the help output. |

## Example

**Command-line:**

```bash
mvn io.github.carstenartur:ai-knowledge-maven-plugin:VERSION:help
```

**`pom.xml` binding:**

```xml
<execution>
  <id>ai-knowledge-help</id>
  <goals>
    <goal>help</goal>
  </goals>
</execution>
```

---

Back to the [goal overview](index.html)