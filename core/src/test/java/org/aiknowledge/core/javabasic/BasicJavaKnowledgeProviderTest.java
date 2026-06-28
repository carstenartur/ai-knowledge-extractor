package org.aiknowledge.core.javabasic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.aiknowledge.core.javaspi.JavaKnowledgeRequest;
import org.aiknowledge.core.javaspi.JavaKnowledgeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BasicJavaKnowledgeProviderTest {
    @TempDir
    Path temp;

    @Test
    void extractsLegacyClassAndTestArtifactsAndStructuredFacts() throws Exception {
        Path root = temp.resolve("fixture");
        Files.createDirectories(root.resolve("src/main/java/example"));
        Files.createDirectories(root.resolve("src/test/java/example"));
        Path mainFile = root.resolve("src/main/java/example/App.java");
        Path testFile = root.resolve("src/test/java/example/AppTest.java");
        Files.writeString(mainFile, "package example;\nimport example.shared.Helper;\npublic class App { public void run() {} }\n");
        Files.writeString(testFile, "package example;\nclass AppTest { @org.junit.jupiter.api.Test void run() {} }\n");

        BasicJavaKnowledgeProvider provider = new BasicJavaKnowledgeProvider();
        JavaKnowledgeResult main = provider.extract(request(root, mainFile));
        JavaKnowledgeResult test = provider.extract(request(root, testFile));

        Map classFact = (Map) main.classFacts().get(0);
        Map testFact = (Map) test.testFacts().get(0);
        assertEquals("example.App", classFact.get("class"));
        assertEquals("src/main/java/example/App.java", classFact.get("sourceFile"));
        assertEquals("example.AppTest", testFact.get("testClass"));
        assertEquals("src/test/java/example/AppTest.java", testFact.get("sourceFile"));
        assertTrue(main.typeFacts().toString().contains("example.App"));
        assertTrue(main.methodFacts().toString().contains("public void run"));
        List signatures = (List) main.methodFacts().stream()
                .map(methodFact -> ((Map) methodFact).get("signature"))
                .collect(Collectors.toList());
        assertEquals(List.of("public void run"), signatures);
        assertTrue(main.packageFacts().toString().contains("example"));
        assertTrue(main.referenceFacts().toString().contains("example.shared.Helper"));
        assertTrue(main.warnings().toString().contains("heuristic-line-parser"));
    }

    private static JavaKnowledgeRequest request(Path root, Path file) {
        return new JavaKnowledgeRequest(
                root,
                file,
                root.relativize(file).toString().replace('\\', '/'),
                List.of(Map.of("name", "fixture", "path", "")),
                List.of(root.resolve("src/main/java")),
                List.of(root.resolve("src/test/java")),
                Map.of("buildSystem", "gradle"),
                List.of(),
                Map.of());
    }
}
