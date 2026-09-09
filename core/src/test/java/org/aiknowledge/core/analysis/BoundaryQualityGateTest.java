package org.aiknowledge.core.analysis;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.aiknowledge.core.RepositorySnapshot;
import org.junit.jupiter.api.Test;

class BoundaryQualityGateTest {
    @Test void defaultsAreDisabledAndNoClientEvidenceIsNotApplicable() {
        RepositorySnapshot mixed = fixture();
        Map<String, Object> boundary = BoundaryAnalyzer.analyze(mixed);
        assertEquals(true, BoundaryQualityGate.evaluate(BoundaryGateOptions.disabled(), boundary, mixed).get("passed"));
        var strict = new BoundaryGateOptions(0.0, 0, 0.0, 0, 0, 0.0, 0, true);
        RepositorySnapshot empty = new RepositorySnapshot();
        empty.relations.add(Map.of("kind", "SOURCE_UNIT_IMPORTS_MODULE", "runtime", true,
                "external", true, "packageName", "unknown", "sourceFile", "web.ts"));
        var skipped = BoundaryQualityGate.evaluate(strict, BoundaryAnalyzer.analyze(empty), empty);
        assertEquals(true, skipped.get("passed"));
        assertEquals("not-applicable", skipped.get("applicability"));
        assertEquals("insufficient-client-evidence", skipped.get("confidence"));
    }

    @Test void everyConfiguredLimitReportsMeasuredValuesAndRawLowConfidenceEvidence() {
        RepositorySnapshot snapshot = fixture();
        var boundary = BoundaryAnalyzer.analyze(snapshot);
        List<BoundaryGateOptions> policies = List.of(
                new BoundaryGateOptions(0.0, null, null, null, null, null, null, false),
                new BoundaryGateOptions(null, 0, null, null, null, null, null, false),
                new BoundaryGateOptions(null, null, 0.0, null, null, null, null, false),
                new BoundaryGateOptions(null, null, null, 0, null, null, null, false),
                new BoundaryGateOptions(null, null, null, null, 1, null, null, false),
                new BoundaryGateOptions(null, null, null, null, null, 0.0, null, false),
                new BoundaryGateOptions(null, null, null, null, null, null, 1, false),
                new BoundaryGateOptions(null, null, null, null, null, null, null, true));
        for (var policy : policies) {
            var gate = BoundaryQualityGate.evaluate(policy, boundary, snapshot);
            assertEquals(false, gate.get("passed"));
            assertEquals("low", gate.get("confidence"));
            var violations = (List<?>) gate.get("violations");
            assertEquals(1, violations.size(), policy.toString());
            var violation = (Map<?, ?>) violations.get(0);
            assertTrue(((Number) violation.get("measured")).doubleValue() > ((Number) violation.get("threshold")).doubleValue());
            assertTrue(violation.get("calls").toString().contains("web.ts"));
            assertTrue(violation.containsKey("dimension"));
        }
        var values = BoundaryQualityGate.measurements(boundary);
        assertEquals(2.0, values.get("boundaryStateInterpretations"), "Do not count callable-wide evidence once per call");
        assertEquals(2, values.get("boundaryUnresolvedCalls"));
        assertEquals(1L, values.get("boundaryDynamicCalls"));
        assertEquals(true, BoundaryQualityGate.evaluate(
                new BoundaryGateOptions(100.0, 2, 1.0, 1, 3, 100.0, 2, false), boundary, snapshot).get("passed"));
    }

    @Test void frontendOnlyAndAmbiguousCallsRemainDistinctFromMissingEvidence() {
        RepositorySnapshot snapshot = fixture();
        snapshot.boundaries.removeIf(value -> value instanceof Map<?, ?> fact && "server-endpoint".equals(fact.get("kind")));
        var gate = BoundaryQualityGate.evaluate(new BoundaryGateOptions(null, 0, null, null, null, null, null, false),
                BoundaryAnalyzer.analyze(snapshot), snapshot);
        assertEquals("frontend-only", gate.get("confidence"));
        assertEquals("applicable", gate.get("applicability"));
        assertEquals(false, gate.get("passed"));
        snapshot.boundaries.add(endpoint("one"));
        snapshot.boundaries.add(endpoint("two"));
        var analysis = BoundaryAnalyzer.analyze(snapshot);
        assertEquals(1, analysis.get("ambiguousCallCount"));
        assertEquals(3, BoundaryQualityGate.measurements(analysis).get("boundaryUnresolvedCalls"));
    }

    @Test void moduleScopeCallablesInDifferentFilesNeverCreateFalseFanOut() {
        var snapshot = new RepositorySnapshot();
        snapshot.boundaries.add(call("a", "/a", "a.ts", ""));
        snapshot.boundaries.add(call("b", "/b", "b.ts", ""));
        assertEquals(1, BoundaryAnalyzer.analyze(snapshot).get("maxEndpointFanOutPerCallable"));
    }

    @Test void invalidNumericPoliciesFailExplicitly() {
        assertThrows(IllegalArgumentException.class, () -> new BoundaryGateOptions(Double.NaN, null, null, null, null, null, null, false));
        assertThrows(IllegalArgumentException.class, () -> new BoundaryGateOptions(null, null, 1.1, null, null, null, null, false));
        assertNull(new BoundaryGateOptions(-1.0, -1, -1.0, -1, -1, -1.0, -1, false).maxBoundaryScore());
    }

    private static RepositorySnapshot fixture() {
        var snapshot = new RepositorySnapshot();
        snapshot.boundaries.add(call("one", "/a", "web.ts", "workflow"));
        snapshot.boundaries.add(call("two", "/b", "web.ts", "workflow"));
        snapshot.boundaries.add(call("dynamic", "<dynamic>", "web.ts", "workflow"));
        snapshot.boundaries.add(endpoint("endpoint"));
        snapshot.relations.add(Map.of("kind", "SOURCE_UNIT_IMPORTS_MODULE", "runtime", true,
                "external", true, "packageName", "unknown", "sourceFile", "web.ts"));
        return snapshot;
    }
    private static Map<String, Object> call(String id, String path, String file, String callable) {
        var fact = new LinkedHashMap<String, Object>();
        fact.put("id", id); fact.put("kind", "client-call"); fact.put("method", "GET");
        fact.put("protocol", "http"); fact.put("normalizedPath", path); fact.put("callable", callable);
        fact.put("sourceFile", file); fact.put("confidence", "low"); fact.put("awaited", true);
        fact.put("backendStateInterpretationCount", 2); fact.put("client", "fetch");
        return fact;
    }
    private static Map<String, Object> endpoint(String id) {
        return Map.of("id", id, "kind", "server-endpoint", "method", "GET", "protocol", "http", "normalizedPath", "/a");
    }
}
