package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelProfileSupportTest {
    @TempDir
    Path temp;

    @Test
    void analyzeLoadsUserDefinedModelProfiles() throws Exception {
        Path project = temp.resolve("profile-fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/ProfiledApp.java"), "package example;\npublic class ProfiledApp { public void run() {} }\n");
        Files.writeString(project.resolve("ai-knowledge/model-profiles.yaml"), """
                - id: tiny-ci-profile
                  practicalContextBudget: 1
                  hardContextLimit: 2
                  targetCompressionRatio: 1.0
                  compressionPreference: strict CI budget
                - id: invalid-profile
                  practicalContextBudget: -10
                  hardContextLimit: -20
                  targetCompressionRatio: 2.5
                  compressionPreference: invalid values
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().analyze(ExtractionOptions.defaults(project, output));

        String complexity = Files.readString(output.resolve("complexity.json"));
        assertTrue(complexity.contains("tiny-ci-profile"));
        assertTrue(complexity.contains("estimatedCompressedTokens"));
        assertTrue(complexity.contains("Profile tiny-ci-profile"));
        assertTrue(complexity.contains("maxCognitiveDebt"));
        assertTrue(complexity.contains("invalid-profile"));
        assertTrue(complexity.contains("Configured practical context budget must be positive"));
        assertTrue(complexity.contains("Configured target compression ratio is above 1"));
        assertTrue(complexity.contains("\"practicalContextBudget\":1"));
        assertTrue(complexity.contains("\"targetCompressionRatio\":1.0"));
        assertTrue(complexity.contains("\"modelProfileDirectory\":\"ai-knowledge\""));
    }

    @Test
    void benchmarkUsesProfileSpecificBudgetNames() throws Exception {
        Path project = temp.resolve("benchmark-profile-fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/ProfiledApp.java"), "package example;\npublic class ProfiledApp { public void run() {} }\n");

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().benchmark(ExtractionOptions.defaults(project, output));

        String benchmark = Files.readString(output.resolve("benchmark.json"));
        assertTrue(benchmark.contains("compressedFitsPracticalBudget"));
        assertTrue(benchmark.contains("compressedFitsHardLimit"));
        assertTrue(benchmark.contains("rawFitsPracticalBudget"));
        assertTrue(benchmark.contains("rawFitsHardLimit"));
        assertTrue(benchmark.contains("\"profile\":\"raw\""));
        assertTrue(benchmark.contains("\"profile\":\"compact\""));
        assertTrue(benchmark.contains("\"profile\":\"review\""));
        assertTrue(benchmark.contains("\"profile\":\"architecture\""));
        assertTrue(benchmark.contains("\"profile\":\"deep-research\""));
        assertTrue(benchmark.contains("\"budgetRisk\""));
        assertTrue(benchmark.contains("\"missingContextRisk\""));
        assertTrue(benchmark.contains("\"recommendedProfile\":\"review\""));
        assertTrue(benchmark.contains("\"empirical\":{\"enabled\":false"));
        assertTrue(benchmark.contains("\"fixtureFile\":\"ai-knowledge/benchmark-fixtures.yaml\""));
    }

    @Test
    void benchmarkCanRunEmpiricalFixturesWhenOptedInAndIsReproducible() throws Exception {
        Path project = temp.resolve("empirical-benchmark-fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/ProfiledApp.java"), "package example;\npublic class ProfiledApp { public void run() {} }\n");
        Files.writeString(project.resolve("ai-knowledge/benchmark-fixtures.yaml"), """
                - id: dedupe-case
                  profile: review
                  tokenUsage: 2500
                  latencyMs: 800
                  reviewQuality: 0.9
                  suggestions: [extract-service, extract-service, add-test]
                  existingFeatures: [search, index]
                  suggestedFeatures: [index]
                  taskSuccess: false
                - id: success-case
                  profile: architecture
                  tokenUsage: 1900
                  latencyMs: 600
                  reviewQuality: 0.7
                  suggestions: [add-doc]
                  existingFeatures: [auth]
                  suggestedFeatures: [auth]
                  taskSuccess: true
                """);

        Path first = project.resolve("build/ai-knowledge-one");
        Path second = project.resolve("build/ai-knowledge-two");
        ExtractionOptions optionsOne = new ExtractionOptions(
                project,
                first,
                project.resolve("ai-knowledge"),
                project.resolve("ai-knowledge"),
                false,
                100.0d,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                true,
                project.resolve("ai-knowledge/benchmark-fixtures.yaml"));
        ExtractionOptions optionsTwo = new ExtractionOptions(
                project,
                second,
                project.resolve("ai-knowledge"),
                project.resolve("ai-knowledge"),
                false,
                100.0d,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                true,
                project.resolve("ai-knowledge/benchmark-fixtures.yaml"));
        new AiKnowledgeRunner().benchmark(optionsOne);
        new AiKnowledgeRunner().benchmark(optionsTwo);

        String firstReport = Files.readString(first.resolve("benchmark.json"));
        String secondReport = Files.readString(second.resolve("benchmark.json"));
        assertEquals(firstReport, secondReport);
        assertTrue(firstReport.contains("\"empirical\":{\"enabled\":true"));
        assertTrue(firstReport.contains("\"fixtureCount\":2"));
        assertTrue(firstReport.contains("\"duplicateSuggestions\":1"));
        assertTrue(firstReport.contains("\"fixtureFile\":\"ai-knowledge/benchmark-fixtures.yaml\""));
        assertTrue(firstReport.contains("\"missedExistingFeatures\":[\"search\"]"));
        assertTrue(firstReport.contains("\"totalMissedExistingFeatures\":1"));
        assertTrue(firstReport.contains("\"taskSuccessRate\":0.5"));
    }
}
