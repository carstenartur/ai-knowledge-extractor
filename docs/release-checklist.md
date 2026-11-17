# Release checklist

The [release process](release.md) describes the workflow inputs and the reviewed release request. Public distribution applies to the active schema-v2 line. Version 0.2.2 was the first release published to Maven Central under `io.github.carstenartur`.

## Before tagging

- [ ] Confirm the selected branch, release version and next snapshot against [the support policy](version-support.md).
- [ ] Check CI on the source commit, including the build, consumer fixtures and signed publication rehearsal.
- [ ] Review the [changelog](../CHANGELOG.md) and [consumer migration instructions](publishing.md). Check the signing key and Central credentials in the [public distribution setup](public-distribution.md). Plugin Portal credentials are needed only for an explicitly requested Portal publication.
- [ ] Run the Release workflow or submit a release request with `dry_run=true` or `dryRun: true`, respectively. Check the release-version build and artifact checksums.

## After publication

- [ ] Run the real release for the qualified candidate. Check the stable tag, GitHub release assets and `SHA256SUMS`.
- [ ] Verify that the signed Central deployment reached `PUBLISHED` and that the Core, Maven plugin, Gradle implementation and marker are available at the selected version.
- [ ] Preserve the successful **anonymous-central-consumers** report from empty-cache Gradle, Maven and Core consumers.
- [ ] **Optional when publishing to the Gradle Plugin Portal:** confirm the exact Portal listing/approval and preserve **anonymous-public-consumers** with `publicAvailabilityVerified=true`.
- [ ] Record the exact version and run/report link with the release. For the first public release, attach the evidence to [issue #100](https://github.com/carstenartur/ai-knowledge-extractor/issues/100).
- [ ] Verify the next-snapshot PR on the original branch.
