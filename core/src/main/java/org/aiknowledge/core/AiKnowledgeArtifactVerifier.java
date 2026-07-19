package org.aiknowledge.core;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Verifies the stable cross-plugin artifact contract emitted by the extractor.
 *
 * <p>This class validates structural integrity and cross-document consistency.
 * Consumer-specific thresholds remain owned by the configured quality gate.</p>
 */
public final class AiKnowledgeArtifactVerifier {
    private static final String CONTEXT_METHOD =
        "line-weighted-prioritized-capability-selector-working-set-proxy";

    private static final Map<String, String> ENVELOPES = Map.ofEntries(
        Map.entry("modules.json", "modules"),
        Map.entry("classes.json", "classes"),
        Map.entry("tests.json", "tests"),
        Map.entry("docs.json", "docs"),
        Map.entry("dependencies.json", "dependencies"),
        Map.entry("capabilities.json", "capabilities"),
        Map.entry("claims.json", "claims"),
        Map.entry("evidence.json", "evidence"));

    private static final List<String> QUALITY_JSON = List.of(
        "index.json",
        "modules.json",
        "classes.json",
        "tests.json",
        "docs.json",
        "dependencies.json",
        "capabilities.json",
        "claims.json",
        "evidence.json",
        "context-packs/index.json",
        "complexity.json",
        "metrics-snapshot.json",
        "trend.json",
        "check.json");

    private static final List<String> QUALITY_TEXT = List.of(
        "review-context.md", "complexity.html", "trend.html");

    private static final List<String> COMPLETE_JSON = List.of(
        "optimization.json", "benchmark.json");

    private static final List<String> COMPLETE_TEXT = List.of(
        "optimization.html", "benchmark.html");

    public VerificationReport verifyQualityGate(Path outputDirectory) {
        return verify(outputDirectory, Profile.QUALITY_GATE);
    }

    public VerificationReport verifyCompleteLifecycle(Path outputDirectory) {
        return verify(outputDirectory, Profile.COMPLETE_LIFECYCLE);
    }

    public VerificationReport verify(Path outputDirectory, Profile profile) {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(profile, "profile");
        Path root = outputDirectory.toAbsolutePath().normalize();
        List<String> errors = new ArrayList<>();
        Map<String, Object> documents = new LinkedHashMap<>();

        if (!Files.isDirectory(root)) {
            errors.add("artifact root does not exist or is not a directory: " + root);
            return new VerificationReport(profile, root, errors, List.of());
        }

        QUALITY_JSON.forEach(file -> readJson(root, file, documents, errors));
        QUALITY_TEXT.forEach(file -> readText(root, file, errors));
        if (profile == Profile.COMPLETE_LIFECYCLE) {
            COMPLETE_JSON.forEach(file -> readJson(root, file, documents, errors));
            COMPLETE_TEXT.forEach(file -> readText(root, file, errors));
        }

        validateEnvelopesAndCounts(documents, errors);
        validateReviewContext(root, errors);
        validateContextPacks(root, documents, errors);
        validateComplexity(documents, errors);
        validateCheck(documents, errors);
        validateNonEmptyObject(documents, "metrics-snapshot.json", errors);
        validateNonEmptyObject(documents, "trend.json", errors);
        if (profile == Profile.COMPLETE_LIFECYCLE) {
            validateNonEmptyObject(documents, "optimization.json", errors);
            validateNonEmptyObject(documents, "benchmark.json", errors);
        }

        return new VerificationReport(profile, root, errors, List.of());
    }

    private static void validateEnvelopesAndCounts(
            Map<String, Object> documents,
            List<String> errors) {
        Map<String, Integer> actualCounts = new LinkedHashMap<>();
        ENVELOPES.forEach((file, key) -> {
            Map<String, Object> envelope = object(documents.get(file), file, errors);
            actualCounts.put(key, array(envelope.get(key), file + "." + key, errors).size());
        });

        Map<String, Object> index = object(documents.get("index.json"), "index.json", errors);
        requireInteger(index.get("schemaVersion"), 1, "index.json.schemaVersion", errors);
        requireText(index.get("repository"), "index.json.repository", errors);
        requireTextValue(index.get("generationMode"), "deterministic-static",
            "index.json.generationMode", errors);
        Map<String, Object> counts = object(index.get("counts"), "index.json.counts", errors);
        actualCounts.forEach((name, expected) -> {
            Integer actual = integer(counts.get(name));
            if (actual == null) {
                errors.add("index.json.counts." + name + " must be an integer");
            } else if (!actual.equals(expected)) {
                errors.add("index.json count mismatch for " + name
                    + ": expected " + expected + " but found " + actual);
            }
        });
    }

    private static void validateReviewContext(Path root, List<String> errors) {
        Path file = requiredFile(root, "review-context.md", errors);
        if (file == null) return;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (!text.contains("# AI Knowledge Review Context")) {
                errors.add("review-context.md lacks its canonical title");
            }
            if (!text.contains("## Repository Overview")) {
                errors.add("review-context.md lacks repository overview");
            }
        } catch (IOException exception) {
            errors.add("cannot read review-context.md: " + exception.getMessage());
        }
    }

    private static void validateContextPacks(
            Path root,
            Map<String, Object> documents,
            List<String> errors) {
        Map<String, Object> capabilityEnvelope = object(
            documents.get("capabilities.json"), "capabilities.json", errors);
        Set<String> capabilityIds = ids(
            array(capabilityEnvelope.get("capabilities"),
                "capabilities.json.capabilities", errors),
            "capabilities.json.capabilities", errors);

        Map<String, Object> index = object(
            documents.get("context-packs/index.json"),
            "context-packs/index.json", errors);
        List<Object> entries = array(index.get("contextPacks"),
            "context-packs/index.json.contextPacks", errors);
        Set<String> indexedIds = new LinkedHashSet<>();
        Set<String> indexedFiles = new LinkedHashSet<>();

        for (int position = 0; position < entries.size(); position++) {
            String location = "context-packs/index.json.contextPacks[" + position + "]";
            Map<String, Object> entry = object(entries.get(position), location, errors);
            String id = requireText(entry.get("id"), location + ".id", errors);
            String label = requireText(entry.get("label"), location + ".label", errors);
            String status = requireText(entry.get("status"), location + ".status", errors);
            String fileName = requireText(entry.get("file"), location + ".file", errors);
            Integer tokenEstimate = integer(entry.get("tokenEstimate"));
            if (tokenEstimate == null || tokenEstimate < 0) {
                errors.add(location + ".tokenEstimate must be a non-negative integer");
            }
            requireText(entry.get("intendedUse"), location + ".intendedUse", errors);
            if (id == null || fileName == null) continue;
            if (!indexedIds.add(id)) errors.add("duplicate context-pack id: " + id);
            if (!indexedFiles.add(fileName)) errors.add("duplicate context-pack file: " + fileName);

            String expectedFile = "context-packs/" + id + ".json";
            if (!expectedFile.equals(fileName)) {
                errors.add(location + ".file must be " + expectedFile);
                continue;
            }
            Path file = requiredFile(root, fileName, errors);
            if (file == null) continue;
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                Map<String, Object> pack = object(StrictJsonReader.parse(json), fileName, errors);
                requireTextValue(pack.get("id"), id, fileName + ".id", errors);
                if (label != null) requireTextValue(pack.get("label"), label, fileName + ".label", errors);
                if (status != null) requireTextValue(pack.get("status"), status, fileName + ".status", errors);
                for (String field : List.of(
                        "modules", "types", "tests", "docs", "evidence",
                        "claims", "suggestedFiles")) {
                    array(pack.get(field), fileName + "." + field, errors);
                }
                if (pack.containsKey("warnings")) {
                    array(pack.get("warnings"), fileName + ".warnings", errors);
                }
                if (tokenEstimate != null && tokenEstimate != json.length() / 4) {
                    errors.add(location + ".tokenEstimate does not match " + fileName);
                }
            } catch (IOException | IllegalArgumentException exception) {
                errors.add("cannot parse " + fileName + ": " + exception.getMessage());
            }
        }

        Set<String> missing = new LinkedHashSet<>(capabilityIds);
        missing.removeAll(indexedIds);
        Set<String> unexpected = new LinkedHashSet<>(indexedIds);
        unexpected.removeAll(capabilityIds);
        if (!missing.isEmpty()) errors.add("context-pack index is missing capability ids: " + missing);
        if (!unexpected.isEmpty()) errors.add("context-pack index has unexpected ids: " + unexpected);
    }

    private static Set<String> ids(
            List<Object> entries,
            String location,
            List<String> errors) {
        Set<String> result = new LinkedHashSet<>();
        for (int position = 0; position < entries.size(); position++) {
            Map<String, Object> entry = object(entries.get(position),
                location + "[" + position + "]", errors);
            String id = text(entry.get("id"));
            if (id == null || id.isBlank()) continue;
            if (!result.add(id)) errors.add("duplicate capability id: " + id);
        }
        return result;
    }

    private static void validateComplexity(
            Map<String, Object> documents,
            List<String> errors) {
        Map<String, Object> complexity = object(
            documents.get("complexity.json"), "complexity.json", errors);
        object(complexity.get("codeComplexity"),
            "complexity.json.codeComplexity", errors);
        Map<String, Object> costDrivers = object(complexity.get("aiCostDrivers"),
            "complexity.json.aiCostDrivers", errors);
        object(costDrivers.get("tokenCostDrivers"),
            "complexity.json.aiCostDrivers.tokenCostDrivers", errors);

        Map<String, Object> footprint = object(complexity.get("contextFootprint"),
            "complexity.json.contextFootprint", errors);
        requireInteger(footprint.get("schemaVersion"), 3,
            "complexity.json.contextFootprint.schemaVersion", errors);
        String status = requireText(footprint.get("measurementStatus"),
            "complexity.json.contextFootprint.measurementStatus", errors);
        if (status != null && !Set.of("MEASURED", "NO_CAPABILITY_SAMPLES").contains(status)) {
            errors.add("unsupported context-footprint measurementStatus: " + status);
        }
        requireTextValue(footprint.get("method"), CONTEXT_METHOD,
            "complexity.json.contextFootprint.method", errors);

        for (String field : List.of(
                "repositoryContextTokens", "productionContextTokens",
                "testEvidenceTokens", "documentationContextTokens",
                "productionLines", "totalContextLines",
                "medianCapabilityWorkingSetTokens", "p90CapabilityWorkingSetTokens",
                "capabilityCount", "capabilitySampleCount",
                "capabilitiesWithoutSelectors", "capabilitiesWithoutResolvedTypes",
                "unresolvedCapabilityTypeReferences",
                "unresolvedCapabilityModuleReferences",
                "unresolvedCapabilityPackageReferences",
                "tokensPerKloc", "p90RepositoryContextShare",
                "evidenceToProductionRatio")) {
            requireNonNegativeNumber(footprint.get(field),
                "complexity.json.contextFootprint." + field, errors);
        }
        BigDecimal normalized = requireRange(footprint.get("normalizedContextDebt"),
            BigDecimal.ZERO, new BigDecimal("100"),
            "complexity.json.contextFootprint.normalizedContextDebt", errors);
        requireRange(footprint.get("contextEfficiencyScore"), BigDecimal.ZERO,
            new BigDecimal("100"),
            "complexity.json.contextFootprint.contextEfficiencyScore", errors);
        object(footprint.get("capabilityReferenceSources"),
            "complexity.json.contextFootprint.capabilityReferenceSources", errors);
        object(footprint.get("capabilityWorkingSetSources"),
            "complexity.json.contextFootprint.capabilityWorkingSetSources", errors);

        BigDecimal topLevel = decimal(complexity.get("aiContextDebt"));
        if (normalized != null && (topLevel == null || topLevel.compareTo(normalized) != 0)) {
            errors.add("complexity.json.aiContextDebt differs from contextFootprint.normalizedContextDebt");
        }
        Integer sampleCount = integer(footprint.get("capabilitySampleCount"));
        if ("MEASURED".equals(status) && (sampleCount == null || sampleCount <= 0)) {
            errors.add("MEASURED context footprint requires capability samples");
        }
        if ("NO_CAPABILITY_SAMPLES".equals(status)) {
            if (sampleCount == null || sampleCount != 0) {
                errors.add("NO_CAPABILITY_SAMPLES requires capabilitySampleCount=0");
            }
            if (normalized == null || normalized.compareTo(new BigDecimal("100")) != 0) {
                errors.add("NO_CAPABILITY_SAMPLES requires normalizedContextDebt=100");
            }
        }
    }

    private static void validateCheck(
            Map<String, Object> documents,
            List<String> errors) {
        Map<String, Object> check = object(documents.get("check.json"), "check.json", errors);
        Boolean passed = check.get("passed") instanceof Boolean value ? value : null;
        if (passed == null) errors.add("check.json.passed must be a boolean");
        List<Object> violations = array(check.get("violations"), "check.json.violations", errors);
        if (Boolean.TRUE.equals(passed) && !violations.isEmpty()) {
            errors.add("check.json cannot pass with violations");
        }
        if (Boolean.FALSE.equals(passed) && violations.isEmpty()) {
            errors.add("check.json cannot fail without violations");
        }
        object(check.get("methodComplexityThresholds"),
            "check.json.methodComplexityThresholds", errors);
        object(check.get("knowledgeQualityGates"),
            "check.json.knowledgeQualityGates", errors);

        Map<String, Object> complexity = object(
            documents.get("complexity.json"), "complexity.json", errors);
        if (!Objects.equals(check.get("contextFootprint"), complexity.get("contextFootprint"))) {
            errors.add("check.json.contextFootprint differs from complexity.json");
        }
        if (!Objects.equals(check.get("codeComplexity"), complexity.get("codeComplexity"))) {
            errors.add("check.json.codeComplexity differs from complexity.json");
        }
        BigDecimal checkDebt = decimal(check.get("aiContextDebt"));
        BigDecimal complexityDebt = decimal(complexity.get("aiContextDebt"));
        if (checkDebt == null || complexityDebt == null
                || checkDebt.compareTo(complexityDebt) != 0) {
            errors.add("check.json.aiContextDebt differs from complexity.json");
        }
    }

    private static void validateNonEmptyObject(
            Map<String, Object> documents,
            String file,
            List<String> errors) {
        if (object(documents.get(file), file, errors).isEmpty()) {
            errors.add(file + " must contain a non-empty object");
        }
    }

    private static void readJson(Path root, String relative,
            Map<String, Object> documents, List<String> errors) {
        Path file = requiredFile(root, relative, errors);
        if (file == null) return;
        try {
            documents.put(relative, StrictJsonReader.read(file));
        } catch (IOException | IllegalArgumentException exception) {
            errors.add("cannot parse " + relative + ": " + exception.getMessage());
        }
    }

    private static void readText(Path root, String relative, List<String> errors) {
        Path file = requiredFile(root, relative, errors);
        if (file == null) return;
        try {
            if (Files.readString(file, StandardCharsets.UTF_8).isBlank()) {
                errors.add(relative + " must not be blank");
            }
        } catch (IOException exception) {
            errors.add("cannot read " + relative + ": " + exception.getMessage());
        }
    }

    private static Path requiredFile(Path root, String relative, List<String> errors) {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            errors.add("artifact path escapes output directory: " + relative);
            return null;
        }
        if (Files.isSymbolicLink(resolved)) {
            errors.add("artifact must not be a symbolic link: " + relative);
            return null;
        }
        if (!Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
            errors.add("missing required artifact: " + relative);
            return null;
        }
        try {
            if (Files.size(resolved) <= 0) {
                errors.add("required artifact is empty: " + relative);
                return null;
            }
        } catch (IOException exception) {
            errors.add("cannot inspect " + relative + ": " + exception.getMessage());
            return null;
        }
        return resolved;
    }

    private static Map<String, Object> object(Object value,
            String location, List<String> errors) {
        if (!(value instanceof Map<?, ?> raw)) {
            errors.add(location + " must contain an object");
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                errors.add(location + " contains a non-string object key");
            } else {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private static List<Object> array(Object value,
            String location, List<String> errors) {
        if (!(value instanceof List<?> list)) {
            errors.add(location + " must contain an array");
            return List.of();
        }
        return List.copyOf(list);
    }

    private static String requireText(Object value,
            String location, List<String> errors) {
        String text = text(value);
        if (text == null || text.isBlank()) {
            errors.add(location + " must be a non-blank string");
            return null;
        }
        return text;
    }

    private static void requireTextValue(Object value, String expected,
            String location, List<String> errors) {
        String actual = requireText(value, location, errors);
        if (actual != null && !expected.equals(actual)) {
            errors.add(location + " must be " + expected + " but was " + actual);
        }
    }

    private static void requireInteger(Object value, int expected,
            String location, List<String> errors) {
        Integer actual = integer(value);
        if (actual == null) {
            errors.add(location + " must be an integer");
        } else if (actual != expected) {
            errors.add(location + " must be " + expected + " but was " + actual);
        }
    }

    private static void requireNonNegativeNumber(Object value,
            String location, List<String> errors) {
        BigDecimal number = decimal(value);
        if (number == null || number.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(location + " must be a non-negative number");
        }
    }

    private static BigDecimal requireRange(Object value, BigDecimal minimum,
            BigDecimal maximum, String location, List<String> errors) {
        BigDecimal number = decimal(value);
        if (number == null || number.compareTo(minimum) < 0
                || number.compareTo(maximum) > 0) {
            errors.add(location + " must be in [" + minimum + "," + maximum + "]");
            return null;
        }
        return number;
    }

    private static String text(Object value) {
        return value instanceof String string ? string : null;
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof BigDecimal number ? number : null;
    }

    private static Integer integer(Object value) {
        BigDecimal number = decimal(value);
        if (number == null) return null;
        try {
            return number.intValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    public enum Profile {
        QUALITY_GATE,
        COMPLETE_LIFECYCLE
    }

    public record VerificationReport(
        Profile profile,
        Path outputDirectory,
        List<String> errors,
        List<String> warnings
    ) {
        public VerificationReport {
            Objects.requireNonNull(profile, "profile");
            outputDirectory = Objects.requireNonNull(outputDirectory,
                "outputDirectory").toAbsolutePath().normalize();
            errors = List.copyOf(errors == null ? List.of() : errors);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }

        public boolean passed() {
            return errors.isEmpty();
        }

        public void requireValid() {
            if (!passed()) {
                throw new IllegalStateException(
                    "AI knowledge artifact verification failed: "
                        + String.join("; ", errors));
            }
        }
    }
}
