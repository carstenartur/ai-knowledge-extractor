package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextFootprintMetricsTest {
    @Test
    void usesLineWeightedSizesAndCapabilityWorkingSets() {
        RepositorySnapshot snapshot = new RepositorySnapshot();
        snapshot.classes.add(classFact("example.A", 100));
        snapshot.classes.add(classFact("example.B", 50));
        snapshot.tests.add(testFact("example.ATest", 40));
        snapshot.docs.add(Map.of("title", "Architecture", "lineCount", 20));
        snapshot.capabilities.add(Map.of("id", "a", "classes", List.of("example.A")));
        snapshot.capabilities.add(Map.of("id", "b", "classes", List.of("example.B")));

        Map<String, Object> metrics = ContextFootprintMetrics.calculate(snapshot);

        assertEquals(1200, metrics.get("productionContextTokens"));
        assertEquals(320, metrics.get("testEvidenceTokens"));
        assertEquals(120, metrics.get("documentationContextTokens"));
        assertEquals(800, metrics.get("p90CapabilityWorkingSetTokens"));
        assertEquals(2, metrics.get("capabilitySampleCount"));
        assertTrue(((Number) metrics.get("normalizedContextDebt")).doubleValue() < 100.0d);
    }

    @Test
    void splittingAClassDoesNotIncreaseDebtWhenCapabilityWorkingSetStaysEqual() {
        RepositorySnapshot monolith = new RepositorySnapshot();
        monolith.classes.add(classFact("example.Monolith", 150));
        monolith.capabilities.add(Map.of("id", "feature", "classes", List.of("example.Monolith")));

        RepositorySnapshot split = new RepositorySnapshot();
        split.classes.add(classFact("example.PartA", 100));
        split.classes.add(classFact("example.PartB", 50));
        split.capabilities.add(Map.of("id", "feature", "classes", List.of("example.PartA", "example.PartB")));

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
