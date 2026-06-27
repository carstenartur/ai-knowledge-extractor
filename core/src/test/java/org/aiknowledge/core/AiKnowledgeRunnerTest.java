package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiKnowledgeRunnerTest {
    @TempDir
    Path temp;

    @Test
    void generateWritesCoreArtifacts() throws Exception {
        Path project = temp.resolve("fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("src/test/java/example"));
        Files.createDirectories(project.resolve("docs"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/App.java"), "package example;\n\npublic class App {\n    public void run() {}\n}\n");
        Files.writeString(project.resolve("src/test/java/example/AppTest.java"), "package example;\n\nclass AppTest {\n    @org.junit.jupiter.api.Test\n    void run() {}\n}\n");
        Files.writeString(project.resolve("docs/design.md"), "# Design\n");

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        assertTrue(Files.isRegularFile(output.resolve("index.json")));
        assertTrue(Files.isRegularFile(output.resolve("modules.json")));
        assertTrue(Files.isRegularFile(output.resolve("classes.json")));
        assertTrue(Files.isRegularFile(output.resolve("tests.json")));
        assertTrue(Files.isRegularFile(output.resolve("docs.json")));
        assertTrue(Files.readString(output.resolve("index.json")).contains("schemaVersion"));
        assertTrue(Files.readString(output.resolve("classes.json")).contains("example.App"));
    }

    @Test
    void generateWritesEnrichedScannerMetadata() throws Exception {
        Path project = temp.resolve("scanner-fixture");
        Files.createDirectories(project.resolve("src/main/java/example/feature"));
        Files.createDirectories(project.resolve("src/test/java/example/feature"));
        Files.createDirectories(project.resolve("docs"));
        Files.writeString(project.resolve("build.gradle"), """
                plugins { id 'java' }
                implementation 'org.example:library:1.0'
                testImplementation project(':test-support')
                """);
        Files.writeString(project.resolve("src/main/java/example/feature/SearchStrategies.java"), """
                package example.feature;

                import example.shared.Helper;
                import java.util.List;

                public class SearchStrategies extends BaseSearch implements StrategyApi {
                    public void run() {}
                    protected String name() { return "search"; }
                }
                """);
        Files.writeString(project.resolve("src/test/java/example/feature/SearchStrategiesTest.java"), """
                package example.feature;

                import org.junit.jupiter.api.Tag;

                class SearchStrategiesTest {
                    @Tag("fast")
                    @org.junit.jupiter.api.Test
                    void run() {}
                }
                """);
        Files.writeString(project.resolve("docs/search-strategies.md"), "# Search Strategies\nSee [SearchStrategies](../src/main/java/example/feature/SearchStrategies.java).\n");

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String modules = Files.readString(output.resolve("modules.json"));
        String classes = Files.readString(output.resolve("classes.json"));
        String tests = Files.readString(output.resolve("tests.json"));
        String docs = Files.readString(output.resolve("docs.json"));
        String capabilities = Files.readString(output.resolve("capabilities.json"));
        String claims = Files.readString(output.resolve("claims.json"));

        assertTrue(modules.contains("sourceSets"));
        assertTrue(modules.contains("main/java"));
        assertTrue(modules.contains("externalDependencies"));
        assertTrue(modules.contains("projectDependencies"));
        assertTrue(classes.contains("kind"));
        assertTrue(classes.contains("imports"));
        assertTrue(classes.contains("superclass"));
        assertTrue(classes.contains("BaseSearch"));
        assertTrue(classes.contains("interfaces"));
        assertTrue(classes.contains("StrategyApi"));
        assertTrue(classes.contains("referencedProjectClasses"));
        assertTrue(classes.contains("example.shared.Helper"));
        assertTrue(tests.contains("testedClass"));
        assertTrue(tests.contains("example.feature.SearchStrategies"));
        assertTrue(tests.contains("tags"));
        assertTrue(tests.contains("fast"));
        assertTrue(docs.contains("links"));
        assertTrue(docs.contains("SearchStrategies"));
        assertTrue(capabilities.contains("search-strategies"));
        assertTrue(capabilities.contains("implemented"));
        assertTrue(claims.contains("implementedBy"));
        assertTrue(claims.contains("verifiedBy"));
        assertTrue(claims.contains("documentedBy"));
    }

    @Test
    void generateScansMavenBuildFiles() throws Exception {
        Path project = temp.resolve("maven-fixture");
        Files.createDirectories(project.resolve("src/main/java/example/maven"));
        Files.writeString(project.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>maven-fixture</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.example</groupId>
                      <artifactId>sample-lib</artifactId>
                      <version>1.2.3</version>
                      <scope>runtime</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);
        Files.writeString(project.resolve("src/main/java/example/maven/MavenType.java"), "package example.maven;\npublic class MavenType {}\n");

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String modules = Files.readString(output.resolve("modules.json"));
        String dependencies = Files.readString(output.resolve("dependencies.json"));
        assertTrue(modules.contains("\"buildSystem\":\"maven\""));
        assertTrue(dependencies.contains("org.example:sample-lib:1.2.3"));
        assertTrue(dependencies.contains("\"scope\":\"runtime\""));
    }

    @Test
    void generateIsDeterministicAcrossRepeatedRuns() throws Exception {
        Path project = temp.resolve("deterministic-fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\nimplementation 'org.example:library:1.0'\n");
        Files.writeString(project.resolve("src/main/java/example/App.java"), "package example;\npublic class App {}\n");

        Path first = project.resolve("build/ai-knowledge-one");
        Path second = project.resolve("build/ai-knowledge-two");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, first));
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, second));

        for (String artifact : new String[] {"index.json", "modules.json", "classes.json", "tests.json", "docs.json", "dependencies.json", "capabilities.json", "claims.json"}) {
            assertEquals(Files.readString(first.resolve(artifact)), Files.readString(second.resolve(artifact)), artifact + " should be deterministic");
        }
    }

    @Test
    void generateScopesModulePackagesAndHandlesMalformedTag() throws Exception {
        Path project = temp.resolve("multi-module-fixture");
        Files.createDirectories(project.resolve("api/src/main/java/api/pkg"));
        Files.createDirectories(project.resolve("api/src/test/java/api/pkg"));
        Files.createDirectories(project.resolve("service/src/main/java/service/pkg"));
        Files.writeString(project.resolve("api/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("service/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("api/src/main/java/api/pkg/ApiType.java"), "package api.pkg;\npublic class ApiType {}\n");
        Files.writeString(project.resolve("api/src/main/java/DefaultType.java"), "public class DefaultType {}\n");
        Files.writeString(project.resolve("api/src/test/java/api/pkg/ApiTypeTest.java"), """
                package api.pkg;

                class ApiTypeTest {
                    @Tag(
                    @org.junit.jupiter.api.Test
                    void run() {}
                }
                """);
        Files.writeString(project.resolve("service/src/main/java/service/pkg/ServiceType.java"), "package service.pkg;\npublic class ServiceType {}\n");

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String modules = Files.readString(output.resolve("modules.json"));
        String tests = Files.readString(output.resolve("tests.json"));
        String apiModule = objectContaining(modules, "\"path\":\"api\"");
        String serviceModule = objectContaining(modules, "\"path\":\"service\"");

        assertTrue(apiModule.contains("api.pkg"));
        assertFalse(apiModule.contains("service.pkg"));
        assertFalse(apiModule.contains("\"mainPackages\":[\"\"]"));
        assertTrue(serviceModule.contains("service.pkg"));
        assertFalse(serviceModule.contains("api.pkg"));
        assertTrue(tests.contains("ApiTypeTest"));
    }

    @Test
    void generateMergesSeedListsAdditivelyAndHandlesQuoteEdges() throws Exception {
        Path project = temp.resolve("seed-fixture");
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("ai-knowledge/capabilities.seed.yaml"), """
                - id: seeded-capability
                  status: partial
                  classes: ['example.App']
                - id: seeded-capability
                  classes: ['example.App', 'example.Other']
                - id: quote-edge
                  status: '
                """);
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: seeded-claim
                  implementedBy: ['example.App']
                - id: seeded-claim
                  implementedBy: ['example.App', 'example.Other']
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String capabilities = Files.readString(output.resolve("capabilities.json"));
        String claims = Files.readString(output.resolve("claims.json"));
        assertTrue(capabilities.contains("seeded-capability"));
        assertTrue(capabilities.contains("quote-edge"));
        assertEquals(1, occurrences(capabilities, "example.App"));
        assertTrue(capabilities.contains("example.Other"));
        assertTrue(claims.contains("seeded-claim"));
        assertEquals(1, occurrences(claims, "example.App"));
        assertTrue(claims.contains("example.Other"));
    }

    private static String objectContaining(String json, String marker) {
        int markerIndex = json.indexOf(marker);
        assertTrue(markerIndex >= 0, "Missing marker " + marker + " in " + json);
        int start = json.lastIndexOf('{', markerIndex);
        int end = json.indexOf('}', markerIndex);
        return json.substring(start, end + 1);
    }

    private static int occurrences(String text, String value) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }
}
