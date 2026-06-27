package org.aiknowledge.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TrendAnalyzer {
    private TrendAnalyzer() {}

    static Map snapshot(Map complexity) {
        Map snapshot = new LinkedHashMap();
        snapshot.put("schemaVersion", 1);
        snapshot.put("estimatedContextTokens", number(complexity, "estimatedContextTokens"));
        snapshot.put("conceptRadius", number(complexity, "conceptRadius"));
        snapshot.put("dependencyRadius", number(complexity, "dependencyRadius"));
        snapshot.put("knowledgeDensity", number(complexity, "knowledgeDensity"));
        snapshot.put("contextLocality", number(complexity, "contextLocality"));
        snapshot.put("compressionRatio", number(complexity, "compressionRatio"));
        snapshot.put("aiCognitiveComplexity", number(complexity, "aiCognitiveComplexity"));
        snapshot.put("aiCognitiveDebt", number(complexity, "aiCognitiveDebt"));
        return snapshot;
    }

    static Map trend(ExtractionOptions options, Map currentComplexity) throws IOException {
        Map current = snapshot(currentComplexity);
        Map baseline = loadBaseline(options.seedDirectory());
        Map report = new LinkedHashMap();
        report.put("schemaVersion", 1);
        report.put("baselinePresent", baseline != null);
        report.put("baselinePath", options.reportPath(baselinePath(options.seedDirectory())));
        report.put("current", current);
        report.put("thresholds", thresholds(options));
        List warnings = new ArrayList();
        List violations = new ArrayList();
        if (baseline == null) {
            warnings.add("No complexity baseline found; write metrics-snapshot.json to ai-knowledge/complexity-baseline.json to enable trend checks.");
            report.put("baseline", null);
            report.put("deltas", new LinkedHashMap());
        } else {
            report.put("baseline", baseline);
            Map deltas = deltas(baseline, current);
            report.put("deltas", deltas);
            addViolation(violations, deltas, "aiCognitiveDebt", options.maxCognitiveDebtIncrease(), "AI cognitive debt increased beyond the configured threshold.");
            addViolation(violations, deltas, "conceptRadius", options.maxConceptRadiusIncrease(), "Concept radius increased beyond the configured threshold.");
            addViolation(violations, deltas, "estimatedContextTokens", options.maxContextTokenIncrease(), "Estimated context size increased beyond the configured threshold.");
        }
        report.put("warnings", warnings);
        report.put("violations", violations);
        report.put("violationCount", violations.size());
        report.put("passed", violations.isEmpty());
        return report;
    }

    static Path baselinePath(Path seedDirectory) {
        return seedDirectory.resolve("complexity-baseline.json").toAbsolutePath().normalize();
    }

    private static Map loadBaseline(Path seedDirectory) throws IOException {
        Path baseline = baselinePath(seedDirectory);
        if (!Files.isRegularFile(baseline)) return null;
        String json = Files.readString(baseline, StandardCharsets.UTF_8);
        Map map = new LinkedHashMap();
        putIfPresent(map, json, "estimatedContextTokens");
        putIfPresent(map, json, "conceptRadius");
        putIfPresent(map, json, "dependencyRadius");
        putIfPresent(map, json, "knowledgeDensity");
        putIfPresent(map, json, "contextLocality");
        putIfPresent(map, json, "compressionRatio");
        putIfPresent(map, json, "aiCognitiveComplexity");
        putIfPresent(map, json, "aiCognitiveDebt");
        return map;
    }

    private static void putIfPresent(Map map, String json, String key) {
        String needle = "\"" + key + "\":";
        int index = json.indexOf(needle);
        if (index < 0) return;
        int start = index + needle.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length()) {
            char ch = json.charAt(end);
            if ((ch >= '0' && ch <= '9') || ch == '-' || ch == '+' || ch == '.' || ch == 'E' || ch == 'e') end++; else break;
        }
        if (end == start) return;
        String value = json.substring(start, end);
        try {
            if (value.contains(".") || value.contains("E") || value.contains("e")) map.put(key, Double.parseDouble(value)); else map.put(key, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            // Ignore malformed baseline values. The missing field simply produces a delta from zero.
        }
    }

    private static Map deltas(Map baseline, Map current) {
        Map deltas = new LinkedHashMap();
        delta(deltas, baseline, current, "estimatedContextTokens");
        delta(deltas, baseline, current, "conceptRadius");
        delta(deltas, baseline, current, "dependencyRadius");
        delta(deltas, baseline, current, "knowledgeDensity");
        delta(deltas, baseline, current, "contextLocality");
        delta(deltas, baseline, current, "compressionRatio");
        delta(deltas, baseline, current, "aiCognitiveComplexity");
        delta(deltas, baseline, current, "aiCognitiveDebt");
        return deltas;
    }

    private static void delta(Map deltas, Map baseline, Map current, String key) {
        double before = number(baseline, key);
        double now = number(current, key);
        Map delta = new LinkedHashMap();
        delta.put("baseline", before);
        delta.put("current", now);
        delta.put("absolute", now - before);
        delta.put("percent", before == 0.0d ? (now == 0.0d ? 0.0d : 100.0d) : (now - before) * 100.0d / Math.abs(before));
        deltas.put(key, delta);
    }

    private static void addViolation(List violations, Map deltas, String key, double threshold, String message) {
        if (threshold == Double.MAX_VALUE) return;
        Map delta = (Map) deltas.get(key);
        if (delta == null) return;
        double increase = ((Number) delta.get("absolute")).doubleValue();
        if (increase <= threshold) return;
        Map violation = new LinkedHashMap();
        violation.put("metric", key);
        violation.put("increase", increase);
        violation.put("threshold", threshold);
        violation.put("message", message);
        violations.add(violation);
    }

    private static Map thresholds(ExtractionOptions options) {
        Map thresholds = new LinkedHashMap();
        thresholds.put("maxCognitiveDebtIncrease", options.maxCognitiveDebtIncrease());
        thresholds.put("maxConceptRadiusIncrease", options.maxConceptRadiusIncrease());
        thresholds.put("maxContextTokenIncrease", options.maxContextTokenIncrease());
        thresholds.put("failOnWarnings", options.failOnWarnings());
        return thresholds;
    }

    private static double number(Map map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) return number.doubleValue();
        return 0.0d;
    }
}
