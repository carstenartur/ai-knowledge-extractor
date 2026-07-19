# Publishing and consuming artifacts

AI Knowledge Extractor publishes three project-oriented artifacts:

| Module | Artifact ID | Purpose |
| --- | --- | --- |
| `core` | `org.aiknowledge:ai-knowledge-core` | Shared deterministic scanner and JSON artifact generation. |
| `gradle-plugin` | `org.aiknowledge:ai-knowledge-gradle-plugin` | Gradle task entry points for build-integrated extraction. |
| `maven` | `org.aiknowledge:ai-knowledge-maven-plugin` | Maven goal entry points for build-integrated extraction. |

The Gradle publication also contains the marker artifact for plugin id
`org.aiknowledge.extractor`. That marker is what makes normal versioned plugin
DSL consumption possible:

```groovy
plugins {
    id 'org.aiknowledge.extractor' version '<release-version>'
}
```

The normal release flow is documented in [`release.md`](release.md). It
publishes all Gradle publications to GitHub Packages and creates a GitHub
Release with the built jar artifacts.

The generated Maven plugin reference documentation (goal overview, parameter
tables, project coordinates) is published to
[GitHub Pages](https://carstenartur.github.io/ai-knowledge-extractor/) on every
push to `main` and on release tags via the `Pages` workflow.

Canonical plugin usage references:

- Gradle plugin: [`gradle-plugin.md`](gradle-plugin.md)
- Maven plugin: [`maven-plugin.md`](maven-plugin.md)

## Local development consumption

For local development, prefer using the repository directly instead of a
published package.

### Gradle composite build

A Gradle consumer can include a local checkout as a composite build:

```groovy
// settings.gradle
pluginManagement {
    includeBuild('../ai-knowledge-extractor')
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Then apply the plugin in the consuming build:

```groovy
plugins {
    id 'org.aiknowledge.extractor'
}
```

The task family is:

- `generateAiKnowledgeIndex`
- `analyzeAiComplexity`
- `optimizeAiKnowledge`
- `benchmarkAiKnowledge`
- `checkAiKnowledgeIndex`
- `publishAiKnowledgeIndex`

Composite builds are a local development workflow, not a released-artifact
distribution channel.

### Maven local repository and marker smoke test

Publish every artifact, including the Gradle plugin marker, to the local Maven
repository:

```bash
gradle publishToMavenLocal
```

The CI fixture then executes the same consumer twice:

1. through `pluginManagement.includeBuild(...)` for source-development mode;
2. through `mavenLocal()` and an explicit plugin version, with no included
   build, to prove that the published marker and implementation dependencies are
   complete.

The second run is the release-consumer contract. A build that only works through
`includeBuild` but lacks a usable marker artifact fails CI before release.

## GitHub Packages consumption

Released artifacts are published to GitHub Packages under:

```text
https://maven.pkg.github.com/carstenartur/ai-knowledge-extractor
```

GitHub Packages requires authentication even for many public package
consumption scenarios. Use a token with package read access and keep credentials
outside source control.

### Gradle plugin repository configuration

Plugin resolution belongs under `pluginManagement.repositories` in
`settings.gradle`:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            url = uri('https://maven.pkg.github.com/carstenartur/ai-knowledge-extractor')
            credentials {
                username = findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR')
                password = findProperty('gpr.key') ?: System.getenv('GITHUB_TOKEN')
            }
        }
    }
}
```

The consuming project can then use the normal plugin DSL:

```groovy
plugins {
    id 'org.aiknowledge.extractor' version '<release-version>'
}
```

GitHub Packages is the canonical released-artifact consumption path for both
Gradle and Maven. A local composite build should be an explicit development
override rather than an automatic replacement for a released version.

### Maven repository configuration

A Maven consumer can add the GitHub Packages repository either in `pom.xml` or
in user-level Maven settings. Prefer user-level settings for credentials.

```xml
<pluginRepositories>
  <pluginRepository>
    <id>github-ai-knowledge-extractor</id>
    <url>https://maven.pkg.github.com/carstenartur/ai-knowledge-extractor</url>
  </pluginRepository>
</pluginRepositories>

<repositories>
  <repository>
    <id>github-ai-knowledge-extractor</id>
    <url>https://maven.pkg.github.com/carstenartur/ai-knowledge-extractor</url>
  </repository>
</repositories>
```

The matching credentials belong in `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github-ai-knowledge-extractor</id>
      <username>${env.GITHUB_ACTOR}</username>
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>
```

## Versioning

Development versions use `X.Y.Z-SNAPSHOT` in `gradle.properties`,
`CITATION.cff` and `.zenodo.json`.

Release versions use `X.Y.Z` and are produced by the `Release` workflow.
Consumers should depend on released versions rather than snapshot versions
unless they are explicitly testing an unreleased checkout.

## Distribution channels at a glance

- Local composite build: development-only, source checkout required, fastest feedback for plugin contributors.
- GitHub Packages: released binary artifacts and Gradle plugin markers for normal consumers.
- Gradle Plugin Portal: not yet part of the release flow; track separately when unauthenticated public plugin distribution is added.
