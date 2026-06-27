package org.aiknowledge.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EmpiricalBenchmarkSupport {
    private EmpiricalBenchmarkSupport() {}

    static Map<String, Object> report(ExtractionOptions options) throws IOException {
        Map<String, Object> empirical = new LinkedHashMap<>();
        empirical.put("enabled", options.empiricalBenchmarkEnabled());
        Path fixtureFile = options.empiricalBenchmarkFixtureFile();
        empirical.put("fixtureFile", fixtureFile.toString());
        if (!options.empiricalBenchmarkEnabled()) {
            empirical.put("fixtureCount", 0);
            empirical.put("results", new ArrayList<>());
            empirical.put("summary", summary(0, 0, 0.0d, 0.0d, 0, 0, 0));
            return empirical;
        }

        if (!Files.isRegularFile(fixtureFile)) {
            throw new IOException("Empirical benchmark fixture file not found: " + fixtureFile);
        }
        List<Map<String, Object>> fixtures = parse(fixtureFile);
        List<Map<String, Object>> results = new ArrayList<>();
        int tokens = 0;
        int latency = 0;
        double quality = 0.0d;
        int duplicates = 0;
        int missed = 0;
        int success = 0;
        for (int i = 0; i < fixtures.size(); i++) {
            Map<String, Object> fixture = fixtures.get(i);
            Map<String, Object> result = evaluate(fixture, i + 1);
            results.add(result);
            tokens += ((Number) result.get("tokenUsage")).intValue();
            latency += ((Number) result.get("latencyMs")).intValue();
            quality += ((Number) result.get("reviewQuality")).doubleValue();
            duplicates += ((Number) result.get("duplicateSuggestions")).intValue();
            List<Object> missedExisting = listValue(result.get("missedExistingFeatures"));
            missed += missedExisting.size();
            if (Boolean.TRUE.equals(result.get("taskSuccess"))) success++;
        }
        empirical.put("fixtureCount", fixtures.size());
        empirical.put("results", results);
        empirical.put("summary", summary(fixtures.size(), tokens, quality, latency, duplicates, missed, success));
        return empirical;
    }

    private static Map<String, Object> evaluate(Map<String, Object> fixture, int index) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", fixture.getOrDefault("id", "fixture-" + index));
        result.put("profile", fixture.getOrDefault("profile", "review"));
        int tokenUsage = intValue(fixture.get("tokenUsage"), intValue(fixture.get("tokens"), 0));
        int latencyMs = intValue(fixture.get("latencyMs"), 0);
        double reviewQuality = boundedQuality(doubleValue(fixture.get("reviewQuality"), 0.0d));
        List<Object> suggestions = listValue(fixture.get("suggestions"));
        List<Object> existingFeatures = listValue(fixture.get("existingFeatures"));
        List<Object> suggestedFeatures = listValue(fixture.get("suggestedFeatures"));
        int duplicateSuggestions = duplicateCount(suggestions);
        List<Object> missedExistingFeatures = missed(existingFeatures, suggestedFeatures);
        boolean baselineSuccess = boolValue(fixture.get("taskSuccess"), true);
        boolean taskSuccess = baselineSuccess && missedExistingFeatures.isEmpty();
        result.put("tokenUsage", tokenUsage);
        result.put("latencyMs", latencyMs);
        result.put("reviewQuality", reviewQuality);
        result.put("duplicateSuggestions", duplicateSuggestions);
        result.put("missedExistingFeatures", missedExistingFeatures);
        result.put("taskSuccess", taskSuccess);
        return result;
    }

    private static Map<String, Object> summary(int fixtures, int tokenUsage, double quality, double totalLatency, int duplicates, int missed, int success) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("averageTokenUsage", roundedAverage(tokenUsage, fixtures));
        summary.put("averageLatencyMs", roundedAverage(totalLatency, fixtures));
        summary.put("averageReviewQuality", fixtures == 0 ? 0.0d : quality / fixtures);
        summary.put("totalDuplicateSuggestions", duplicates);
        summary.put("totalMissedExistingFeatures", missed);
        summary.put("taskSuccessRate", fixtures == 0 ? 0.0d : success / (double) fixtures);
        return summary;
    }

    private static int roundedAverage(double total, int count) {
        if (count <= 0) return 0;
        return (int) Math.round(total / count);
    }

    private static int duplicateCount(List<Object> suggestions) {
        Set<String> seen = new LinkedHashSet<>();
        int duplicates = 0;
        for (Object suggestion : suggestions) {
            if (!seen.add(String.valueOf(suggestion))) duplicates++;
        }
        return duplicates;
    }

    private static List<Object> missed(List<Object> existingFeatures, List<Object> suggestedFeatures) {
        Set<String> suggested = new LinkedHashSet<>();
        for (Object item : suggestedFeatures) suggested.add(String.valueOf(item));
        Set<String> missed = new LinkedHashSet<>();
        for (Object item : existingFeatures) {
            String feature = String.valueOf(item);
            if (!suggested.contains(feature)) missed.add(feature);
        }
        return new ArrayList<>(missed);
    }

    private static List<Map<String, Object>> parse(Path file) throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> current = null;
        for (String raw : Files.readAllLines(file)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("- ")) {
                current = new LinkedHashMap<>();
                result.add(current);
                line = line.substring(2).trim();
            }
            if (current == null || !line.contains(":")) continue;
            int idx = line.indexOf(':');
            current.put(line.substring(0, idx).trim(), parseValue(line.substring(idx + 1).trim()));
        }
        return result;
    }

    private static Object parseValue(String value) {
        String trimmed = stripQuotes(value.trim());
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            List<Object> list = new ArrayList<>();
            String body = trimmed.substring(1, trimmed.length() - 1).trim();
            if (!body.isEmpty()) for (String part : body.split(",")) list.add(stripQuotes(part.trim()));
            return list;
        }
        if ("true".equalsIgnoreCase(trimmed)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(trimmed)) return Boolean.FALSE;
        try {
            if (trimmed.contains(".")) return Double.parseDouble(trimmed);
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {
            return trimmed;
        }
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) return value.substring(1, value.length() - 1);
        return value;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) return Math.max(0, number.intValue());
        if (value == null) return fallback;
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return fallback;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double boundedQuality(double value) {
        if (value < 0.0d) return 0.0d;
        if (value > 1.0d) return 1.0d;
        return value;
    }

    private static boolean boolValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) return true;
        if ("false".equalsIgnoreCase(text)) return false;
        return fallback;
    }

    private static List<Object> listValue(Object value) {
        if (value instanceof List<?> list) return new ArrayList<>(list);
        if (value == null) return new ArrayList<>();
        List<Object> list = new ArrayList<>();
        list.add(value);
        return list;
    }
}
