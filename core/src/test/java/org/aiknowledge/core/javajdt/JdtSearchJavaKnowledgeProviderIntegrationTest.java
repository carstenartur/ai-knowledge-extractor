package org.aiknowledge.core.javajdt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
