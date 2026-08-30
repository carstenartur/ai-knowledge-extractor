# Architecture

AI Knowledge Extractor is split into three layers.

## Core

The `core` module contains the deterministic scanner, language-provider runtime and report generator. It does not depend on Gradle or Maven APIs. The public facade is `AiKnowledgeRunner`.

Main operations:

- `generate` scans the repository and writes the knowledge index.
- `analyze` computes AI cognitive complexity and context budget metrics.
- `optimize` detects knowledge smells and ranks recommendations.
- `benchmark` compares deterministic extraction profiles and can optionally add empirical fixture results.
- `check` applies CI quality gates.


## Source-provider architecture

Repository inventory, build-module discovery and source analysis are separate concerns. The source layer maps the configured Java provider into common facts and composes every matching `SourceKnowledgeProvider`.

Common fact collections are:

- source units;
- symbols and callables;
- typed relations;
- client calls and server endpoints; and
- recoverable provider warnings.

Parser-specific AST objects never enter the shared repository snapshot. Precision differences are represented through `provider`, `confidence`, `complexityProvider` and `complexityAccuracy` metadata. Java and JavaScript/TypeScript callable metrics share the `aiknowledge-control-flow-v1` semantic model.

The built-in providers are:

- JavaScript/TypeScript structural extraction without a Node.js dependency;
- JDT-backed Spring and JAX-RS HTTP endpoint extraction; and
- the configured Java-specific `basic`, `jdt` or `jdt-search` provider mapped to common facts.

External providers are loaded through Java `ServiceLoader`. See [`language-providers-and-boundary-analysis.md`](language-providers-and-boundary-analysis.md) for the extension contract.

## Boundary analysis

The boundary analyzer links normalized frontend operations to server endpoint templates and reports independent dimensions for structural coupling, orchestration, model translation, semantic coupling, error complexity, imported runtime dependency surface and contract clarity.

The analysis is deterministic and operates only on the current checkout. Git history and commit co-change coupling are not used.

## Gradle plugin

The `gradle-plugin` module exposes the plugin id `org.aiknowledge.extractor` and registers root-project tasks for the core operations.

## Maven plugin

The `maven` module packages a Maven plugin artifact named `ai-knowledge-maven-plugin`. Its goals delegate to the same core runner so Maven and Gradle builds produce the same artifact family.

## Design constraints

- deterministic output ordering
- no mandatory model calls
- no SaaS dependency
- stable output directory
- language-neutral facts with explicit provider provenance
- equivalent cross-language structures use shared metric semantics
- unresolved evidence is retained instead of guessed
- usable from repository-local builds and CI
- Gradle and Maven front ends over one shared core
