package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.aiknowledge.core.analysis.ComplexityModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrossLanguageComplexityParityTest {
    @TempDir
    Path temp;

    @Test
    void equivalentJavaAndTypeScriptControlFlowUsesTheSameModelAndScore() throws Exception {
        Files.createDirectories(temp.resolve("src/main/java/example"));
        Files.createDirectories(temp.resolve("web/src"));
        Files.writeString(temp.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(temp.resolve("web/package.json"), "{\"name\":\"web\"}\n");
        Files.writeString(temp.resolve("src/main/java/example/Classifier.java"), """
                package example;
                final class Classifier {
                    int classify(boolean first, boolean second) {
                        if (first && second) {
                            return 1;
                        }
                        return 0;
                    }
                }
                """);
        Files.writeString(temp.resolve("web/src/classifier.ts"), """
                export function classify(first: boolean, second: boolean) {
                    if (first && second) {
                        return 1;
                    }
                    return 0;
                }
                """);

        RepositorySnapshot snapshot = new KnowledgeExtractionPipeline().extract(
                ExtractionOptions.defaults(temp, temp.resolve("build/ai-knowledge")));

        Map<?, ?> java = complexityFact(snapshot, "java", "classify");
        Map<?, ?> typeScript = complexityFact(snapshot, "typescript", "classify");

        assertNotNull(java);
        assertNotNull(typeScript);
        assertEquals(ComplexityModel.ID, java.get("complexityModel"));
        assertEquals(ComplexityModel.ID, typeScript.get("complexityModel"));
        assertEquals(java.get("cyclomaticComplexity"), typeScript.get("cyclomaticComplexity"));
        assertEquals(java.get("cognitiveComplexity"), typeScript.get("cognitiveComplexity"));
    }

    private static Map<?, ?> complexityFact(
            RepositorySnapshot snapshot, String language, String callableName) {
        for (Object value : snapshot.symbols) {
            if (!(value instanceof Map<?, ?> fact)) continue;
            if (!language.equals(fact.get("language"))) continue;
            Object nameValue = fact.get("name");
            Object signatureValue = fact.get("signature");
            String name = nameValue == null ? "" : String.valueOf(nameValue);
            String signature = signatureValue == null ? "" : String.valueOf(signatureValue);
            if ((callableName.equals(name) || signature.contains(callableName + "("))
                    && fact.get("cyclomaticComplexity") instanceof Number) {
                return fact;
            }
        }
        return null;
    }
}
