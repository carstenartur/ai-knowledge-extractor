package org.aiknowledge.core.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.aiknowledge.core.sourcespi.SourceKnowledgeRequest;
import org.aiknowledge.core.sourcespi.SourceKnowledgeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaScriptTypeScriptKnowledgeProviderGoldenTest {
    @TempDir
    Path temp;

    @Test
    void acceptsModernTsxAndKeepsTypeOnlyImportsAsStructuralEvidence() throws Exception {
        SourceKnowledgeResult result = extract("web/src/UserView.tsx", """
                import type { User as UserDto } from './model';
                import React from 'react';

                export function UserView(user: UserDto) {
                  const label = user.profile?.label ?? 'unknown';
                  return <section>{label}</section>;
                }
                """);

        Map<String, Object> unit = result.sourceUnitFacts().get(0);
        assertEquals("typescript", unit.get("language"));
        assertTrue(((List<?>) unit.get("imports")).contains("./model"));
        assertTrue(((List<?>) unit.get("imports")).contains("react"));
        assertTrue(result.symbolFacts().stream().anyMatch(fact -> "UserView".equals(fact.get("name"))));
    }

    @Test
    void codeLikeCommentsDoNotCreatePhantomImportsAndMalformedFilesRemainInspectable() throws Exception {
        SourceKnowledgeResult result = extract("web/src/client.ts", """
                // import phantom from 'phantom-package';
                const example = "import ghost from 'ghost-package'";
                export async function load(id: string) {
                  return fetch(`/api/users/${id}`);
                }
                """);
        List<?> imports = (List<?>) result.sourceUnitFacts().get(0).get("imports");
        assertFalse(imports.contains("phantom-package"));
        assertFalse(imports.contains("ghost-package"));
        assertFalse(result.boundaryFacts().isEmpty());

        SourceKnowledgeResult malformed = extract("web/src/incomplete.ts", "export function broken( {\n");
        assertFalse(malformed.sourceUnitFacts().isEmpty());
    }

    @Test
    void dynamicBoundaryTargetsAreNeverReportedAsHighConfidenceLiteralContracts() throws Exception {
        SourceKnowledgeResult result = extract("web/src/dynamic.ts", """
                export async function load(path: string) {
                  return fetch(path);
                }
                """);
        for (Map<String, Object> boundary : result.boundaryFacts()) {
            assertFalse(Boolean.TRUE.equals(boundary.get("literalPath")));
            assertTrue("low".equals(boundary.get("confidence"))
                    || "<dynamic>".equals(boundary.get("normalizedPath")));
        }
    }

    private SourceKnowledgeResult extract(String relative, String source) throws Exception {
        Path file = temp.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return new JavaScriptTypeScriptKnowledgeProvider().extract(new SourceKnowledgeRequest(
                temp, file, relative, List.of(), Map.of(), Map.of()));
    }
}
