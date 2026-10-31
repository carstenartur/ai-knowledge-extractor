# 0.2.1 release checklist

Prepared 2026-10-31 from `160e19cbb405df49782b8c2738d99b74b66bdd4c`; unchecked items are pending evidence.
Historical [0.2.0 release notes](releases/0.2.0.md) remain unchanged.

## Candidate qualification

- [ ] Normal CI passes on the exact preparation/merge commit, including line/version/readiness checks.
- [ ] `AI_KNOWLEDGE_TARGET_BRANCH=main bash .github/scripts/verify-release-readiness.sh` passes with the supported toolchain.
- [ ] Gradle and Maven consumer fixtures validate locally built artifacts and provider contracts.
- [ ] Signed-distribution rehearsal and clean-consumer checks pass; retain diagnostics.
- [ ] Review [0.2.1 notes](releases/0.2.1.md), schema migration and experimental JavaScript/TypeScript limitations.
- [ ] Development metadata consistently uses `0.2.1-SNAPSHOT`, with `next.release.version=0.2.1`.

## Dry run and publication

- [ ] Existing **Release** runs from `main` with `dry_run=true`, `skip_tests=false`, and `next_development_version=0.2.2-SNAPSHOT`.
- [ ] Inspect exact-source readiness, release-version artifacts and `SHA256SUMS` before a separate publication decision.
- [ ] Let the existing workflow own stable metadata, release dates, tags and package coordinates.
- [ ] Core, Sources, Javadocs, Gradle marker and Maven plugin resolve from authenticated GitHub Packages.
- [ ] GitHub Release contains built JARs and `SHA256SUMS`; tag and release identify the verified commit.
- [ ] Verify the workflow-owned next-development transition to `0.2.2-SNAPSHOT`.

## Remote consumers

- [ ] Gradle fixture passes from an empty Gradle user home without composite builds or local artifacts.
- [ ] Maven fixture passes from an empty Maven local repository against the remote release.
- [ ] Claim public channels only after optional real Maven Central / Gradle Plugin Portal publication and anonymous consumer checks.

This preparation assigns no publication date and does not invoke a publishing request.

## Preparation evidence

- Recorded source CI [41198475713](https://github.com/carstenartur/ai-knowledge-extractor/actions/runs/41198475713) passed on `160e19cbb405df49782b8c2738d99b74b66bdd4c`; this is source evidence, not a result for the new preparation PR.
- The source metadata already targets `0.2.1-SNAPSHOT` / release `0.2.1`; no version bump is needed in this documentation PR.
- Existing relative documentation links, Markdown structure and whitespace were checked against the source tree.
- No local full Java/Gradle/Maven build or release dry run was performed during this preparation. The new PR checks and later release-version rehearsal remain separate gates.
