# Context-footprint metrics

The context-footprint report estimates how much repository context an AI assistant
needs for a capability-centred task. It is deliberately separate from Java method
complexity.

## Identity sources

Capability working sets are resolved from these fields, in order:

1. `matchedTypes`, produced by `CapabilityLinker` from package and type selectors;
2. explicit `classes`, retained for seeded or fallback capabilities.

When both are present, class names are deduplicated. Names that are not present in
the extracted production-class index do not receive invented fallback tokens; they
are counted in `unresolvedCapabilityTypeReferences`.

## Size model

Production and test Java facts use observed `lineCount` multiplied by the configured
code-token proxy. Documentation uses its own line-weighted proxy. A capability
working set is the sum of its resolved production-type token estimates.

The normalized debt is derived from:

- the p90 capability working set as a share of total repository context;
- a bounded penalty when test and documentation evidence is weak.

Repository growth alone therefore does not increase the score when capability-local
working sets remain stable.

## `contextFootprint` schema v2

The object embedded in `complexity.json` and `check.json` contains:

- `schemaVersion`: `2`;
- `measurementStatus`: `available` or
  `unavailable-no-capability-working-set`;
- repository, production, test-evidence and documentation token estimates;
- production and total context lines;
- `medianCapabilityWorkingSetTokens` and `p90CapabilityWorkingSetTokens`;
- `p90RepositoryContextShare` and `evidenceToProductionRatio`;
- `normalizedContextDebt` and `contextEfficiencyScore`;
- `capabilitySampleCount`;
- `unresolvedCapabilityTypeReferences`;
- `capabilityWorkingSetSources`, separating linked, explicit, combined and
  unavailable capabilities;
- `workingSetIdentityFields`, currently `matchedTypes` and `classes`;
- `method`, currently `line-weighted-linked-capability-working-set-proxy`.

When no capability working set can be resolved, the report does not pretend that the
whole repository is one capability. It returns an explicit unavailable status,
zero samples and zero normalized debt. Consumers that require a meaningful context
gate should additionally require `measurementStatus=available` or at least one
capability sample.

## YAML seed dialect

The built-in seed reader supports a top-level YAML list of flat capability or claim
maps. Values may be scalars, inline lists or indented block lists:

```yaml
- id: rewrite-search
  modules:
    - regelsuche-search
  packages:
    - de.regelsuche.search
  typePatterns:
    - '*Search*'
    - '*Strategy*'
```

It is not intended to be a general YAML parser; nested maps and arbitrary YAML
features remain outside this deterministic seed dialect.
