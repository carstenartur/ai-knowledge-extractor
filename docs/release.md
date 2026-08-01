# Release checklist

## Before a release

1. Ensure CI is green on `main`.
2. Confirm the next release version in `release.properties`, for example `next.release.version=0.1.0`.
3. Confirm development metadata uses the matching snapshot version:
   - `gradle.properties`: `projectVersion=0.1.0-SNAPSHOT`
   - `CITATION.cff`: `version: "0.1.0-SNAPSHOT"`
   - `.zenodo.json`: `"version": "0.1.0-SNAPSHOT"`
   - `maven/src/main/resources/META-INF/maven/plugin.xml`: `<version>0.1.0-SNAPSHOT</version>`
   - `site/pom.xml`: `<revision>0.1.0-SNAPSHOT</revision>`
   - Maven consumer example properties: `<aiKnowledge.version>0.1.0-SNAPSHOT</aiKnowledge.version>`
4. Review the user-facing docs:
   - README badges and quick start are current.
   - [`gradle-plugin.md`](gradle-plugin.md) and [`maven-plugin.md`](maven-plugin.md) still match the released plugin behavior.
   - GitHub Pages documentation site at <https://carstenartur.github.io/ai-knowledge-extractor/> reflects the release.
5. Confirm consumer coverage before publishing:
   - Gradle consumer fixture runs `generateAiKnowledgeIndex`, `analyzeAiComplexity`, `optimizeAiKnowledge`, `benchmarkAiKnowledge`, `checkAiKnowledgeIndex`, and `publishAiKnowledgeIndex`.
   - Maven consumer fixture runs `generate`, `analyze`, `optimize`, `benchmark`, `check`, and `help`.
   - At least one released-version consumer path resolves artifacts from GitHub Packages, not only via a local composite build.
   - Verify that the `Pages` workflow ran successfully after tagging and that <https://carstenartur.github.io/ai-knowledge-extractor/> shows the new version.
6. Prepare release notes.
7. Run the `Release` workflow once with `dry_run=true` from `main`.
8. Run the real `Release` workflow from `main`.

## Workflow inputs

The normal release dialog does not accept version strings. The release version is derived from the repository-owned `projectVersion=X.Y.Z-SNAPSHOT` value and must match `next.release.version=X.Y.Z`.

- `next_version_increment`: typed choice `patch`, `minor`, or `major` for the development line after the release.
- `skip_tests`: build release artifacts without running tests.
- `dry_run`: validate metadata and build artifacts without pushing refs, publishing packages, creating a GitHub release, or opening a follow-up PR.

Examples for a repository at `0.1.0-SNAPSHOT`:

| Choice | Release | Next development version |
|---|---:|---:|
| `patch` | `0.1.0` | `0.1.1-SNAPSHOT` |
| `minor` | `0.1.0` | `0.2.0-SNAPSHOT` |
| `major` | `0.1.0` | `1.0.0-SNAPSHOT` |

Exact non-standard version transitions belong in a reviewed repository change, not in an ad-hoc Actions text field.

## Release workflow behavior

The workflow checks out authoritative `main`, derives the release from current snapshot metadata and verifies that `release.properties` agrees. It then:

1. updates `gradle.properties`, Maven plugin descriptor metadata, Maven site revision, Maven consumer examples, `CITATION.cff`, and `.zenodo.json` to the release version,
2. adds release-only date metadata to `CITATION.cff` and `.zenodo.json`,
3. builds and verifies the Gradle project,
4. creates a release branch named `release/vX.Y.Z`,
5. creates an annotated tag named `vX.Y.Z`,
6. publishes the Gradle artifacts to GitHub Packages,
7. creates and publishes a GitHub Release with generated notes and jar assets,
8. calculates the next snapshot from the selected increment and updates it on a `release/prepare-next-X.Y.Z-SNAPSHOT` branch,
9. opens or updates a PR for the next development iteration.

The separate `Prepare next development version` workflow follows the same rule: it derives the released version from repository state and accepts only the typed increment choice.

## Metadata states

Development state:

- `gradle.properties`, Maven plugin descriptor metadata, Maven site revision, Maven consumer examples, `CITATION.cff`, and `.zenodo.json` all use an `X.Y.Z-SNAPSHOT` version.
- `release.properties` uses the corresponding `X.Y.Z` release version without `-SNAPSHOT`.
- `.zenodo.json` does not contain `publication_date`.
- `CITATION.cff` does not contain `date-released`.

Release state:

- `gradle.properties`, Maven plugin descriptor metadata, Maven site revision, Maven consumer examples, `CITATION.cff`, and `.zenodo.json` all use the release version `X.Y.Z`.
- `.zenodo.json` contains `publication_date`.
- `CITATION.cff` contains `date-released`.

Do not create release tags manually for the normal process; let the `Release` workflow create the release branch, tag, package publication, GitHub Release, and follow-up PR.
