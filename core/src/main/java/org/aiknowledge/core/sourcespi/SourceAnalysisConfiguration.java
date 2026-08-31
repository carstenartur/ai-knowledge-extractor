package org.aiknowledge.core.sourcespi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared source-analysis policy for Gradle, Maven and direct core consumers.
 *
 * <p>The policy is intentionally independent of a build-tool extension. Both plugins and direct
 * callers therefore use the same {@code -Daiknowledge.source.*} property names. Providers remain
 * responsible for deciding whether a file extension or source kind is supported; this class owns
 * repository-wide admission, budgets and failure policy.</p>
 *
 * <p>Instances are stateful because file-count and byte budgets apply once per repository scan.
 * A configuration instance belongs to one extraction pipeline and must not be reused for another
 * repository.</p>
 */
public final class SourceAnalysisConfiguration {
    public static final String PREFIX = "aiknowledge.source.";

    private static final String DEFAULT_EXCLUDES = String.join(",",
            "**/node_modules/**",
            "**/build/**",
            "**/target/**",
            "**/out/**",
            "**/dist/**",
            "**/coverage/**",
            "**/.next/**",
            "**/.nuxt/**",
            "**/.cache/**",
            "**/.parcel-cache/**",
            "**/*.min.js",
            "**/*.min.css",
            "**/*.map");

    public enum ErrorPolicy {
        FAIL,
        WARN,
        SKIP;

        static ErrorPolicy parse(String value) {
            if (value == null || value.isBlank()) return WARN;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Unsupported " + PREFIX + "errorPolicy=" + value
                                + "; expected fail, warn or skip.", exception);
            }
        }
    }

    private final Set<String> enabledProviders;
    private final Set<String> disabledProviders;
    private final List<Pattern> includes;
    private final List<Pattern> excludes;
    private final Set<String> ignoredDirectories;
    private final long maxFileBytes;
    private final int maxFiles;
    private final long maxTotalBytes;
    private final boolean includeGenerated;
    private final ErrorPolicy errorPolicy;
    private final Map<String, Boolean> admissionDecisions = new LinkedHashMap<>();
    private int admittedFiles;
    private long admittedBytes;

    private SourceAnalysisConfiguration(
            Set<String> enabledProviders,
            Set<String> disabledProviders,
            List<Pattern> includes,
            List<Pattern> excludes,
            Set<String> ignoredDirectories,
            long maxFileBytes,
            int maxFiles,
            long maxTotalBytes,
            boolean includeGenerated,
            ErrorPolicy errorPolicy) {
        this.enabledProviders = Set.copyOf(enabledProviders);
        this.disabledProviders = Set.copyOf(disabledProviders);
        this.includes = List.copyOf(includes);
        this.excludes = List.copyOf(excludes);
        this.ignoredDirectories = Set.copyOf(ignoredDirectories);
        this.maxFileBytes = maxFileBytes;
        this.maxFiles = maxFiles;
        this.maxTotalBytes = maxTotalBytes;
        this.includeGenerated = includeGenerated;
        this.errorPolicy = errorPolicy;
    }

    public static SourceAnalysisConfiguration fromSystemProperties() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith(PREFIX)) values.put(name, System.getProperty(name));
        }
        return from(values);
    }

    /** Creates a policy from the same full property names accepted on the command line. */
    public static SourceAnalysisConfiguration from(Map<String, String> properties) {
        Map<String, String> values = properties == null ? Map.of() : properties;
        return new SourceAnalysisConfiguration(
                set(values.get(PREFIX + "enabledProviders")),
                set(values.get(PREFIX + "disabledProviders")),
                patterns(values.get(PREFIX + "includes")),
                patterns(join(DEFAULT_EXCLUDES, values.get(PREFIX + "excludes"))),
                lowerSet(values.get(PREFIX + "ignoredDirectories")),
                positiveLong(values.get(PREFIX + "maxFileBytes"), 2_000_000L, "maxFileBytes"),
                positiveInt(values.get(PREFIX + "maxFiles"), 100_000, "maxFiles"),
                positiveLong(values.get(PREFIX + "maxTotalBytes"), 500_000_000L, "maxTotalBytes"),
                Boolean.parseBoolean(values.getOrDefault(PREFIX + "includeGenerated", "false")),
                ErrorPolicy.parse(values.get(PREFIX + "errorPolicy")));
    }

    public boolean providerEnabled(String providerId) {
        String id = providerId == null ? "" : providerId.trim();
        if (id.isEmpty() || disabledProviders.contains(id)) return false;
        return enabledProviders.isEmpty() || enabledProviders.contains(id);
    }

    /**
     * Admits a supported source file once and applies repository-wide budgets.
     * Repeated calls for the same path return the original admission result without consuming the
     * budget again, allowing multiple focused providers to analyse one file.
     */
    public synchronized boolean acceptsSource(Path sourceFile, String sourcePath) throws IOException {
        String normalized = normalize(sourcePath);
        if (admissionDecisions.containsKey(normalized)) return admissionDecisions.get(normalized);
        if (ignoredDirectory(normalized)) return reject(normalized);
        if (!includeGenerated && generated(normalized)) return reject(normalized);
        if (!includes.isEmpty() && includes.stream().noneMatch(pattern -> pattern.matcher(normalized).matches())) {
            return reject(normalized);
        }
        if (excludes.stream().anyMatch(pattern -> pattern.matcher(normalized).matches())) {
            return reject(normalized);
        }

        long size;
        try {
            size = Files.size(sourceFile);
        } catch (IOException exception) {
            admissionDecisions.put(normalized, false);
            throw exception;
        }
        if (size > maxFileBytes || admittedFiles >= maxFiles || admittedBytes + size > maxTotalBytes) {
            return reject(normalized);
        }
        admissionDecisions.put(normalized, true);
        admittedFiles++;
        admittedBytes += size;
        return true;
    }

    public ErrorPolicy errorPolicy() {
        return errorPolicy;
    }

    public Map<String, Object> asEvidence() {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("type", "source-analysis-configuration");
        evidence.put("enabledProviders", enabledProviders.stream().sorted().toList());
        evidence.put("disabledProviders", disabledProviders.stream().sorted().toList());
        evidence.put("maxFileBytes", maxFileBytes);
        evidence.put("maxFiles", maxFiles);
        evidence.put("maxTotalBytes", maxTotalBytes);
        evidence.put("includeGenerated", includeGenerated);
        evidence.put("errorPolicy", errorPolicy.name().toLowerCase(Locale.ROOT));
        evidence.put("admittedFiles", admittedFiles);
        evidence.put("admittedBytes", admittedBytes);
        evidence.put("versionControlHistoryUsed", false);
        return evidence;
    }

    private boolean reject(String path) {
        admissionDecisions.put(path, false);
        return false;
    }

    private boolean ignoredDirectory(String path) {
        if (ignoredDirectories.isEmpty()) return false;
        for (String segment : path.toLowerCase(Locale.ROOT).split("/")) {
            if (ignoredDirectories.contains(segment)) return true;
        }
        return false;
    }

    private static boolean generated(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains("/generated/")
                || lower.startsWith("generated/")
                || lower.contains("/gen/")
                || lower.endsWith(".generated.js")
                || lower.endsWith(".generated.ts")
                || lower.endsWith(".g.js")
                || lower.endsWith(".g.ts");
    }

    private static List<Pattern> patterns(String value) {
        List<Pattern> result = new ArrayList<>();
        for (String glob : split(value)) result.add(Pattern.compile(globRegex(glob)));
        return List.copyOf(result);
    }

    static String globRegex(String glob) {
        String value = normalize(glob);
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '*') {
                boolean doubleStar = index + 1 < value.length() && value.charAt(index + 1) == '*';
                if (doubleStar) {
                    index++;
                    if (index + 1 < value.length() && value.charAt(index + 1) == '/') {
                        index++;
                        regex.append("(?:.*/)?");
                    } else {
                        regex.append(".*");
                    }
                } else {
                    regex.append("[^/]*");
                }
            } else if (current == '?') {
                regex.append("[^/]");
            } else {
                if (".()[]{}+$^|\\".indexOf(current) >= 0) regex.append('\\');
                regex.append(current);
            }
        }
        return regex.append('$').toString();
    }

    private static Set<String> set(String value) {
        return new LinkedHashSet<>(split(value));
    }

    private static Set<String> lowerSet(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (String item : split(value)) result.add(item.toLowerCase(Locale.ROOT));
        return result;
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String item : value.split("[,;\\n]")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    private static String join(String first, String second) {
        if (second == null || second.isBlank()) return first;
        return first + "," + second;
    }

    private static int positiveInt(String value, int fallback, String name) {
        long parsed = positiveLong(value, fallback, name);
        if (parsed > Integer.MAX_VALUE) throw new IllegalArgumentException(PREFIX + name + " is too large");
        return (int) parsed;
    }

    private static long positiveLong(String value, long fallback, String name) {
        if (value == null || value.isBlank()) return fallback;
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed <= 0) throw new NumberFormatException("must be positive");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(PREFIX + name + " must be a positive integer", exception);
        }
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/').trim();
        while (normalized.startsWith("./")) normalized = normalized.substring(2);
        return normalized;
    }
}
