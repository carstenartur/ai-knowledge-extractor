# Integrator quickstart: mixed Java and JavaScript/TypeScript repositories

This guide uses [`examples/mixed-java-web`](../examples/mixed-java-web) to show the complete
frontend/backend path. The extractor itself runs on Java 17 or newer. **Node.js is not required**
for the built-in structural JavaScript/TypeScript provider.

## Supported source files

The built-in provider recognises `.js`, `.jsx`, `.mjs`, `.cjs`, `.ts`, `.tsx` and `.d.ts`. The JDT
HTTP provider recognises Java annotations from Spring MVC/WebFlux and JAX-RS. JavaScript/TypeScript
support in 0.2.x is experimental: facts include `provider`, `confidence`, `complexityProvider` and
`complexityAccuracy` so consumers can distinguish structural evidence from resolved compiler ASTs.

## Gradle

```groovy
plugins {
    id 'org.aiknowledge.extractor' version '0.2.0'
}
```

Generate and analyse:

```bash
./gradlew generateAiKnowledgeIndex analyzeAiComplexity
```

For a snapshot build, replace `0.2.0` with the version published by the selected repository. Do not
copy the example version into production until the release page confirms its publication channel.

## Maven

```xml
<plugin>
  <groupId>org.aiknowledge</groupId>
  <artifactId>ai-knowledge-maven-plugin</artifactId>
  <version>0.2.0</version>
</plugin>
```

```bash
mvn org.aiknowledge:ai-knowledge-maven-plugin:0.2.0:generate \
    org.aiknowledge:ai-knowledge-maven-plugin:0.2.0:analyze
```

## Shared scan configuration

Gradle, Maven and direct core callers use exactly the same JVM properties:

| Property | Default | Meaning |
|---|---:|---|
| `aiknowledge.source.enabledProviders` | all | Comma-separated provider IDs to allow. |
| `aiknowledge.source.disabledProviders` | none | Provider IDs to suppress. Takes precedence. |
| `aiknowledge.source.includes` | all supported sources | Comma-separated repository-relative globs. |
| `aiknowledge.source.excludes` | common generated/vendor trees | Additional exclusion globs. |
| `aiknowledge.source.ignoredDirectories` | none | Additional directory segment names to ignore. |
| `aiknowledge.source.maxFileBytes` | `2000000` | Maximum admitted bytes for one source file. |
| `aiknowledge.source.maxFiles` | `100000` | Maximum source files in one extraction. |
| `aiknowledge.source.maxTotalBytes` | `500000000` | Repository-wide admitted source byte budget. |
| `aiknowledge.source.includeGenerated` | `false` | Admit recognised generated source paths. |
| `aiknowledge.source.errorPolicy` | `warn` | `fail`, `warn`, or `skip` source-admission and provider failures. |

Example:

```bash
./gradlew analyzeAiComplexity \
  -Daiknowledge.source.includes='web/**/*.ts,backend/**/*.java' \
  -Daiknowledge.source.excludes='**/*.spec.ts' \
  -Daiknowledge.source.maxFileBytes=1000000 \
  -Daiknowledge.source.errorPolicy=fail
```

Default exclusions cover `node_modules`, `build`, `target`, `dist`, coverage output, common framework
caches, minified assets and source maps. Multiple providers may analyse one admitted file without
consuming the repository budget twice. With `warn`, file-admission I/O failures are retained in the
`source-analysis-configuration` evidence under `admissionWarnings`; provider parsing limitations
continue to appear in `warnings.json`. With `skip`, both categories are skipped without warning;
with `fail`, either category aborts extraction.

## Expected evidence

The example frontend operation

```text
GET /api/users/${id}
```

and Java endpoint

```text
GET /api/users/{id}
```

normalise to `GET /api/users/{}` and appear in the boundary links. A runtime expression such as
`fetch(createUrl(id))` remains unresolved or is represented as `<dynamic>` with low confidence; it
is never silently promoted to a literal high-confidence contract.

The language-neutral artifacts are `source-units.json`, `symbols.json`, `relations.json`,
`boundaries.json`, and `warnings.json`; the analysis contains `boundary-analysis.json`. Exact output
locations are listed in [`output-schema.md`](output-schema.md).

## Monorepos and multiple frontends

Every `package.json`, Maven `pom.xml`, and Gradle build file contributes module metadata. Use include
and exclude globs to bound very large monorepos. npm workspace declarations are retained as module
evidence, while lockfile-level transitive dependency analysis is not part of 0.2.x.

## Scope boundary

The extractor evaluates the current checkout only. It does not read commit history, calculate
co-change matrices, or include Git-derived change coupling in any score.
