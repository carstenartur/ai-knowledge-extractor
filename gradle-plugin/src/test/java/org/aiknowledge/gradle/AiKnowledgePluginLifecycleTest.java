package org.aiknowledge.gradle;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiKnowledgePluginLifecycleTest {
    @TempDir
    Path project;

    @Test
    void aiKnowledgeCheckRunsCompleteVerifiedLifecycle() throws Exception {
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("src/test/java/example"));
        Files.createDirectories(project.resolve("docs"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("settings.gradle"),
            "rootProject.name = 'consumer-fixture'\n");
        Files.writeString(project.resolve("build.gradle"), """
            plugins {
                id 'java'
                id 'org.aiknowledge.extractor'
            }
            """);
        Files.writeString(project.resolve("src/main/java/example/SearchService.java"),
            "package example; public final class SearchService { public void search() {} }\n");
        Files.writeString(project.resolve("src/test/java/example/SearchServiceTest.java"),
            "package example; class SearchServiceTest { @org.junit.jupiter.api.Test void search() {} }\n");
        Files.writeString(project.resolve("docs/search.md"), "# Search\n");
        Files.writeString(project.resolve("ai-knowledge/capabilities.seed.yaml"), """
            - id: search
              label: Search
              packages: [example]
              typePatterns: ['*Search*']
              testPatterns: ['*Search*Test']
              docPatterns: ['docs/search.md']
            """);

        BuildResult result = GradleRunner.create()
            .withProjectDir(project.toFile())
            .withArguments("aiKnowledgeCheck", "--stacktrace", "--no-daemon")
            .withPluginClasspath()
            .build();

        assertEquals(SUCCESS, result.task(":generateAiKnowledgeIndex").getOutcome());
        assertEquals(SUCCESS, result.task(":analyzeAiComplexity").getOutcome());
        assertEquals(SUCCESS, result.task(":optimizeAiKnowledge").getOutcome());
        assertEquals(SUCCESS, result.task(":benchmarkAiKnowledge").getOutcome());
        assertEquals(SUCCESS, result.task(":checkAiKnowledgeIndex").getOutcome());
        assertEquals(SUCCESS, result.task(":verifyAiKnowledgeArtifacts").getOutcome());
        assertEquals(SUCCESS, result.task(":aiKnowledgeCheck").getOutcome());

        Path output = project.resolve("build/ai-knowledge");
        for (String file : new String[] {
                "index.json", "review-context.md", "complexity.json",
                "optimization.json", "benchmark.json", "check.json"}) {
            assertTrue(Files.isRegularFile(output.resolve(file)), file);
        }
        assertTrue(Files.readString(output.resolve("check.json"))
            .contains("\"passed\":true"));
    }
}
