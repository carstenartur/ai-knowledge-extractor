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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                if (path.startsWith("docs/generated/discovery/") && path.endsWith("/evidence.json")) addDiscoveryEvidence(root, file, snapshot);
                if (path.contains("/src/jmh/java/") && path.endsWith(".java")) addBenchmarkSourceEvidence(path, snapshot);
                if (path.startsWith(".github/workflows/") && (path.endsWith(".yml") || path.endsWith(".yaml"))) addWorkflowEvidence(root, file, snapshot);
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
        counts.put("evidence", snapshot.evidence.size());
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
                dep.put("scope", t.substring(0, t.indexOf(' ')));
                dep.put("buildSystem", "gradle");
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
        data.put("lineCount", source.split("\\R", -1).length);
        data.put("methodCount", methods.size());
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

    private static void addDiscoveryEvidence(Path root, Path file, RepositorySnapshot snapshot) throws IOException {
        String text = read(file);
        Map item = new LinkedHashMap();
        item.put("type", "discovery-evidence");
        item.put("path", rel(root, file));
        putJsonString(item, text, "scenarioId");
        putJsonString(item, text, "inputExpression");
        putJsonString(item, text, "targetExpression");
        putJsonString(item, text, "oracleStatus");
        putJsonBoolean(item, text, "success");
        putJsonBoolean(item, text, "promotionEligible");
        putJsonNumber(item, text, "nodeCount");
        putJsonNumber(item, text, "edgeCount");
        item.put("bridgeRuleCount", jsonArrayCount(text, "bridgeRulesUsed"));
        item.put("learnedMacroCount", jsonArrayCount(text, "learnedMacros"));
        item.put("reusedMacroCount", jsonArrayCount(text, "reusedMacros"));
        snapshot.evidence.add(item);
    }

    private static void addBenchmarkSourceEvidence(String path, RepositorySnapshot snapshot) {
        Map item = new LinkedHashMap();
        item.put("type", "benchmark-source");
        item.put("path", path);
        item.put("name", path.substring(path.lastIndexOf('/') + 1).replace(".java", ""));
        snapshot.evidence.add(item);
    }

    private static void addWorkflowEvidence(Path root, Path file, RepositorySnapshot snapshot) throws IOException {
        String text = read(file);
        Map item = new LinkedHashMap();
        item.put("type", "github-workflow");
        item.put("path", rel(root, file));
        item.put("name", yamlName(text, file.getFileName().toString()));
        item.put("hasWorkflowDispatch", text.contains("workflow_dispatch"));
        item.put("jobCount", countIndentedJobKeys(text));
        snapshot.evidence.add(item);
    }

    private static void putJsonString(Map item, String text, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(text);
        if (matcher.find()) item.put(key, matcher.group(1));
    }

    private static void putJsonBoolean(Map item, String text, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (matcher.find()) item.put(key, Boolean.parseBoolean(matcher.group(1).toLowerCase(Locale.ROOT)));
    }

    private static void putJsonNumber(Map item, String text, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*([0-9]+)").matcher(text);
        if (matcher.find()) item.put(key, Long.parseLong(matcher.group(1)));
    }

    private static int jsonArrayCount(String text, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(text);
        if (!matcher.find()) return 0;
        String body = matcher.group(1).trim();
        if (body.isEmpty()) return 0;
        int count = 1;
        boolean inString = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"') inString = !inString;
            if (c == ',' && !inString) count++;
        }
        return count;
    }

    private static String yamlName(String text, String fallback) {
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("name:")) return trimmed.substring("name:".length()).trim().replace("\"", "").replace("'", "");
        }
        return fallback;
    }

    private static int countIndentedJobKeys(String text) {
        boolean inJobs = false;
        int count = 0;
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (isYamlKey(trimmed, "jobs")) { inJobs = true; continue; }
            if (!inJobs) continue;
            if (!line.startsWith("  ") && !trimmed.isEmpty()) break;
            if (line.startsWith("  ") && !line.startsWith("    ")) {
                int colon = trimmed.indexOf(':');
                if (colon > 0) {
                    String afterColon = trimmed.substring(colon + 1).trim();
                    if (afterColon.isEmpty() || afterColon.startsWith("#")) count++;
                }
            }
        }
        return count;
    }

    private static boolean isYamlKey(String trimmed, String key) {
        if (!trimmed.startsWith(key + ":")) return false;
        String after = trimmed.substring(key.length() + 1).trim();
        return after.isEmpty() || after.startsWith("#");
    }

    private static boolean ignored(String path) { return path.startsWith(".git/") || path.startsWith(".gradle/") || path.contains("/build/") || path.contains("/target/") || path.startsWith("build/") || path.startsWith("target/"); }
    private static String rel(Path root, Path file) { return root.relativize(file).toString().replace(File.separatorChar, '/'); }
    private static String read(Path file) throws IOException { return Files.readString(file, StandardCharsets.UTF_8); }
}
