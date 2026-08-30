package org.aiknowledge.core.javahttp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.aiknowledge.core.sourcespi.SourceKnowledgeRequest;
import org.aiknowledge.core.sourcespi.SourceKnowledgeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaHttpBoundaryKnowledgeProviderTest {
    @TempDir
    Path temp;

    @Test
    void extractsSpringEndpointTemplates() throws Exception {
        Path file = temp.resolve("src/main/java/example/UserController.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                package example;
                import org.springframework.web.bind.annotation.*;
                @RestController
                @RequestMapping("/api/users")
                class UserController {
                    @GetMapping("/{id}") Object get(@PathVariable String id) { return null; }
                    @PostMapping Object create() { return null; }
                }
                """);

        SourceKnowledgeResult result = new JavaHttpBoundaryKnowledgeProvider().extract(
                new SourceKnowledgeRequest(temp, file,
                        "src/main/java/example/UserController.java",
                        List.of(), Map.of(), Map.of()));

        assertEquals(2, result.boundaryFacts().size());
        assertTrue(result.boundaryFacts().stream().anyMatch(fact ->
                "GET".equals(fact.get("method"))
                        && "/api/users/{}".equals(fact.get("normalizedPath"))));
        assertTrue(result.boundaryFacts().stream().anyMatch(fact ->
                "POST".equals(fact.get("method"))
                        && "/api/users".equals(fact.get("normalizedPath"))));
        assertEquals(2, result.relationFacts().size());
    }
}
