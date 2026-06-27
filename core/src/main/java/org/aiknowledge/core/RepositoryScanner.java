package org.aiknowledge.core;

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
    RepositorySnapshot scan(ExtractionOptions options) throws IOException {
        Path root = options.repositoryRoot();
        RepositorySnapshot snapshot = new RepositorySnapshot();
        try (var stream = Files.walk(root)) {
            List files = stream.filter(Files::isRegularFile).filter(p -> !ignored(rel(root, p))).sorted(Comparator.comparing(p -> rel(root, p))).toList();
            for (Object object : files) {
                Path file = (Path) object;
                String path = rel(root, file);
                String name = file.getFileName().toString();
                if (name.equals("build.gradle") || name.equals("build.gradle.kts") || name.equals("pom.xml")) addModule(root, file, snapshot);
                if (path.endsWith(".java")) addJava(root, file, snapshot);
                if (path.endsWith(".md")) addDoc(root, file, snapshot);
            }
        }
        BuildMetadata.enrichModules(root, snapshot);
        CapabilityEvidence.addCapabilities(snapshot);
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
        snapshot.index.put("counts", counts);
        return snapshot;
    }

    private static void addModule(Path root, Path file, RepositorySnapshot snapshot) throws IOException {
        Map module = new LinkedHashMap();
        Path dir = file.getParent();
        module.put("name", dir.equals(root) ? root.getFileName().toString() : dir.getFileName().toString());
        module.put("path", rel(root, dir));
        module.put("buildFile", rel(root, file));
        module.put("buildSystem", file.getFileName().toString().equals("pom.xml") ? "maven" : "gradle");
        BuildMetadata.initializeModuleFields(module);
        snapshot.modules.add(module);
        String buildText = read(file);
        if (file.getFileName().toString().equals("pom.xml")) {
            MavenMetadata.addDependencies(root, file, buildText, snapshot);
            return;
        }
        for (String line : buildText.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("implementation ") || t.startsWith("api ") || t.startsWith("compileOnly ") || t.startsWith("testImplementation ")) {
                Map dep = new LinkedHashMap();
                dep.put("source", rel(root, file));
                dep.put("notation", t);
                snapshot.dependencies.add(dep);
            }
        }
    }

    private static void addJava(Path root, Path file, RepositorySnapshot snapshot) throws IOException {
        String source = read(file);
        String path = rel(root, file);
        String simple = file.getFileName().toString().replace(".java", "");
        String pkg = "";
        List methods = new ArrayList();
        for (String line : source.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("package ")) pkg = parsePackage(t);
            if ((t.startsWith("public ") || t.startsWith("protected ")) && t.contains("(")) methods.add(t.substring(0, t.indexOf('(')).trim());
        }
        boolean test = path.contains("/src/test/") || simple.endsWith("Test") || source.contains("@Test");
        Map data = new LinkedHashMap();
        data.put(test ? "testClass" : "class", pkg.isBlank() ? simple : pkg + "." + simple);
        data.put("sourceFile", path);
        data.put("package", pkg);
        if (test) { data.put("testMethods", methods); JavaSourceMetadata.enrich(data, source, simple, true); snapshot.tests.add(data); }
        else { data.put("publicApiMethods", methods); JavaSourceMetadata.enrich(data, source, simple, false); snapshot.classes.add(data); }
    }

    private static String parsePackage(String line) {
        String value = line.substring("package ".length()).trim();
        int semicolon = value.indexOf(';');
        return semicolon >= 0 ? value.substring(0, semicolon).trim() : value.trim();
    }

    private static void addDoc(Path root, Path file, RepositorySnapshot snapshot) throws IOException {
        String text = read(file);
        List headings = new ArrayList();
        for (String line : text.split("\\R")) if (line.startsWith("#")) headings.add(line.replaceFirst("^#+\\s*", ""));
        Map doc = new LinkedHashMap();
        doc.put("path", rel(root, file));
        doc.put("title", headings.isEmpty() ? file.getFileName().toString() : headings.get(0));
        doc.put("headings", headings);
        doc.put("links", MarkdownMetadata.links(text));
        snapshot.docs.add(doc);
    }

    private static boolean ignored(String path) { return path.startsWith(".git/") || path.startsWith(".gradle/") || path.contains("/build/") || path.contains("/target/") || path.startsWith("build/") || path.startsWith("target/"); }
    private static String rel(Path root, Path file) { return root.relativize(file).toString().replace(File.separatorChar, '/'); }
    private static String read(Path file) throws IOException { return Files.readString(file, StandardCharsets.UTF_8); }
}
