package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrendAnalyzerTest {
    @TempDir
    Path temp;

    @Test
    void analyzeWritesTrendAndSnapshotReports() throws Exception {
        Path project = project("trend-fixture");
        Path output = project.resolve("build/ai-knowledge");

        new AiKnowledgeRunner().analyze(ExtractionOptions.defaults(project, output));

        assertTrue(Files.isRegularFile(output.resolve("metrics-snapshot.json")));
        assertTrue(Files.isRegularFile(output.resolve("trend.json")));
        assertTrue(Files.isRegularFile(output.resolve("trend.html")));
        String trend = Files.readString(output.resolve("trend.json"));
        assertTrue(trend.contains("baselinePresent"));
        assertTrue(trend.contains("No complexity baseline found"));
    }

    @Test
    void checkFailsWhenTrendThresholdIsExceeded() throws Exception {
        Path project = project("trend-gate-fixture");
        Files.writeString(project.resolve("ai-knowledge/complexity-baseline.json"), """
                {
                  "schemaVersion": 1,
                  "estimatedContextTokens": 0,
                  "conceptRadius": 1,
                  "aiCognitiveDebt": 0.0
                }
                """);
        Path output = project.resolve("build/ai-knowledge");
        ExtractionOptions options = new ExtractionOptions(
                project,
                output,
                project.resolve("ai-knowledge"),
                project.resolve("ai-knowledge"),
                false,
                100.0d,
                0.0d,
                Double.MAX_VALUE,
                Double.MAX_VALUE);

        assertThrows(IOException.class, () -> new AiKnowledgeRunner().check(options));

        String check = Files.readString(output.resolve("check.json"));
        assertTrue(check.contains("trendViolationCount"));
        assertTrue(check.contains("\"passed\":false"));
        String trend = Files.readString(output.resolve("trend.json"));
        assertTrue(trend.contains("aiCognitiveDebt"));
        assertTrue(trend.contains("AI cognitive debt increased"));
    }

    private Path project(String name) throws Exception {
        Path project = temp.resolve(name);
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/App.java"), "package example;\npublic class App { public void run() {} }\n");
        return project;
    }
}
