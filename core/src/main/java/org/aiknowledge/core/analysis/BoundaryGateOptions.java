package org.aiknowledge.core.analysis;

import java.util.LinkedHashMap;
import java.util.Map;

/** Opt-in boundary limits. Null or negative numeric values disable the corresponding limit. */
public record BoundaryGateOptions(
        Double maxBoundaryScore,
        Integer maxBoundaryUnresolvedCalls,
        Double maxBoundaryUnresolvedRatio,
        Integer maxBoundaryDynamicCalls,
        Integer maxBoundaryEndpointFanOut,
        Double maxBoundaryDependencySurfaceScore,
        Integer maxBoundaryStateInterpretations,
        boolean failOnHighSeverityBoundaryFindings) {
    public BoundaryGateOptions {
        maxBoundaryScore = limit(maxBoundaryScore);
        maxBoundaryUnresolvedCalls = limit(maxBoundaryUnresolvedCalls);
        maxBoundaryUnresolvedRatio = limit(maxBoundaryUnresolvedRatio);
        if (maxBoundaryUnresolvedRatio != null && maxBoundaryUnresolvedRatio > 1) {
            throw new IllegalArgumentException("maxBoundaryUnresolvedRatio must be between 0 and 1, or negative to disable");
        }
        maxBoundaryDynamicCalls = limit(maxBoundaryDynamicCalls);
        maxBoundaryEndpointFanOut = limit(maxBoundaryEndpointFanOut);
        maxBoundaryDependencySurfaceScore = limit(maxBoundaryDependencySurfaceScore);
        maxBoundaryStateInterpretations = limit(maxBoundaryStateInterpretations);
    }
    public static BoundaryGateOptions disabled() {
        return new BoundaryGateOptions(null, null, null, null, null, null, null, false);
    }
    public Map<String, Object> thresholds() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("maxBoundaryScore", maxBoundaryScore);
        values.put("maxBoundaryUnresolvedCalls", maxBoundaryUnresolvedCalls);
        values.put("maxBoundaryUnresolvedRatio", maxBoundaryUnresolvedRatio);
        values.put("maxBoundaryDynamicCalls", maxBoundaryDynamicCalls);
        values.put("maxBoundaryEndpointFanOut", maxBoundaryEndpointFanOut);
        values.put("maxBoundaryDependencySurfaceScore", maxBoundaryDependencySurfaceScore);
        values.put("maxBoundaryStateInterpretations", maxBoundaryStateInterpretations);
        values.put("failOnHighSeverityBoundaryFindings", failOnHighSeverityBoundaryFindings);
        return values;
    }
    private static Double limit(Double value) {
        if (value == null) return null;
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Boundary limits must be finite numbers");
        return value < 0 ? null : value;
    }
    private static Integer limit(Integer value) { return value == null || value < 0 ? null : value; }
}
