package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextFootprintMetricsTest {
    @Test
    void usesLinkedAndExplicitCapabilityWorkingSets() {
        RepositorySnapshot snapshot = new RepositorySnapshot();
        snapshot.classes.add(classFact("example.A", 100));
        snapshot.classes.add(classFact("example.B", 50));
        snapshot.tests.add(testFact("example.ATest", 40));
        snapshot.docs.add(Map.of("title", "Architecture", "lineCount", 20));
        snapshot.capabilities.add(Map.of("id", "linked", "matchedTypes", List.of("example.A")));
        snapshot.capabilities.add(Map.of("id", "explicit", "classes", List.of("example.B")));
        snapshot.capabilities.add(Map.of(
                "id", "combined",
                "matchedTypes", List.of("example.A"),
                "classes", List.of("example.A", "example.B", "example.Missing")));

        Map<String, Object> metrics = ContextFootprintMetrics.calculate(snapshot);

        assertEquals(1200, metrics.get("productionContextTokens"));
        assertEquals(320, metrics.get("testEvidenceTokens"));
        assertEquals(120, metrics.get("documentationContextTokens"));
        assertEquals(1200, metrics.get("p90CapabilityWorkingSetTokens"));
        assertEquals(3, metrics.get("capabilitySampleCount"));
        assertEquals(1, metrics.get("unresolvedCapabilityTypeReferences"));
        assertEquals("available", metrics.get("measurementStatus"));
        assertEquals(
                Map.of(
                        "linkedMatchedTypes", 1,
                        "explicitClasses", 1,
                        "combinedLinkedAndExplicit", 1,
                        "withoutResolvedTypes", 0),
                metrics.get("capabilityWorkingSetSources"));
        assertTrue(((Number) metrics.get("normalizedContextDebt")).doubleValue() <= 100.0d);
    }

    @Test
    void doesNotChargeFallbackTokensForUnknownCapabilityTypes() {
        RepositorySnapshot snapshot = new RepositorySnapshot();
        snapshot.classes.add(classFact("example.A", 100));
        snapshot.capabilities.add(Map.of(
                "id", "feature",
                "matchedTypes", List.of("example.A", "example.Unknown"),
                "classes", List.of("example.A")));

        Map<String, Object> metrics = ContextFootprintMetrics.calculate(snapshot);

        assertEquals(800, metrics.get("p90CapabilityWorkingSetTokens"));
        assertEquals(1, metrics.get("unresolvedCapabilityTypeReferences"));
        assertEquals(1, metrics.get("capabilitySampleCount"));
    }

    @Test
    void reportsMissingCapabilitySamplesWithoutInventingRepositorySizedWork() {
        RepositorySnapshot snapshot = new RepositorySnapshot();
        snapshot.classes.add(classFact("example.A", 100));

        Map<String, Object> metrics = ContextFootprintMetrics.calculate(snapshot);

        assertEquals("unavailable-no-capability-working-set", metrics.get("measurementStatus"));
        assertEquals(0, metrics.get("capabilitySampleCount"));
        assertEquals(0, metrics.get("p90CapabilityWorkingSetTokens"));
        assertEquals(0.0d, metrics.get("normalizedContextDebt"));
        assertEquals(0.0d, metrics.get("contextEfficiencyScore"));
    }

    @Test
    void splittingAClassDoesNotIncreaseDebtWhenCapabilityWorkingSetStaysEqual() {
        RepositorySnapshot monolith = new RepositorySnapshot();
        monolith.classes.add(classFact("example.Monolith", 150));
        monolith.capabilities.add(Map.of("id", "feature", "matchedTypes", List.of("example.Monolith")));

        RepositorySnapshot split = new RepositorySnapshot();
        split.classes.add(classFact("example.PartA", 100));
        split.classes.add(classFact("example.PartB", 50));
        split.capabilities.add(Map.of(
                "id", "feature",
                "matchedTypes", List.of("example.PartA", "example.PartB")));

        Map<String, Object> before = ContextFootprintMetrics.calculate(monolith);
        Map<String, Object> after = ContextFootprintMetrics.calculate(split);

        assertEquals(before.get("repositoryContextTokens"), after.get("repositoryContextTokens"));
        assertEquals(before.get("normalizedContextDebt"), after.get("normalizedContextDebt"));
    }

    private static Map<String, Object> classFact(String name, int lines) {
        return Map.of("class", name, "lineCount", lines);
    }

    private static Map<String, Object> testFact(String name, int lines) {
        return Map.of("testClass", name, "lineCount", lines);
    }
}
