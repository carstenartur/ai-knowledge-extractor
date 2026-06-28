package org.aiknowledge.core.javajdt;

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

class JdtJavaKnowledgeProviderTest {
    @TempDir
    Path temp;

    @Test
    void extractsTypesImplementationsAndReferencesFromFixture() throws Exception {
        Path root = temp.resolve("fixture");
        Files.createDirectories(root.resolve("src/main/java/example/api"));
        Files.createDirectories(root.resolve("src/main/java/example/impl"));
        Files.createDirectories(root.resolve("src/main/java/example/app"));
        Files.createDirectories(root.resolve("src/test/java/example/app"));

        Path service = root.resolve("src/main/java/example/api/Service.java");
        Path impl = root.resolve("src/main/java/example/impl/ServiceImpl.java");
        Path app = root.resolve("src/main/java/example/app/App.java");
        Path appTest = root.resolve("src/test/java/example/app/AppTest.java");

        Files.writeString(service, "package example.api;\npublic interface Service { void run(); }\n");
        Files.writeString(impl, "package example.impl;\nimport example.api.Service;\npublic class ServiceImpl implements Service { public void run() {} }\n");
        Files.writeString(app, "package example.app;\nimport example.api.Service;\nimport example.impl.ServiceImpl;\npublic class App { private final Service service = new ServiceImpl(); }\n");
        Files.writeString(appTest, "package example.app;\nimport org.junit.jupiter.api.Tag;\nclass AppTest { @Tag(\"fast\") @org.junit.jupiter.api.Test void run() { new App(); } }\n");

        JdtJavaKnowledgeProvider provider = new JdtJavaKnowledgeProvider();
        JavaKnowledgeResult serviceResult = provider.extract(request(root, service));
        JavaKnowledgeResult appResult = provider.extract(request(root, app));
        JavaKnowledgeResult testResult = provider.extract(request(root, appTest));

        Map<?, ?> serviceClass = (Map<?, ?>) serviceResult.classFacts().get(0);
        Map<?, ?> serviceType = (Map<?, ?>) serviceResult.typeFacts().get(0);
        Map<?, ?> appClass = (Map<?, ?>) appResult.classFacts().get(0);
        Map<?, ?> appTestFact = (Map<?, ?>) testResult.testFacts().get(0);

        assertEquals("interface", serviceType.get("kind"));
        assertTrue(String.valueOf(serviceClass.get("implementations")).contains("example.impl.ServiceImpl"));
        assertTrue(String.valueOf(appClass.get("referencedProjectClasses")).contains("example.api.Service"));
        assertTrue(String.valueOf(appClass.get("referencedProjectClasses")).contains("example.impl.ServiceImpl"));
        assertEquals("example.app.App", String.valueOf(appTestFact.get("testedClass")));
        assertTrue(String.valueOf(appTestFact.get("referencedProductionTypes")).contains("example.app.App"));
        assertTrue(testResult.warnings().toString().contains("jdt-classpath-incomplete"));
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
                Map.of("javaProvider", "jdt"));
    }
}
