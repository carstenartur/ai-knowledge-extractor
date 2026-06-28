package org.aiknowledge.core.javabasic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.aiknowledge.core.JavaSourceMetadata;
import org.aiknowledge.core.javaspi.JavaKnowledgeRequest;
import org.aiknowledge.core.javaspi.JavaKnowledgeProvider;
import org.aiknowledge.core.javaspi.JavaKnowledgeResult;

public final class BasicJavaKnowledgeProvider implements JavaKnowledgeProvider {
    private static final Pattern ACCESS_MODIFIER = Pattern.compile("\\b(public|protected)\\b");

    @Override
    public JavaKnowledgeResult extract(JavaKnowledgeRequest request) throws IOException {
        if (!request.sourcePath().endsWith(".java")) return JavaKnowledgeResult.empty();
        String source = read(request.sourceFile());
        String simple = request.sourceFile().getFileName().toString().replace(".java", "");
        String pkg = "";
        List<String> methods = new ArrayList<>();
        List<String> imports = new ArrayList<>();
        for (String line : source.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("package ")) pkg = parsePackage(trimmed);
            if (trimmed.startsWith("import ")) imports.add(parseImport(trimmed));
            methods.addAll(extractMethodSignatures(trimmed));
        }
        boolean test = request.sourcePath().contains("/src/test/") || simple.endsWith("Test") || source.contains("@Test");
        Map data = new LinkedHashMap();
        data.put(test ? "testClass" : "class", pkg.isBlank() ? simple : pkg + "." + simple);
        data.put("sourceFile", request.sourcePath());
        data.put("package", pkg);
        data.put("lineCount", source.split("\\R", -1).length);
        data.put("methodCount", methods.size());
        List classFacts = new ArrayList();
        List testFacts = new ArrayList();
        if (test) {
            data.put("testMethods", methods);
            JavaSourceMetadata.enrich(data, source, simple, true);
            testFacts.add(data);
        } else {
            data.put("publicApiMethods", methods);
            JavaSourceMetadata.enrich(data, source, simple, false);
            classFacts.add(data);
        }
        String typeName = String.valueOf(data.get(test ? "testClass" : "class"));
        List typeFacts = List.of(typeFact(typeName, request.sourcePath(), pkg, simple, test));
        List methodFacts = methodFacts(typeName, methods);
        List packageFacts = pkg.isBlank() ? List.of() : List.of(packageFact(pkg, request.sourcePath()));
        List referenceFacts = referenceFacts(typeName, imports);
        List warnings = List.of(warning("heuristic-line-parser", "Basic provider uses line-based heuristics and may miss nested or complex Java syntax."));
        return new JavaKnowledgeResult(typeFacts, methodFacts, testFacts, packageFacts, referenceFacts, classFacts, warnings);
    }

    private static String parsePackage(String line) {
        String value = line.substring("package ".length()).trim();
        int semicolon = value.indexOf(';');
        return semicolon >= 0 ? value.substring(0, semicolon).trim() : value.trim();
    }

    private static String parseImport(String line) {
        String value = line.substring("import ".length()).trim();
        if (value.startsWith("static ")) value = value.substring("static ".length()).trim();
        int semicolon = value.indexOf(';');
        return semicolon >= 0 ? value.substring(0, semicolon).trim() : value.trim();
    }

    private static List<String> extractMethodSignatures(String line) {
        List<String> signatures = new ArrayList<>();
        Matcher matcher = ACCESS_MODIFIER.matcher(line);
        while (matcher.find()) {
            int start = matcher.start();
            int openParen = line.indexOf('(', start);
            if (openParen < 0) continue;
            String signature = line.substring(start, openParen).trim();
            if (!isMethodLikeSignature(signature)) continue;
            signatures.add(signature);
        }
        return signatures;
    }

    private static boolean isMethodLikeSignature(String signature) {
        if (signature.isBlank()) return false;
        if (signature.contains("{") || signature.contains("=")) return false;
        String normalized = " " + signature + " ";
        return !normalized.contains(" class ")
                && !normalized.contains(" interface ")
                && !normalized.contains(" enum ")
                && !normalized.contains(" record ");
    }

    private static List methodFacts(String typeName, List<String> methods) {
        List methodFacts = new ArrayList();
        for (String method : methods) {
            Map fact = new LinkedHashMap();
            fact.put("type", typeName);
            fact.put("signature", method);
            methodFacts.add(fact);
        }
        return methodFacts;
    }

    private static List referenceFacts(String typeName, List<String> imports) {
        List referenceFacts = new ArrayList();
        for (String reference : imports) {
            Map fact = new LinkedHashMap();
            fact.put("type", typeName);
            fact.put("reference", reference);
            referenceFacts.add(fact);
        }
        return referenceFacts;
    }

    private static Map typeFact(String typeName, String sourcePath, String pkg, String simple, boolean test) {
        Map fact = new LinkedHashMap();
        fact.put("name", typeName);
        fact.put("simpleName", simple);
        fact.put("package", pkg);
        fact.put("sourceFile", sourcePath);
        fact.put("test", test);
        return fact;
    }

    private static Map packageFact(String pkg, String sourcePath) {
        Map fact = new LinkedHashMap();
        fact.put("package", pkg);
        fact.put("sourceFile", sourcePath);
        return fact;
    }

    private static Map warning(String code, String message) {
        Map warning = new LinkedHashMap();
        warning.put("code", code);
        warning.put("message", message);
        return warning;
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
