package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.aiknowledge.core.analysis.BoundaryAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MixedJavaWebRepositoryIntegrationTest {
    @TempDir
    Path temp;

    @Test
    void linksATypeScriptClientToAJavaEndpointWithoutUsingVersionControlHistory() throws Exception {
        Path project = temp.resolve("mixed-java-web");
        Files.createDirectories(project.resolve("web/src"));
        Files.createDirectories(project.resolve("backend/src/architecture/java/example"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("web/package.json"), "{\"name\":\"example-web\",\"dependencies\":{\"axios\":\"1.12.0\"}}\n");
        Files.writeString(project.resolve("web/src/users.ts"), """
                export async function loadUser(id: string) {
                  return fetch(`/api/users/${id}`);
                }
                """);
        Files.writeString(project.resolve("backend/src/architecture/java/example/UserController.java"), """
                package example;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;
                @RestController
                @RequestMapping("/api/users")
                class UserController {
                    @GetMapping("/{id}") Object get() { return null; }
                }
                """);

        RepositorySnapshot snapshot = new KnowledgeExtractionPipeline().extract(
                ExtractionOptions.defaults(project, project.resolve("build/ai-knowledge")));
        Map<String, Object> boundary = BoundaryAnalyzer.analyze(snapshot);

        assertTrue(((Number) boundary.get("clientCallCount")).intValue() >= 1);
        assertTrue(((Number) boundary.get("serverEndpointCount")).intValue() >= 1);
        assertTrue(((Number) boundary.get("linkedCallCount")).intValue() >= 1);
        assertEquals(Boolean.FALSE, boundary.get("versionControlHistoryUsed"));
    }
}
