package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.aiknowledge.core.analysis.BoundaryAnalyzer;
import org.aiknowledge.core.analysis.BoundaryScoringModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Independent source labels and expected relative rankings, not a human validation study. */
class BoundaryCorpusEvaluationTest {
    @TempDir Path outputs;

    @Test void evaluatesLabelledSourcesRanksAndWeightSensitivity() throws Exception {
        Path corpus = Path.of("..", "examples", "boundary-corpus");
        Map<?, ?> manifest = (Map<?, ?>) StrictJsonReader.read(corpus.resolve("manifest.json"));
        List<Map<String, Object>> cases = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, int[]> grouped = new java.util.TreeMap<>();
        for (Object item : (List<?>) manifest.get("cases")) {
            Map<?, ?> label = (Map<?, ?>) item;
            String id = String.valueOf(label.get("id"));
            RepositorySnapshot snapshot = new KnowledgeExtractionPipeline().extract(
                    ExtractionOptions.defaults(corpus.resolve(id), outputs.resolve(id)));
            var analysis = BoundaryAnalyzer.analyze(snapshot);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            entry.put("framework", label.get("framework"));
            entry.put("boundaryAnalysis", analysis);
            entry.put("rawEvidence", Map.of("sourceUnits", snapshot.sourceUnits, "boundaries", snapshot.boundaries,
                    "relations", snapshot.relations, "dependencies", snapshot.dependencies, "warnings", snapshot.warnings));
            Set<String> clients = new TreeSet<>();
            Set<String> endpoints = new TreeSet<>();
            for (Object value : snapshot.boundaries) {
                Map<?, ?> fact = (Map<?, ?>) value;
                if ("client-call".equals(fact.get("kind"))) {
                    clients.add(fact.get("sourceFile") + "|" + fact.get("line") + "|" + fact.get("method") + "|" + fact.get("normalizedPath"));
                } else if ("server-endpoint".equals(fact.get("kind"))) {
                    endpoints.add(fact.get("sourceFile") + "|" + fact.get("method") + "|" + fact.get("normalizedPath"));
                }
            }
            Set<String> links = new TreeSet<>();
            for (Object value : (List<?>) analysis.get("links")) {
                Map<?, ?> link = (Map<?, ?>) value;
                links.add(link.get("sourceFile") + "|" + link.get("line") + "|" + link.get("operation") + "|" + link.get("status"));
            }
            Map<String, Object> metrics = new LinkedHashMap<>();
            Map<String, Set<String>> actual = Map.of("clients", clients, "endpoints", endpoints, "links", links);
            for (String kind : List.of("clients", "endpoints", "links")) {
                Set<String> expected = new TreeSet<>();
                for (Object value : (List<?>) label.get(kind)) expected.add(String.valueOf(value));
                var measure = compare(expected, actual.get(kind));
                metrics.put(kind, measure);
                int[] totals = grouped.computeIfAbsent(label.get("framework") + " / " + kind, ignored -> new int[3]);
                totals[0] += (int) measure.get("truePositives");
                totals[1] += (int) measure.get("falsePositives");
                totals[2] += (int) measure.get("falseNegatives");
                if (!expected.equals(actual.get(kind))) errors.add(id + " " + kind + ": " + measure);
            }
            entry.put("metrics", metrics);
            entry.put("weightSensitivity", sensitivity(analysis));
            cases.add(entry);
        }
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        cases.forEach(entry -> byId.put((String) entry.get("id"), entry));
        List<Map<String, Object>> rankings = new ArrayList<>();
        for (Object item : (List<?>) manifest.get("rankings")) {
            List<?> pair = (List<?>) item;
            Map<?, ?> lower = (Map<?, ?>) byId.get(pair.get(0)).get("boundaryAnalysis");
            Map<?, ?> higher = (Map<?, ?>) byId.get(pair.get(1)).get("boundaryAnalysis");
            boolean passed = ((Number) lower.get("score")).intValue() < ((Number) higher.get("score")).intValue();
            List<String> inversions = new ArrayList<>();
            Map<?, ?> a = (Map<?, ?>) byId.get(pair.get(0)).get("weightSensitivity");
            Map<?, ?> b = (Map<?, ?>) byId.get(pair.get(1)).get("weightSensitivity");
            for (Object key : a.keySet()) if (((Number) a.get(key)).intValue() >= ((Number) b.get(key)).intValue()) inversions.add(String.valueOf(key));
            rankings.add(Map.of("lower", pair.get(0), "higher", pair.get(1), "passed", passed, "sensitivityInversions", inversions));
            if (!passed) errors.add("Ranking inversion: " + pair);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("corpusVersion", manifest.get("corpusVersion"));
        report.put("scoringModelVersion", BoundaryScoringModel.VERSION);
        report.put("validation", "synthetic-regression-corpus");
        report.put("blindedExpertReviewPerformed", false);
        Map<String, Object> metricsByFramework = new LinkedHashMap<>();
        grouped.forEach((key, totals) -> metricsByFramework.put(key, metrics(totals[0], totals[1], totals[2])));
        report.put("metricsByFrameworkAndKind", metricsByFramework);
        report.put("cases", cases);
        report.put("rankings", rankings);
        report.put("errors", errors);
        Path directory = Path.of("build", "reports", "boundary-corpus");
        Files.createDirectories(directory);
        StableIo.writeJson(directory.resolve("report.json"), report);
        StringBuilder markdown = new StringBuilder("# Boundary corpus evaluation\n\nSynthetic regression evidence; no blinded expert review. Model " + BoundaryScoringModel.VERSION + ".\n\n| Scenario | Score | Confidence |\n|---|---:|---|\n");
        for (var entry : cases) {
            Map<?, ?> analysis = (Map<?, ?>) entry.get("boundaryAnalysis");
            markdown.append("| ").append(entry.get("id")).append(" | ").append(analysis.get("score")).append(" | ").append(analysis.get("confidence")).append(" |\n");
        }
        StableIo.writeText(directory.resolve("report.md"), markdown.toString());
        assertTrue(errors.isEmpty(), () -> String.join("\n", errors));
    }
    private static Map<String, Object> compare(Set<String> expected, Set<String> actual) {
        Set<String> fp = new TreeSet<>(actual); fp.removeAll(expected);
        Set<String> fn = new TreeSet<>(expected); fn.removeAll(actual);
        var result = metrics(actual.size() - fp.size(), fp.size(), fn.size());
        result.put("unexpected", new ArrayList<>(fp)); result.put("missing", new ArrayList<>(fn));
        return result;
    }
    private static Map<String, Object> metrics(int tp, int fp, int fn) {
        var result = new LinkedHashMap<String, Object>();
        result.put("truePositives", tp); result.put("falsePositives", fp); result.put("falseNegatives", fn);
        result.put("precision", tp + fp == 0 ? null : (double) tp / (tp + fp));
        result.put("recall", tp + fn == 0 ? null : (double) tp / (tp + fn));
        return result;
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Integer> sensitivity(Map<String, Object> analysis) {
        Map<String, Map<String, ?>> dimensions = (Map<String, Map<String, ?>>) analysis.get("dimensions");
        var scores = new LinkedHashMap<String, Integer>();
        for (String name : BoundaryScoringModel.WEIGHTS.keySet()) {
            for (double factor : List.of(0.75, 1.25)) {
                var weights = new LinkedHashMap<>(BoundaryScoringModel.WEIGHTS);
                weights.put(name, weights.get(name) * factor);
                scores.put(name + "*" + factor, BoundaryScoringModel.score(dimensions, weights));
            }
        }
        return scores;
    }
}
