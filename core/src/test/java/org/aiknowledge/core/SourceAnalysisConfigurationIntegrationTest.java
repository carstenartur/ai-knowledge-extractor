package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.aiknowledge.core.javascript.JavaScriptTypeScriptKnowledgeProvider;
import org.aiknowledge.core.sourcespi.SourceAnalysisConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceAnalysisConfigurationIntegrationTest {
    @TempDir
    Path temp;

    @Test
    void disablesAndEnablesTheSameProviderForEveryBuildIntegration() throws Exception {
        Path project = temp.resolve("mixed");
        Files.createDirectories(project.resolve("web/src"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("web/package.json"), "{\"name\":\"web\"}\n");
        Files.writeString(project.resolve("web/src/client.ts"), "export const load = () => fetch('/api/items');\n");

        String key = SourceAnalysisConfiguration.PREFIX + "disabledProviders";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, JavaScriptTypeScriptKnowledgeProvider.PROVIDER_ID);
            RepositorySnapshot disabled = new KnowledgeExtractionPipeline().extract(
                    ExtractionOptions.defaults(project, project.resolve("out-disabled")));
            assertFalse(disabled.sourceUnits.toString().contains("typescript"));

            System.clearProperty(key);
            RepositorySnapshot enabled = new KnowledgeExtractionPipeline().extract(
                    ExtractionOptions.defaults(project, project.resolve("out-enabled")));
            assertTrue(enabled.sourceUnits.toString().contains("typescript"));
        } finally {
            if (previous == null) System.clearProperty(key);
            else System.setProperty(key, previous);
        }
    }
}
