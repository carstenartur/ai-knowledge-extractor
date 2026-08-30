package org.aiknowledge.core.repositoryscan;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.aiknowledge.core.BuildMetadata;
import org.aiknowledge.core.MavenMetadata;
import org.aiknowledge.core.PackageJsonMetadata;
import org.aiknowledge.core.RepositorySnapshot;

/** Discovers Maven, Gradle and npm-compatible build modules. */
public final class BuildModuleScanner {
    public void extract(Path root, Path file, String path, RepositorySnapshot snapshot) throws IOException {
        String name = file.getFileName().toString();
        if (!List.of("build.gradle", "build.gradle.kts", "pom.xml", "package.json").contains(name)) {
            return;
        }
        Path dir = file.getParent();
        String modulePath = root.relativize(dir).toString().replace(File.separatorChar, '/');
        String buildSystem = switch (name) {
            case "pom.xml" -> "maven";
            case "package.json" -> "npm";
            default -> "gradle";
        };
        Map module = module(snapshot, root, dir, modulePath, path, buildSystem);
        String buildText = read(file);
        if ("pom.xml".equals(name)) {
            MavenMetadata.addDependencies(root, file, buildText, snapshot);
        } else if ("package.json".equals(name)) {
            PackageJsonMetadata.enrich(root, file, path, module, snapshot);
        } else {
            addGradleDependencies(path, buildText, snapshot);
        }
    }

    private static Map module(
            RepositorySnapshot snapshot,
            Path root,
            Path dir,
            String modulePath,
            String buildFile,
            String buildSystem) {
        for (Object value : snapshot.modules) {
            if (!(value instanceof Map existing)) continue;
            if (!modulePath.equals(String.valueOf(existing.getOrDefault("path", "")))) continue;
            addUnique(existing, "buildFiles", buildFile);
            addUnique(existing, "buildSystems", buildSystem);
            existing.putIfAbsent("buildFile", buildFile);
            existing.putIfAbsent("buildSystem", buildSystem);
            return existing;
        }
        Map module = new LinkedHashMap();
        module.put("name", dir.equals(root) ? root.getFileName().toString() : dir.getFileName().toString());
        module.put("path", modulePath);
        module.put("buildFile", buildFile);
        module.put("buildSystem", buildSystem);
        module.put("buildFiles", new ArrayList<>(List.of(buildFile)));
        module.put("buildSystems", new ArrayList<>(List.of(buildSystem)));
        BuildMetadata.initializeModuleFields(module);
        snapshot.modules.add(module);
        return module;
    }

    private static void addGradleDependencies(
            String path,
            String buildText,
            RepositorySnapshot snapshot) {
        for (String line : buildText.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("implementation ")
                    || trimmed.startsWith("api ")
                    || trimmed.startsWith("compileOnly ")
                    || trimmed.startsWith("runtimeOnly ")
                    || trimmed.startsWith("testImplementation ")) {
                Map dep = new LinkedHashMap();
                dep.put("source", path);
                dep.put("notation", trimmed);
                dep.put("scope", trimmed.substring(0, trimmed.indexOf(' ')));
                dep.put("buildSystem", "gradle");
                dep.put("ecosystem", "maven");
                snapshot.dependencies.add(dep);
            }
        }
    }

    private static void addUnique(Map module, String key, String value) {
        List values = (List) module.computeIfAbsent(key, ignored -> new ArrayList());
        if (!values.contains(value)) values.add(value);
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
