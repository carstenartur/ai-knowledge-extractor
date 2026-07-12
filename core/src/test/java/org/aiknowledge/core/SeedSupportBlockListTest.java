package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeedSupportBlockListTest {
    @TempDir
    Path temp;

    @Test
    void blockListSelectorsLinkFactsAndProduceAWorkingSetSample() throws Exception {
        Path project = temp.resolve("regelsuche-shaped-fixture");
        Files.createDirectories(project.resolve("search/src/main/java/de/regelsuche/search"));
        Files.createDirectories(project.resolve("search/src/test/java/de/regelsuche/search"));
        Files.createDirectories(project.resolve("docs"));
        Files.createDirectories(project.resolve("ai-knowledge"));

        Files.writeString(project.resolve("search/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(
                project.resolve("search/src/main/java/de/regelsuche/search/RewriteSearch.java"),
                """
                package de.regelsuche.search;

                public final class RewriteSearch {
                    public String search(String input) {
                        return input;
                    }
                }
                """);
        Files.writeString(
                project.resolve("search/src/test/java/de/regelsuche/search/RewriteSearchTest.java"),
                """
                package de.regelsuche.search;

                class RewriteSearchTest {
                    @org.junit.jupiter.api.Test
                    void search() {}
                }
                """);
        Files.writeString(project.resolve("docs/search.md"), "# Search\n");
        Files.writeString(project.resolve("ai-knowledge/capabilities.seed.yaml"), """
                - id: rewrite-search
                  label: Rewrite Search
                  modules:
                    - search
                  packages:
                    - de.regelsuche.search
                  typePatterns:
                    - '*Search*'
                    - '*Rewrite*'
                  testPatterns:
                    - '*Search*Test'
                  docPatterns:
                    - docs/search.md
                """);

        Path output = project.resolve("build/ai-knowledge");
        Map report = new AiKnowledgeRunner().analyze(ExtractionOptions.defaults(project, output));
        String capabilities = Files.readString(output.resolve("capabilities.json"));
        Map footprint = (Map) report.get("contextFootprint");

        assertTrue(capabilities.contains("de.regelsuche.search.RewriteSearch"));
        assertTrue(capabilities.contains("de.regelsuche.search.RewriteSearchTest"));
        assertTrue(capabilities.contains("docs/search.md"));
        assertTrue(capabilities.contains("implemented-and-tested"));
        assertEquals(1, footprint.get("capabilitySampleCount"));
        assertEquals("available", footprint.get("measurementStatus"));
        assertTrue(((Number) footprint.get("p90CapabilityWorkingSetTokens")).intValue() > 0);
        assertTrue(((Number) footprint.get("normalizedContextDebt")).doubleValue() < 100.0d);
    }
}
