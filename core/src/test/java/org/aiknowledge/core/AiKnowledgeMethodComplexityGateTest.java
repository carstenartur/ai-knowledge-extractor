package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiKnowledgeMethodComplexityGateTest {
    @TempDir
    Path temp;

    @Test
    void checkFailsWhenMethodCognitiveComplexityExceedsThreshold() throws Exception {
        Path project = temp.resolve("fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/App.java"), """
                package example;
                public class App {
                    public int complex(boolean a, boolean b) {
                        if (a) {
                            if (b) {
                                return 1;
                            }
                        }
                        return 0;
                    }
                }
                """);
        Path output = project.resolve("build/ai-knowledge");
        ExtractionOptions options = new ExtractionOptions(
                project,
                output,
                project.resolve("ai-knowledge"),
                project.resolve("ai-knowledge"),
                false,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                false,
                null,
                false,
                false,
                0,
                Integer.MAX_VALUE,
                1,
                Integer.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                List.of());

        IOException exception = assertThrows(IOException.class, () -> new AiKnowledgeRunner().check(options));

        assertTrue(exception.getMessage().contains("maxMethodCognitiveComplexity"));
        assertTrue(Files.readString(output.resolve("check.json")).contains("maxMethodCognitiveComplexity"));
    }
}
