package org.aiknowledge.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BuildMetadata {
    private BuildMetadata() {
    }

    public static void initializeModuleFields(Map module) {
        module.putIfAbsent("sourceSets", new ArrayList());
        module.putIfAbsent("mainPackages", new ArrayList());
        module.putIfAbsent("projectDependencies", new ArrayList());
        module.putIfAbsent("externalDependencies", new ArrayList());
    }

    public static void enrichModules(Path root, RepositorySnapshot snapshot) {
        for (Object object : snapshot.modules) {
            Map module = (Map) object;
            initializeModuleFields(module);
            String modulePath = String.valueOf(module.getOrDefault("path", ""));
            Path moduleRoot = modulePath.isBlank() ? root : root.resolve(modulePath);
            addSourceSets(moduleRoot, module);
            for (Object classObject : snapshot.classes) {
                Map classMap = (Map) classObject;
                if (belongsToModule(modulePath, String.valueOf(classMap.get("sourceFile")))) {
                    addMainPackage(module, classMap.get("package"));
                }
            }
            for (Object sourceObject : snapshot.sourceUnits) {
                if (!(sourceObject instanceof Map source)) continue;
                if (Boolean.TRUE.equals(source.get("test"))) continue;
                if (belongsToModule(modulePath, String.valueOf(source.get("sourceFile")))) {
                    Object namespace = source.get("package");
                    if (namespace == null || String.valueOf(namespace).isBlank()) {
                        namespace = source.get("namespace");
                    }
                    addMainPackage(module, namespace);
                }
            }
            for (Object dependencyObject : snapshot.dependencies) {
                Map dependency = (Map) dependencyObject;
                if (belongsToModule(modulePath, String.valueOf(dependency.get("source")))) {
                    addDependency(dependency, module);
                }
            }
        }
    }

    private static boolean belongsToModule(String modulePath, String sourceFile) {
        if (modulePath == null || modulePath.isBlank()) return true;
        return sourceFile != null
                && (sourceFile.equals(modulePath) || sourceFile.startsWith(modulePath + "/"));
    }

    static void addDependency(Map dep, Map module) {
        Object notation = dep.get("notation");
        if (notation == null) return;
        List projectDependencies = (List) module.get("projectDependencies");
        List externalDependencies = (List) module.get("externalDependencies");
        String value = String.valueOf(notation);
        if (value.contains("project(")) addUnique(projectDependencies, value);
        else addUnique(externalDependencies, value);
    }

    static void addSourceSets(Path moduleRoot, Map module) {
        List sourceSets = (List) module.get("sourceSets");
        if (moduleRoot.resolve("src/main/java").toFile().isDirectory()) addUnique(sourceSets, "main/java");
        if (moduleRoot.resolve("src/test/java").toFile().isDirectory()) addUnique(sourceSets, "test/java");
        if (moduleRoot.resolve("src").toFile().isDirectory()) addUnique(sourceSets, "src");
        if (moduleRoot.resolve("app").toFile().isDirectory()) addUnique(sourceSets, "app");
        if (moduleRoot.resolve("test").toFile().isDirectory()) addUnique(sourceSets, "test");
        if (moduleRoot.resolve("tests").toFile().isDirectory()) addUnique(sourceSets, "tests");
    }

    static void addMainPackage(Map module, Object packageName) {
        if (packageName == null) return;
        String value = String.valueOf(packageName);
        if (value.isBlank()) return;
        addUnique((List) module.get("mainPackages"), value);
    }

    private static void addUnique(List list, Object value) {
        if (!list.contains(value)) list.add(value);
    }
}
