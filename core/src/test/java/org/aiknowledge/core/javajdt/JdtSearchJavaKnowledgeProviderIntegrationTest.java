package org.aiknowledge.core.javajdt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.aiknowledge.core.javaspi.JavaKnowledgeRequest;
import org.aiknowledge.core.javaspi.JavaKnowledgeResult;
import org.eclipse.core.runtime.Platform;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdtSearchJavaKnowledgeProviderIntegrationTest {
    @TempDir
    Path temp;

    @Test
    void searchModeReportsWarningOutsideWorkspaceRuntime() throws Exception {
        Assumptions.assumeFalse(Platform.isRunning(), "This assertion is for the plain JVM test runtime");
        Path root = temp.resolve("fallback-fixture");
        Path app = writeFixture(root);

        JavaKnowledgeResult result = new JdtSearchJavaKnowledgeProvider().extract(request(root, app));

        assertTrue(result.warnings().toString().contains("jdt-search-workspace-unavailable"));
        assertFalse(result.relationFacts().isEmpty());
    }

    @Test
    void searchModeCreatesWorkspaceProjectAndEmitsSearchEngineRelationsForMultiModuleFixture() throws Exception {
        Assumptions.assumeTrue(Platform.isRunning(), "Requires Eclipse workspace runtime");
        Path root = temp.resolve("multi-module-search-fixture");
        Path app = writeFixture(root);

        String previous = System.getProperty("aiknowledge.jdt.workspace.mode");
        try {
            System.setProperty("aiknowledge.jdt.workspace.mode", "create");
            JavaKnowledgeResult result = new JdtSearchJavaKnowledgeProvider().extract(request(root, app));
            assertTrue(result.relationFacts().stream().anyMatch(f -> {
                Map<?, ?> relation = (Map<?, ?>) f;
                return "jdt-search".equals(relation.get("provider"))
                        && "TYPE_REFERENCES_TYPE".equals(relation.get("kind"))
                        && "example.app.App".equals(relation.get("source"))
                        && "example.api.Service".equals(relation.get("target"))
                        && relation.containsKey("offset")
                        && relation.containsKey("length");
            }));
        } finally {
            if (previous == null) System.clearProperty("aiknowledge.jdt.workspace.mode");
            else System.setProperty("aiknowledge.jdt.workspace.mode", previous);
        }
    }

    @Test
    void forkedSearchModeWritesWorkerRequestAndFactsJson() throws Exception {
        Assumptions.assumeFalse(Platform.isRunning(), "This assertion verifies plain JVM forked worker path");
        Path root = temp.resolve("forked-worker-fixture");
        Path app = writeFixture(root);
        Path workspace = temp.resolve("jdt-worker-workspace");

        String previousExecutionMode = System.getProperty("aiknowledge.jdt.search.execution.mode");
        String previousWorkspaceDirectory = System.getProperty("aiknowledge.jdt.workspace.directory");
        String previousKeepWorkspace = System.getProperty("aiknowledge.jdt.workspace.keep");
        try {
            System.setProperty("aiknowledge.jdt.search.execution.mode", "forked");
            System.setProperty("aiknowledge.jdt.workspace.directory", workspace.toString());
            System.setProperty("aiknowledge.jdt.workspace.keep", "true");

            JavaKnowledgeResult result = new JdtSearchJavaKnowledgeProvider().extract(request(root, app));

            Path requestJson = workspace.resolve("jdt-search-request.json");
            Path factsJson = workspace.resolve("jdt-search-facts.json");
            assertTrue(Files.exists(requestJson));
            assertTrue(Files.exists(factsJson));
            assertTrue(Files.readString(requestJson).contains("\"sourcePath\":\"app/src/main/java/example/app/App.java\""));
            assertTrue(Files.readString(factsJson).contains("\"warnings\""));
            assertFalse(result.warnings().toString().contains("jdt-search-worker-failed"));
            assertTrue(result.relationFacts().stream().anyMatch(f -> {
                Map<?, ?> relation = (Map<?, ?>) f;
                return "jdt-search".equals(relation.get("provider"))
                        && relation.containsKey("accuracy")
                        && relation.containsKey("offset")
                        && relation.containsKey("length")
                        && relation.containsKey("sourceFile");
            }));
        } finally {
            restore("aiknowledge.jdt.search.execution.mode", previousExecutionMode);
            restore("aiknowledge.jdt.workspace.directory", previousWorkspaceDirectory);
            restore("aiknowledge.jdt.workspace.keep", previousKeepWorkspace);
        }
    }

    @Test
    void forkedSearchModeKeepsAstFactsWhenFallbackDisabled() throws Exception {
        Assumptions.assumeFalse(Platform.isRunning(), "This assertion verifies plain JVM forked worker path");
        Path root = temp.resolve("forked-worker-no-fallback-fixture");
        Path app = writeFixture(root);
        JavaKnowledgeRequest request = request(root, app);

        String previousFallback = System.getProperty("aiknowledge.jdt.search.fallback.to.ast");
        String cacheKey = cacheKey(request);
        try {
            System.setProperty("aiknowledge.jdt.search.fallback.to.ast", "false");
            searchIndexCache().put(cacheKey, successfulSearchIndex(request.sourcePath()));

            JavaKnowledgeResult result = new JdtSearchJavaKnowledgeProvider().extract(request);

            assertFalse(result.typeFacts().isEmpty());
            assertFalse(result.methodFacts().isEmpty());
            assertTrue(result.relationFacts().stream().anyMatch(f -> {
                Map<?, ?> relation = (Map<?, ?>) f;
                return "jdt-search".equals(relation.get("provider"));
            }));
        } finally {
            searchIndexCache().remove(cacheKey);
            restore("aiknowledge.jdt.search.fallback.to.ast", previousFallback);
        }
    }

    @Test
    void searchModeCanDisableAstFallbackOnFailure() throws Exception {
        Assumptions.assumeFalse(Platform.isRunning(), "This assertion verifies plain JVM failure behavior");
        Path root = temp.resolve("fallback-disabled-fixture");
        Path app = writeFixture(root);

        String previousFallback = System.getProperty("aiknowledge.jdt.search.fallback.to.ast");
        try {
            System.setProperty("aiknowledge.jdt.search.fallback.to.ast", "false");
            assertThrows(IOException.class, () -> new JdtSearchJavaKnowledgeProvider().extract(request(root, app)));
        } finally {
            restore("aiknowledge.jdt.search.fallback.to.ast", previousFallback);
        }
    }

    private static Path writeFixture(Path root) throws Exception {
        Files.createDirectories(root.resolve("api/src/main/java/example/api"));
        Files.createDirectories(root.resolve("impl/src/main/java/example/impl"));
        Files.createDirectories(root.resolve("app/src/main/java/example/app"));
        Files.writeString(root.resolve("api/src/main/java/example/api/Service.java"),
                "package example.api;\npublic interface Service { String name(); }\n");
        Files.writeString(root.resolve("impl/src/main/java/example/impl/ServiceImpl.java"),
                "package example.impl;\nimport example.api.Service;\npublic class ServiceImpl implements Service { public String name() { return nameValue(); } private String nameValue() { return \"impl\"; } }\n");
        Path app = root.resolve("app/src/main/java/example/app/App.java");
        Files.writeString(app,
                "package example.app;\n" +
                "import example.api.Service;\n" +
                "import example.impl.ServiceImpl;\n" +
                "public class App { private final Service service = new ServiceImpl(); public String run() { return service.name(); } }\n");
        return app;
    }

    private static JavaKnowledgeRequest request(Path root, Path sourceFile) {
        return new JavaKnowledgeRequest(
                root,
                sourceFile,
                root.relativize(sourceFile).toString().replace('\\', '/'),
                List.of(Map.of("name", "api", "path", "api"), Map.of("name", "impl", "path", "impl"), Map.of("name", "app", "path", "app")),
                List.of(root.resolve("api/src/main/java"), root.resolve("impl/src/main/java"), root.resolve("app/src/main/java")),
                List.of(),
                Map.of("buildSystem", "gradle"),
                List.of(),
                Map.of("javaProvider", "jdt", "jdtMode", "search", "jdtWorkspaceMode", "create"));
    }

    private static void restore(String key, String previousValue) {
        if (previousValue == null) System.clearProperty(key);
        else System.setProperty(key, previousValue);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> searchIndexCache() throws Exception {
        Field field = JdtSearchJavaKnowledgeProvider.class.getDeclaredField("SEARCH_INDEX_CACHE");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(null);
    }

    private static String cacheKey(JavaKnowledgeRequest request) throws Exception {
        Method method = JdtSearchJavaKnowledgeProvider.class.getDeclaredMethod("cacheKey", JavaKnowledgeRequest.class);
        method.setAccessible(true);
        return (String) method.invoke(null, request);
    }

    private static Object successfulSearchIndex(String sourcePath) throws Exception {
        Class<?> type = Class.forName("org.aiknowledge.core.javajdt.JdtSearchJavaKnowledgeProvider$SearchIndex");
        Constructor<?> constructor = type.getDeclaredConstructor(Map.class, List.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                Map.of(sourcePath, List.of(Map.of(
                        "kind", "TYPE_REFERENCES_TYPE",
                        "source", "example.app.App",
                        "target", "example.api.Service",
                        "sourceFile", sourcePath,
                        "offset", 0,
                        "length", 1,
                        "provider", "jdt-search",
                        "confidence", "search",
                        "accuracy", "A_ACCURATE"))),
                List.of(),
                false);
    }
}
