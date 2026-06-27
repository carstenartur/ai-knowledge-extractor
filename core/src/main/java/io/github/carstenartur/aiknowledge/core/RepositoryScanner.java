package io.github.carstenartur.aiknowledge.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RepositoryScanner {
    private static final String[] CAPS = {"equality-saturation", "e-graph", "macro-rule-learning", "replay", "counterexample-search", "proof-bridge", "benchmark-report", "search-strategies", "assumption-handling", "rule-inventory"};

    RepositorySnapshot scan(ExtractionOptions options) throws IOException {
        Path root = options.repositoryRoot();
        RepositorySnapshot snapshot = new RepositorySnapshot();
        try (var stream = Files.walk(root)) {
            List files = stream.filter(Files::isRegularFile).filter(p -> !ignored(rel(root, p))).sorted(Comparator.comparing(p -> rel(root, p))).toList();
            for (Object object : files) {
                Path file = (Path) object;
                String path = rel(root, file);
                String name = file.getFileName().toString();
                if (name.equals("build.gradle") || name.equals("build.gradle.kts") || name.equals("pom.xml")) build(root, file, snapshot);
                if (path.endsWith(".java")) javaFile(root, file, snapshot);
                if (path.endsWith(".md")) doc(root, file, snapshot);
            }
        }
        capabilities(snapshot);
        SeedSupport.mergeSeeds(options, snapshot);
        Map counts = new LinkedHashMap();
        counts.put("modules", snapshot.modules.size());
        counts.put("classes", snapshot.classes.size());
        counts.put("tests", snapshot.tests.size());
        counts.put("docs", snapshot.docs.size());
        counts.put("dependencies", snapshot.dependencies.size());
        counts.put("capabilities", snapshot.capabilities.size());
        counts.put("claims", snapshot.claims.size());
        snapshot.index.put("schemaVersion", 1);
        snapshot.index.put("repository", root.getFileName().toString());
        snapshot.index.put("generationMode", "deterministic-static");
        snapshot.index.put("artifactDirectory", "build/ai-knowledge");
        snapshot.index.put("counts", counts);
        return snapshot;
    }

    private static void build(Path root, Path file, RepositorySnapshot snapshot) throws IOException {
        Map module = new LinkedHashMap();
        Path dir = file.getParent();
        module.put("name", dir.equals(root) ? root.getFileName().toString() : dir.getFileName().toString());
        module.put("path", rel(root, dir));
        module.put("buildFile", rel(root, file));
        module.put("buildSystem", file.getFileName().toString().equals("pom.xml") ? "maven" : "gradle");
        snapshot.modules.add(module);
        for (String line : text(file).split("\\R")) {
            String t = line.trim();
            if (t.startsWith("implementation ") || t.startsWith("api ") || t.startsWith("compileOnly ") || t.startsWith("testImplementation ")) {
                Map dep = new LinkedHashMap();
                dep.put("source", rel(root, file));
                dep.put("notation", t);
                snapshot.dependencies.add(dep);
            }
        }
    }

    private static void javaFile(Path root, Path file, RepositorySnapshot snapshot) throws IOException {
        String source = text(file);
        String path = rel(root, file);
        String simple = file.getFileName().toString().replace(".java", "");
        String pkg = "";
        List methods = new ArrayList();
        List imports = new ArrayList();
        for (String line : source.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("package ")) pkg = t.substring(8).replace(";", "").trim();
            if (t.startsWith("import ")) imports.add(t.substring(7).replace(";", "").trim());
            if ((t.startsWith("public ") || t.startsWith("protected ")) && t.contains("(") && !t.contains(" class ")) methods.add(t.substring(0, t.indexOf('(')).trim());
        }
        boolean test = path.contains("/src/test/") || simple.endsWith("Test") || source.contains("@Test");
        Map data = new LinkedHashMap();
        data.put(test ? "testClass" : "class", pkg.isBlank() ? simple : pkg + "." + simple);
        data.put("sourceFile", path);
        data.put("package", pkg);
        if (test) {
            data.put("testMethods", methods);
            snapshot.tests.add(data);
        } else {
            data.put("publicApiMethods", methods);
            data.put("imports", imports);
            snapshot.classes.add(data);
        }
    }

    private static void doc(Path root, Path file, RepositorySnapshot snapshot) throws IOException {
        List headings = new ArrayList();
        for (String line : text(file).split("\\R")) if (line.startsWith("#")) headings.add(line.replaceFirst("^#+\\s*", ""));
        Map doc = new LinkedHashMap();
        doc.put("path", rel(root, file));
        doc.put("title", headings.isEmpty() ? file.getFileName().toString() : headings.get(0));
        doc.put("headings", headings);
        snapshot.docs.add(doc);
    }

    private static void capabilities(RepositorySnapshot snapshot) {
        for (String id : CAPS) {
            List classes = evidence(snapshot.classes, id);
            List tests = evidence(snapshot.tests, id);
            List docs = evidence(snapshot.docs, id);
            String status = !classes.isEmpty() && !tests.isEmpty() ? "implemented" : !classes.isEmpty() || !tests.isEmpty() ? "partial" : !docs.isEmpty() ? "documented" : "unknown";
            Map cap = new LinkedHashMap();
            cap.put("id", id); cap.put("status", status); cap.put("classes", classes); cap.put("tests", tests); cap.put("docs", docs);
            snapshot.capabilities.add(cap);
            Map claim = new LinkedHashMap();
            claim.put("id", id); claim.put("status", status); claim.put("implementedBy", classes); claim.put("verifiedBy", tests); claim.put("documentedBy", docs);
            snapshot.claims.add(claim);
        }
    }

    private static List evidence(List source, String id) {
        List result = new ArrayList();
        String key = id.replace("-", "");
        for (Object item : source) {
            Map map = (Map) item;
            if (map.toString().toLowerCase().replace("-", "").contains(key)) result.add(map.getOrDefault("class", map.getOrDefault("testClass", map.getOrDefault("path", ""))));
        }
        return result;
    }

    private static boolean ignored(String path) { return path.startsWith(".git/") || path.startsWith(".gradle/") || path.startsWith("build/") || path.startsWith("target/") || path.contains("/build/") || path.contains("/target/"); }
    private static String rel(Path root, Path file) { return root.relativize(file).toString().replace(File.separatorChar, '/'); }
    private static String text(Path file) throws IOException { return Files.readString(file, StandardCharsets.UTF_8); }
}
