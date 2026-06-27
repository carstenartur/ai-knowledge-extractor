package org.aiknowledge.core;

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
}
