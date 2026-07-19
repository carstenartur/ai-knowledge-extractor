# Artifact verification and trust boundary

AI Knowledge Extractor 0.1.8 introduces repository-owned verification for the artifacts it emits. Consumers no longer need to reproduce the generic file and cross-document contract in project-specific shell or Python code.

## Canonical commands

Gradle:

```bash
./gradlew aiKnowledgeCheck
```

Maven:

```bash
mvn org.aiknowledge:ai-knowledge-maven-plugin:<version>:check
```

Both commands scan the repository once, generate the complete artifact set from that frozen snapshot, execute the configured quality gate and then validate the generated files. Focused Gradle tasks remain available for individual report families, but the canonical lifecycle does not chain them together and therefore does not rescan the checkout repeatedly.

To validate an existing Gradle output directory without regenerating any artifact, use:

```bash
./gradlew verifyAiKnowledgeArtifacts
```

## Verification profiles

The Java core exposes `AiKnowledgeArtifactVerifier` with two profiles.

### `QUALITY_GATE`

This profile covers the outputs produced by `AiKnowledgeRunner.check()`:

- raw repository knowledge envelopes;
- `review-context.md` and context packs;
- complexity and trend reports;
- `check.json`.

`checkAiKnowledgeIndex` uses this profile after writing `check.json`.

### `COMPLETE_LIFECYCLE`

This profile adds:

- `optimization.json` and `optimization.html`;
- `benchmark.json` and `benchmark.html`.

Gradle `verifyAiKnowledgeArtifacts`, Gradle `aiKnowledgeCheck`, Maven `check` and `AiKnowledgeRunner.verify()` use this profile. The standalone Gradle verifier only reads the configured output directory; the other three entry points generate the complete lifecycle first.

## Fail-closed checks

The verifier rejects:

- a missing output directory;
- missing, empty or symbolic-link artifacts;
- artifact paths that escape the configured output directory;
- malformed JSON;
- duplicate JSON object fields;
- trailing JSON tokens;
- invalid numbers, escapes or Unicode surrogate pairs;
- raw envelope arrays whose sizes differ from `index.json.counts`;
- duplicate capability or context-pack identities;
- a context-pack index that omits capabilities or references unexpected packs;
- context-pack path, identity, label, status or token-estimate drift;
- missing context-pack files;
- malformed context-footprint v3 structures;
- incompatible `measurementStatus`, sample-count and normalized-debt combinations;
- disagreement between top-level `aiContextDebt` and `contextFootprint.normalizedContextDebt`;
- disagreement between `check.json` and `complexity.json`;
- a passing `check.json` with violations or a failing `check.json` without violations.

The parser and verifier are dependency-free Java 17 code in the core artifact, so Gradle and Maven use the same implementation.

## What verification does not claim

Artifact verification establishes syntax, completeness and internal consistency. It does not decide that a repository is well documented, easy for an AI system to understand or suitable for release.

Those decisions remain in the configured quality gate, including:

- maximum normalized context debt;
- trend thresholds;
- warning policy;
- method complexity thresholds;
- required capability evidence;
- required claim verification;
- minimum context-pack count;
- maximum context-pack size.

The context-footprint contract intentionally accepts both:

- `MEASURED`, with at least one resolved capability working-set sample;
- `NO_CAPABILITY_SAMPLES`, with zero samples and fail-closed normalized debt 100.

A consumer may reject `NO_CAPABILITY_SAMPLES` by configuring capability-evidence requirements. The generic verifier must preserve the state rather than silently upgrading or inventing evidence.

## `check.json`

`check.json` is an official artifact, not an optional workflow convenience. `AiKnowledgeRunner.check()` writes it before returning or raising a quality-gate failure. It retains:

- `passed`;
- normalized and legacy context debt;
- context-footprint v3 data;
- code-complexity data;
- method-complexity thresholds;
- violations and warning policy;
- trend status and thresholds;
- claim-failure count;
- knowledge-quality-gate results.

CI systems should retain `check.json` even when the quality gate fails because it contains the primary machine-readable diagnosis.

## Consumer integration rule

Consumers should invoke the published lifecycle rather than copy its implementation. A local Gradle composite build may be used while developing the extractor itself, but normal builds should consume a released package version.
