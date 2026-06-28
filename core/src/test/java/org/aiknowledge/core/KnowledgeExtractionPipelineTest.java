package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.aiknowledge.core.javaspi.JavaKnowledgeRequest;
import org.aiknowledge.core.javaspi.JavaKnowledgeProvider;
import org.aiknowledge.core.javaspi.JavaKnowledgeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnowledgeExtractionPipelineTest {
    @TempDir
    Path temp;

    @Test
    void usesConfiguredJavaProviderViaConstructorInjection() throws Exception {
        Path project = temp.resolve("spi-fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/App.java"), "package example;\npublic class App {}\n");

        JavaKnowledgeProvider provider = new JavaKnowledgeProvider() {
            @Override
            public JavaKnowledgeResult extract(JavaKnowledgeRequest request) {
                Map classData = new LinkedHashMap();
                classData.put("class", "custom.ProviderType");
                classData.put("sourceFile", request.sourcePath());
                Map typeFact = new LinkedHashMap();
                typeFact.put("name", "custom.ProviderType");
                Map methodFact = new LinkedHashMap();
                methodFact.put("signature", "public void work");
                Map packageFact = new LinkedHashMap();
                packageFact.put("package", "custom");
                Map referenceFact = new LinkedHashMap();
                referenceFact.put("reference", "custom.dep.Helper");
                Map warning = new LinkedHashMap();
                warning.put("code", "provider-warning");
                return new JavaKnowledgeResult(
                        List.of(typeFact),
                        List.of(methodFact),
                        List.of(),
                        List.of(packageFact),
                        List.of(referenceFact),
                        List.of(classData),
                        List.of(warning));
            }
        };

        RepositorySnapshot snapshot = new KnowledgeExtractionPipeline(provider).extract(ExtractionOptions.defaults(project, project.resolve("build/ai-knowledge")));

        Object classFact = snapshot.classes.get(0);
        assertTrue(classFact instanceof Map<?, ?>);
        Map<?, ?> classes = (Map<?, ?>) classFact;
        assertTrue(classes.toString().contains("custom.ProviderType"));
        assertFalse(classes.toString().contains("example.App"));
        assertTrue(classes.containsKey("typeFacts"));
        assertTrue(classes.containsKey("methodFacts"));
        assertTrue(classes.containsKey("packageFacts"));
        assertTrue(classes.containsKey("referenceFacts"));
        assertTrue(classes.containsKey("warnings"));
    }

    @Test
    void passesRepositoryAndBuildContextToJavaProviderRequest() throws Exception {
        Path project = temp.resolve("request-fixture");
        Files.createDirectories(project.resolve("module-a/src/main/java/example"));
        Files.createDirectories(project.resolve("module-a/src/test/java/example"));
        Files.writeString(project.resolve("module-a/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("module-a/src/main/java/example/App.java"), "package example;\npublic class App {}\n");
        Files.writeString(project.resolve("module-a/src/test/java/example/AppTest.java"), "package example;\nclass AppTest {}\n");

        ArrayList<JavaKnowledgeRequest> capturedRequests = new ArrayList<>();
        JavaKnowledgeProvider provider = request -> {
            capturedRequests.add(request);
            return JavaKnowledgeResult.empty();
        };

        new KnowledgeExtractionPipeline(provider).extract(ExtractionOptions.defaults(project, project.resolve("build/ai-knowledge")));

        JavaKnowledgeRequest request = capturedRequests.get(0);
        assertTrue(request.repositoryRoot().equals(project));
        assertTrue(request.modules().stream().anyMatch(module -> "module-a".equals(String.valueOf(((Map) module).get("name")))));
        assertTrue(request.sourceRoots().stream().anyMatch(path -> path.toString().replace('\\', '/').endsWith("module-a/src/main/java")));
        assertTrue(request.testSourceRoots().stream().anyMatch(path -> path.toString().replace('\\', '/').endsWith("module-a/src/test/java")));
        assertTrue(request.buildMetadata().containsKey("modules"));
        assertTrue(capturedRequests.size() >= 2);
    }

    @Test
    void selectsJdtProviderFromConfigurationProperty() throws Exception {
        Path project = temp.resolve("jdt-provider-fixture");
        Files.createDirectories(project.resolve("src/main/java/example/api"));
        Files.createDirectories(project.resolve("src/main/java/example/impl"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/api/Service.java"), "package example.api;\npublic interface Service {}\n");
        Files.writeString(project.resolve("src/main/java/example/impl/ServiceImpl.java"), "package example.impl;\nimport example.api.Service;\npublic class ServiceImpl implements Service {}\n");

        String previous = System.getProperty("aiknowledge.javaProvider");
        try {
            System.setProperty("aiknowledge.javaProvider", "jdt");
            RepositorySnapshot snapshot = new KnowledgeExtractionPipeline().extract(ExtractionOptions.defaults(project, project.resolve("build/ai-knowledge")));
            assertTrue(snapshot.classes.toString().contains("implementations"));
            assertTrue(snapshot.classes.toString().contains("example.impl.ServiceImpl"));
        } finally {
            if (previous == null) System.clearProperty("aiknowledge.javaProvider"); else System.setProperty("aiknowledge.javaProvider", previous);
        }
    }
}
