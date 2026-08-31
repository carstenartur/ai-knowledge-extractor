# Schema v2 contract

Schema v2 adds language-neutral facts while retaining the established Java-oriented artifacts.
List artifacts are JSON objects whose top-level key matches the file name.

| Artifact | Top-level key | Primary identity |
|---|---|---|
| `source-units.json` | `sourceUnits` | `id` |
| `symbols.json` | `symbols` | `id` |
| `relations.json` | `relations` | tuple `kind`, `source`, `target`, `sourceFile` |
| `boundaries.json` | `boundaries` | `id` |
| `warnings.json` | `warnings` | no stable external identity |
| `boundary-analysis.json` | object | one repository analysis |

## Common rules

- Paths are repository-relative UTF-8 strings with `/` separators.
- Producers omit unavailable optional values rather than writing misleading placeholders.
- Collection fields are arrays; an empty collection is `[]`, never `null`.
- Output is deterministically sorted by stable identity and source position where available.
- Referenced IDs must either exist in the same extraction or name an external/unresolved target
  explicitly through confidence metadata.
- `provider` identifies the producer and `confidence` explains evidence quality.
- Consumers must ignore unknown additive fields.
- Removing fields, changing identity rules or changing established semantics requires a new schema
  major version.

## Source unit example

```json
{
  "id": "source:web/src/users.ts",
  "name": "web/src/users.ts",
  "kind": "service-module",
  "language": "typescript",
  "sourceFile": "web/src/users.ts",
  "provider": "javascript-typescript-structural",
  "confidence": "syntactic-structural"
}
```

## Relation example

```json
{
  "kind": "CALLABLE_CALLS_BOUNDARY",
  "source": "web/src/users.ts#loadUser@5",
  "target": "GET /api/users/{}",
  "sourceFile": "web/src/users.ts",
  "provider": "javascript-typescript-structural"
}
```

## Boundary example

```json
{
  "id": "client:web/src/users.ts:6:1",
  "kind": "client-call",
  "protocol": "http",
  "method": "GET",
  "path": "/api/users/${id}",
  "normalizedPath": "/api/users/{}",
  "sourceFile": "web/src/users.ts",
  "provider": "javascript-typescript-structural",
  "confidence": "syntactic-structural"
}
```

## Boundary analysis

The analysis exposes separate dimensions, raw links, findings, confidence and limitations. The
aggregate score is a structural proxy for simultaneously required knowledge, not a measurement of
a particular developer. `versionControlHistoryUsed` is always `false`: commit history and co-change
coupling are not inputs.
