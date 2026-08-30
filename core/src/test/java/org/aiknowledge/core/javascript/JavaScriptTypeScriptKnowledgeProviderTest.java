package org.aiknowledge.core.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.aiknowledge.core.analysis.BoundaryPath;
import org.aiknowledge.core.analysis.ComplexityModel;
import org.aiknowledge.core.sourcespi.SourceKnowledgeRequest;
import org.aiknowledge.core.sourcespi.SourceKnowledgeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaScriptTypeScriptKnowledgeProviderTest {
    @TempDir
    Path temp;

    @Test
    void extractsImportsCallablesComplexityAndBoundaryCalls() throws Exception {
        Path file = temp.resolve("web/src/user-client.ts");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                import axios from 'axios';
                import type { UserDto } from './types';

                export async function loadUser(id: string) {
                  try {
                    const response = await fetch(`/api/users/${id}`);
                    if (!response.ok || response.status === 404) {
                      throw new Error('missing');
                    }
                    const dto: UserDto = await response.json();
                    return { displayName: dto.name };
                  } catch (error) {
                    return { displayName: 'unknown' };
                  }
                }

                export const saveUser = async (user: UserDto) => {
                  return axios.post('/api/users', user);
                };
                """);

        SourceKnowledgeResult result = new JavaScriptTypeScriptKnowledgeProvider().extract(
                new SourceKnowledgeRequest(
                        temp,
                        file,
                        "web/src/user-client.ts",
                        List.of(Map.of("name", "web", "path", "web")),
                        Map.of(),
                        Map.of()));

        Map<String, Object> unit = result.sourceUnitFacts().get(0);
        assertEquals("typescript", unit.get("language"));
        assertEquals("user-client", unit.get("simpleName"));
        assertEquals(List.of("./types", "axios"), unit.get("imports"));
        assertEquals(List.of("axios"), unit.get("runtimeImports"));
        assertEquals(List.of("./types"), unit.get("typeOnlyImports"));

        assertEquals(2, result.boundaryFacts().size());
        assertTrue(result.boundaryFacts().stream().anyMatch(fact ->
                "GET".equals(fact.get("method"))
                        && "/api/users/{}".equals(fact.get("normalizedPath"))));
        assertTrue(result.boundaryFacts().stream().anyMatch(fact ->
                "POST".equals(fact.get("method"))
                        && "/api/users".equals(fact.get("normalizedPath"))));
        assertTrue(result.symbolFacts().stream().anyMatch(fact ->
                ComplexityModel.ID.equals(fact.get("complexityModel"))
                        && ((Number) fact.get("cyclomaticComplexity")).intValue() >= 3));
        assertTrue(result.relationFacts().stream().anyMatch(fact ->
                Boolean.TRUE.equals(fact.get("typeOnly"))
                        && "./types".equals(fact.get("target"))));
    }

    @Test
    void preservesDynamicBoundaryEvidenceWithoutGuessingATarget() throws Exception {
        Path file = temp.resolve("src/client.js");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "export function load(url) { return fetch(url); }");

        SourceKnowledgeResult result = new JavaScriptTypeScriptKnowledgeProvider().extract(
                new SourceKnowledgeRequest(temp, file, "src/client.js", List.of(), Map.of(), Map.of()));

        Map<String, Object> boundary = result.boundaryFacts().get(0);
        assertEquals(BoundaryPath.DYNAMIC, boundary.get("normalizedPath"));
        assertEquals("dynamic", boundary.get("pathExpressionKind"));
        assertFalse((Boolean) boundary.get("literalPath"));
        assertEquals("low", boundary.get("confidence"));
    }
}
