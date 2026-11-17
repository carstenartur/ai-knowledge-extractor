# Consuming AI Knowledge Extractor

Release [0.2.2](https://github.com/carstenartur/ai-knowledge-extractor/releases/tag/v0.2.2) provides downloadable JARs and checksums. Core, the Maven plugin and the Gradle plugin (including its marker) are available from [Maven Central](https://central.sonatype.com/artifact/io.github.carstenartur/ai-knowledge-maven-plugin/0.2.2). Follow [issue #100](https://github.com/carstenartur/ai-knowledge-extractor/issues/100) for Plugin Portal availability.

| Consumer | Coordinate |
| --- | --- |
| Core library | `io.github.carstenartur:ai-knowledge-core` |
| Maven plugin | `io.github.carstenartur:ai-knowledge-maven-plugin` |
| Gradle plugins DSL | `io.github.carstenartur.ai-knowledge-extractor` |
| Explicit Gradle implementation | `io.github.carstenartur:ai-knowledge-gradle-plugin` |

## Maven

Maven includes Central in its default repository and plugin repository settings. Pin the same published version wherever a build uses Core and the Maven plugin. Run the following command from the root of the project to analyse.

```bash
mvn io.github.carstenartur:ai-knowledge-maven-plugin:0.2.2:check
```

To call the extractor from Java, declare Core as a normal dependency.

```xml
<dependency>
  <groupId>io.github.carstenartur</groupId>
  <artifactId>ai-knowledge-core</artifactId>
  <version>0.2.2</version>
</dependency>
```

## Gradle

Place `mavenCentral()` in the plugin-management repositories in `settings.gradle`. This lets Gradle resolve the marker, implementation and Core dependency for `0.2.2` from Maven Central. Keep the Portal in the repository list for other plugins.

```groovy
// settings.gradle
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

```groovy
// build.gradle
plugins {
    id 'io.github.carstenartur.ai-knowledge-extractor' version '0.2.2'
}
```

Run `./gradlew aiKnowledgeCheck` to generate and verify the reports. See the [Gradle task guide](gradle-plugin.md) and the [Maven goal guide](maven-plugin.md).

## Upgrading from GitHub Packages

Releases up to and including 0.2.1 on the active line use the Maven group `org.aiknowledge` and Gradle plugin ID `org.aiknowledge.extractor`. For a Central release, change the group or plugin ID to the one in the table and update the version together. The Java package names, imports and `aiknowledge.*` configuration properties remain compatible. Consumers can remove the GitHub Packages repository and its credentials once all other dependencies are available elsewhere.

To reproduce an earlier release, use the [versioned GitHub Packages setup for 0.2.1](https://github.com/carstenartur/ai-knowledge-extractor/blob/v0.2.1/docs/publishing.md). It includes authenticated repository settings for both Maven and Gradle.

## Testing a development checkout

Gradle users can add `includeBuild('../ai-knowledge-extractor')` under `pluginManagement` in `settings.gradle`, then apply the plugin without a version. To test snapshots using Maven, run `./gradlew publishToMavenLocal` in the extractor checkout and set the consumer version to its `projectVersion` in `gradle.properties`.

Maintainers can find the release and channel verification steps in the [release process](release.md) and the [public distribution runbook](public-distribution.md).
