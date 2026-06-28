package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.aiknowledge.core.javaspi.JavaKnowledgeProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnowledgeExtractionPipelineTest {
    @TempDir
    Path temp;

    @Test
    void usesConfiguredJavaProviderViaSpi() throws Exception {
        Path project = temp.resolve("spi-fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/App.java"), "package example;\npublic class App {}\n");

        JavaKnowledgeProvider provider = new JavaKnowledgeProvider() {
            @Override
            public boolean supports(String path) {
                return path.endsWith(".java");
            }

            @Override
            public void extract(Path root, Path file, String path, RepositorySnapshot snapshot) {
                Map clazz = new LinkedHashMap();
                clazz.put("class", "custom.ProviderType");
                clazz.put("sourceFile", path);
                snapshot.classes.add(clazz);
            }
        };

        RepositorySnapshot snapshot = new KnowledgeExtractionPipeline(provider).extract(ExtractionOptions.defaults(project, project.resolve("build/ai-knowledge")));

        String classes = snapshot.classes.toString();
        assertTrue(classes.contains("custom.ProviderType"));
        assertFalse(classes.contains("example.App"));
    }
}
