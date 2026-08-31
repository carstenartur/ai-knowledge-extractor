# 0.2.0 release checklist

## Code and contracts

- [x] `gradle clean check` succeeds on the exact release-candidate source tree.
- [x] Gradle and Maven consumer fixtures run against locally published artifacts.
- [x] The mixed Java/TypeScript integration test links at least one operation.
- [x] Schema-v2 examples and artifact verifier agree.
- [x] JavaScript/TypeScript remains marked experimental.
- [x] No bootstrap, transfer archive or absolute runner path is published.
- [x] No Git-history or co-change analysis is introduced.
- [x] CI and Pages use the same warning-free Maven Site contract.

## Publication

- [ ] `0.2.0` is consistent in Gradle, Maven Site, `CITATION.cff`, Zenodo metadata and release notes.
- [ ] Core, Sources, Javadocs, the Gradle plugin marker and the Maven plugin resolve from GitHub Packages.
- [ ] The GitHub Release contains all built JARs and a `SHA256SUMS` manifest.
- [ ] GitHub tag and release point to the verified release commit.
- [ ] Zenodo metadata describes JavaScript/TypeScript support as experimental.
- [ ] The release page and documentation state that Maven Central and the Gradle Plugin Portal are not 0.2.0 channels.

## Post-publication smoke test

- [ ] Run the Gradle fixture from an empty Gradle user home, without a composite build or `mavenLocal()`.
- [ ] Run the Maven fixture from an empty Maven local repository, without `mavenLocal()`.
- [ ] Resolve all artifacts from authenticated GitHub Packages coordinates.
- [ ] Generate `source-units.json`, `boundaries.json` and `boundary-analysis.json` from a mixed Java/TypeScript example.
- [ ] Verify the published documentation names GitHub Packages as the real distribution channel.

The checkboxes in the publication and post-publication sections are evidence requirements, not
claims made in advance. They are completed only after the real workflow and remote consumer test
have succeeded.
