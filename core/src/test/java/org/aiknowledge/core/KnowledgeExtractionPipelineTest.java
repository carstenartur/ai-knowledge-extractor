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
                return new JavaKnowledgeResult(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(classData), List.of());
            }
        };

        RepositorySnapshot snapshot = new KnowledgeExtractionPipeline(provider).extract(ExtractionOptions.defaults(project, project.resolve("build/ai-knowledge")));

        String classes = snapshot.classes.toString();
        assertTrue(classes.contains("custom.ProviderType"));
        assertFalse(classes.contains("example.App"));
    }

    @Test
    void passesRepositoryAndBuildContextToJavaProviderRequest() throws Exception {
        Path project = temp.resolve("request-fixture");
        Files.createDirectories(project.resolve("module-a/src/main/java/example"));
        Files.createDirectories(project.resolve("module-a/src/test/java/example"));
        Files.writeString(project.resolve("module-a/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("module-a/src/main/java/example/App.java"), "package example;\npublic class App {}\n");

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
    }
}
