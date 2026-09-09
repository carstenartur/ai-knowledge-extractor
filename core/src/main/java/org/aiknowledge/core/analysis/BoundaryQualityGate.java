package org.aiknowledge.core.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.aiknowledge.core.RepositorySnapshot;

/** Shared boundary policy evaluation; raw evidence and extraction confidence accompany failures. */
public final class BoundaryQualityGate {
    private BoundaryQualityGate() { }

    public static Map<String, Object> measurements(Map<?, ?> boundary) {
        Map<String, Object> result = new LinkedHashMap<>();
        double calls = number(boundary, "clientCallCount");
        double unresolved = number(boundary, "unresolvedCallCount") + number(boundary, "ambiguousCallCount");
        result.put("boundaryScore", number(boundary, "score"));
        result.put("boundaryUnresolvedCalls", (int) unresolved);
        result.put("boundaryUnresolvedRatio", calls == 0 ? 0.0 : unresolved / calls);
        result.put("boundaryDynamicCalls", list(boundary.get("links")).stream()
                .filter(link -> "unresolved-dynamic".equals(link.get("status"))).count());
        result.put("boundaryEndpointFanOut", number(boundary, "maxEndpointFanOutPerCallable"));
        result.put("boundaryDependencySurfaceScore", number(map(boundary.get("dependencySurface")), "score"));
        result.put("boundaryStateInterpretations", number(boundary, "backendStateInterpretationCount"));
        result.put("boundaryHighSeverityFindings", list(boundary.get("findings")).stream()
                .filter(finding -> "high".equals(finding.get("severity"))).count());
        return result;
    }

    public static Map<String, Object> evaluate(BoundaryGateOptions options,
            Map<?, ?> boundary, RepositorySnapshot snapshot) {
        Map<String, Object> values = measurements(boundary);
        List<Map<String, Object>> violations = new ArrayList<>();
        boolean applicable = number(boundary, "clientCallCount") > 0;
        if (applicable) {
            check(violations, values, "boundaryScore", options.maxBoundaryScore(), "aggregate", boundary, snapshot);
            check(violations, values, "boundaryUnresolvedCalls", options.maxBoundaryUnresolvedCalls(), "structuralCoupling", boundary, snapshot);
            check(violations, values, "boundaryUnresolvedRatio", options.maxBoundaryUnresolvedRatio(), "structuralCoupling", boundary, snapshot);
            check(violations, values, "boundaryDynamicCalls", options.maxBoundaryDynamicCalls(), "structuralCoupling", boundary, snapshot);
            check(violations, values, "boundaryEndpointFanOut", options.maxBoundaryEndpointFanOut(), "structuralCoupling", boundary, snapshot);
            check(violations, values, "boundaryDependencySurfaceScore", options.maxBoundaryDependencySurfaceScore(), "dependencySurface", boundary, snapshot);
            check(violations, values, "boundaryStateInterpretations", options.maxBoundaryStateInterpretations(), "semanticCoupling", boundary, snapshot);
            if (options.failOnHighSeverityBoundaryFindings()) {
                check(violations, values, "boundaryHighSeverityFindings", 0, "findings", boundary, snapshot);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", violations.isEmpty());
        result.put("applicability", applicable ? "applicable" : "not-applicable");
        result.put("confidence", boundary.get("confidence"));
        result.put("extractionConfidence", boundary.get("extractionConfidence"));
        result.put("scoringModelVersion", boundary.get("scoringModelVersion"));
        result.put("thresholds", options.thresholds());
        result.put("measurements", values);
        result.put("violations", violations);
        result.put("note", applicable
                ? "Thresholds apply to observed evidence, including low-confidence calls; inspect provenance before changing architecture."
                : "No client boundary evidence was extracted; boundary policy is not applicable, not evidence of a clean interface.");
        return result;
    }

    private static void check(List<Map<String, Object>> violations, Map<String, Object> values,
            String metric, Number limit, String dimension, Map<?, ?> boundary, RepositorySnapshot snapshot) {
        if (limit == null || number(values, metric) <= limit.doubleValue()) return;
        Map<String, Object> violation = new LinkedHashMap<>();
        violation.put("metric", metric);
        violation.put("measured", values.get(metric));
        violation.put("threshold", limit);
        violation.put("dimension", dimension);
        violation.put("confidence", boundary.get("confidence"));
        violation.put("extractionConfidence", boundary.get("extractionConfidence"));
        violation.put("message", metric + " = " + values.get(metric) + " exceeds " + limit);
        List<Map<?, ?>> links = list(boundary.get("links"));
        if (metric.equals("boundaryDynamicCalls")) {
            links = links.stream().filter(link -> "unresolved-dynamic".equals(link.get("status"))).toList();
        } else if (metric.startsWith("boundaryUnresolved")) {
            links = links.stream().filter(link -> !"linked".equals(link.get("status"))).toList();
        }
        violation.put("links", links);
        List<String> ids = links.stream().map(link -> String.valueOf(link.get("clientCallId"))).toList();
        violation.put("calls", list(snapshot.boundaries).stream()
                .filter(call -> ids.contains(String.valueOf(call.get("id")))).toList());
        if (metric.equals("boundaryEndpointFanOut") || metric.equals("boundaryStateInterpretations")) {
            String field = metric.equals("boundaryEndpointFanOut") ? "endpointFanOut" : "backendStateInterpretationCount";
            violation.put("callableProfiles", list(boundary.get("callableProfiles")).stream()
                    .filter(profile -> number(profile, field) > (metric.equals("boundaryEndpointFanOut") ? limit.doubleValue() : 0)).toList());
        }
        if (dimension.equals("aggregate") || dimension.equals("dependencySurface") || dimension.equals("findings")) {
            violation.put("dimensions", boundary.get("dimensions"));
            violation.put("dependencySurface", boundary.get("dependencySurface"));
            violation.put("dependencyRelations", list(snapshot.relations).stream()
                    .filter(relation -> "SOURCE_UNIT_IMPORTS_MODULE".equals(relation.get("kind"))
                            && Boolean.TRUE.equals(relation.get("runtime"))).toList());
        }
        if (dimension.equals("findings")) violation.put("findings", list(boundary.get("findings")).stream()
                .filter(finding -> "high".equals(finding.get("severity"))).toList());
        violations.add(violation);
    }

    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    private static List<Map<?, ?>> list(Object value) {
        List<Map<?, ?>> result = new ArrayList<>();
        if (value instanceof List<?> list) for (Object item : list) if (item instanceof Map<?, ?> map) result.add(map);
        return result;
    }
    private static double number(Map<?, ?> values, String key) {
        return values.get(key) instanceof Number n ? n.doubleValue() : 0;
    }
}
