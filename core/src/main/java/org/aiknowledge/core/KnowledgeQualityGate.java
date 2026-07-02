package org.aiknowledge.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * Evaluates knowledge quality gates against the extracted repository snapshot.
 *
 * <p>Quality gates check that the generated knowledge is actually usable for AI-assisted
 * repository work — not just that the pipeline ran without error.
 *
 * <p>Gate results are included in {@code check.json} under the key
 * {@code knowledgeQualityGates}.
 */
public final class KnowledgeQualityGate {

    private KnowledgeQualityGate() {}

    /**
     * Evaluates all configured quality gates and returns a summary map suitable for
     * inclusion in {@code check.json}.
     *
     * <p>The returned map contains:
     * <ul>
     *   <li>{@code passed} – {@code true} only when all enabled gates pass.</li>
     *   <li>{@code gates} – list of per-gate result maps, each with
     *       {@code gate}, {@code passed} and (when failing) {@code violations}.</li>
     * </ul>
     */
    public static Map evaluate(ExtractionOptions options, RepositorySnapshot snapshot) throws IOException {
        List<Map> gateResults = new ArrayList<>();

        if (options.requireCapabilityEvidence()) {
            gateResults.add(checkCapabilityEvidence(snapshot));
        }
        if (options.requireClaimVerification()) {
            gateResults.add(checkClaimVerification(snapshot));
        }
        if (options.minContextPackCount() > 0) {
            gateResults.add(checkMinContextPackCount(options.outputDirectory(), options.minContextPackCount()));
        }
        if (options.maxContextPackTokens() < Integer.MAX_VALUE) {
            gateResults.add(checkContextPackTokens(options.outputDirectory(), options.maxContextPackTokens()));
        }

        boolean allPassed = gateResults.stream().allMatch(g -> Boolean.TRUE.equals(g.get("passed")));
        Map summary = new LinkedHashMap();
        summary.put("passed", allPassed);
        summary.put("gates", gateResults);
        return summary;
    }

    // ── Individual gate checks ───────────────────────────────────────────────

    /**
     * Checks that every capability has at least one matched module, type, doc or evidence item.
     * Capabilities with {@code status=unknown} (i.e., {@code no-evidence-found}) are violations.
     */
    @SuppressWarnings("unchecked")
    static Map checkCapabilityEvidence(RepositorySnapshot snapshot) {
        List<String> violations = new ArrayList<>();
        for (Object obj : snapshot.capabilities) {
            if (!(obj instanceof Map cap)) continue;
            String id = String.valueOf(cap.getOrDefault("id", ""));
            if (id.isBlank()) continue;
            if (hasNoLinkedEvidence(cap)) {
                String label = String.valueOf(cap.getOrDefault("label", id));
                violations.add("capability:" + id + " (" + label + ") has no matched module/type/doc/evidence");
            }
        }
        return buildGateResult("requireCapabilityEvidence", violations);
    }

    /**
     * Checks that no claim has {@code status=unverified}. Claims without structural
     * rule fields are tagged {@code unverified} by the claim verifier; projects should
     * either add verifiable rules or promote the claim to a seed with an explicit status.
     */
    @SuppressWarnings("unchecked")
    static Map checkClaimVerification(RepositorySnapshot snapshot) {
        List<String> violations = new ArrayList<>();
        for (Object obj : snapshot.claims) {
            if (!(obj instanceof Map claim)) continue;
            String id = String.valueOf(claim.getOrDefault("id", ""));
            if ("unverified".equals(claim.get("status"))) {
                violations.add("claim:" + id + " has status=unverified");
            }
        }
        return buildGateResult("requireClaimVerification", violations);
    }

    /**
     * Checks that the number of generated context packs meets the configured minimum.
     * Counts packs from {@code context-packs/index.json} for consistency with the
     * {@code maxContextPackTokens} gate which also reads from that file.
     */
    static Map checkMinContextPackCount(Path outputDirectory, int minCount) throws IOException {
        List<String> violations = new ArrayList<>();
        Path indexPath = outputDirectory.resolve("context-packs/index.json");
        int actual = 0;
        if (Files.isRegularFile(indexPath)) {
            String json = Files.readString(indexPath);
            int listStart = json.indexOf('[');
            int listEnd = json.lastIndexOf(']');
            if (listStart >= 0 && listEnd > listStart) {
                String listContent = json.substring(listStart + 1, listEnd);
                actual = splitTopLevelObjects(listContent).size();
            }
        }
        if (actual < minCount) {
            violations.add("context pack count " + actual + " is below required minimum " + minCount);
        }
        return buildGateResult("minContextPackCount", violations);
    }

    /**
     * Checks that every context pack's token estimate is within the configured maximum.
     * Reads token estimates from the generated {@code context-packs/index.json} using
     * simple string scanning (the file is written by this tool and has a known structure).
     */
    static Map checkContextPackTokens(Path outputDirectory, int maxTokens) throws IOException {
        List<String> violations = new ArrayList<>();
        Path indexPath = outputDirectory.resolve("context-packs/index.json");
        if (!Files.isRegularFile(indexPath)) {
            return buildGateResult("maxContextPackTokens", violations);
        }
        String json = Files.readString(indexPath);
        // Each pack entry contains "id":"<id>" and "tokenEstimate":<n> at the same object level.
        // Scan for pack objects between outermost [ ] list of contextPacks.
        int listStart = json.indexOf('[');
        int listEnd = json.lastIndexOf(']');
        if (listStart < 0 || listEnd < listStart) return buildGateResult("maxContextPackTokens", violations);
        String listContent = json.substring(listStart + 1, listEnd);
        // Split by top-level object boundaries
        for (String packEntry : splitTopLevelObjects(listContent)) {
            String id = extractStringField(packEntry, "id");
            int tokens = extractIntField(packEntry, "tokenEstimate");
            if (id != null && tokens >= 0 && tokens > maxTokens) {
                violations.add("context-pack:" + id + " token estimate " + tokens + " exceeds maximum " + maxTokens);
            }
        }
        return buildGateResult("maxContextPackTokens", violations);
    }

    /** Splits a JSON text into top-level {...} object bodies.
     *
     * <p>This parser handles string delimiters and single-char {@code \\} escapes. The
     * JSON produced by this tool uses ASCII-safe capability IDs and numeric token estimates,
     * so multi-char unicode escapes ({@code \\uXXXX}) do not occur in the scanned fields.
     */
    private static List<String> splitTopLevelObjects(String text) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') {
                if (depth == 0) start = i + 1;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    result.add(text.substring(start, i));
                    start = -1;
                }
            }
        }
        return result;
    }

    /** Extracts the value of a simple string field {@code "key":"value"} from a JSON fragment. */
    private static String extractStringField(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int idx = json.indexOf(needle);
        if (idx < 0) return null;
        int start = idx + needle.length();
        int end = json.indexOf('"', start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    /** Extracts the integer value of a numeric field {@code "key":number} from a JSON fragment. */
    private static int extractIntField(String json, String key) {
        String needle = "\"" + key + "\":";
        int idx = json.indexOf(needle);
        if (idx < 0) return -1;
        int start = idx + needle.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (end == start) return -1;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean hasNoLinkedEvidence(Map cap) {
        return isEmptyList(cap.get("matchedModules"))
                && isEmptyList(cap.get("matchedTypes"))
                && isEmptyList(cap.get("matchedDocs"))
                && isEmptyList(cap.get("matchedEvidence"))
                && isEmptyList(cap.get("classes"))
                && isEmptyList(cap.get("types"))
                && isEmptyList(cap.get("docs"));
    }

    private static boolean isEmptyList(Object value) {
        return !(value instanceof List<?> list) || list.isEmpty();
    }

    private static Map buildGateResult(String gateName, List<String> violations) {
        Map result = new LinkedHashMap();
        result.put("gate", gateName);
        result.put("passed", violations.isEmpty());
        if (!violations.isEmpty()) {
            result.put("violations", violations);
        }
        return result;
    }
}
