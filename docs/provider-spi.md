# Source provider SPI for language and tooling integrations

Implement `org.aiknowledge.core.sourcespi.SourceKnowledgeProvider` and register the implementation
in `META-INF/services/org.aiknowledge.core.sourcespi.SourceKnowledgeProvider`.

## Contract

1. `id()` is stable, globally distinctive and suitable for configuration.
2. `supports(path)` is a cheap path-only check and performs no I/O.
3. One provider instance analyses many files. Implementations must be stateless, thread-safe, or
   synchronized even though the current pipeline invokes them sequentially.
4. Identical bytes and configuration produce equivalent facts, stable IDs and stable ordering.
5. Do not emit timestamps, random IDs, absolute checkout paths, network results or Git-history data.
6. Parser AST objects never cross the SPI. Emit JSON-compatible values only.
7. Recoverable parser limitations are warnings. Unreadable input raises `IOException`; the shared
   `aiknowledge.source.errorPolicy` determines whether extraction fails, warns, or skips.

## Result categories

- `sourceUnitFacts`: file/module/type containers;
- `symbolFacts`: types, fields, values, declarations and executable symbols with `kind=callable`;
- `relationFacts`: typed directed relations;
- `boundaryFacts`: client operations or server endpoints;
- `warningFacts`: human-readable recoverable limitations.

Every fact should contain a stable `id` when it can be referenced, `sourceFile`, `provider`, and
`confidence`. Source files use repository-relative `/` separators. Confidence vocabulary is
`high`, `medium`, `low`, `syntactic`, `syntactic-structural`, `partial-expression`, or
`provider-defined`; providers should use the most precise existing term before introducing another.

## Deterministic IDs

Prefer semantic IDs such as:

```text
source:web/src/users.ts
web/src/users.ts#loadUser@5
endpoint:GET:/api/users/{}:example.UserController#get
```

Do not base an ID on list position unless the position is itself source-stable. Relations reference
those IDs through `source` and `target`.

## Minimal conformance test

A provider test should:

- run the provider twice on the same fixture and compare results;
- verify no absolute path occurs in emitted values;
- verify all lists are non-null;
- verify IDs remain stable after moving the repository root;
- cover valid, incomplete and syntactically invalid input;
- document every low-confidence fallback.

The built-in JavaScript/TypeScript provider is intentionally replaceable by a compiler-API or
Tree-sitter provider without changing downstream boundary scoring. A future Roslyn, Clang, Python
AST, Make, Visual Studio or NetBeans provider should use the same fact categories rather than
creating language-specific report semantics.

## Provider contract version and validation

`contractVersion()` declares the provider fact contract, currently `SourceFactContract.VERSION`
(`1.0`). It is independent of index schema v2 and of any scoring-model version. A `1.x` provider
may add fields or relation kinds; the extractor preserves them without interpreting unknown kinds.
Any other major version is rejected with an actionable diagnostic before artifacts are written.
Implementations compiled before this additive method receive the default 1.0 contract; they must
still emit valid facts. Parser precision remains in `provider`, `confidence`, `complexityProvider`
and `complexityAccuracy`, not in a separate language-specific score.

| Category | Required provider fields |
|---|---|
| Source units | `id`, `kind`, `language` |
| Symbols | `id`, `kind` |
| Relations | `kind`, `source`, `target` |
| Boundaries | `id`, `kind`, `protocol` |
| Warnings | `code`, `message` |

All required fields are nonblank strings. The pipeline supplies missing `sourceFile`, `provider`
and `confidence` (`provider-defined`). Explicit values are preserved. Paths must be repository
relative; values must be finite JSON primitives, lists or string-keyed maps. The entire result is
validated and copied with sorted map keys before any facts are admitted. Malformed contracts are fatal even when
recoverable parser errors are configured as warnings.

`SourceFactContract` supplies stable provenance/identity fields and built-in relation names.
Duplicate provider IDs (including collisions with built-ins) are rejected deterministically; an
external implementation cannot silently replace a built-in. Use a unique ID and explicitly disable
the original provider if replacing it. Ordering is descending priority, then ID.

Before 1.0, the provider contract is experimental, but incompatible changes still require an
explicit contract-major increment and migration notes. Additive minor extensions remain readable.
See [`schema-v2-contract.md`](schema-v2-contract.md) for the distinct artifact evolution policy.

## Loading external providers

Providers execute trusted code with the build process's filesystem, network and credential access.
Classloader separation is dependency isolation, not a security sandbox.

Gradle plugin-DSL consumers use the resolvable, non-consumable `aiKnowledgeProviders` configuration:

```groovy
plugins { id 'org.aiknowledge.extractor' version '<version>' }
repositories { mavenCentral() /* plus your provider artifact repository */ }
dependencies { aiKnowledgeProviders 'example.aiknowledge:text-source-provider:1.0' }
```

This configuration does not extend application compile/runtime configurations. Each task owns and
closes a parent-first URLClassLoader after extraction, using the plugin API as its parent. Provider
implementation dependencies can therefore be resolved separately; versions already supplied by the
plugin parent take precedence. The provider must declare Core as `provided` (Maven) or `compileOnly`
(Gradle), and must not bundle another copy of the SPI.

Maven uses its plugin realm, owned and closed by Maven:

```xml
<plugin>
  <groupId>org.aiknowledge</groupId>
  <artifactId>ai-knowledge-maven-plugin</artifactId>
  <version>${aiKnowledge.version}</version>
  <dependencies>
    <dependency>
      <groupId>example.aiknowledge</groupId>
      <artifactId>text-source-provider</artifactId>
      <version>1.0</version>
    </dependency>
  </dependencies>
</plugin>
```

Core callers use an explicit, caller-owned loader:

```java
try (var loader = new java.net.URLClassLoader(providerUrls,
        org.aiknowledge.core.AiKnowledgeRunner.class.getClassLoader())) {
    new org.aiknowledge.core.AiKnowledgeRunner(loader).verify(options);
}
```

The default runner uses its own classloader; it does not depend on mutable thread-context loader
visibility. The complete independent [sample provider project](../examples/fixtures/source-provider/pom.xml)
is built with `mvn -DaiKnowledge.version=<version> install` after making Core available in the selected
repository. Its [implementation](../examples/fixtures/source-provider/src/main/java/fixture/TextProvider.java)
and [service registration](../examples/fixtures/source-provider/src/main/resources/META-INF/services/org.aiknowledge.core.sourcespi.SourceKnowledgeProvider)
are consumed by the Gradle and Maven fixtures. The sample deliberately declares contract 1.1 and
emits an unknown additive field/relation to exercise forward reading. The binary verification also
loads incompatible and duplicate-ID variants and proves that retained outputs are unchanged.

## Explicit non-goal

Providers must not inspect commit history or calculate co-change/change-coupling evidence. That data
is deliberately excluded from the product and from the score model.
