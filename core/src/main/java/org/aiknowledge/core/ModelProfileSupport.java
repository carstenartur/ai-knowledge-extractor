package org.aiknowledge.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ModelProfileSupport {
    private ModelProfileSupport() {}

    static List<Map> profiles(ExtractionOptions options) throws IOException {
        List<Map> profiles = new ArrayList<>(defaults());
        Path directory = options.modelProfileDirectory();
        if (directory == null || !Files.isDirectory(directory)) return profiles;
        merge(directory.resolve("model-profiles.yaml"), profiles);
        merge(directory.resolve("model-profiles.yml"), profiles);
        return profiles;
    }

    static List<Map> profileMetrics(ExtractionOptions options, int estimatedTokens) throws IOException {
        List<Map> result = new ArrayList<>();
        for (Map profile : profiles(options)) result.add(metric(profile, estimatedTokens));
        return result;
    }

    private static List<Map> defaults() {
        List<Map> result = new ArrayList<>();
        result.add(profile("raw", 128000, 256000, 1.0d, "minimal compaction for maximal context retention"));
        result.add(profile("compact", 32000, 64000, 0.35d, "aggressive compaction for constrained model budgets"));
        result.add(profile("review", 128000, 256000, 0.6d, "balanced context for code review tasks"));
        result.add(profile("architecture", 192000, 384000, 0.75d, "broader architecture-level context"));
        result.add(profile("deep-research", 256000, 1000000, 0.85d, "maximum breadth for deep repository exploration"));
        return result;
    }

    private static Map profile(String id, int practicalBudget, int hardLimit, double compressionRatio, String preference) {
        Map map = new LinkedHashMap();
        map.put("id", id);
        map.put("practicalContextBudget", practicalBudget);
        map.put("hardContextLimit", hardLimit);
        map.put("targetCompressionRatio", compressionRatio);
        map.put("compressionPreference", preference);
        return map;
    }

    private static Map metric(Map profile, int estimatedTokens) {
        String id = String.valueOf(profile.get("id"));
        List warnings = new ArrayList();
        int practicalBudget = positiveBudget(profile.get("practicalContextBudget"), 128000, "practical context budget", warnings);
        int hardLimit = positiveBudget(profile.get("hardContextLimit"), practicalBudget, "hard context limit", warnings);
        if (hardLimit < practicalBudget) {
            warnings.add("Configured hard context limit is below the practical budget; using the practical budget.");
            hardLimit = practicalBudget;
        }
        double fallbackRatio = practicalBudget <= 0 ? 1.0d : Math.min(1.0d, practicalBudget / Math.max(1.0d, estimatedTokens));
        double targetCompressionRatio = clampedRatio(profile.get("targetCompressionRatio"), fallbackRatio, warnings);
        int compressedTokens = Math.max(0, (int) Math.round(estimatedTokens * targetCompressionRatio));
        if (estimatedTokens > practicalBudget) warnings.add("Estimated raw context exceeds the practical budget.");
        if (estimatedTokens > hardLimit) warnings.add("Estimated raw context exceeds the hard context limit.");
        if (compressedTokens > practicalBudget) warnings.add("Target compression still exceeds the practical budget.");
        if (compressedTokens > hardLimit) warnings.add("Target compression still exceeds the hard context limit.");
        Map metric = new LinkedHashMap();
        metric.put("id", id);
        metric.put("practicalContextBudget", practicalBudget);
        metric.put("hardContextLimit", hardLimit);
        metric.put("targetCompressionRatio", targetCompressionRatio);
        metric.put("compressionPreference", profile.getOrDefault("compressionPreference", "balanced"));
        metric.put("estimatedRawTokens", estimatedTokens);
        metric.put("estimatedCompressedTokens", compressedTokens);
        metric.put("fitsPracticalBudget", estimatedTokens <= practicalBudget);
        metric.put("fitsHardLimit", estimatedTokens <= hardLimit);
        metric.put("compressedFitsPracticalBudget", compressedTokens <= practicalBudget);
        metric.put("compressedFitsHardLimit", compressedTokens <= hardLimit);
        metric.put("warnings", warnings);
        metric.put("warningCount", warnings.size());
        return metric;
    }

    private static void merge(Path file, List<Map> profiles) throws IOException {
        if (!Files.isRegularFile(file)) return;
        for (Map item : parse(file)) {
            Object id = item.get("id");
            if (id == null) continue;
            Map existing = find(profiles, String.valueOf(id));
            if (existing == null) profiles.add(normalize(item)); else existing.putAll(normalize(item));
        }
    }

    private static Map normalize(Map source) {
        Map target = new LinkedHashMap(source);
        if (!target.containsKey("practicalContextBudget") && target.containsKey("practicalBudget")) target.put("practicalContextBudget", target.get("practicalBudget"));
        if (!target.containsKey("targetCompressionRatio") && target.containsKey("compressionRatio")) target.put("targetCompressionRatio", target.get("compressionRatio"));
        return target;
    }

    private static Map find(List<Map> profiles, String id) {
        for (Map profile : profiles) if (id.equals(String.valueOf(profile.get("id")))) return profile;
        return null;
    }

    private static List<Map> parse(Path file) throws IOException {
        List<Map> result = new ArrayList<>();
        Map current = null;
        for (String raw : Files.readAllLines(file)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("- ")) {
                current = new LinkedHashMap();
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
        if (trimmed.isEmpty()) return "";
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

    private static int positiveBudget(Object value, int fallback, String label, List warnings) {
        int parsed = intValue(value, fallback);
        if (parsed > 0) return parsed;
        int normalizedFallback = Math.max(1, fallback);
        warnings.add("Configured " + label + " must be positive; using " + normalizedFallback + ".");
        return normalizedFallback;
    }

    private static double clampedRatio(Object value, double fallback, List warnings) {
        double parsed = doubleValue(value, fallback);
        if (parsed < 0.0d) {
            warnings.add("Configured target compression ratio is below 0; using 0.");
            return 0.0d;
        }
        if (parsed > 1.0d) {
            warnings.add("Configured target compression ratio is above 1; using 1.");
            return 1.0d;
        }
        return parsed;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return fallback;
        try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; }
    }

    private static double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return fallback;
        try { return Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; }
    }
}
