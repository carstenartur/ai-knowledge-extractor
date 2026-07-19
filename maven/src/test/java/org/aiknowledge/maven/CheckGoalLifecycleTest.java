package org.aiknowledge.maven;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckGoalLifecycleTest {
    @TempDir
    Path temp;

    @Test
    void checkGoalProducesAndVerifiesCompleteLifecycle() throws Exception {
        Path project = temp.resolve("fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("src/test/java/example"));
        Files.createDirectories(project.resolve("docs"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("pom.xml"), """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>example</groupId>
              <artifactId>fixture</artifactId>
              <version>1.0.0</version>
            </project>
            """);
        Files.writeString(project.resolve("src/main/java/example/SearchService.java"),
            "package example; public final class SearchService { public void search() {} }\n");
        Files.writeString(project.resolve("src/test/java/example/SearchServiceTest.java"),
            "package example; class SearchServiceTest { void search() {} }\n");
        Files.writeString(project.resolve("docs/search.md"), "# Search\n");
        Files.writeString(project.resolve("ai-knowledge/capabilities.seed.yaml"), """
            - id: search
              label: Search
              packages: [example]
              typePatterns: ['*Search*']
              testPatterns: ['*Search*Test']
              docPatterns: ['docs/search.md']
            """);

        Path output = project.resolve("target/ai-knowledge");
        CheckGoal goal = configuredGoal(project, output);
        goal.execute();

        for (String file : new String[] {
                "index.json", "review-context.md", "complexity.json",
                "optimization.json", "benchmark.json", "check.json"}) {
            assertTrue(Files.isRegularFile(output.resolve(file)), file);
        }
        assertTrue(Files.readString(output.resolve("check.json"))
            .contains("\"passed\":true"));
    }

    private static CheckGoal configuredGoal(Path project, Path output) {
        CheckGoal goal = new CheckGoal();
        goal.basedir = project.toFile();
        goal.outputDirectory = output.toFile();
        goal.seedDirectory = project.resolve("ai-knowledge").toFile();
        goal.modelProfileDirectory = project.resolve("ai-knowledge").toFile();
        goal.failOnWarnings = false;
        goal.maxCognitiveDebt = 100.0d;
        goal.maxCognitiveDebtIncrease = Double.MAX_VALUE;
        goal.maxConceptRadiusIncrease = Double.MAX_VALUE;
        goal.maxContextTokenIncrease = Double.MAX_VALUE;
        goal.empiricalBenchmarkEnabled = false;
        goal.empiricalBenchmarkFixtureFile = null;
        goal.requireCapabilityEvidence = false;
        goal.requireClaimVerification = false;
        goal.minContextPackCount = 0;
        goal.maxContextPackTokens = Integer.MAX_VALUE;
        goal.maxMethodCognitiveComplexity = Integer.MAX_VALUE;
        goal.maxMethodCyclomaticComplexity = Integer.MAX_VALUE;
        goal.maxAverageMethodCognitiveComplexity = Double.MAX_VALUE;
        goal.maxAverageMethodCyclomaticComplexity = Double.MAX_VALUE;
        goal.maxMethodsAboveCognitiveThreshold = Integer.MAX_VALUE;
        goal.maxMethodsAboveCyclomaticThreshold = Integer.MAX_VALUE;
        goal.javaProvider = "basic";
        goal.jdtMode = "ast";
        goal.jdtSearchExecutionMode = "forked";
        goal.jdtSearchFallbackToAst = true;
        goal.jdtWorkspaceMode = "off";
        goal.jdtWorkspaceDirectory = null;
        goal.keepJdtWorkspace = false;
        goal.compileClasspathElements = List.of();
        return goal;
    }
}
