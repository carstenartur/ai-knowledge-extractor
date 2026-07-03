package org.aiknowledge.core.javajdt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        JavaKnowledgeResult implResult = provider.extract(request(root, impl));
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

    @Test
    void fieldFactsArePopulatedWithDeclaringTypeAndMetadata() throws Exception {
        Path root = temp.resolve("field-fixture");
        Files.createDirectories(root.resolve("src/main/java/example"));

        Path app = root.resolve("src/main/java/example/App.java");
        Files.writeString(app,
                "package example;\n" +
                "public class App {\n" +
                "    private final String name;\n" +
                "    protected int count;\n" +
                "}\n");

        JdtJavaKnowledgeProvider provider = new JdtJavaKnowledgeProvider();
        JavaKnowledgeResult result = provider.extract(request(root, app));

        assertFalse(result.fieldFacts().isEmpty(), "fieldFacts must be non-empty");
        List<?> fieldFacts = result.fieldFacts();
        Map<?, ?> nameFact = fieldFacts.stream()
                .filter(f -> "name".equals(((Map<?, ?>) f).get("name")))
                .map(f -> (Map<?, ?>) f)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected field 'name' in fieldFacts"));
        assertEquals("example.App", nameFact.get("declaringType"));
        assertEquals("String", nameFact.get("fieldType"));
        assertTrue(nameFact.containsKey("sourceFile"), "fieldFact must include sourceFile");
        assertTrue(nameFact.containsKey("line"), "fieldFact must include line");
        assertEquals("jdt-ast", nameFact.get("provider"));
        assertEquals("syntactic", nameFact.get("confidence"));

        Map<?, ?> countFact = fieldFacts.stream()
                .filter(f -> "count".equals(((Map<?, ?>) f).get("name")))
                .map(f -> (Map<?, ?>) f)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected field 'count' in fieldFacts"));
        assertEquals("int", countFact.get("fieldType"));
        assertTrue(countFact.containsKey("modifiers"), "fieldFact must include modifiers");
    }

    @Test
    void relationFactsIncludeImplementsExtendsAndFieldHasType() throws Exception {
        Path root = temp.resolve("relation-fixture");
        Files.createDirectories(root.resolve("src/main/java/example"));

        Path serviceIface = root.resolve("src/main/java/example/Service.java");
        Path baseClass = root.resolve("src/main/java/example/Base.java");
        Path serviceImpl = root.resolve("src/main/java/example/ServiceImpl.java");

        Files.writeString(serviceIface, "package example;\npublic interface Service { void execute(); }\n");
        Files.writeString(baseClass, "package example;\npublic abstract class Base {}\n");
        Files.writeString(serviceImpl,
                "package example;\n" +
                "public class ServiceImpl extends Base implements Service {\n" +
                "    private final Service delegate;\n" +
                "    public void execute() {}\n" +
                "}\n");

        JdtJavaKnowledgeProvider provider = new JdtJavaKnowledgeProvider();
        JavaKnowledgeResult result = provider.extract(request(root, serviceImpl));

        assertFalse(result.relationFacts().isEmpty(), "relationFacts must be non-empty");
        List<?> relations = result.relationFacts();

        boolean hasImplements = relations.stream().anyMatch(f -> {
            Map<?, ?> m = (Map<?, ?>) f;
            return "TYPE_IMPLEMENTS_TYPE".equals(m.get("kind"))
                    && "example.ServiceImpl".equals(m.get("source"))
                    && String.valueOf(m.get("target")).contains("Service");
        });
        assertTrue(hasImplements, "relationFacts must contain TYPE_IMPLEMENTS_TYPE for interface");

        boolean hasExtends = relations.stream().anyMatch(f -> {
            Map<?, ?> m = (Map<?, ?>) f;
            return "TYPE_EXTENDS_TYPE".equals(m.get("kind"))
                    && "example.ServiceImpl".equals(m.get("source"))
                    && String.valueOf(m.get("target")).contains("Base");
        });
        assertTrue(hasExtends, "relationFacts must contain TYPE_EXTENDS_TYPE for superclass");

        boolean hasFieldType = relations.stream().anyMatch(f -> {
            Map<?, ?> m = (Map<?, ?>) f;
            return "FIELD_HAS_TYPE".equals(m.get("kind"))
                    && String.valueOf(m.get("source")).contains("delegate")
                    && String.valueOf(m.get("target")).contains("Service");
        });
        assertTrue(hasFieldType, "relationFacts must contain FIELD_HAS_TYPE for delegate field");

        boolean hasPackage = relations.stream().anyMatch(f -> {
            Map<?, ?> m = (Map<?, ?>) f;
            return "PACKAGE_CONTAINS_TYPE".equals(m.get("kind"))
                    && "example".equals(m.get("source"))
                    && "example.ServiceImpl".equals(m.get("target"));
        });
        assertTrue(hasPackage, "relationFacts must contain PACKAGE_CONTAINS_TYPE");

        // All relation facts must have provider and confidence
        for (Object factObj : relations) {
            Map<?, ?> m = (Map<?, ?>) factObj;
            assertTrue(m.containsKey("provider"), "relation must have provider");
            assertTrue(m.containsKey("confidence"), "relation must have confidence");
            assertTrue(m.containsKey("sourceFile"), "relation must have sourceFile");
        }
    }

    @Test
    void relationFactsIncludeTestReferencesProductionType() throws Exception {
        Path root = temp.resolve("test-ref-fixture");
        Files.createDirectories(root.resolve("src/main/java/example"));
        Files.createDirectories(root.resolve("src/test/java/example"));

        Path prodClass = root.resolve("src/main/java/example/Calculator.java");
        Path testClass = root.resolve("src/test/java/example/CalculatorTest.java");

        Files.writeString(prodClass, "package example;\npublic class Calculator { public int add(int a, int b) { return a + b; } }\n");
        Files.writeString(testClass, "package example;\nclass CalculatorTest { void testAdd() { new Calculator().add(1, 2); } }\n");

        JdtJavaKnowledgeProvider provider = new JdtJavaKnowledgeProvider();
        // Extract production class first to populate index
        provider.extract(request(root, prodClass));
        JavaKnowledgeResult testResult = provider.extract(request(root, testClass));

        List<?> relations = testResult.relationFacts();
        boolean hasTestRef = relations.stream().anyMatch(f -> {
            Map<?, ?> m = (Map<?, ?>) f;
            return "TEST_REFERENCES_PRODUCTION_TYPE".equals(m.get("kind"))
                    && "example.CalculatorTest".equals(m.get("source"))
                    && String.valueOf(m.get("target")).contains("Calculator");
        });
        assertTrue(hasTestRef, "relationFacts must contain TEST_REFERENCES_PRODUCTION_TYPE");
    }

    @Test
    void relationFactsIncludeMethodReturnAndParameterTypes() throws Exception {
        Path root = temp.resolve("method-type-fixture");
        Files.createDirectories(root.resolve("src/main/java/example"));

        Path service = root.resolve("src/main/java/example/Processor.java");
        Files.writeString(service,
                "package example;\n" +
                "import java.util.List;\n" +
                "public class Processor {\n" +
                "    public String process(List<String> items) { return null; }\n" +
                "}\n");

        JdtJavaKnowledgeProvider provider = new JdtJavaKnowledgeProvider();
        JavaKnowledgeResult result = provider.extract(request(root, service));

        List<?> relations = result.relationFacts();
        boolean hasReturnType = relations.stream().anyMatch(f -> {
            Map<?, ?> m = (Map<?, ?>) f;
            return "METHOD_RETURNS_TYPE".equals(m.get("kind"))
                    && String.valueOf(m.get("source")).contains("process")
                    && String.valueOf(m.get("target")).contains("String");
        });
        assertTrue(hasReturnType, "relationFacts must contain METHOD_RETURNS_TYPE");

        boolean hasParamType = relations.stream().anyMatch(f -> {
            Map<?, ?> m = (Map<?, ?>) f;
            return "METHOD_PARAMETER_HAS_TYPE".equals(m.get("kind"))
                    && String.valueOf(m.get("source")).contains("process");
        });
        assertTrue(hasParamType, "relationFacts must contain METHOD_PARAMETER_HAS_TYPE");
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
