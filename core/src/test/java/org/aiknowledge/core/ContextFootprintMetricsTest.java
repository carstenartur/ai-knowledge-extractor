package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextFootprintMetricsTest {
    @Test
    void usesLinkedTypesForLineWeightedCapabilityWorkingSets() {
        RepositorySnapshot snapshot = new RepositorySnapshot();
        snapshot.classes.add(classFact("example.A", 100));
        snapshot.classes.add(classFact("example.B", 50));
        snapshot.tests.add(testFact("example.ATest", 40));
        snapshot.docs.add(Map.of("title", "Architecture", "lineCount", 20));
        snapshot.capabilities.add(Map.of("id", "a", "matchedTypes", List.of("example.A")));
        snapshot.capabilities.add(Map.of("id", "b", "matchedTypes", List.of("example.B")));

        Map<String, Object> metrics = ContextFootprintMetrics.calculate(snapshot);

        assertEquals(3, metrics.get("schemaVersion"));
        assertEquals("MEASURED", metrics.get("measurementStatus"));
        assertEquals(1200, metrics.get("productionContextTokens"));
        assertEquals(320, metrics.get("testEvidenceTokens"));
        assertEquals(120, metrics.get("documentationContextTokens"));
        assertEquals(800, metrics.get("p90CapabilityWorkingSetTokens"));
        assertEquals(2, metrics.get("capabilitySampleCount"));
        assertEquals(0, metrics.get("unresolvedCapabilityTypeReferences"));
        assertTrue(((Number) metrics.get("normalizedContextDebt")).doubleValue() < 100.0d);
    }

    @Test
    void resolvesRegelsucheStyleModuleOnlyCapabilitiesFromSourcePaths() {
        RepositorySnapshot snapshot = new RepositorySnapshot();
        snapshot.modules.add(Map.of("name", "app", "path", "app"));
        snapshot.modules.add(Map.of("name", "regelsuche-core", "path", "regelsuche-core"));
        snapshot.classes.add(classFact(
                "de.regelsuche.App", 100, "app/src/main/java/de/regelsuche/App.java", "de.regelsuche"));
        snapshot.classes.add(classFact(
                "de.regelsuche.ast.Expr", 50,
                "regelsuche-core/src/main/java/de/regelsuche/ast/Expr.java", "de.regelsuche.ast"));
        snapshot.capabilities.add(Map.of(
                "id", "web-workbench",
                "modules", List.of("app"),
                "ownerModules", List.of("app"),
                "matchedModules", List.of("app")));
        snapshot.capabilities.add(Map.of(
                "id", "rewrite-core",
                "matchedModules", List.of("regelsuche-core")));

        Map<String, Object> metrics = ContextFootprintMetrics.calculate(snapshot);

        assertEquals("MEASURED", metrics.get("measurementStatus"));
        assertEquals(2, metrics.get("capabilitySampleCount"));
        assertEquals(800, metrics.get("p90CapabilityWorkingSetTokens"));
        assertEquals(0, metrics.get("unresolvedCapabilityModuleReferences"));
        Map<?, ?> sources = (Map<?, ?>) metrics.get("capabilityReferenceSources");
        assertEquals(2, sources.get("matchedModules"));
        assertEquals(1, sources.get("modules"));
        assertEquals(1, sources.get("ownerModules"));
    }

    @Test
    void resolvesPackageSelectorsAndDeduplicatesOverlappingTypes() {
        RepositorySnapshot snapshot = new RepositorySnapshot();
        snapshot.classes.add(classFact(
                "example.search.Engine", 100,
                "search/src/main/java/example/search/Engine.java", "example.search"));
        snapshot.classes.add(classFact(
                "example.search.index.Index", 50,
                "search/src/main/java/example/search/index/Index.java", "example.search.index"));
        snapshot.classes.add(classFact(
                "example.other.Other", 20,
                "other/src/main/java/example/other/Other.java", "example.other"));
        snapshot.capabilities.add(Map.of(
                "id", "search",
                "matchedTypes", List.of("example.search.Engine"),
                "packages", "example.search"));

        Map<String, Object> metrics = ContextFootprintMetrics.calculate(snapshot);

        assertEquals(1, metrics.get("capabilitySampleCount"));
        assertEquals(1200, metrics.get("p90CapabilityWorkingSetTokens"));
        assertEquals(0, metrics.get("unresolvedCapabilityPackageReferences"));
    }

    @Test
    void retainsExplicitClassesAndCountsUnresolvedReferences() {
        RepositorySnapshot snapshot = new RepositorySnapshot();
        snapshot.classes.add(classFact("example.A", 100));
        snapshot.classes.add(classFact("example.B", 50));
        snapshot.capabilities.add(Map.of(
                "id", "feature",
                "matchedTypes", List.of("example.A"),
                "classes", List.of("example.A", "example.B", "example.Missing")));

        Map<String, Object> metrics = ContextFootprintMetrics.calculate(snapshot);

        assertEquals(1, metrics.get("capabilitySampleCount"));
        assertEquals(1200, metrics.get("p90CapabilityWorkingSetTokens"));
        assertEquals(1, metrics.get("unresolvedCapabilityTypeReferences"));
    }

    @Test
    void missingCapabilitySamplesAreExplicitAndFailClosed() {
        RepositorySnapshot snapshot = new RepositorySnapshot();
        snapshot.classes.add(classFact("example.A", 100));
        snapshot.capabilities.add(Map.of("id", "unlinked"));

        Map<String, Object> metrics = ContextFootprintMetrics.calculate(snapshot);

        assertEquals("NO_CAPABILITY_SAMPLES", metrics.get("measurementStatus"));
        assertEquals(0, metrics.get("capabilitySampleCount"));
        assertEquals(1, metrics.get("capabilitiesWithoutSelectors"));
        assertEquals(0, metrics.get("p90CapabilityWorkingSetTokens"));
        assertEquals(100.0d, metrics.get("normalizedContextDebt"));
    }

    @Test
    void splittingAClassDoesNotIncreaseDebtWhenCapabilityWorkingSetStaysEqual() {
        RepositorySnapshot monolith = new RepositorySnapshot();
        monolith.classes.add(classFact("example.Monolith", 150));
        monolith.capabilities.add(Map.of(
                "id", "feature", "matchedTypes", List.of("example.Monolith")));

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
        return classFact(name, lines, "src/main/java/" + name.replace('.', '/') + ".java", packageOf(name));
    }

    private static Map<String, Object> classFact(
            String name,
            int lines,
            String sourceFile,
            String packageName) {
        return Map.of(
                "class", name,
                "lineCount", lines,
                "sourceFile", sourceFile,
                "package", packageName);
    }

    private static Map<String, Object> testFact(String name, int lines) {
        return Map.of("testClass", name, "lineCount", lines);
    }

    private static String packageOf(String name) {
        int separator = name.lastIndexOf('.');
        return separator < 0 ? "" : name.substring(0, separator);
    }
}
