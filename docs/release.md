# Release checklist

## Before a release

1. Ensure CI is green on `main`.
2. Confirm the next release version in `release.properties`, for example `next.release.version=0.1.0`.
3. Confirm development metadata uses the matching snapshot version:
   - `gradle.properties`: `projectVersion=0.1.0-SNAPSHOT`
   - `CITATION.cff`: `version: "0.1.0-SNAPSHOT"`
   - `.zenodo.json`: `"version": "0.1.0-SNAPSHOT"`
4. Review the user-facing docs:
   - README badges and quick start are current.
   - [`gradle-plugin.md`](gradle-plugin.md) and [`maven-plugin.md`](maven-plugin.md) still match the released plugin behavior.
5. Confirm consumer coverage before publishing:
   - Gradle consumer fixture runs `generateAiKnowledgeIndex`, `analyzeAiComplexity`, `optimizeAiKnowledge`, `benchmarkAiKnowledge`, `checkAiKnowledgeIndex`, and `publishAiKnowledgeIndex`.
   - Maven consumer fixture runs `generate`, `analyze`, `optimize`, `benchmark`, `check`, and `help`.
   - At least one released-version consumer path resolves artifacts from GitHub Packages, not only via a local composite build.
6. Prepare release notes.
7. Run the `Release` workflow once with `dry_run=true` from `main`.
8. Run the real `Release` workflow from `main`.

## Workflow inputs

- `release_version`: required release version without leading `v`, for example `0.1.0`.
- `next_development_version`: optional next snapshot version, for example `0.1.1-SNAPSHOT`. If omitted, the patch version is incremented automatically.
- `skip_tests`: build release artifacts without running tests.
- `dry_run`: validate metadata and build artifacts without pushing refs, publishing packages, creating a GitHub release, or opening a follow-up PR.

## Release workflow behavior

The workflow validates that the requested release matches the current snapshot metadata. It then:

1. updates `gradle.properties`, `CITATION.cff`, and `.zenodo.json` to the release version,
2. adds release-only date metadata to `CITATION.cff` and `.zenodo.json`,
3. builds and verifies the Gradle project,
4. creates a release branch named `release/vX.Y.Z`,
5. creates an annotated tag named `vX.Y.Z`,
6. publishes the Gradle artifacts to GitHub Packages,
7. creates and publishes a GitHub Release with generated notes and jar assets,
8. bumps the repository back to the next snapshot version on a `release/prepare-next-X.Y.Z-SNAPSHOT` branch,
9. opens or updates a PR for the next development iteration.

## Metadata states

Development state:

- `gradle.properties`, `CITATION.cff`, and `.zenodo.json` all use an `X.Y.Z-SNAPSHOT` version.
- `.zenodo.json` does not contain `publication_date`.
- `CITATION.cff` does not contain `date-released`.

Release state:

- `gradle.properties`, `CITATION.cff`, and `.zenodo.json` all use the release version `X.Y.Z`.
- `.zenodo.json` contains `publication_date`.
- `CITATION.cff` contains `date-released`.

Do not create release tags manually for the normal process; let the `Release` workflow create the release branch, tag, package publication, GitHub Release, and follow-up PR.
