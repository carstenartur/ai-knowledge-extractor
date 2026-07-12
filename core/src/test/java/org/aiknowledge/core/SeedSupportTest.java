package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.aiknowledge.core.linker.CapabilityLinker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeedSupportTest {
    @TempDir
    Path temp;

    @Test
    void yamlBlockSequencesRemainListsAndLinkCapabilityFacts() throws Exception {
        Path project = temp.resolve("fixture");
        Path seedDirectory = project.resolve("ai-knowledge");
        Files.createDirectories(seedDirectory);
        Files.writeString(seedDirectory.resolve("capabilities.seed.yaml"), """
                - id: rewrite-search
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
                    - docs/*.md
                """);

        RepositorySnapshot snapshot = new RepositorySnapshot();
        snapshot.modules.add(Map.of("name", "search"));
        snapshot.classes.add(Map.of(
                "class", "de.regelsuche.search.RewriteSearch",
                "package", "de.regelsuche.search",
                "lineCount", 20));
        snapshot.tests.add(Map.of(
                "testClass", "de.regelsuche.search.RewriteSearchTest",
                "package", "de.regelsuche.search",
                "lineCount", 10));
        snapshot.docs.add(Map.of("path", "docs/search.md", "lineCount", 5));

        SeedSupport.mergeSeeds(
                ExtractionOptions.defaults(project, project.resolve("build/ai-knowledge")),
                snapshot);
        new CapabilityLinker().link(snapshot);

        assertEquals(1, snapshot.capabilities.size(),
                "nested list items must not become phantom top-level capability maps");
        Map capability = (Map) snapshot.capabilities.getFirst();
        assertEquals(List.of("search"), capability.get("modules"));
        assertEquals(List.of("de.regelsuche.search"), capability.get("packages"));
        assertEquals(List.of("*Search*", "*Rewrite*"), capability.get("typePatterns"));
        assertEquals(List.of("*Search*Test"), capability.get("testPatterns"));
        assertEquals(List.of("docs/*.md"), capability.get("docPatterns"));
        assertEquals(List.of("search"), capability.get("matchedModules"));
        assertEquals(List.of("de.regelsuche.search.RewriteSearch"), capability.get("matchedTypes"));
        assertEquals(List.of("de.regelsuche.search.RewriteSearchTest"), capability.get("matchedTests"));
        assertEquals(List.of("docs/search.md"), capability.get("matchedDocs"));
        assertEquals("implemented-and-tested", capability.get("status"));
    }
}
