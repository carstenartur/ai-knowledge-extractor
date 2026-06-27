# Architecture

AI Knowledge Extractor is split into three layers.

## Core

The `core` module contains the deterministic scanner and report generator. It does not depend on Gradle or Maven APIs. The public facade is `AiKnowledgeRunner`.

Main operations:

- `generate` scans the repository and writes the knowledge index.
- `analyze` computes AI cognitive complexity and context budget metrics.
- `optimize` detects knowledge smells and ranks recommendations.
- `benchmark` compares deterministic extraction profiles.
- `check` applies CI quality gates.

## Gradle plugin

The `gradle-plugin` module exposes the plugin id `org.aiknowledge.extractor` and registers root-project tasks for the core operations.

## Maven plugin

The `maven` module packages a Maven plugin artifact named `ai-knowledge-maven-plugin`. Its goals delegate to the same core runner so Maven and Gradle builds produce the same artifact family.

## Design constraints

- deterministic output ordering
- no mandatory model calls
- no SaaS dependency
- stable output directory
- usable from repository-local builds and CI
- Gradle and Maven front ends over one shared core
