# Release checklist

## Before a release

1. Ensure CI is green on `main`.
2. Decide the release version, for example `0.1.0`.
3. Update `version` in `CITATION.cff` if needed.
4. Confirm `.zenodo.json` metadata is accurate.
5. Create a GitHub release tag, for example `v0.1.0`.

## Package publishing

The `Publish` workflow runs for created GitHub releases and publishes artifacts to GitHub Packages.

## Archive metadata

The repository contains `.zenodo.json` and `CITATION.cff` so release metadata and citation metadata can stay versioned with the code.

## Version alignment

Keep these values aligned for a release:

- Git tag, for example `v0.1.0`.
- Gradle release version, for example `-PreleaseVersion=0.1.0`.
- `version` in `CITATION.cff`.
