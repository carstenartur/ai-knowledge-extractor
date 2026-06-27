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
        addUnique((List) module.get("mainPackages"), packageName);
    }

    private static void addUnique(List list, Object value) {
        if (!list.contains(value)) list.add(value);
    }
}
