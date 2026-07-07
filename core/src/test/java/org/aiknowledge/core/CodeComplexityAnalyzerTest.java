package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.aiknowledge.core.javaspi.JavaKnowledgeRequest;
import org.aiknowledge.core.javaspi.JavaKnowledgeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeComplexityAnalyzerTest {
    @TempDir
    Path temp;

    @Test
    void flatMethodHasBaseCyclomaticAndZeroCognitiveComplexity() throws Exception {
        JavaKnowledgeResult result = analyze("""
                package example;
                public class Sample {
                    public int answer() { return 42; }
                }
                """);

        Map<?, ?> method = firstMethod(result, "answer");
        assertEquals(1, method.get("cyclomaticComplexity"));
        assertEquals(0, method.get("cognitiveComplexity"));
        assertEquals("jdt-ast", method.get("complexityProvider"));
    }

    @Test
    void nestedIfIsCognitivelyMoreExpensiveThanGuardClauses() throws Exception {
        JavaKnowledgeResult nested = analyze("""
                package example;
                public class Sample {
                    public int nested(boolean a, boolean b) {
                        if (a) {
                            if (b) {
                                return 1;
                            }
                        }
                        return 0;
                    }
                }
                """);
        JavaKnowledgeResult guarded = analyze("""
                package example;
                public class Sample {
                    public int guarded(boolean a, boolean b) {
                        if (!a) return 0;
                        if (!b) return 0;
                        return 1;
                    }
                }
                """);

        Map<?, ?> nestedMethod = firstMethod(nested, "nested");
        Map<?, ?> guardedMethod = firstMethod(guarded, "guarded");

        assertEquals(nestedMethod.get("cyclomaticComplexity"), guardedMethod.get("cyclomaticComplexity"));
        assertTrue(number(nestedMethod, "cognitiveComplexity") > number(guardedMethod, "cognitiveComplexity"));
    }

    @Test
    void loopsSwitchCatchTernaryAndBooleanOperatorsContributeDecisionPoints() throws Exception {
        JavaKnowledgeResult result = analyze("""
                package example;
                public class Sample {
                    public int complex(int x, boolean a, boolean b) {
                        try {
                            for (int i = 0; i < x; i++) {
                                if (a && b) return i;
                            }
                            return switch (x) {
                                case 0 -> 0;
                                default -> a ? 1 : 2;
                            };
                        } catch (RuntimeException ex) {
                            return -1;
                        }
                    }
                }
                """);

        Map<?, ?> method = firstMethod(result, "complex");
        assertTrue(number(method, "cyclomaticComplexity") >= 7, "expected McCabe decision points");
        assertTrue(number(method, "cognitiveComplexity") >= 6, "expected cognitive decision and nesting cost");
        assertTrue(number(method, "maxNestingDepth") >= 2);
        assertTrue(method.containsKey("decisionPointsByKind"));
    }

    private JavaKnowledgeResult analyze(String source) throws Exception {
        Path root = temp.resolve("fixture-" + Math.abs(source.hashCode()));
        Path file = root.resolve("src/main/java/example/Sample.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        JavaKnowledgeRequest request = new JavaKnowledgeRequest(
                root,
                file,
                "src/main/java/example/Sample.java",
                List.of(Map.of("name", "fixture", "path", "")),
                List.of(root.resolve("src/main/java")),
                List.of(root.resolve("src/test/java")),
                Map.of("buildSystem", "gradle"),
                List.of(),
                Map.of("javaProvider", "basic"));
        return new CodeComplexityAnalyzer().enrich(request, JavaKnowledgeResult.empty());
    }

    private static Map<?, ?> firstMethod(JavaKnowledgeResult result, String name) {
        for (Object item : result.methodFacts()) {
            Map<?, ?> method = (Map<?, ?>) item;
            if (String.valueOf(method.get("signature")).contains(name + "(")) {
                return method;
            }
        }
        throw new AssertionError("Missing method " + name + " in " + result.methodFacts());
    }

    private static int number(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
