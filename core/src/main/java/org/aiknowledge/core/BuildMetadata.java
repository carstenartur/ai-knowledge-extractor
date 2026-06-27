package org.aiknowledge.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class BuildMetadata {
    private BuildMetadata() {
    }

    static void initializeModuleFields(Map module) {
        module.put("sourceSets", new ArrayList());
        module.put("mainPackages", new ArrayList());
        module.put("projectDependencies", new ArrayList());
        module.put("externalDependencies", new ArrayList());
    }

    static void enrichModules(Path root, RepositorySnapshot snapshot) {
        for (Object object : snapshot.modules) {
            Map module = (Map) object;
            String modulePath = String.valueOf(module.get("path"));
            Path moduleRoot = modulePath.isBlank() ? root : root.resolve(modulePath);
            addSourceSets(moduleRoot, module);
            for (Object classObject : snapshot.classes) {
                Map classMap = (Map) classObject;
                if (belongsToModule(modulePath, String.valueOf(classMap.get("sourceFile")))) addMainPackage(module, classMap.get("package"));
            }
            for (Object dependencyObject : snapshot.dependencies) {
                Map dependency = (Map) dependencyObject;
                if (String.valueOf(dependency.get("source")).equals(String.valueOf(module.get("buildFile")))) addDependency(dependency, module);
            }
        }
    }

    private static boolean belongsToModule(String modulePath, String sourceFile) {
        if (modulePath == null || modulePath.isBlank()) return true;
        return sourceFile != null && sourceFile.startsWith(modulePath + "/");
    }

    static void addDependency(Map dep, Map module) {
        Object notation = dep.get("notation");
        if (notation == null) return;
        List projectDependencies = (List) module.get("projectDependencies");
        List externalDependencies = (List) module.get("externalDependencies");
        String value = String.valueOf(notation);
        if (value.contains("project(")) addUnique(projectDependencies, value); else addUnique(externalDependencies, value);
    }

    static void addSourceSets(Path moduleRoot, Map module) {
        List sourceSets = (List) module.get("sourceSets");
        if (moduleRoot.resolve("src/main/java").toFile().isDirectory()) addUnique(sourceSets, "main/java");
        if (moduleRoot.resolve("src/test/java").toFile().isDirectory()) addUnique(sourceSets, "test/java");
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
