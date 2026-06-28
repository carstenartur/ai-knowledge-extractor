package org.aiknowledge.core.repositoryscan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.aiknowledge.core.RepositorySnapshot;

public final class RepositoryEvidenceScanner {
    public void extract(Path root, Path file, String path, RepositorySnapshot snapshot) throws IOException {
        if (path.startsWith("docs/generated/discovery/") && path.endsWith("/evidence.json")) addDiscoveryEvidence(path, file, snapshot);
        if (path.contains("/src/jmh/java/") && path.endsWith(".java")) addBenchmarkSourceEvidence(path, snapshot);
        if (path.startsWith(".github/workflows/") && (path.endsWith(".yml") || path.endsWith(".yaml"))) addWorkflowEvidence(path, file, snapshot);
    }

    private static void addDiscoveryEvidence(String path, Path file, RepositorySnapshot snapshot) throws IOException {
        String text = read(file);
        Map item = new LinkedHashMap();
        item.put("type", "discovery-evidence");
        item.put("path", path);
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

    private static void addWorkflowEvidence(String path, Path file, RepositorySnapshot snapshot) throws IOException {
        String text = read(file);
        Map item = new LinkedHashMap();
        item.put("type", "github-workflow");
        item.put("path", path);
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
            if (isYamlKey(trimmed, "jobs")) {
                inJobs = true;
                continue;
            }
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

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
