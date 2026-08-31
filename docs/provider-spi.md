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
- `warnings`: human-readable recoverable limitations.

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

## Schema evolution

Schema-v2 additions are additive. Removing or changing the meaning of a field requires a new schema
major version and migration notes. See [`schema-v2-contract.md`](schema-v2-contract.md).

## Explicit non-goal

Providers must not inspect commit history or calculate co-change/change-coupling evidence. That data
is deliberately excluded from the product and from the score model.
