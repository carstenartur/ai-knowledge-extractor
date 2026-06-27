package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().analyze(ExtractionOptions.defaults(project, output));

        String complexity = Files.readString(output.resolve("complexity.json"));
        assertTrue(complexity.contains("tiny-ci-profile"));
        assertTrue(complexity.contains("estimatedCompressedTokens"));
        assertTrue(complexity.contains("Profile tiny-ci-profile"));
        assertTrue(complexity.contains("maxCognitiveDebt"));
    }
}
