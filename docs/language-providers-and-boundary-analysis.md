# Language providers and frontend/backend boundary analysis

This document is the developer and integrator reference for the language-neutral source-provider architecture, JavaScript/TypeScript extraction and frontend/backend boundary analysis.

## Purpose

The extractor previously treated Java facts as the primary code model. The current design keeps the existing Java artifacts for consumers that need them, but introduces a language-neutral layer for Java, JavaScript, TypeScript and future providers.

The design has three goals:

1. equivalent structures in different programming languages should have equivalent meanings and comparable metrics;
2. parser-specific precision differences must remain visible instead of silently changing the scoring model; and
3. support for another language or project system must not require changes to every downstream report.

The boundary analysis uses only the current checkout. It does **not** read Git history and does not calculate co-change or commit-based change coupling.

## Extraction flow

The core pipeline performs these steps:

1. inventory repository files while excluding generated output and dependency caches;
2. discover Gradle, Maven and npm-compatible modules and declared dependencies;
3. run the selected Java provider for the established Java-specific artifacts;
4. map Java results to the language-neutral facts;
5. run every `SourceKnowledgeProvider` that supports a source path;
6. link capabilities and verify claims;
7. write raw facts and analysis reports.

Multiple source providers may analyze the same file. A general language provider can therefore coexist with a focused framework provider. The built-in Java HTTP provider, for example, contributes endpoint facts without replacing the configured Java structural provider.

## Provider SPI

The public SPI is in `org.aiknowledge.core.sourcespi`:

- `SourceKnowledgeProvider`
- `SourceKnowledgeRequest`
- `SourceKnowledgeResult`

A provider implements:

```java
public interface SourceKnowledgeProvider {
    String id();

    boolean supports(String sourcePath);

    SourceKnowledgeResult extract(SourceKnowledgeRequest request)
            throws IOException;

    default int priority() {
        return 0;
    }
}
```

`SourceKnowledgeRequest` supplies the repository root, source file, normalized repository-relative source path, discovered build modules, build metadata and provider configuration. Providers must not write files or mutate the supplied module facts.

`SourceKnowledgeResult` contains five independent fact collections:

| Collection | Meaning |
|---|---|
| `sourceUnitFacts` | Source modules, files, compilation units or language-equivalent top-level units. |
| `symbolFacts` | Types, functions, methods, fields, hooks, components and other named declarations. |
| `relationFacts` | Typed edges between source units, symbols, imports and other entities. |
| `boundaryFacts` | Client calls and server endpoint contracts. |
| `warningFacts` | Recoverable limitations, unsupported syntax and confidence warnings. |

No provider-specific AST object may cross the SPI. Downstream analyzers consume maps containing stable scalar, list and map values.

### Registering an external provider

Package the provider in a normal Java library that depends on `org.aiknowledge:ai-knowledge-core:<version>`. Add the implementation class name to:

```text
META-INF/services/org.aiknowledge.core.sourcespi.SourceKnowledgeProvider
```

Example service file:

```text
com.example.extractor.PythonSourceKnowledgeProvider
```

Minimal implementation:

```java
package com.example.extractor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.aiknowledge.core.sourcespi.SourceKnowledgeProvider;
import org.aiknowledge.core.sourcespi.SourceKnowledgeRequest;
import org.aiknowledge.core.sourcespi.SourceKnowledgeResult;

public final class PythonSourceKnowledgeProvider
        implements SourceKnowledgeProvider {

    @Override
    public String id() {
        return "python-ast";
    }

    @Override
    public boolean supports(String sourcePath) {
        return sourcePath.endsWith(".py");
    }

    @Override
    public SourceKnowledgeResult extract(SourceKnowledgeRequest request)
            throws IOException {
        Map<String, Object> unit = new LinkedHashMap<>();
        unit.put("id", "source:" + request.sourcePath());
        unit.put("name", request.sourcePath());
        unit.put("language", "python");
        unit.put("sourceFile", request.sourcePath());
        unit.put("provider", id());
        unit.put("confidence", "ast");
        return new SourceKnowledgeResult(
                List.of(unit), List.of(), List.of(), List.of(), List.of());
    }
}
```

When two providers return the same provider id, the `ServiceLoader` provider replaces the built-in registration with that id. Use globally unique, stable ids.

## Common fact conventions

Every emitted fact should contain enough provenance to explain the result:

| Field | Purpose |
|---|---|
| `id` | Stable identifier within one extraction. |
| `kind` | Language-neutral entity or relation kind. |
| `language` | Language identifier such as `java`, `javascript` or `typescript`. |
| `sourceFile` | Repository-relative path using `/`. |
| `module` | Discovered build-module name when available. |
| `provider` | Provider id. |
| `confidence` | Provider-defined confidence such as `ast`, `syntactic-structural`, `partial-expression` or `low`. |

Providers should additionally include source line or offset evidence when it is available. Lists and map keys should be emitted in deterministic order.

### Recommended relation kinds

The initial implementation uses relations such as:

- `SOURCE_UNIT_IMPORTS_MODULE`
- `SOURCE_UNIT_DECLARES_SYMBOL`
- `CALLABLE_CALLS_BOUNDARY`
- `CALLABLE_EXPOSES_BOUNDARY`
- Java structural relations such as `TYPE_EXTENDS_TYPE`, `TYPE_IMPLEMENTS_TYPE`, `FIELD_HAS_TYPE`, `METHOD_RETURNS_TYPE` and `METHOD_PARAMETER_HAS_TYPE`

New providers should reuse an existing relation kind when the semantics are equivalent. Introduce a new kind only when the distinction is meaningful to consumers.

## JavaScript and TypeScript provider

`JavaScriptTypeScriptKnowledgeProvider` handles:

- `.js`, `.jsx`, `.mjs`, `.cjs`, `.ts` and `.tsx` files;
- TypeScript declaration files ending in `.d.ts`;
- ES imports and re-exports;
- `import type` as type-only evidence;
- CommonJS `require`;
- dynamic `import()` calls;
- classes, interfaces, type aliases and enums;
- named functions, arrow functions and methods;
- exports and test-file classification;
- `fetch`, Axios, GraphQL, WebSocket and `EventSource` client calls;
- structural cyclomatic and cognitive complexity;
- observable status interpretation, error handling and response/model transformation signals.

The provider has no Node.js runtime dependency. It masks comments and strings, uses balanced structural parsing and marks the resulting precision as `syntactic-structural` or `token-structural`. This makes the default build portable and deterministic, but it is not a replacement for the complete TypeScript compiler type system.

### Runtime and type-only dependencies

Import relations distinguish:

```text
runtime: true|false
typeOnly: true|false
dynamic: true|false
external: true|false
packageName: <top-level npm package>
```

The dependency-surface analyzer compares these imports with `package.json` scopes:

- `dependencies`
- `devDependencies`
- `peerDependencies`
- `optionalDependencies`

Unused development dependencies do not increase the boundary score. Type-only imports remain visible in the raw facts but do not count as runtime dependency surface. A runtime import declared only in `devDependencies` and an undeclared runtime import are reported as findings.

### Dynamic URLs

Literal strings and template literals are normalized when their route shape can be established. Runtime expressions that cannot be resolved are retained as evidence with:

```text
normalizedPath: <dynamic>
literalPath: false
confidence: low
```

The analyzer does not guess a target for a dynamic path.

## Java HTTP endpoint provider

`JavaHttpBoundaryKnowledgeProvider` uses Eclipse JDT syntax trees to extract server endpoint contracts independently of the configured Java structural provider.

Supported Spring annotations include:

- `@RequestMapping`
- `@GetMapping`
- `@PostMapping`
- `@PutMapping`
- `@PatchMapping`
- `@DeleteMapping`

Class-level and method-level routes are combined. `@RequestMapping(method = RequestMethod.…)` is also interpreted.

Supported JAX-RS evidence includes `@Path` and the standard HTTP method annotations such as `@GET`, `@POST`, `@PUT`, `@PATCH`, `@DELETE`, `@HEAD` and `@OPTIONS`.

Functional routing DSLs and reflective endpoint registration are not fully resolved. The provider emits a warning when recognizable functional Java routing constructs are present.

## Route normalization and linking

Client and server paths are normalized to a common route-template representation. For example:

```text
Frontend: GET /api/users/${id}
Backend:  GET /api/users/{id}
Normal:   GET /api/users/{}
```

A link is classified as:

- `linked`: exactly one matching endpoint;
- `ambiguous`: more than one matching endpoint;
- `unresolved`: no matching endpoint;
- `unresolved-dynamic`: the client target is runtime-generated.

The link includes client-call id, operation, source file, line, endpoint candidates and confidence.

## Shared complexity model

Java and JavaScript/TypeScript callable facts use:

```text
complexityModel: aiknowledge-control-flow-v1
```

The shared semantic model counts comparable decision structures, including conditionals, loops, catch blocks, switch labels, conditional expressions and boolean decision operators. Parser precision remains separate:

```text
complexityProvider
complexityAccuracy
provider
confidence
```

The JDT implementation has AST precision. The default JavaScript/TypeScript implementation has token-structural precision. A future compiler-API provider should retain the common model id if it preserves the same scoring semantics.

## Boundary cognitive-load proxy

`BoundaryAnalyzer` produces an explainable structural proxy on a 0–100 scale. It is not a measurement of a particular developer's mental state.

Current dimensions and aggregate weights are:

| Dimension | Aggregate weight | Evidence |
|---|---:|---|
| `structuralCoupling` | 20% | Endpoint fan-out and unresolved or ambiguous links. |
| `orchestration` | 20% | Multiple and awaited client operations in one callable. |
| `modelTranslation` | 15% | Response mapping and view-model construction signals. |
| `semanticCoupling` | 15% | Frontend comparisons of backend status, state, phase or code values. |
| `errorComplexity` | 10% | Catch logic, status branches and inconsistent handling. |
| `dependencySurface` | 10% | Actually imported runtime packages and mixed client mechanisms. |
| contract uncertainty | 10% | Inverse of `contractClarity`: route resolution, explicit methods, literal paths and generated clients. |

`contractClarity` is reported with a higher-is-better score; the aggregate uses its uncertainty (`100 - contractClarity`). Each dimension includes an explanation, and the report provides findings, recommendations, links, confidence and limitations.

The report explicitly contains:

```text
versionControlHistoryUsed: false
changeCouplingIncluded: false
```

No `git log`, JGit history traversal or commit co-change metric is used.

## Generated artifacts

The `generate` lifecycle adds these raw language-neutral artifacts to the configured output directory:

- `source-units.json`
- `symbols.json`
- `relations.json`
- `boundaries.json`
- `warnings.json`

The `analyze`, `check` and complete verification lifecycles additionally create:

- `boundary-analysis.json`
- `boundary-analysis.html`

`boundary-analysis.json` is also embedded under `boundaryAnalysis` in `complexity.json` and `check.json`. The artifact verifier requires the standalone and embedded representations to be identical.

## Integration through Gradle and Maven

No additional configuration is required for JavaScript or TypeScript discovery. The provider runs automatically for supported files in the repository, and `package.json` is discovered alongside Gradle and Maven module metadata.

Gradle:

```bash
./gradlew aiKnowledgeCheck
```

Maven:

```bash
mvn org.aiknowledge:ai-knowledge-maven-plugin:<version>:check
```

No Node.js installation is required for the built-in structural provider. Java projects can continue selecting `basic`, `jdt` or `jdt-search` for the Java-specific extraction while the HTTP endpoint and JavaScript/TypeScript providers run compositionally.

## Provider quality requirements

Before publishing a third-party provider, verify that it:

1. never emits absolute machine-local paths;
2. produces deterministic fact and collection ordering;
3. includes provider and confidence metadata;
4. retains unresolved evidence instead of inventing a target;
5. uses the common complexity model only when its semantics match;
6. supplies focused unit tests and a mixed-repository integration test;
7. documents unsupported syntax and framework limitations; and
8. performs no network access during deterministic extraction.

## Known limitations

The first release of this architecture does not fully resolve:

- TypeScript compiler types, path aliases and overload resolution;
- JavaScript decorators and every framework-specific client abstraction;
- Spring functional routes and arbitrary Java routing DSLs;
- GraphQL server schema-to-resolver linking;
- runtime-generated routes or reflective endpoint registration;
- browser-to-backend calls hidden behind generated code that is excluded from scanning.

These are coverage limitations, not reasons to use a different scoring model. Future providers should improve evidence precision while keeping common fact and metric semantics.
