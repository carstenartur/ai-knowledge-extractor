package org.aiknowledge.core.javabasic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.aiknowledge.core.JavaSourceMetadata;
import org.aiknowledge.core.RepositorySnapshot;
import org.aiknowledge.core.javaspi.JavaKnowledgeProvider;

public final class BasicJavaKnowledgeProvider implements JavaKnowledgeProvider {
    @Override
    public boolean supports(String path) {
        return path.endsWith(".java");
    }

    @Override
    public void extract(Path root, Path file, String path, RepositorySnapshot snapshot) throws IOException {
        String source = read(file);
        String simple = file.getFileName().toString().replace(".java", "");
        String pkg = "";
        List methods = new ArrayList();
        for (String line : source.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("package ")) pkg = parsePackage(trimmed);
            if ((trimmed.startsWith("public ") || trimmed.startsWith("protected ")) && trimmed.contains("(")) {
                methods.add(trimmed.substring(0, trimmed.indexOf('(')).trim());
            }
        }
        boolean test = path.contains("/src/test/") || simple.endsWith("Test") || source.contains("@Test");
        Map data = new LinkedHashMap();
        data.put(test ? "testClass" : "class", pkg.isBlank() ? simple : pkg + "." + simple);
        data.put("sourceFile", path);
        data.put("package", pkg);
        data.put("lineCount", source.split("\\R", -1).length);
        data.put("methodCount", methods.size());
        if (test) {
            data.put("testMethods", methods);
            JavaSourceMetadata.enrich(data, source, simple, true);
            snapshot.tests.add(data);
            return;
        }
        data.put("publicApiMethods", methods);
        JavaSourceMetadata.enrich(data, source, simple, false);
        snapshot.classes.add(data);
    }

    private static String parsePackage(String line) {
        String value = line.substring("package ".length()).trim();
        int semicolon = value.indexOf(';');
        return semicolon >= 0 ? value.substring(0, semicolon).trim() : value.trim();
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
