package org.aiknowledge.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.aiknowledge.core.linker.ClaimVerifier;

/** Public facade used by Gradle, Maven and future CLI integrations. */
public final class AiKnowledgeRunner {
    public RepositorySnapshot generate(ExtractionOptions options) throws IOException {
        RepositorySnapshot snapshot = new RepositoryScanner().scan(options);
        writeKnowledgeIndex(options.outputDirectory(), options, snapshot);
        return snapshot;
    }

    public Map analyze(ExtractionOptions options) throws IOException {
        RepositorySnapshot snapshot = generate(options);
        ReportAnalyzer analyzer = new ReportAnalyzer();
        Map report = analyzer.complexity(options, snapshot);
        writeAnalysisReports(options, report);
        return report;
    }

    public Map optimize(ExtractionOptions options) throws IOException {
        RepositorySnapshot snapshot = generate(options);
        ReportAnalyzer analyzer = new ReportAnalyzer();
        Map complexity = analyzer.complexity(options, snapshot);
        Map report = analyzer.optimization(options, snapshot, complexity);
        writeAnalysisReports(options, complexity);
        StableIo.writeJson(options.outputDirectory().resolve("optimization.json"), report);
        StableIo.writeText(options.outputDirectory().resolve("optimization.html"), ReportAnalyzer.html("AI Knowledge Optimization", report));
        return report;
    }

    public Map benchmark(ExtractionOptions options) throws IOException {
        RepositorySnapshot snapshot = generate(options);
        ReportAnalyzer analyzer = new ReportAnalyzer();
        Map complexity = analyzer.complexity(options, snapshot);
        Map report = analyzer.benchmark(options, snapshot, complexity);
        writeAnalysisReports(options, complexity);
        StableIo.writeJson(options.outputDirectory().resolve("benchmark.json"), report);
        StableIo.writeText(options.outputDirectory().resolve("benchmark.html"), ReportAnalyzer.html("AI Extraction Benchmark", report));
        return report;
    }

    public Map check(ExtractionOptions options) throws IOException {
        RepositorySnapshot snapshot = generate(options);
        ReportAnalyzer analyzer = new ReportAnalyzer();
        Map complexity = analyzer.complexity(options, snapshot);
        writeAnalysisReports(options, complexity);
        Map trend = TrendAnalyzer.trend(options, complexity);
        double debt = ((Number) complexity.getOrDefault("aiCognitiveDebt", 0.0d)).doubleValue();
        int warnings = ((Number) complexity.getOrDefault("warningCount", 0)).intValue();
        int trendViolations = ((Number) trend.getOrDefault("violationCount", 0)).intValue();
        int claimFailures = ClaimVerifier.countErrorFailures(snapshot.claims);
        Map knowledgeGates = KnowledgeQualityGate.evaluate(options, snapshot);
        boolean knowledgeGatesPassed = Boolean.TRUE.equals(knowledgeGates.get("passed"));
        List<String> violations = qualityGateViolations(options, complexity, debt, trendViolations, claimFailures, knowledgeGatesPassed, warnings);
        boolean passed = violations.isEmpty();
        Map check = new LinkedHashMap();
        check.put("passed", passed);
        check.put("aiContextDebt", complexity.getOrDefault("aiContextDebt", debt));
        check.put("aiCognitiveDebt", debt);
        check.put("maxCognitiveDebt", options.maxCognitiveDebt());
        check.put("codeComplexity", complexity.getOrDefault("codeComplexity", Map.of()));
        check.put("methodComplexityThresholds", methodComplexityThresholds(options));
        check.put("violations", violations);
        check.put("warningCount", warnings);
        check.put("failOnWarnings", options.failOnWarnings());
        check.put("trendViolationCount", trendViolations);
        check.put("trendPassed", trend.get("passed"));
        check.put("trendThresholds", trend.get("thresholds"));
        check.put("claimFailureCount", claimFailures);
        check.put("knowledgeQualityGates", knowledgeGates);
        StableIo.writeJson(options.outputDirectory().resolve("check.json"), check);
        if (!passed) {
            throw new IOException("AI knowledge quality gate failed: " + String.join("; ", violations));
        }
        return check;
    }

    private static List<String> qualityGateViolations(
            ExtractionOptions options,
            Map complexity,
            double debt,
            int trendViolations,
            int claimFailures,
            boolean knowledgeGatesPassed,
            int warnings) {
        List<String> violations = new ArrayList<>();
        if (debt > options.maxCognitiveDebt()) {
            violations.add("aiContextDebt=" + debt + " > maxCognitiveDebt=" + options.maxCognitiveDebt());
        }
        Map code = codeComplexity(complexity);
        addIntViolation(violations, code, "maxMethodCognitiveComplexity", options.maxMethodCognitiveComplexity());
        addIntViolation(violations, code, "maxMethodCyclomaticComplexity", options.maxMethodCyclomaticComplexity());
        addDoubleViolation(violations, code, "averageMethodCognitiveComplexity", options.maxAverageMethodCognitiveComplexity());
        addDoubleViolation(violations, code, "averageMethodCyclomaticComplexity", options.maxAverageMethodCyclomaticComplexity());
        addIntViolation(violations, code, "methodsAboveCognitiveThreshold", options.maxMethodsAboveCognitiveThreshold());
        addIntViolation(violations, code, "methodsAboveCyclomaticThreshold", options.maxMethodsAboveCyclomaticThreshold());
        if (trendViolations > 0) violations.add("trendViolations=" + trendViolations);
        if (claimFailures > 0) violations.add("claimFailures=" + claimFailures);
        if (!knowledgeGatesPassed) violations.add("knowledgeQualityGatesPassed=false");
        if (options.failOnWarnings() && warnings > 0) violations.add("warnings=" + warnings + " while failOnWarnings=true");
        return violations;
    }

    private static Map codeComplexity(Map complexity) {
        Object code = complexity.get("codeComplexity");
        return code instanceof Map map ? map : Map.of();
    }

    private static void addIntViolation(List<String> violations, Map metrics, String metric, int max) {
        if (max == Integer.MAX_VALUE) return;
        int actual = intMetric(metrics, metric);
        if (actual > max) violations.add(metric + "=" + actual + " > " + metricLimitName(metric) + "=" + max);
    }

    private static void addDoubleViolation(List<String> violations, Map metrics, String metric, double max) {
        if (max == Double.MAX_VALUE) return;
        double actual = doubleMetric(metrics, metric);
        if (actual > max) violations.add(metric + "=" + actual + " > " + metricLimitName(metric) + "=" + max);
    }

    private static String metricLimitName(String metric) {
        return switch (metric) {
            case "maxMethodCognitiveComplexity" -> "maxMethodCognitiveComplexity";
            case "maxMethodCyclomaticComplexity" -> "maxMethodCyclomaticComplexity";
            case "averageMethodCognitiveComplexity" -> "maxAverageMethodCognitiveComplexity";
            case "averageMethodCyclomaticComplexity" -> "maxAverageMethodCyclomaticComplexity";
            case "methodsAboveCognitiveThreshold" -> "maxMethodsAboveCognitiveThreshold";
            case "methodsAboveCyclomaticThreshold" -> "maxMethodsAboveCyclomaticThreshold";
            default -> "max" + metric;
        };
    }

    private static int intMetric(Map metrics, String metric) {
        Object value = metrics.get(metric);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static double doubleMetric(Map metrics, String metric) {
        Object value = metrics.get(metric);
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private static Map methodComplexityThresholds(ExtractionOptions options) {
        Map thresholds = new LinkedHashMap();
        thresholds.put("maxMethodCognitiveComplexity", options.maxMethodCognitiveComplexity());
        thresholds.put("maxMethodCyclomaticComplexity", options.maxMethodCyclomaticComplexity());
        thresholds.put("maxAverageMethodCognitiveComplexity", options.maxAverageMethodCognitiveComplexity());
        thresholds.put("maxAverageMethodCyclomaticComplexity", options.maxAverageMethodCyclomaticComplexity());
        thresholds.put("maxMethodsAboveCognitiveThreshold", options.maxMethodsAboveCognitiveThreshold());
        thresholds.put("maxMethodsAboveCyclomaticThreshold", options.maxMethodsAboveCyclomaticThreshold());
        return thresholds;
    }

    private static void writeAnalysisReports(ExtractionOptions options, Map complexity) throws IOException {
        Map snapshot = TrendAnalyzer.snapshot(complexity);
        Map trend = TrendAnalyzer.trend(options, complexity);
        StableIo.writeJson(options.outputDirectory().resolve("complexity.json"), complexity);
        StableIo.writeText(options.outputDirectory().resolve("complexity.html"), ReportAnalyzer.html("AI Cognitive Complexity", complexity));
        StableIo.writeJson(options.outputDirectory().resolve("metrics-snapshot.json"), snapshot);
        StableIo.writeJson(options.outputDirectory().resolve("trend.json"), trend);
        StableIo.writeText(options.outputDirectory().resolve("trend.html"), ReportAnalyzer.html("AI Complexity Trend", trend));
    }

    private static void writeKnowledgeIndex(Path outputDirectory, ExtractionOptions options, RepositorySnapshot snapshot) throws IOException {
        Files.createDirectories(outputDirectory);
        StableIo.writeJson(outputDirectory.resolve("index.json"), snapshot.index);
        StableIo.writeJson(outputDirectory.resolve("modules.json"), envelope("modules", snapshot.modules));
        StableIo.writeJson(outputDirectory.resolve("classes.json"), envelope("classes", snapshot.classes));
        StableIo.writeJson(outputDirectory.resolve("tests.json"), envelope("tests", snapshot.tests));
        StableIo.writeJson(outputDirectory.resolve("docs.json"), envelope("docs", snapshot.docs));
        StableIo.writeJson(outputDirectory.resolve("dependencies.json"), envelope("dependencies", snapshot.dependencies));
        StableIo.writeJson(outputDirectory.resolve("capabilities.json"), envelope("capabilities", snapshot.capabilities));
        StableIo.writeJson(outputDirectory.resolve("claims.json"), envelope("claims", snapshot.claims));
        StableIo.writeJson(outputDirectory.resolve("evidence.json"), envelope("evidence", snapshot.evidence));
        ReviewContextGenerator.generate(options, snapshot);
    }

    private static Map envelope(String key, Object value) {
        Map map = new LinkedHashMap();
        map.put(key, value);
        return map;
    }
}
