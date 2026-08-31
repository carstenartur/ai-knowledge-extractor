package org.aiknowledge.core.sourcespi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceAnalysisConfigurationTest {
    @TempDir
    Path temp;

    @Test
    void appliesSharedProviderGlobDirectoryAndSizePolicy() throws Exception {
        Path accepted = temp.resolve("web/src/app.ts");
        Path test = temp.resolve("web/src/app.spec.ts");
        Path ignored = temp.resolve("web/vendor/app.ts");
        Path oversized = temp.resolve("web/src/large.ts");
        Files.createDirectories(accepted.getParent());
        Files.createDirectories(ignored.getParent());
        Files.writeString(accepted, "export const value = 1;");
        Files.writeString(test, "export const value = 2;");
        Files.writeString(ignored, "export const value = 3;");
        Files.writeString(oversized, "x".repeat(1_001));

        SourceAnalysisConfiguration configuration = SourceAnalysisConfiguration.from(Map.of(
                SourceAnalysisConfiguration.PREFIX + "enabledProviders", "javascript-typescript-structural",
                SourceAnalysisConfiguration.PREFIX + "disabledProviders", "other",
                SourceAnalysisConfiguration.PREFIX + "includes", "web/**/*.ts",
                SourceAnalysisConfiguration.PREFIX + "excludes", "**/*.spec.ts",
                SourceAnalysisConfiguration.PREFIX + "ignoredDirectories", "vendor",
                SourceAnalysisConfiguration.PREFIX + "maxFileBytes", "1000"));

        assertTrue(configuration.providerEnabled("javascript-typescript-structural"));
        assertFalse(configuration.providerEnabled("other"));
        assertFalse(configuration.providerEnabled("unlisted"));
        assertTrue(configuration.acceptsSource(accepted, "web/src/app.ts"));
        assertTrue(configuration.acceptsSource(accepted, "web/src/app.ts"),
                "a second provider must not consume the budget twice");
        assertFalse(configuration.acceptsSource(test, "web/src/app.spec.ts"));
        assertFalse(configuration.acceptsSource(ignored, "web/vendor/app.ts"));
        assertFalse(configuration.acceptsSource(oversized, "web/src/large.ts"));
        Files.delete(oversized);
        assertFalse(configuration.acceptsSource(oversized, "web/src/large.ts"),
                "a rejected path must keep its original admission decision");
    }

    @Test
    void rejectsInvalidLimitsAndErrorPoliciesEarly() {
        assertThrows(IllegalArgumentException.class, () -> SourceAnalysisConfiguration.from(Map.of(
                SourceAnalysisConfiguration.PREFIX + "maxFiles", "0")));
        assertThrows(IllegalArgumentException.class, () -> SourceAnalysisConfiguration.from(Map.of(
                SourceAnalysisConfiguration.PREFIX + "errorPolicy", "invented")));
    }
}
