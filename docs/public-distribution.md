# Publishing to Maven Central and the Gradle Plugin Portal

A release from the active schema-v2 line publishes the Core library, Maven plugin and Gradle artifacts under `io.github.carstenartur`. The first candidate for these coordinates is 0.2.2. The [consumer guide](publishing.md) covers installation and migration from the 0.2.1 GitHub Packages release.

## Published artifacts

| Artifact | Maven coordinate (append the release version) |
| --- | --- |
| Core | `io.github.carstenartur:ai-knowledge-core` |
| Maven plugin | `io.github.carstenartur:ai-knowledge-maven-plugin` |
| Gradle implementation used by the marker | `io.github.carstenartur:gradle-plugin` |
| Explicit Gradle implementation coordinate | `io.github.carstenartur:ai-knowledge-gradle-plugin` |
| Gradle marker (POM) | `io.github.carstenartur.ai-knowledge-extractor:io.github.carstenartur.ai-knowledge-extractor.gradle.plugin` |

The marker POM points to the Gradle implementation, which depends on Core. Binary publications include source and Javadoc JARs. The rehearsal checks the dependency graph, signing, checksums and version in the packaged Maven plugin descriptor.

## Publisher setup

1. Verify ownership of `io.github.carstenartur` in the [Central Publisher Portal](https://central.sonatype.org/register/namespace/). Create a [publisher token](https://central.sonatype.org/publish/generate-portal-token/) for that namespace.
2. Make the signing key’s public part available as described in [Central’s signing requirements](https://central.sonatype.org/publish/requirements/gpg/).
3. Obtain [Gradle Plugin Portal API credentials](https://docs.gradle.org/current/userguide/publishing_gradle_plugins.html#account_setup) for the account publishing the new plugin ID, `io.github.carstenartur.ai-knowledge-extractor`.
4. Make the following secrets available to the GitHub Actions **public-distribution** environment. If the environment requires review, approve the deployment in the workflow run.

| Secret | Value | Previously supported name |
| --- | --- | --- |
| `CENTRAL_USERNAME` | Token username issued by Central | `MAVEN_CENTRAL_USERNAME` |
| `CENTRAL_PASSWORD` | Token password issued by Central | `MAVEN_CENTRAL_PASSWORD` |
| `MAVEN_GPG_KEY` | ASCII-armored private signing key | `MAVEN_SIGNING_KEY` |
| `MAVEN_GPG_PASSPHRASE` | Passphrase for the signing key | `MAVEN_SIGNING_PASSWORD` |
| `GRADLE_PUBLISH_KEY` | Plugin Portal API key | — |
| `GRADLE_PUBLISH_SECRET` | Plugin Portal API secret | — |

## From tag to anonymous consumers

Start a [release](release.md) from the supported branch. A successful active-line release starts **Public distribution**. The GitHub release and the next-snapshot PR use the existing branch policy.

1. **Qualify the tag.** The workflow checks the GitHub release, `SHA256SUMS`, release branch, supported series, source-parent CI and version metadata. A signed rehearsal builds into a disposable repository, then exercises the Gradle plugin, Maven plugin and Core API. The rehearsal’s `publicAvailabilityVerified` field is `false`.
2. **Publish to Central.** The Maven Release Plugin’s `release:perform` goal uses [the release POM](../release/pom.xml) to build the qualified SCM tag. The coordinator delegates to Gradle for the signed publications and waits for Central’s `PUBLISHED` state. A separate Central-only run exercises the marker, Maven plugin and Core without Plugin Portal access. The `anonymous-central-consumers` artifact contains the summary and output for installations that use Central directly.
3. **Publish to the Plugin Portal.** The workflow checks Central for the implementation and Core, validates the Gradle plugin with the Portal and submits it. A new plugin ID may require approval from the Portal before it can resolve there.
4. **Verify the public repositories.** The workflow checks that the exact version is listed in the Portal’s plugin catalog, since its Maven endpoint can redirect a missing marker to Central. The anonymous Gradle fixture resolves the marker from the Portal and the implementation from Central. The Maven and Core fixtures resolve from Central. All run with fresh caches, and check the generated output on mixed Java/TypeScript source. The resulting `anonymous-public-consumers` artifact identifies the version and records `publicAvailabilityVerified=true` when all three succeed.

For the first public release, attach the successful run and its report to [issue #100](https://github.com/carstenartur/ai-knowledge-extractor/issues/100) and record the verified versions in the consumer documentation.

## Recovering an interrupted publication

Open [Actions → Public distribution](https://github.com/carstenartur/ai-knowledge-extractor/actions/workflows/public-distribution.yml), select `main`, and use the same stable `X.Y.Z` as the GitHub release.

| Mode | When to run it |
| --- | --- |
| `validate` | Repeat qualification and the signed rehearsal. |
| `publish-all` | Publish and verify both channels in sequence. The active-line Release workflow selects this mode automatically. |
| `publish-central` | Publish the qualified tag to Central. After an interrupted upload, check the deployment’s state in the Central Portal first. |
| `publish-portal` | Complete a missing Portal publication after the Central artifacts are visible. |
| `verify-public` | Repeat anonymous verification after propagation or Portal approval. |

When the deployment already shows `PUBLISHED`, continue with the missing channel or verification. Readers can use the [Central marker repository setup](publishing.md#gradle) as soon as the Central artifacts are available. Correct a released binary by publishing a new patch version.
