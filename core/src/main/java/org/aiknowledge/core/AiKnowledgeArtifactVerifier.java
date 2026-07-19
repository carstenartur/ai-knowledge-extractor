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
 * <p>This verifier checks structural integrity and cross-document consistency.
 * Consumer policy remains in {@link KnowledgeQualityGate} and the configured
 * quality thresholds; unresolved selectors are therefore visible but are not
 * universally forbidden here.</p>
 */
public final class AiKnowledgeArtifactVerifier {
    private static final String CONTEXT_METHOD =
        "line-weighted-prioritized-capability-selector-working-set-proxy";

    private static final Map<String, String> ENVELOPE_FILES = Map.ofEntries(
        Map.entry("modules.json", "modules"),
        Map.entry("classes.json", "classes"),
        Map.entry("tests.json", "tests"),
        Map.entry("docs.json", "docs"),
        Map.entry("dependencies.json", "dependencies"),
        Map.entry("capabilities.json", "capabilities"),
        Map.entry("claims.json", "claims"),
        Map.entry("evidence.json", "evidence"));

    private static final List<String> QUALITY_GATE_JSON = List.of(
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

    private static final List<String> QUALITY_GATE_TEXT = List.of(
        "review-context.md",
        "complexity.html",
        "trend.html");

    private static final List<String> COMPLETE_JSON = List.of(
        "optimization.json",
        "benchmark.json");

    private static final List<String> COMPLETE_TEXT = List.of(
        "optimization.html",
        "benchmark.html");

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
        List<String> warnings = new ArrayList<>();
        Map<String, Object> documents = new LinkedHashMap<>();

        if (!Files.isDirectory(root)) {
            errors.add("artifact root does not exist or is not a directory: " + root);
            return new VerificationReport(profile, root, errors, warnings);
        }

        for (String relative : QUALITY_GATE_JSON) {
            readJson(root, relative, documents, errors);
        }
        for (String relative : QUALITY_GATE_TEXT) {
            readText(root, relative, errors);
        }
        if (profile == Profile.COMPLETE_LIFECYCLE) {
            for (String relative : COMPLETE_JSON) {
                readJson(root, relative, documents, errors);
            }
            for (String relative : COMPLETE_TEXT) {
                readText(root, relative, errors);
            }
        }

        validateEnvelopesAndCounts(documents, errors);
        validateReviewContext(root, errors);
        validateContextPacks(root, documents, errors);
        validateComplexity(documents, errors);
        validateCheck(documents, errors);
        validateReportObject(documents, "metrics-snapshot.json", errors);
        validateReportObject(documents, "trend.json", errors);
        if (profile == Profile.COMPLETE_LIFECYCLE) {
            validateReportObject(documents, "optimization.json", errors);
            validateReportObject(documents, "benchmark.json", errors);
        }

        return new VerificationReport(profile, root, errors, warnings);
    }

    private static void validateEnvelopesAndCounts(
        Map<String, Object> documents,
        List<String> errors
    ) {
        Map<String, Integer> actualCounts = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : ENVELOPE_FILES.entrySet()) {
            Map<String, Object> envelope = object(
                documents.get(entry.getKey()), entry.getKey(), errors);
            List<Object> items = array(
                envelope.get(entry.getValue()),
                entry.getKey() + "." + entry.getValue(),
                errors);
            actualCounts.put(entry.getValue(), items.size());
        }

        Map<String, Object> index = object(
            documents.get("index.json"), "index.json", errors);
        requireInteger(index.get("schemaVersion"), 1, "index.json.schemaVersion", errors);
        requireText(index.get("repository"), "index.json.repository", errors);
        requireTextValue(
            index.get("generationMode"),
            "deterministic-static",
            "index.json.generationMode",
            errors);
        Map<String, Object> counts = object(
            index.get("counts"), "index.json.counts", errors);
        actualCounts.forEach((name, expected) -> {
            Integer actual = integer(counts.get(name));
            if (actual == null) {
                errors.add("index.json.counts." + name + " must be an integer");
            } else if (actual != expected) {
                errors.add("index.json count mismatch for " + name
                    + ": expected " + expected + " but found " + actual);
            }
        });
    }

    private static void validateReviewContext(Path root, List<String> errors) {
        Path file = safeRequiredFile(root, "review-context.md", errors);
        if (file == null) {
            return;
        }
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
        List<String> errors
    ) {
        Map<String, Object> capabilitiesEnvelope = object(
            documents.get("capabilities.json"), "capabilities.json", errors);
        List<Object> capabilities = array(
            capabilitiesEnvelope.get("capabilities"),
            "capabilities.json.capabilities",
            errors);
        Set<String> capabilityIds = new LinkedHashSet<>();
        for (int index = 0; index < capabilities.size(); index++) {
            Map<String, Object> capability = object(
                capabilities.get(index),
                "capabilities.json.capabilities[" + index + "]",
                errors);
            String id = text(capability.get("id"));
            if (id == null || id.isBlank()) {
                continue;
            }
            if (!capabilityIds.add(id)) {
                errors.add("duplicate capability id: " + id);
            }
        }

        Map<String, Object> packIndex = object(
            documents.get("context-packs/index.json"),
            "context-packs/index.json",
            errors);
        List<Object> entries = array(
            packIndex.get("contextPacks"),
            "context-packs/index.json.contextPacks",
            errors);
        Set<String> indexedIds = new LinkedHashSet<>();
        Set<String> indexedFiles = new LinkedHashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            String location = "context-packs/index.json.contextPacks[" + index + "]";
            Map<String, Object> entry = object(entries.get(index), location, errors);
            String id = requireText(entry.get("id"), location + ".id", errors);
            String label = requireText(entry.get("label"), location + ".label", errors);
            String status = requireText(entry.get("status"), location + ".status", errors);
            String fileName = requireText(entry.get("file"), location + ".file", errors);
            Integer tokenEstimate = integer(entry.get("tokenEstimate"));
            if (tokenEstimate == null || tokenEstimate < 0) {
                errors.add(location + ".tokenEstimate must be a non-negative integer");
            }
            requireText(entry.get("intendedUse"), location + ".intendedUse", errors);
            if (id == null || fileName == null) {
                continue;
            }
            if (!indexedIds.add(id)) {
                errors.add("duplicate context-pack id: " + id);
            }
            if (!indexedFiles.add(fileName)) {
                errors.add("duplicate context-pack file: " + fileName);
            }
            String expectedFile = "context-packs/" + id + ".json";
            if (!expectedFile.equals(fileName)) {
                errors.add(location + ".file must be " + expectedFile);
                continue;
            }
            Path packFile = safeRequiredFile(root, fileName, errors);
            if (packFile == null) {
                continue;
            }
            try {
                String packJson = Files.readString(packFile, StandardCharsets.UTF_8);
                Object parsed = StrictJsonReader.parse(packJson);
                Map<String, Object> pack = object(parsed, fileName, errors);
                requireTextValue(pack.get("id"), id, fileName + ".id", errors);
                if (label != null) {
                    requireTextValue(pack.get("label"), label, fileName + ".label", errors);
                }
                if (status != null) {
                    requireTextValue(pack.get("status"), status, fileName + ".status", errors);
                }
                for (String field : List.of(
                        "modules", "types", "tests", "docs", "evidence",
                        "claims", "suggestedFiles")) {
                    array(pack.get(field), fileName + "." + field, errors);
                }
                if (pack.containsKey("warnings")) {
                    array(pack.get("warnings"), fileName + ".warnings", errors);
                }
                if (tokenEstimate != null
                        && tokenEstimate != packJson.length() / 4) {
                    errors.add(location + ".tokenEstimate does not match "
                        + fileName + " character estimate");
                }
            } catch (IOException | IllegalArgumentException exception) {
                errors.add("cannot parse " + fileName + ": " + exception.getMessage());
            }
        }
        if (!indexedIds.equals(capabilityIds)) {
            Set<String> missing = new LinkedHashSet<>(capabilityIds);
            missing.removeAll(indexedIds);
            Set<String> unexpected = new LinkedHashSet<>(indexedIds);
            unexpected.removeAll(capabilityIds);
            if (!missing.isEmpty()) {
                errors.add("context-pack index is missing capability ids: " + missing);
            }
            if (!unexpected.isEmpty()) {
                errors.add("context-pack index has unexpected ids: " + unexpected);
            }
        }
    }

    private static void validateComplexity(
        Map<String, Object> documents,
        List<String> errors
    ) {
        Map<String, Object> complexity = object(
            documents.get("complexity.json"), "complexity.json", errors);
        object(complexity.get("codeComplexity"),
            "complexity.json.codeComplexity", errors);
        Map<String, Object> costDrivers = object(
            complexity.get("aiCostDrivers"),
            "complexity.json.aiCostDrivers",
            errors);
        array(costDrivers.get("tokenCostDrivers"),
            "complexity.json.aiCostDrivers.tokenCostDrivers", errors);

        Map<String, Object> footprint = object(
            complexity.get("contextFootprint"),
            "complexity.json.contextFootprint",
            errors);
        requireInteger(
            footprint.get("schemaVersion"), 3,
            "complexity.json.contextFootprint.schemaVersion", errors);
        String status = requireText(
            footprint.get("measurementStatus"),
            "complexity.json.contextFootprint.measurementStatus",
            errors);
        if (status != null
                && !Set.of("MEASURED", "NO_CAPABILITY_SAMPLES").contains(status)) {
            errors.add("unsupported context-footprint measurementStatus: " + status);
        }
        requireTextValue(
            footprint.get("method"),
            CONTEXT_METHOD,
            "complexity.json.contextFootprint.method",
            errors);

        for (String field : List.of(
                "repositoryContextTokens",
                "productionContextTokens",
                "testEvidenceTokens",
                "documentationContextTokens",
                "productionLines",
                "totalContextLines",
                "medianCapabilityWorkingSetTokens",
                "p90CapabilityWorkingSetTokens",
                "capabilityCount",
                "capabilitySampleCount",
                "capabilitiesWithoutSelectors",
                "capabilitiesWithoutResolvedTypes",
                "unresolvedCapabilityTypeReferences",
                "unresolvedCapabilityModuleReferences",
                "unresolvedCapabilityPackageReferences")) {
            requireNonNegativeNumber(
                footprint.get(field),
                "complexity.json.contextFootprint." + field,
                errors);
        }
        for (String field : List.of(
                "tokensPerKloc",
                "p90RepositoryContextShare",
                "evidenceToProductionRatio")) {
            requireNonNegativeNumber(
                footprint.get(field),
                "complexity.json.contextFootprint." + field,
                errors);
        }
        BigDecimal normalized = requireRange(
            footprint.get("normalizedContextDebt"),
            BigDecimal.ZERO,
            new BigDecimal("100"),
            "complexity.json.contextFootprint.normalizedContextDebt",
            errors);
        requireRange(
            footprint.get("contextEfficiencyScore"),
            BigDecimal.ZERO,
            new BigDecimal("100"),
            "complexity.json.contextFootprint.contextEfficiencyScore",
            errors);
        object(footprint.get("capabilityReferenceSources"),
            "complexity.json.contextFootprint.capabilityReferenceSources", errors);
        object(footprint.get("capabilityWorkingSetSources"),
            "complexity.json.contextFootprint.capabilityWorkingSetSources", errors);

        BigDecimal topLevelDebt = decimal(complexity.get("aiContextDebt"));
        if (normalized != null
                && (topLevelDebt == null || topLevelDebt.compareTo(normalized) != 0)) {
            errors.add("complexity.json.aiContextDebt differs from "
                + "contextFootprint.normalizedContextDebt");
        }
        Integer samples = integer(footprint.get("capabilitySampleCount"));
        if ("MEASURED".equals(status) && (samples == null || samples <= 0)) {
            errors.add("MEASURED context footprint requires capability samples");
        }
        if ("NO_CAPABILITY_SAMPLES".equals(status)) {
            if (samples == null || samples != 0) {
                errors.add("NO_CAPABILITY_SAMPLES requires capabilitySampleCount=0");
            }
            if (normalized == null || normalized.compareTo(new BigDecimal("100")) != 0) {
                errors.add("NO_CAPABILITY_SAMPLES requires normalizedContextDebt=100");
            }
        }
    }

    private static void validateCheck(
        Map<String, Object> documents,
        List<String> errors
    ) {
        Map<String, Object> check = object(
            documents.get("check.json"), "check.json", errors);
        Boolean passed = bool(check.get("passed"));
        if (passed == null) {
            errors.add("check.json.passed must be a boolean");
        }
        List<Object> violations = array(
            check.get("violations"), "check.json.violations", errors);
        if (Boolean.TRUE.equals(passed) && !violations.isEmpty()) {
            errors.add("check.json cannot pass with violations");
        }
        if (Boolean.FALSE.equals(passed) && violations.isEmpty()) {
            errors.add("check.json cannot fail without violations");
        }
        object(check.get("contextFootprint"),
            "check.json.contextFootprint", errors);
        object(check.get("codeComplexity"),
            "check.json.codeComplexity", errors);
        object(check.get("methodComplexityThresholds"),
            "check.json.methodComplexityThresholds", errors);
        object(check.get("knowledgeQualityGates"),
            "check.json.knowledgeQualityGates", errors);

        Map<String, Object> complexity = object(
            documents.get("complexity.json"), "complexity.json", errors);
        if (!Objects.equals(check.get("contextFootprint"),
                complexity.get("contextFootprint"))) {
            errors.add("check.json.contextFootprint differs from complexity.json");
        }
        if (!Objects.equals(check.get("codeComplexity"),
                complexity.get("codeComplexity"))) {
            errors.add("check.json.codeComplexity differs from complexity.json");
        }
        BigDecimal checkDebt = decimal(check.get("aiContextDebt"));
        BigDecimal complexityDebt = decimal(complexity.get("aiContextDebt"));
        if (checkDebt == null || complexityDebt == null
                || checkDebt.compareTo(complexityDebt) != 0) {
            errors.add("check.json.aiContextDebt differs from complexity.json");
        }
    }

    private static void validateReportObject(
        Map<String, Object> documents,
        String file,
        List<String> errors
    ) {
        Map<String, Object> report = object(documents.get(file), file, errors);
        if (report.isEmpty()) {
            errors.add(file + " must contain a non-empty object");
        }
    }

    private static void readJson(
        Path root,
        String relative,
        Map<String, Object> documents,
        List<String> errors
    ) {
        Path file = safeRequiredFile(root, relative, errors);
        if (file == null) {
            return;
        }
        try {
            documents.put(relative, StrictJsonReader.read(file));
        } catch (IOException | IllegalArgumentException exception) {
            errors.add("cannot parse " + relative + ": " + exception.getMessage());
        }
    }

    private static void readText(
        Path root,
        String relative,
        List<String> errors
    ) {
        Path file = safeRequiredFile(root, relative, errors);
        if (file == null) {
            return;
        }
        try {
            if (Files.readString(file, StandardCharsets.UTF_8).isBlank()) {
                errors.add(relative + " must not be blank");
            }
        } catch (IOException exception) {
            errors.add("cannot read " + relative + ": " + exception.getMessage());
        }
    }

    private static Path safeRequiredFile(
        Path root,
        String relative,
        List<String> errors
    ) {
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

    private static Map<String, Object> object(
        Object value,
        String location,
        List<String> errors
    ) {
        if (!(value instanceof Map<?, ?> raw)) {
            errors.add(location + " must contain an object");
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                errors.add(location + " contains a non-string object key");
                continue;
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static List<Object> array(
        Object value,
        String location,
        List<String> errors
    ) {
        if (!(value instanceof List<?> list)) {
            errors.add(location + " must contain an array");
            return List.of();
        }
        return List.copyOf(list);
    }

    private static String requireText(
        Object value,
        String location,
        List<String> errors
    ) {
        String text = text(value);
        if (text == null || text.isBlank()) {
            errors.add(location + " must be a non-blank string");
            return null;
        }
        return text;
    }

    private static void requireTextValue(
        Object value,
        String expected,
        String location,
        List<String> errors
    ) {
        String actual = requireText(value, location, errors);
        if (actual != null && !expected.equals(actual)) {
            errors.add(location + " must be " + expected + " but was " + actual);
        }
    }

    private static void requireInteger(
        Object value,
        int expected,
        String location,
        List<String> errors
    ) {
        Integer actual = integer(value);
        if (actual == null) {
            errors.add(location + " must be an integer");
        } else if (actual != expected) {
            errors.add(location + " must be " + expected + " but was " + actual);
        }
    }

    private static void requireNonNegativeNumber(
        Object value,
        String location,
        List<String> errors
    ) {
        BigDecimal number = decimal(value);
        if (number == null || number.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(location + " must be a non-negative number");
        }
    }

    private static BigDecimal requireRange(
        Object value,
        BigDecimal minimum,
        BigDecimal maximum,
        String location,
        List<String> errors
    ) {
        BigDecimal number = decimal(value);
        if (number == null
                || number.compareTo(minimum) < 0
                || number.compareTo(maximum) > 0) {
            errors.add(location + " must be in [" + minimum + "," + maximum + "]");
            return null;
        }
        return number;
    }

    private static String text(Object value) {
        return value instanceof String string ? string : null;
    }

    private static Boolean bool(Object value) {
        return value instanceof Boolean bool ? bool : null;
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof BigDecimal number ? number : null;
    }

    private static Integer integer(Object value) {
        BigDecimal number = decimal(value);
        if (number == null) {
            return null;
        }
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
            outputDirectory = Objects.requireNonNull(
                outputDirectory, "outputDirectory").toAbsolutePath().normalize();
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
