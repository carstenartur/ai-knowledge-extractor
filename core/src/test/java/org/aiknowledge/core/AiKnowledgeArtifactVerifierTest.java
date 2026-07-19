package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiKnowledgeArtifactVerifierTest {
    @TempDir
    Path temp;

    @Test
    void verifiesQualityGateAndCompleteLifecycleFromRealRunnerOutputs()
            throws Exception {
        Fixture fixture = fixture("complete");
        AiKnowledgeRunner runner = new AiKnowledgeRunner();

        runner.optimize(fixture.options());
        runner.benchmark(fixture.options());
        runner.check(fixture.options());

        AiKnowledgeArtifactVerifier verifier = new AiKnowledgeArtifactVerifier();
        var quality = verifier.verifyQualityGate(fixture.output());
        var complete = verifier.verifyCompleteLifecycle(fixture.output());

        assertTrue(quality.passed(), quality.errors().toString());
        assertTrue(complete.passed(), complete.errors().toString());
        assertTrue(Files.isRegularFile(fixture.output().resolve("check.json")));
        assertTrue(Files.readString(fixture.output().resolve("check.json"))
            .contains("\"passed\":true"));
    }

    @Test
    void qualityProfileDoesNotRequireOptimizationOrBenchmark() throws Exception {
        Fixture fixture = fixture("quality");
        new AiKnowledgeRunner().check(fixture.options());

        AiKnowledgeArtifactVerifier verifier = new AiKnowledgeArtifactVerifier();
        assertTrue(verifier.verifyQualityGate(fixture.output()).passed());
        assertFalse(verifier.verifyCompleteLifecycle(fixture.output()).passed());
    }

    @Test
    void rejectsDuplicateJsonFieldsAndIndexCountDrift() throws Exception {
        Fixture duplicateFixture = fixture("duplicate");
        new AiKnowledgeRunner().check(duplicateFixture.options());
        Path duplicateCheck = duplicateFixture.output().resolve("check.json");
        String check = Files.readString(duplicateCheck);
        Files.writeString(duplicateCheck,
            check.replaceFirst("\\{", "{\"passed\":true,"));

        AiKnowledgeArtifactVerifier verifier = new AiKnowledgeArtifactVerifier();
        var duplicate = verifier.verifyQualityGate(duplicateFixture.output());
        assertFalse(duplicate.passed());
        assertTrue(duplicate.errors().stream().anyMatch(error ->
            error.contains("duplicate object field")));

        Fixture countFixture = fixture("count-drift");
        new AiKnowledgeRunner().check(countFixture.options());
        Path index = countFixture.output().resolve("index.json");
        String indexJson = Files.readString(index);
        Files.writeString(index,
            indexJson.replace("\"classes\":1", "\"classes\":99"));

        var countDrift = verifier.verifyQualityGate(countFixture.output());
        assertFalse(countDrift.passed());
        assertTrue(countDrift.errors().stream().anyMatch(error ->
            error.contains("count mismatch for classes")));
    }

    @Test
    void rejectsMissingContextPackReferencedByIndex() throws Exception {
        Fixture fixture = fixture("missing-pack");
        new AiKnowledgeRunner().check(fixture.options());
        Files.delete(fixture.output().resolve("context-packs/search.json"));

        var report = new AiKnowledgeArtifactVerifier()
            .verifyQualityGate(fixture.output());

        assertFalse(report.passed());
        assertTrue(report.errors().stream().anyMatch(error ->
            error.contains("missing required artifact: context-packs/search.json")));
    }

    private Fixture fixture(String name) throws Exception {
        Path project = temp.resolve(name);
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("src/test/java/example"));
        Files.createDirectories(project.resolve("docs"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("build.gradle"),
            "plugins { id 'java' }\n");
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

        Path output = project.resolve("build/ai-knowledge");
        return new Fixture(output, ExtractionOptions.defaults(project, output));
    }

    private record Fixture(Path output, ExtractionOptions options) {
    }
}
