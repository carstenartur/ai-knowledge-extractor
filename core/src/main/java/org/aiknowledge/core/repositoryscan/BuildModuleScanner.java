package org.aiknowledge.core.repositoryscan;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.aiknowledge.core.BuildMetadata;
import org.aiknowledge.core.MavenMetadata;
import org.aiknowledge.core.RepositorySnapshot;

public final class BuildModuleScanner {
    public void extract(Path root, Path file, String path, RepositorySnapshot snapshot) throws IOException {
        String name = file.getFileName().toString();
        if (!name.equals("build.gradle") && !name.equals("build.gradle.kts") && !name.equals("pom.xml")) return;
        Map module = new LinkedHashMap();
        Path dir = file.getParent();
        module.put("name", dir.equals(root) ? root.getFileName().toString() : dir.getFileName().toString());
        module.put("path", root.relativize(dir).toString().replace(File.separatorChar, '/'));
        module.put("buildFile", path);
        module.put("buildSystem", name.equals("pom.xml") ? "maven" : "gradle");
        BuildMetadata.initializeModuleFields(module);
        snapshot.modules.add(module);
        String buildText = read(file);
        if (name.equals("pom.xml")) {
            MavenMetadata.addDependencies(root, file, buildText, snapshot);
            return;
        }
        for (String line : buildText.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("implementation ")
                    || trimmed.startsWith("api ")
                    || trimmed.startsWith("compileOnly ")
                    || trimmed.startsWith("testImplementation ")) {
                Map dep = new LinkedHashMap();
                dep.put("source", path);
                dep.put("notation", trimmed);
                dep.put("scope", trimmed.substring(0, trimmed.indexOf(' ')));
                dep.put("buildSystem", "gradle");
                snapshot.dependencies.add(dep);
            }
        }
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
