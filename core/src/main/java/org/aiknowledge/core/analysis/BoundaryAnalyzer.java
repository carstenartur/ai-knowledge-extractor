package org.aiknowledge.core.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.aiknowledge.core.RepositorySnapshot;

/** Evidence-based, language-neutral frontend/backend boundary analysis. */
public final class BoundaryAnalyzer {
    private BoundaryAnalyzer() {
    }

    public static Map<String, Object> analyze(RepositorySnapshot snapshot) {
        List<Map<String, Object>> clients = facts(snapshot.boundaries, "client-call");
        List<Map<String, Object>> servers = facts(snapshot.boundaries, "server-endpoint");
        List<Map<String, Object>> links = new ArrayList<>();
        int linked = 0;
        int ambiguous = 0;
        int unresolved = 0;

        for (Map<String, Object> client : clients) {
            List<Map<String, Object>> candidates = candidates(client, servers);
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("clientCallId", text(client.get("id")));
            link.put("operation", operation(client));
            link.put("sourceFile", text(client.get("sourceFile")));
            link.put("line", number(client.get("line")));
            link.put("endpointIds", candidates.stream()
                    .map(value -> text(value.get("id"))).toList());
            if (BoundaryPath.DYNAMIC.equals(normalized(client))) {
                link.put("status", "unresolved-dynamic");
                link.put("confidence", "low");
                unresolved++;
            } else if (candidates.size() == 1) {
                link.put("status", "linked");
                link.put("confidence", linkConfidence(client, candidates.get(0)));
                linked++;
            } else if (candidates.size() > 1) {
                link.put("status", "ambiguous");
                link.put("confidence", "partial");
                ambiguous++;
            } else {
                link.put("status", "unresolved");
                link.put("confidence", "none");
                unresolved++;
            }
            links.add(link);
        }
        links.sort(Comparator.comparing(value -> text(value.get("clientCallId"))));

        Map<String, CallProfile> profiles = profiles(clients);
        int maxFanOut = profiles.values().stream()
                .mapToInt(CallProfile::distinctOperations).max().orElse(0);
        int maxCallsPerCallable = profiles.values().stream()
                .mapToInt(CallProfile::calls).max().orElse(0);
        long orchestrationCallables = profiles.values().stream()
                .filter(value -> value.calls() >= 2 && value.awaited() >= 2)
                .count();
        int transformations = profiles.values().stream()
                .mapToInt(CallProfile::transformations).sum();
        int stateInterpretations = profiles.values().stream()
                .mapToInt(CallProfile::stateInterpretations).sum();
        int errorBranches = profiles.values().stream()
                .mapToInt(CallProfile::errorBranches).sum();
        long noErrorHandling = profiles.values().stream()
                .filter(value -> "none".equals(value.errorHandling())).count();
        long withErrorHandling = profiles.size() - noErrorHandling;

        double unresolvedRatio = clients.isEmpty()
                ? 0.0
                : (double) (unresolved + ambiguous) / clients.size();
        int structural = clamp((maxFanOut - 1) * 22 + (int) Math.round(unresolvedRatio * 30));
        int orchestration = clamp((maxCallsPerCallable - 1) * 20
                + (int) orchestrationCallables * 15);
        int translation = clamp(transformations * 18);
        int semantic = clamp(stateInterpretations * 16);
        int error = clamp(errorBranches * 10
                + (withErrorHandling > 0 && noErrorHandling > 0 ? 20 : 0));

        double linkedRatio = clients.isEmpty() ? 1.0 : (double) linked / clients.size();
        double knownMethodRatio = ratio(clients, value -> !Set.of("", "*", "UNKNOWN")
                .contains(text(value.get("method")).toUpperCase(Locale.ROOT)));
        double literalPathRatio = ratio(clients, value -> Boolean.TRUE.equals(value.get("literalPath")));
        double generatedClientRatio = ratio(clients,
                value -> "generated-client".equals(value.get("contractSource")));
        int contractClarity = clamp((int) Math.round(100.0 * (
                0.55 * linkedRatio
                        + 0.20 * knownMethodRatio
                        + 0.20 * literalPathRatio
                        + 0.05 * generatedClientRatio)));
        int contractUncertainty = 100 - contractClarity;

        Map<String, Object> dependencySurface =
                FrontendDependencySurfaceAnalyzer.analyze(snapshot);
        int dependencyScore = number(dependencySurface.get("score"));

        int total = clamp((int) Math.round(
                0.20 * structural
                        + 0.20 * orchestration
                        + 0.15 * translation
                        + 0.15 * semantic
                        + 0.10 * error
                        + 0.10 * dependencyScore
                        + 0.10 * contractUncertainty));

        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("structuralCoupling", dimension(structural,
                "Endpoint fan-out, call-site spread and unresolved structural links."));
        dimensions.put("orchestration", dimension(orchestration,
                "Sequential or multi-call workflows implemented in frontend callables."));
        dimensions.put("modelTranslation", dimension(translation,
                "Observable response mapping and view-model construction near boundary calls."));
        dimensions.put("semanticCoupling", dimension(semantic,
                "Frontend interpretation of backend status, state, phase or error-code values."));
        dimensions.put("errorComplexity", dimension(error,
                "Distributed status branches, catch logic and inconsistent error handling."));
        dimensions.put("dependencySurface", dimension(dependencyScore,
                "Actually imported runtime packages and the diversity of boundary client mechanisms."));
        dimensions.put("contractClarity", dimension(contractClarity,
                "Resolvable operations, explicit methods and literal or generated contracts; higher is better."));

        List<Map<String, Object>> findings = findings(
                clients,
                unresolved,
                ambiguous,
                maxFanOut,
                orchestrationCallables,
                transformations,
                stateInterpretations,
                noErrorHandling,
                withErrorHandling);
        Object dependencyFindings = dependencySurface.get("findings");
        if (dependencyFindings instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> raw) findings.add(copy(raw));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("method", "evidence-weighted-cross-language-boundary-proxy-v1");
        result.put("score", total);
        result.put("rating", rating(total));
        result.put("confidence", confidence(clients.size(), servers.size(), linkedRatio));
        result.put("dimensions", dimensions);
        result.put("dependencySurface", dependencySurface);
        result.put("clientCallCount", clients.size());
        result.put("serverEndpointCount", servers.size());
        result.put("linkedCallCount", linked);
        result.put("ambiguousCallCount", ambiguous);
        result.put("unresolvedCallCount", unresolved);
        result.put("maxEndpointFanOutPerCallable", maxFanOut);
        result.put("maxCallsPerCallable", maxCallsPerCallable);
        result.put("links", links);
        result.put("findings", findings);
        result.put("versionControlHistoryUsed", false);
        result.put("changeCouplingIncluded", false);
        result.put("limitations", List.of(
                "The score is a structural proxy for required simultaneous knowledge, not a measurement of a particular person's mental state.",
                "Runtime-generated URLs, reflective routes and framework-specific functional routing may remain unresolved.",
                "Git commit history and co-change coupling are intentionally not used.",
                "Provider confidence and raw evidence should be inspected before using the score as a quality gate."));
        return result;
    }

    private static List<Map<String, Object>> candidates(
            Map<String, Object> client,
            List<Map<String, Object>> servers) {
        String protocol = text(client.get("protocol"));
        String method = BoundaryPath.normalizeMethod(client.get("method"));
        String path = normalized(client);
        if (BoundaryPath.DYNAMIC.equals(path)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> server : servers) {
            if (!protocolCompatible(protocol, text(server.get("protocol")))) continue;
            String serverMethod = BoundaryPath.normalizeMethod(server.get("method"));
            if (!(method.equals(serverMethod) || "*".equals(serverMethod) || "*".equals(method))) {
                continue;
            }
            if (path.equals(normalized(server))) result.add(server);
        }
        result.sort(Comparator.comparing(value -> text(value.get("id"))));
        return result;
    }

    private static boolean protocolCompatible(String client, String server) {
        if (client.equals(server)) return true;
        return "http".equals(client) && (server.isEmpty() || "http".equals(server));
    }

    private static String normalized(Map<String, Object> fact) {
        String normalized = text(fact.get("normalizedPath"));
        return normalized.isEmpty()
                ? BoundaryPath.normalize(text(fact.get("path")))
                : normalized;
    }

    private static String operation(Map<String, Object> fact) {
        return BoundaryPath.normalizeMethod(fact.get("method")) + " " + normalized(fact);
    }

    private static String linkConfidence(
            Map<String, Object> client,
            Map<String, Object> server) {
        return Boolean.TRUE.equals(client.get("literalPath"))
                        && !"partial-expression".equals(server.get("confidence"))
                ? "high"
                : "medium";
    }

    private static Map<String, CallProfile> profiles(List<Map<String, Object>> clients) {
        Map<String, MutableProfile> mutable = new LinkedHashMap<>();
        for (Map<String, Object> client : clients) {
            String callable = text(client.get("callable"));
            MutableProfile profile = mutable.computeIfAbsent(callable,
                    ignored -> new MutableProfile());
            profile.calls++;
            profile.operations.add(operation(client));
            if (Boolean.TRUE.equals(client.get("awaited"))) profile.awaited++;
            profile.transformations = Math.max(
                    profile.transformations,
                    number(client.get("modelTransformationCount")));
            profile.stateInterpretations = Math.max(
                    profile.stateInterpretations,
                    number(client.get("backendStateInterpretationCount")));
            profile.errorBranches = Math.max(
                    profile.errorBranches,
                    number(client.get("errorBranchCount")));
            String handling = text(client.get("errorHandling"));
            if (!"none".equals(handling) && !handling.isBlank()) {
                profile.errorHandling = handling;
            }
        }
        Map<String, CallProfile> result = new LinkedHashMap<>();
        for (Map.Entry<String, MutableProfile> entry : mutable.entrySet()) {
            MutableProfile value = entry.getValue();
            result.put(entry.getKey(), new CallProfile(
                    value.calls,
                    value.operations.size(),
                    value.awaited,
                    value.transformations,
                    value.stateInterpretations,
                    value.errorBranches,
                    value.errorHandling));
        }
        return result;
    }

    private static List<Map<String, Object>> findings(
            List<Map<String, Object>> clients,
            int unresolved,
            int ambiguous,
            int maxFanOut,
            long orchestrationCallables,
            int transformations,
            int stateInterpretations,
            long noErrorHandling,
            long withErrorHandling) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (maxFanOut >= 4) {
            result.add(finding("HIGH_ENDPOINT_FAN_OUT", "high",
                    "At least one frontend callable depends on " + maxFanOut
                            + " distinct backend operations.",
                    "Consider a use-case endpoint or backend-for-frontend facade that owns the orchestration."));
        } else if (maxFanOut >= 2) {
            result.add(finding("ENDPOINT_FAN_OUT", "medium",
                    "A frontend callable combines up to " + maxFanOut + " backend operations.",
                    "Verify that call ordering and partial-failure policy belong in the frontend."));
        }
        if (orchestrationCallables > 0) {
            result.add(finding("FRONTEND_ORCHESTRATES_BACKEND_WORKFLOW", "high",
                    orchestrationCallables + " callable(s) contain multiple awaited boundary calls.",
                    "Move domain workflow sequencing behind one backend operation where the sequence is business-relevant."));
        }
        if (transformations > 0) {
            result.add(finding("MANUAL_MODEL_TRANSLATION", "medium",
                    transformations + " model-translation signal(s) occur near boundary calls.",
                    "Consolidate mapping in one adapter and compare frontend and backend field semantics."));
        }
        if (stateInterpretations > 0) {
            result.add(finding("BACKEND_STATE_INTERPRETATION", "high",
                    stateInterpretations + " backend-state comparison signal(s) occur in frontend callables.",
                    "Expose typed capabilities or allowed actions instead of duplicating backend state-machine rules."));
        }
        if (unresolved + ambiguous > 0) {
            result.add(finding("UNRESOLVED_BOUNDARY_LINKS", "medium",
                    unresolved + " client call(s) are unresolved and " + ambiguous + " are ambiguous.",
                    "Prefer generated contracts, explicit methods and normalized route templates."));
        }
        if (withErrorHandling > 0 && noErrorHandling > 0) {
            result.add(finding("INCONSISTENT_ERROR_HANDLING", "medium",
                    "Boundary callables mix explicit and absent error handling.",
                    "Adopt one typed error model and a shared boundary adapter."));
        }
        if (clients.isEmpty()) {
            result.add(finding("NO_CLIENT_BOUNDARY_EVIDENCE", "info",
                    "No supported frontend boundary call was extracted.",
                    "Confirm provider coverage if the repository contains a web frontend."));
        }
        return result;
    }

    private static Map<String, Object> dimension(int score, String explanation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score);
        result.put("explanation", explanation);
        return result;
    }

    private static Map<String, Object> finding(
            String code,
            String severity,
            String message,
            String recommendation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("severity", severity);
        result.put("message", message);
        result.put("recommendation", recommendation);
        return result;
    }

    private static String rating(int score) {
        if (score >= 75) return "very-high";
        if (score >= 50) return "high";
        if (score >= 25) return "moderate";
        return "low";
    }

    private static String confidence(int clients, int servers, double linkedRatio) {
        if (clients == 0) return "insufficient-client-evidence";
        if (servers == 0) return "frontend-only";
        if (linkedRatio >= 0.8) return "high";
        if (linkedRatio >= 0.4) return "medium";
        return "low";
    }

    private static List<Map<String, Object>> facts(List values, String kind) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> raw) || !kind.equals(raw.get("kind"))) continue;
            result.add(copy(raw));
        }
        result.sort(Comparator.comparing(value -> text(value.get("id"))));
        return result;
    }

    private static Map<String, Object> copy(Map<?, ?> raw) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    private static double ratio(
            List<Map<String, Object>> values,
            java.util.function.Predicate<Map<String, Object>> predicate) {
        if (values.isEmpty()) return 1.0;
        return (double) values.stream().filter(predicate).count() / values.size();
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static final class MutableProfile {
        private int calls;
        private final Set<String> operations = new LinkedHashSet<>();
        private int awaited;
        private int transformations;
        private int stateInterpretations;
        private int errorBranches;
        private String errorHandling = "none";
    }

    private record CallProfile(
            int calls,
            int distinctOperations,
            int awaited,
            int transformations,
            int stateInterpretations,
            int errorBranches,
            String errorHandling) {
    }
}
