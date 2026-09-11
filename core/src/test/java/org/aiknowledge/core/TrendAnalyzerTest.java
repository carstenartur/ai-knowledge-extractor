package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrendAnalyzerTest {
    @TempDir
    Path temp;

    @Test
    void analyzeWritesTrendAndSnapshotReports() throws Exception {
        Path project = project("trend-fixture");
        Path output = project.resolve("build/ai-knowledge");

        new AiKnowledgeRunner().analyze(ExtractionOptions.defaults(project, output));

        assertTrue(Files.isRegularFile(output.resolve("metrics-snapshot.json")));
        assertTrue(Files.isRegularFile(output.resolve("trend.json")));
        assertTrue(Files.isRegularFile(output.resolve("trend.html")));
        String trend = Files.readString(output.resolve("trend.json"));
        assertTrue(trend.contains("baselinePresent"));
        assertTrue(trend.contains("\"baselinePath\":\"ai-knowledge/complexity-baseline.json\""));
        assertTrue(trend.contains("No complexity baseline found"));
    }

    @Test
    void checkFailsWhenTrendThresholdIsExceeded() throws Exception {
        Path project = project("trend-gate-fixture");
        Files.writeString(project.resolve("ai-knowledge/complexity-baseline.json"), """
                {
                  "schemaVersion": 1,
                  "estimatedContextTokens": 0,
                  "conceptRadius": 1,
                  "aiCognitiveDebt": 0.0
                }
                """);
        Path output = project.resolve("build/ai-knowledge");
        ExtractionOptions options = new ExtractionOptions(
                project,
                output,
                project.resolve("ai-knowledge"),
                project.resolve("ai-knowledge"),
                false,
                100.0d,
                0.0d,
                Double.MAX_VALUE,
                Double.MAX_VALUE);

        assertThrows(IOException.class, () -> new AiKnowledgeRunner().check(options));

        String check = Files.readString(output.resolve("check.json"));
        assertTrue(check.contains("trendViolationCount"));
        assertTrue(check.contains("\"passed\":false"));
        String trend = Files.readString(output.resolve("trend.json"));
        assertTrue(trend.contains("\"baselinePresent\":true"));
        var trendValue = (java.util.Map<?, ?>) StrictJsonReader.read(output.resolve("trend.json"));
        var baselineValue = (java.util.Map<?, ?>) trendValue.get("baseline");
        assertEquals(0, ((Number) baselineValue.get("estimatedContextTokens")).intValue());
        assertTrue(trend.contains("\"conceptRadius\":1"));
        assertTrue(trend.contains("\"aiCognitiveDebt\":0.0"));
        assertTrue(trend.contains("AI cognitive debt increased"));
    }

    @Test
    void normalizedContextDebtTrendIgnoresLegacyDebtGrowth() throws Exception {
        Path project = project("normalized-context-debt-trend");
        Files.writeString(project.resolve("ai-knowledge/complexity-baseline.json"), """
                {
                  "schemaVersion": 1,
                  "aiCognitiveDebt": 600.0,
                  "aiContextDebt": 17.11,
                  "contextDebtModelVersion": "context-footprint-v3"
                }
                """);
        var trend = TrendAnalyzer.trend(
                trendOptions(project, 0.0d),
                Map.of(
                        "aiCognitiveDebt", 700.0d,
                        "aiContextDebt", 17.10d,
                        "contextFootprint", Map.of("schemaVersion", 3)));

        var deltas = (Map<?, ?>) trend.get("deltas");
        assertEquals(-0.01d,
                ((Number) ((Map<?, ?>) deltas.get("aiContextDebt")).get("absolute")).doubleValue(),
                0.000001d);
        assertEquals(100.0d,
                ((Number) ((Map<?, ?>) deltas.get("aiCognitiveDebt")).get("absolute")).doubleValue());
        assertEquals(0, trend.get("violationCount"),
                "the trend gate must use the normalized debt model, not its legacy diagnostic alias");
    }

    @Test
    void legacyDebtBaselineRequiresReviewedNormalizedBaselineBeforeDebtTrendGating() throws Exception {
        Path project = project("legacy-context-debt-baseline");
        Files.writeString(project.resolve("ai-knowledge/complexity-baseline.json"), """
                {
                  "schemaVersion": 1,
                  "aiCognitiveDebt": 600.0
                }
                """);
        var trend = TrendAnalyzer.trend(
                trendOptions(project, 0.0d),
                Map.of(
                        "aiCognitiveDebt", 700.0d,
                        "aiContextDebt", 17.10d,
                        "contextFootprint", Map.of("schemaVersion", 3)));

        assertEquals(0, trend.get("violationCount"));
        assertTrue(trend.get("warnings").toString().contains("context-debt model"));
        assertFalse(((Map<?, ?>) trend.get("deltas")).containsKey("aiContextDebt"));
    }

    @Test
    void boundaryDeltasRequirePresentMetricsAndTheSameModelVersion() throws Exception {
        Path project = project("boundary-trend");
        var options = ExtractionOptions.defaults(project, project.resolve("build/ai-knowledge"));
        var current = java.util.Map.of("boundaryAnalysis", java.util.Map.of("score", 12,
                "scoringModelVersion", "1.0", "clientCallCount", 1));
        Path baseline = project.resolve("ai-knowledge/complexity-baseline.json");
        Files.writeString(baseline, "{\"schemaVersion\":1, \"boundaryScore\" : 2, \"boundaryScoringModelVersion\":\"1.0\"}");
        var comparable = TrendAnalyzer.trend(options, current);
        var deltas = (java.util.Map<?, ?>) comparable.get("deltas");
        assertEquals(10.0, ((java.util.Map<?, ?>) deltas.get("boundaryScore")).get("absolute"));
        assertFalse(deltas.containsKey("boundaryDynamicCalls"));
        Files.writeString(baseline, "{\"boundaryScore\":2,\"boundaryScoringModelVersion\":\"2.0\"}");
        var incompatible = TrendAnalyzer.trend(options, current);
        assertFalse(((java.util.Map<?, ?>) incompatible.get("deltas")).containsKey("boundaryScore"));
        assertTrue(incompatible.get("warnings").toString().contains("scoring-model version"));
        Files.writeString(baseline, "{\"schemaVersion\":1}");
        assertFalse(((java.util.Map<?, ?>) TrendAnalyzer.trend(options, current).get("deltas")).containsKey("boundaryScore"));
    }

    private ExtractionOptions trendOptions(Path project, double maxDebtIncrease) {
        return new ExtractionOptions(
                project,
                project.resolve("build/ai-knowledge"),
                project.resolve("ai-knowledge"),
                project.resolve("ai-knowledge"),
                false,
                100.0d,
                maxDebtIncrease,
                Double.MAX_VALUE,
                Double.MAX_VALUE);
    }

    private Path project(String name) throws Exception {
        Path project = temp.resolve(name);
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/App.java"), "package example;\npublic class App { public void run() {} }\n");
        return project;
    }
}
