package org.aiknowledge.core;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class MavenMetadata {
    private MavenMetadata() {
    }

    static void addDependencies(Path root, Path pom, String text, RepositorySnapshot snapshot) {
        int start = 0;
        while ((start = text.indexOf("<dependency>", start)) >= 0) {
            int end = text.indexOf("</dependency>", start);
            if (end < 0) return;
            String block = text.substring(start, end);
            String groupId = tag(block, "groupId");
            String artifactId = tag(block, "artifactId");
            if (!groupId.isBlank() && !artifactId.isBlank()) {
                Map dep = new LinkedHashMap();
                dep.put("source", rel(root, pom));
                dep.put("notation", groupId + ":" + artifactId + versionSuffix(block));
                dep.put("scope", scope(block));
                dep.put("buildSystem", "maven");
                snapshot.dependencies.add(dep);
            }
            start = end + "</dependency>".length();
        }
    }

    private static String versionSuffix(String block) {
        String version = tag(block, "version");
        return version.isBlank() ? "" : ":" + version;
    }

    private static String scope(String block) {
        String scope = tag(block, "scope");
        return scope.isBlank() ? "compile" : scope;
    }

    private static String tag(String block, String name) {
        int start = block.indexOf("<" + name + ">");
        int end = block.indexOf("</" + name + ">");
        if (start < 0 || end <= start) return "";
        return block.substring(start + name.length() + 2, end).trim();
    }

    private static String rel(Path root, Path file) {
        return root.relativize(file).toString().replace(File.separatorChar, '/');
    }
}
