# Opt-in boundary quality gates

Boundary limits are disabled by default. They enforce explicitly chosen structural policies;
they are not calibrated limits on human cognitive load. Core, Gradle and Maven use the same
`BoundaryGateOptions` and `BoundaryQualityGate` implementation.

| Gradle extension / Maven parameter | Measurement | Disabled value |
|---|---|---|
| `maxBoundaryScore` | Aggregate structural proxy (0–100) | negative / null in Core |
| `maxBoundaryUnresolvedCalls` | Unresolved (including dynamic) plus ambiguous calls | negative / null |
| `maxBoundaryUnresolvedRatio` | That count divided by all client calls (0–1) | negative / null |
| `maxBoundaryDynamicCalls` | Calls with a dynamic unresolved target | negative / null |
| `maxBoundaryEndpointFanOut` | Maximum distinct operations within one file and callable | negative / null |
| `maxBoundaryDependencySurfaceScore` | Imported runtime dependency surface score | negative / null |
| `maxBoundaryStateInterpretations` | Sum of callable-level backend-state interpretation signals | negative / null |
| `failOnHighSeverityBoundaryFindings` | Any high-severity boundary finding | `false` |

A maximum is inclusive. Nonfinite values and ratios greater than 1 are configuration errors.
Dynamic calls count in the unresolved total and in their separate metric. A callable-wide state
signal is counted once, even when the callable contains multiple client calls.

## Configuration

Gradle:

```groovy
aiKnowledge {
    maxBoundaryDynamicCalls = 0
    maxBoundaryEndpointFanOut = 3
}
```

The equivalent Gradle project properties are
`-PaiKnowledge.maxBoundaryDynamicCalls=0 -PaiKnowledge.maxBoundaryEndpointFanOut=3`.

Maven plugin configuration:

```xml
<configuration>
  <maxBoundaryDynamicCalls>0</maxBoundaryDynamicCalls>
  <maxBoundaryEndpointFanOut>3</maxBoundaryEndpointFanOut>
</configuration>
```

Maven also accepts `-DaiKnowledge.maxBoundaryDynamicCalls=0` and the corresponding names for every
parameter. Core callers attach `BoundaryGateOptions` with `ExtractionOptions.withBoundaryGates(...)`.
`withClasspathEntries(...)` preserves that policy.

## Interpreting results

`check.json.boundaryQualityGate` records the thresholds (null means disabled), all measured values,
applicability, confidence, scoring-model version and structured violations. The top-level `passed`
value includes boundary violations. `check.html` explains the same failures with the relevant calls,
files, operations, callable profiles, dependency relations and dimensions. Failed checks still write
both reports before throwing the build failure.

- **No client evidence:** all boundary gates are not applicable. This is not proof that the interface
  is good; the repository may be Java-only or the frontend may be outside provider coverage.
- **Frontend-only:** the gate remains applicable, with `confidence=frontend-only`; unresolved
  endpoints may simply reside outside this checkout. Calibrate unresolved-call limits accordingly.
- **Low-confidence extraction:** the configured policy still applies to observed facts, and raw
  provider confidence remains attached to each call. `extractionConfidence` distinguishes weak or
  syntactic extraction from the analysis's link-coverage `confidence`.

Start in report-only mode. On a repository intended to contain all frontend/backend contracts,
after reviewing dynamic findings, a zero-dynamic-call limit can enforce the project's explicit URL
policy. A fan-out limit of three is an example for teams that expect a narrow client adapter, not a
universal recommended score cutoff. Set aggregate, dependency and state thresholds only after
reviewing representative baselines; absent or intentionally dynamic contracts can be legitimate.

`metrics-snapshot.json` includes the stable `boundary*` measurements and
`boundaryScoringModelVersion`. Boundary deltas are computed only when both snapshots contain the
metric and use the same scoring-model version. Missing older values are not treated as historical
zeroes. See [the corpus and validation limits](boundary-corpus.md).

No Git traversal, commit-based change coupling or co-change input is used.
