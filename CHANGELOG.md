# Changelog

All notable changes are documented here. The project follows semantic versioning while below 1.0;
minor releases may add new artifact families but must document schema changes.

## Unreleased

### Added

- opt-in signed Maven Central / Gradle Plugin Portal publishing, a protected release workflow,
  publication metadata validation and clean anonymous consumer verification;
- a CI rehearsal using disposable signing keys; real public availability still requires account
  setup and successful publication to both services;

- opt-in boundary policies with equivalent Gradle/Maven parameters, failure evidence and HTML reports;
- stable boundary trend measurements and an independently versioned, inspectable scoring model;
- a labelled eleven-scenario corpus with extraction precision/recall, ranking and weight-sensitivity reports;

- a concrete schema-v1 to v2 migration guide and baseline regeneration checklist;
- independent mixed-language consumers with retained-artifact and binary provider validation;
- explicit Gradle provider dependencies, caller-owned Core loading, and fact-contract versioning;
- deterministic duplicate-provider and incompatible-contract rejection before writing artifacts;

- machine-readable policy for simultaneously supported active, maintenance and end-of-life release
  lines;
- branch/version validation in CI and release workflows;
- exact-head verification and automatic merge of generated post-release metadata PRs;
- documented compatibility, backport and end-of-life rules for supported version lines.

### Fixed

- quoted JavaScript API-call examples no longer create phantom boundary calls; executable nested
  template expressions remain visible;
- module-level calls in different files no longer combine into false callable fan-out.

### Changed

- release and prepare-next workflows operate on the selected configured branch instead of assuming
  `main`;
- automated next-development transitions stay in the same X.Y series; starting a new minor or major
  line requires an explicit reviewed policy change;
- maintenance releases cannot replace the active line as GitHub “Latest”.

## 0.2.0 – 2026-08-31

### Added

- language-neutral source provider SPI;
- experimental JavaScript/TypeScript structural extraction;
- Java HTTP endpoint extraction and cross-language boundary linking;
- explainable boundary and dependency-surface reports;
- schema-v2 artifacts;
- shared source admission, provider and error-policy configuration;
- mixed Java/TypeScript integration and golden tests;
- integrator, provider and schema contracts;
- published-marker Gradle and Maven consumer verification;
- deterministic release-readiness, Maven Site and POSIX-locale Javadoc checks.

### Explicitly not included

- Git commit history;
- co-change matrices;
- commit-derived change coupling.

### Distribution

Version 0.2.0 is released through GitHub Releases and GitHub Packages. Maven Central and the Gradle
Plugin Portal are not publication channels for this release.
