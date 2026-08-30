package org.aiknowledge.core.repositoryscan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.aiknowledge.core.RepositorySnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildModuleScannerPackageJsonTest {
    @TempDir
    Path temp;

    @Test
    void extractsNpmModuleScriptsAndDependencyScopes() throws Exception {
        Path packageJson = temp.resolve("web/package.json");
        Files.createDirectories(packageJson.getParent());
        Files.writeString(packageJson, """
                {
                  "name": "example-web",
                  "packageManager": "npm@11",
                  "scripts": { "test": "vitest", "build": "vite build" },
                  "dependencies": { "react": "^19.0.0" },
                  "devDependencies": { "typescript": "^5.9.0" }
                }
                """);
        RepositorySnapshot snapshot = new RepositorySnapshot();

        new BuildModuleScanner().extract(temp, packageJson, "web/package.json", snapshot);

        assertEquals(1, snapshot.modules.size());
        Map<?, ?> module = (Map<?, ?>) snapshot.modules.get(0);
        assertEquals("example-web", module.get("name"));
        assertEquals("npm", module.get("buildSystem"));
        assertEquals(2, snapshot.dependencies.size());
        assertTrue(snapshot.dependencies.stream()
                .anyMatch(value -> value instanceof Map<?, ?> dependency
                        && "react".equals(dependency.get("artifact"))
                        && "dependencies".equals(dependency.get("scope"))));
    }
}
