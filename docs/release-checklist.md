# 0.2.0 release checklist

## Code and contracts

- [ ] `gradle clean check` succeeds on the exact release commit.
- [ ] Gradle and Maven consumer fixtures run against locally published artifacts.
- [ ] The mixed Java/TypeScript integration test links at least one operation.
- [ ] Schema-v2 examples and artifact verifier agree.
- [ ] JavaScript/TypeScript remains marked experimental.
- [ ] No bootstrap, transfer archive or absolute runner path is published.
- [ ] No Git-history or co-change analysis is introduced.

## Publication

- [ ] `0.2.0` is consistent in Gradle, Maven site, `CITATION.cff`, Zenodo metadata and release notes.
- [ ] Core, Sources, Javadocs, Gradle plugin marker and Maven plugin are signed.
- [ ] Maven Central staging closes and the coordinates resolve from a clean consumer environment.
- [ ] Checksums are generated for release artifacts.
- [ ] GitHub tag and release point to the verified commit.
- [ ] Zenodo metadata describes JavaScript/TypeScript support as experimental.

## Post-publication smoke test

- [ ] Run the Gradle fixture without a composite build.
- [ ] Run the Maven fixture without `mavenLocal()`.
- [ ] Generate `source-units.json`, `boundaries.json` and `boundary-analysis.json` from the mixed example.
- [ ] Verify the published documentation names the real distribution channel.
