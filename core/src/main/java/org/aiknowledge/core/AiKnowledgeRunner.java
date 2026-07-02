package org.aiknowledge.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.aiknowledge.core.linker.ClaimVerifier;

/** Public facade used by Gradle, Maven and future CLI integrations. */
public final class AiKnowledgeRunner {
    public RepositorySnapshot generate(ExtractionOptions options) throws IOException {
        RepositorySnapshot snapshot = new RepositoryScanner().scan(options);
        writeKnowledgeIndex(options.outputDirectory(), snapshot);
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
        boolean passed = debt <= options.maxCognitiveDebt() && trendViolations == 0 && claimFailures == 0 && (!options.failOnWarnings() || warnings == 0);
        Map check = new LinkedHashMap();
        check.put("passed", passed);
        check.put("aiCognitiveDebt", debt);
        check.put("maxCognitiveDebt", options.maxCognitiveDebt());
        check.put("warningCount", warnings);
        check.put("failOnWarnings", options.failOnWarnings());
        check.put("trendViolationCount", trendViolations);
        check.put("trendPassed", trend.get("passed"));
        check.put("trendThresholds", trend.get("thresholds"));
        check.put("claimFailureCount", claimFailures);
        StableIo.writeJson(options.outputDirectory().resolve("check.json"), check);
        if (!passed) {
            throw new IOException("AI knowledge quality gate failed: debt=" + debt + ", warnings=" + warnings + ", trendViolations=" + trendViolations + ", claimFailures=" + claimFailures);
        }
        return check;
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

    private static void writeKnowledgeIndex(Path outputDirectory, RepositorySnapshot snapshot) throws IOException {
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
    }

    private static Map envelope(String key, Object value) {
        Map map = new LinkedHashMap();
        map.put(key, value);
        return map;
    }
}
