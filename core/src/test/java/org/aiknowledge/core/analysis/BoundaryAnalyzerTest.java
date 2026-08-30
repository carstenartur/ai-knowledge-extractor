package org.aiknowledge.core.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.aiknowledge.core.RepositorySnapshot;
import org.junit.jupiter.api.Test;

class BoundaryAnalyzerTest {
    @Test
    void linksEquivalentTemplatesAcrossLanguages() {
        RepositorySnapshot snapshot = new RepositorySnapshot();
        snapshot.boundaries.add(boundary(
                "client", "client-call", "GET", "/api/users/${id}",
                "/api/users/{}", "loadUser", "typescript"));
        snapshot.boundaries.add(boundary(
                "server", "server-endpoint", "GET", "/api/users/{id}",
                "/api/users/{}", "UserController#get", "java"));

        Map<String, Object> analysis = BoundaryAnalyzer.analyze(snapshot);

        assertEquals(1, analysis.get("linkedCallCount"));
        assertEquals("linked", ((java.util.List<?>) analysis.get("links")).stream()
                .map(value -> (Map<?, ?>) value)
                .findFirst().orElseThrow().get("status"));
        assertEquals(Boolean.FALSE, analysis.get("versionControlHistoryUsed"));
        assertEquals(Boolean.FALSE, analysis.get("changeCouplingIncluded"));
    }

    @Test
    void reportsFrontendWorkflowOrchestration() {
        RepositorySnapshot snapshot = new RepositorySnapshot();
        for (int index = 0; index < 4; index++) {
            Map<String, Object> client = boundary(
                    "client-" + index,
                    "client-call",
                    "GET",
                    "/api/step/" + index,
                    "/api/step/" + index,
                    "runWorkflow",
                    "typescript");
            client.put("awaited", true);
            snapshot.boundaries.add(client);
        }

        Map<String, Object> analysis = BoundaryAnalyzer.analyze(snapshot);

        assertTrue(((Number) analysis.get("score")).intValue() > 0);
        assertTrue(((java.util.List<?>) analysis.get("findings")).stream()
                .map(value -> (Map<?, ?>) value)
                .anyMatch(value -> "FRONTEND_ORCHESTRATES_BACKEND_WORKFLOW"
                        .equals(value.get("code"))));
    }

    @Test
    void excludesTypeOnlyAndUnusedDevDependenciesFromRuntimeSurface() {
        RepositorySnapshot snapshot = new RepositorySnapshot();
        snapshot.dependencies.add(Map.of(
                "ecosystem", "npm", "scope", "dependencies", "artifact", "axios"));
        snapshot.dependencies.add(Map.of(
                "ecosystem", "npm", "scope", "devDependencies", "artifact", "typescript"));
        snapshot.relations.add(Map.of(
                "kind", "SOURCE_UNIT_IMPORTS_MODULE",
                "external", true,
                "runtime", true,
                "packageName", "axios",
                "sourceFile", "src/client.ts"));
        snapshot.relations.add(Map.of(
                "kind", "SOURCE_UNIT_IMPORTS_MODULE",
                "external", true,
                "runtime", false,
                "packageName", "typescript",
                "sourceFile", "src/client.ts"));
        snapshot.boundaries.add(boundary(
                "client", "client-call", "GET", "/api", "/api", "load", "typescript"));

        Map<String, Object> surface = FrontendDependencySurfaceAnalyzer.analyze(snapshot);

        assertEquals(java.util.List.of("axios"), surface.get("runtimeImportedPackages"));
        assertEquals(java.util.List.of("typescript"), surface.get("typeOnlyImportedPackages"));
        assertFalse(((java.util.List<?>) surface.get("runtimeImportsDeclaredOnlyAsDev"))
                .contains("typescript"));
    }

    private static Map<String, Object> boundary(
            String id,
            String kind,
            String method,
            String path,
            String normalized,
            String callable,
            String language) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("id", id);
        fact.put("kind", kind);
        fact.put("protocol", "http");
        fact.put("method", method);
        fact.put("path", path);
        fact.put("normalizedPath", normalized);
        fact.put("callable", callable);
        fact.put("language", language);
        fact.put("literalPath", true);
        fact.put("errorHandling", "none");
        fact.put("sourceFile", "src/client.ts");
        fact.put("client", "fetch");
        return fact;
    }
}
