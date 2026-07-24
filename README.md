# AI Knowledge Extractor

[![CI](https://github.com/carstenartur/ai-knowledge-extractor/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/carstenartur/ai-knowledge-extractor/actions/workflows/ci.yml)
[![Tests](https://img.shields.io/endpoint?url=https://carstenartur.github.io/ai-knowledge-extractor/site/badges/tests.json)](https://carstenartur.github.io/ai-knowledge-extractor/site/tests/)
[![License](https://img.shields.io/github/license/carstenartur/ai-knowledge-extractor)](LICENSE)
![Java 17](https://img.shields.io/badge/Java-17-blue)
[![Latest release](https://img.shields.io/github/v/release/carstenartur/ai-knowledge-extractor?sort=semver)](https://github.com/carstenartur/ai-knowledge-extractor/releases)
[![GitHub Packages](https://img.shields.io/badge/packages-GitHub%20Packages-blue)](docs/publishing.md)
[![Citation](https://img.shields.io/badge/citation-CFF-informational)](CITATION.cff)
[![Documentation](https://img.shields.io/badge/docs-GitHub%20Pages-blue)](https://carstenartur.github.io/ai-knowledge-extractor/site/)
[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.21180224.svg)](https://doi.org/10.5281/zenodo.21180224)

Deterministic build-integrated knowledge extraction for AI-assisted code understanding.

This repository provides a Java core plus Gradle and Maven entry points. It generates stable files under `build/ai-knowledge/` for modules, classes, tests, docs, dependencies, capabilities, claims, complexity metrics, optimization hints and context-profile benchmark estimates.

## Why this project exists

The project grew out of a recurring practical problem in AI-assisted software development: smaller and less expensive language models were repeatedly overwhelmed by repository-scale tasks. Selecting a larger model often solved the immediate problem, but increased inference cost and did not address the amount of irrelevant, duplicated or implicit information presented to the model.

This project explores a complementary approach: instead of always increasing model capability, reduce the amount and difficulty of reasoning required from the model. The intended outcome is that a development task can be completed reliably by the least expensive model capable of performing it.

AI Knowledge Extractor therefore attempts to:

- extract explicit structural and semantic facts from a repository;
- select and package task-relevant context instead of entire codebases;
- expose relationships that a model would otherwise have to reconstruct;
- estimate the complexity and model-profile budget of the resulting context;
- record repeatable proxy metrics and baselines so changes can be compared across builds; and
- detect and optionally reject gradual metric growth through configurable CI thresholds.

## Research question and working hypothesis

> Can a software repository be transformed into a smaller, structured representation that preserves the knowledge required for a task while reducing the reasoning burden imposed on an LLM?

The working hypothesis is that explicit repository knowledge can reduce context volume, ambiguity and costly rediscovery without removing information needed to solve the task. If successful, this should lower token consumption and latency, improve reliability and allow smaller, less expensive models to perform work that would otherwise require larger models.

Knowledge extraction is therefore a means rather than the final objective. The longer-term objective is to provide a model with precisely the information needed for a development task: no more, no less.

## What “cognitive load” means here

The project uses *cognitive load* as an operational engineering term, not as a claim that language models possess human cognition. It refers to the burden created by factors such as context size, dependency spread, ambiguity, traversal depth, the number of relevant entities and relationships, and the amount of structure that must be inferred before a task can be solved.

Any attempt to reduce this burden needs a repeatable way to observe it. Without a measure, there is no objective basis for deciding whether one representation is less demanding than another or whether later repository changes have silently undone an earlier improvement. A core purpose of this project is therefore to produce deterministic proxy metrics that can be versioned, compared and checked across builds.

There is currently no universally validated measure for this burden. The complexity metrics, model profiles, benchmark estimates and optimization hints produced by this project are experimental proxies. They are intended to make competing representations measurable and testable, not to present the underlying research problem as solved. Establishing whether these proxies predict real task success across models is part of the project's continuing empirical work.

## Preventing cognitive-load drift

Cognitive load rarely grows in one obvious step. It can increase incrementally as code, dependencies, relationships and generated context accumulate. Each change may appear harmless, while their combined effect may eventually exceed the budget of the smaller model a team intended to use.

To make that drift visible, the project supports committed metric baselines, trend reports and configurable quality gates. CI can be configured to reject increases beyond selected thresholds—for example in estimated context tokens, concept radius or AI cognitive debt—and to limit individual context-pack size. This is intended to preserve an accepted cognitive-load budget as the repository evolves, instead of treating reduction as a one-time optimization.

These thresholds are disabled by default and must be calibrated for the repository, task set and target model profiles. They are experimental guardrails rather than proof that a model will succeed or fail. Their practical value must be validated against empirical task outcomes. Configuration details are documented in [`docs/trend-gates.md`](docs/trend-gates.md).

## Quick start

- Gradle: apply plugin `org.aiknowledge.extractor` and run `./gradlew aiKnowledgeCheck` for the complete verified lifecycle.
- Maven: invoke `mvn org.aiknowledge:ai-knowledge-maven-plugin:<version>:check` for the complete verified lifecycle.
- `generateAiKnowledgeIndex` / Maven `generate` remain available when only the raw knowledge index is needed.
- Consumer setup, tasks, extension parameters, goals and repository configuration are documented in [`docs/gradle-plugin.md`](docs/gradle-plugin.md), [`docs/maven-plugin.md`](docs/maven-plugin.md) and [`docs/publishing.md`](docs/publishing.md).
- Releases are published through the GitHub Actions [`Release` workflow](https://github.com/carstenartur/ai-knowledge-extractor/actions/workflows/publish.yml); use the documented `dry_run` mode before the first real release.

The complete lifecycle generates index, context, complexity, trend, optimization, benchmark and quality-gate outputs, then verifies their structural and cross-document integrity. It rejects missing or empty files, malformed or duplicate-field JSON, count drift, broken context-pack references and internally inconsistent v3 context metrics. Repository-specific thresholds remain controlled by the normal quality-gate configuration.

## Gradle

Plugin id: `org.aiknowledge.extractor`

Tasks:

- `generateAiKnowledgeIndex`
- `analyzeAiComplexity`
- `optimizeAiKnowledge`
- `benchmarkAiKnowledge`
- `checkAiKnowledgeIndex`
- `verifyAiKnowledgeArtifacts`
- `aiKnowledgeCheck`
- `publishAiKnowledgeIndex`

Canonical CI command:

```bash
./gradlew aiKnowledgeCheck
```

Optional empirical benchmark layer (disabled by default):

- `aiKnowledge.empiricalBenchmarkEnabled = true`
- `aiKnowledge.empiricalBenchmarkFixtureFile = file("ai-knowledge/benchmark-fixtures.yaml")`

For local plugin development, consumers may use a Gradle composite build with `includeBuild('../ai-knowledge-extractor')`.

Canonical Gradle plugin usage (plugin id, tasks, extension defaults, CI/local examples): [`docs/gradle-plugin.md`](docs/gradle-plugin.md).

## Maven

Maven plugin coordinates: `org.aiknowledge:ai-knowledge-maven-plugin:<version>`.

Goals: `generate`, `analyze`, `optimize`, `benchmark`, `check`, `help`.

`check` generates the complete report set, executes configured quality gates and verifies the emitted artifacts:

```bash
mvn org.aiknowledge:ai-knowledge-maven-plugin:<version>:check
```

Maven help goal:

```bash
mvn org.aiknowledge:ai-knowledge-maven-plugin:<version>:help -Ddetail=true
```

Optional empirical benchmark layer parameters:

- `empiricalBenchmarkEnabled` (default `false`)
- `empiricalBenchmarkFixtureFile` (default `${project.basedir}/ai-knowledge/benchmark-fixtures.yaml`)

Canonical Maven goal, parameter and help usage reference: [`docs/maven-plugin.md`](docs/maven-plugin.md). For the generated Maven plugin reference with full parameter tables for all goals, see the [GitHub Pages documentation site](https://carstenartur.github.io/ai-knowledge-extractor/site/).

## Scope

Implemented as deterministic static analysis without external model calls:

- repository knowledge extraction
- AI cognitive complexity estimate
- trend reports and CI quality gates
- knowledge-smell recommendations
- deterministic extraction-profile benchmark scaffold with optional empirical fixture layer
- configurable model-profile budget metrics
- strict artifact and cross-document verification

## Extraction architecture packages

The core extractor is organized as package-level modules (within `core`) so responsibilities stay separated while preserving the current artifacts:

- `org.aiknowledge.core.model`: stable repository fact model helpers (`RepositoryFacts` index/count assembly).
- `org.aiknowledge.core`: orchestration, outputs and artifact verification (`AiKnowledgeRunner`, `AiKnowledgeArtifactVerifier`, `KnowledgeExtractionPipeline`).
- `org.aiknowledge.core.repositoryscan`: repository inventory and non-Java scanners (build files, docs, workflows, discovery evidence, benchmark sources).
- `org.aiknowledge.core.javaspi`: Java provider interface (`JavaKnowledgeProvider`).
- `org.aiknowledge.core.javabasic`: default heuristic Java provider implementation.
- `org.aiknowledge.core.linker`: capability/claim linking from extracted evidence.
- `org.aiknowledge.core.context`: seed/context merge step before output writing.

### Java provider selection and limitations

- Java extraction is resolved via Java `ServiceLoader` (`JavaKnowledgeProvider` SPI) and selected through `-Daiknowledge.javaProvider=<basic|jdt|providerClassName>`.
- The built-in fallback is `org.aiknowledge.core.javabasic.BasicJavaKnowledgeProvider`.
- The optional JDT provider is `org.aiknowledge.core.javajdt.JdtJavaKnowledgeProvider` and enriches type/interface/reference facts (`classes.json` and `tests.json`) using Eclipse JDT model/search APIs.
- Use the JDT provider when you need stronger interface implementation mapping, type reference discovery and test-to-production links; keep `basic` for lightweight heuristic extraction.
- JDT runs best with complete compile classpaths. If classpath data is incomplete, the provider records warnings in extracted facts (for example `jdt-classpath-incomplete` / `jdt-bindings-incomplete`).
- `BasicJavaKnowledgeProvider` preserves current `classes.json` and `tests.json` fields and adds structured Java facts, but it is still heuristic line-based parsing and may miss complex Java syntax.

### JDT provider: fieldFacts and relationFacts

When `-Daiknowledge.javaProvider=jdt` is active, the provider emits two additional fact collections per source file.

`fieldFacts` contains one entry per field declaration with declaring type, name, declared type, modifiers, source location, provider and confidence.

`relationFacts` records structural relationships such as package containment, inheritance, interface implementation, field/parameter/return types, cross-file references and test-to-production references.

Each relation fact includes `source`, `target`, `sourceFile`, `offset`/`length` (when available), `line`, `provider`, and `confidence`.

### Classpath configuration for the JDT provider

The Gradle plugin automatically resolves the `compileClasspath` configuration and passes it to the JDT provider. You can configure JDT search/workspace mode directly in the extension block:

```groovy
aiKnowledge {
    javaProvider            = "jdt"      // basic | jdt
    jdtMode                 = "search"   // ast | search
    jdtSearchExecutionMode  = "forked"   // embedded | forked
    jdtSearchFallbackToAst  = true
    jdtWorkspaceMode        = "create"   // create | existing | off
    jdtWorkspaceDirectory   = "$buildDir/ai-knowledge/jdt-workspace"
    keepJdtWorkspace        = false
}
```

Or via system properties:

```bash
./gradlew aiKnowledgeCheck \
  -Daiknowledge.javaProvider=jdt \
  -Daiknowledge.jdt.mode=search \
  -Daiknowledge.jdt.search.execution.mode=forked \
  -Daiknowledge.jdt.workspace.mode=create \
  -Daiknowledge.jdt.workspace.directory="$PWD/build/ai-knowledge/jdt-workspace"
```

The Maven plugin accepts the same parameters, and automatically injects `${project.compileClasspathElements}` so bindings resolve correctly when the project has been compiled.

## Model profiles

Default model profiles and project-specific `ai-knowledge/model-profiles.yaml` configuration are documented in [`docs/model-profiles.md`](docs/model-profiles.md).

## Trend gates

Trend baselines, `metrics-snapshot.json`, `trend.json` and CI threshold configuration are documented in [`docs/trend-gates.md`](docs/trend-gates.md).

## Publishing and consumption

Artifact IDs, local development consumption, GitHub Packages repository configuration and release-version handling are documented in [`docs/publishing.md`](docs/publishing.md). Plugin-specific canonical usage docs: [`docs/gradle-plugin.md`](docs/gradle-plugin.md) and [`docs/maven-plugin.md`](docs/maven-plugin.md).

## Release process

Release execution, `dry_run`, metadata updates, tagging and the follow-up PR flow are documented in [`docs/release.md`](docs/release.md).

## Citation

Citation metadata is maintained in `CITATION.cff`. Release metadata is maintained in `.zenodo.json`. See `docs/release.md` for the release checklist.

License: Apache-2.0