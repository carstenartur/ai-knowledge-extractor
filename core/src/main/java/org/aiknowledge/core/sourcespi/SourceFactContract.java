package org.aiknowledge.core.sourcespi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned, JSON-compatible fact contract, independent of the artifact schema. */
public final class SourceFactContract {
    public static final String VERSION = "1.0";
    public static final String ID = "id";
    public static final String KIND = "kind";
    public static final String LANGUAGE = "language";
    public static final String SOURCE = "source";
    public static final String TARGET = "target";
    public static final String SOURCE_FILE = "sourceFile";
    public static final String PROVIDER = "provider";
    public static final String CONFIDENCE = "confidence";
    public static final String COMPLEXITY_PROVIDER = "complexityProvider";
    public static final String COMPLEXITY_ACCURACY = "complexityAccuracy";
    public static final String SOURCE_UNIT_IMPORTS_MODULE = "SOURCE_UNIT_IMPORTS_MODULE";
    public static final String CALLABLE_CALLS_BOUNDARY = "CALLABLE_CALLS_BOUNDARY";
    public static final String CALLABLE_EXPOSES_BOUNDARY = "CALLABLE_EXPOSES_BOUNDARY";
    public static final String SOURCE_UNIT_DECLARES_SYMBOL = "SOURCE_UNIT_DECLARES_SYMBOL";

    private SourceFactContract() { }

    /** Accepts additive minor versions; a different major requires a compatible extractor. */
    public static void requireCompatible(String providerId, String version) {
        if (version == null || !version.matches("1\\.[0-9]+")) {
            throw new ContractException("Source provider " + providerId + " requires fact contract "
                    + version + "; this extractor supports major 1 (" + VERSION
                    + "). Install a compatible provider or upgrade the extractor.");
        }
    }

    /** Validates every category before admitting any facts; preserves unknown additive fields. */
    public static SourceKnowledgeResult normalize(
            SourceKnowledgeResult result, String providerId, String sourcePath) {
        if (result == null) throw new ContractException("Source provider " + providerId + " returned null while analyzing " + sourcePath);
        return new SourceKnowledgeResult(
                facts(result.sourceUnitFacts(), providerId, sourcePath, ID, KIND, LANGUAGE),
                facts(result.symbolFacts(), providerId, sourcePath, ID, KIND),
                facts(result.relationFacts(), providerId, sourcePath, KIND, SOURCE, TARGET),
                facts(result.boundaryFacts(), providerId, sourcePath, ID, KIND, "protocol"),
                facts(result.warningFacts(), providerId, sourcePath, "code", "message"));
    }

    private static List<Map<String, Object>> facts(List<Map<String, Object>> facts,
            String providerId, String sourcePath, String... required) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> raw : facts) {
            String context = providerId + " while analyzing '" + sourcePath + "' (sourceFile="
                    + raw.getOrDefault(SOURCE_FILE, sourcePath) + ")";
            Map<String, Object> fact = new LinkedHashMap<>(raw);
            fact.putIfAbsent(SOURCE_FILE, sourcePath);
            fact.putIfAbsent(PROVIDER, providerId);
            fact.putIfAbsent(CONFIDENCE, "provider-defined");
            for (String key : required) requireText(fact, key, context);
            for (String key : List.of(SOURCE_FILE, PROVIDER, CONFIDENCE)) requireText(fact, key, context);
            String path = (String) fact.get(SOURCE_FILE);
            if (path.startsWith("/") || path.contains("\\") || path.matches("^[A-Za-z]:.*")
                    || List.of(path.split("/", -1)).contains("..")) {
                throw new ContractException("Source provider " + context
                        + " must emit repository-relative sourceFile paths");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> copy = (Map<String, Object>) json(fact, context, 0);
            normalized.add(copy);
        }
        return List.copyOf(normalized);
    }

    private static void requireText(Map<String, Object> fact, String field, String providerId) {
        if (!(fact.get(field) instanceof String value) || value.isBlank()) {
            throw new ContractException("Source provider " + providerId
                    + " emitted a fact without required string field " + field);
        }
    }

    private static Object json(Object value, String providerId, int depth) {
        if (depth > 64) throw new ContractException("Source provider " + providerId + " emitted deeply nested facts");
        if (value == null || value instanceof String || value instanceof Boolean) return value;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof java.math.BigInteger || value instanceof java.math.BigDecimal) return value;
        if (value instanceof Double number && Double.isFinite(number)) return value;
        if (value instanceof Float number && Float.isFinite(number)) return value;
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) copy.add(json(item, providerId, depth + 1));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new java.util.TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new ContractException("Source provider " + providerId + " emitted a non-string JSON key");
                }
                copy.put(key, json(entry.getValue(), providerId, depth + 1));
            }
            return Collections.unmodifiableMap(copy);
        }
        throw new ContractException("Source provider " + providerId + " emitted a non-JSON fact value");
    }

    /** Fatal provider-contract violation, independent of recoverable source parser errors. */
    public static final class ContractException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        public ContractException(String message) { super(message); }
    }
}
