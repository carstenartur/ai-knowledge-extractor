package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(Files.readString(output.resolve("classes.json")).contains("example.App"));
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
