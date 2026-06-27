package io.github.carstenartur.aiknowledge.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Public facade used by Gradle, Maven and future CLI integrations. */
public final class AiKnowledgeRunner {
    public RepositorySnapshot generate(ExtractionOptions options) throws IOException {
        RepositorySnapshot snapshot = new RepositoryScanner().scan(options);
        writeKnowledgeIndex(options.outputDirectory(), snapshot);
        return snapshot;
    }

    public Map analyze(ExtractionOptions options) throws IOException {
        RepositorySnapshot snapshot = generate(options);
        Map report = new ReportAnalyzer().complexity(options, snapshot);
        StableIo.writeJson(options.outputDirectory().resolve("complexity.json"), report);
        StableIo.writeText(options.outputDirectory().resolve("complexity.html"), ReportAnalyzer.html("AI Cognitive Complexity", report));
        return report;
    }

    public Map optimize(ExtractionOptions options) throws IOException {
        RepositorySnapshot snapshot = generate(options);
        ReportAnalyzer analyzer = new ReportAnalyzer();
        Map complexity = analyzer.complexity(options, snapshot);
        Map report = analyzer.optimization(options, snapshot, complexity);
        StableIo.writeJson(options.outputDirectory().resolve("complexity.json"), complexity);
        StableIo.writeText(options.outputDirectory().resolve("complexity.html"), ReportAnalyzer.html("AI Cognitive Complexity", complexity));
        StableIo.writeJson(options.outputDirectory().resolve("optimization.json"), report);
        StableIo.writeText(options.outputDirectory().resolve("optimization.html"), ReportAnalyzer.html("AI Knowledge Optimization", report));
        return report;
    }

    public Map benchmark(ExtractionOptions options) throws IOException {
        RepositorySnapshot snapshot = generate(options);
        ReportAnalyzer analyzer = new ReportAnalyzer();
        Map complexity = analyzer.complexity(options, snapshot);
        Map report = analyzer.benchmark(options, snapshot, complexity);
        StableIo.writeJson(options.outputDirectory().resolve("complexity.json"), complexity);
        StableIo.writeText(options.outputDirectory().resolve("complexity.html"), ReportAnalyzer.html("AI Cognitive Complexity", complexity));
        StableIo.writeJson(options.outputDirectory().resolve("benchmark.json"), report);
        StableIo.writeText(options.outputDirectory().resolve("benchmark.html"), ReportAnalyzer.html("AI Extraction Benchmark", report));
        return report;
    }

    public Map check(ExtractionOptions options) throws IOException {
        Map complexity = analyze(options);
        double debt = ((Number) complexity.getOrDefault("aiCognitiveDebt", 0.0d)).doubleValue();
        int warnings = ((Number) complexity.getOrDefault("warningCount", 0)).intValue();
        boolean passed = debt <= options.maxCognitiveDebt() && (!options.failOnWarnings() || warnings == 0);
        Map check = new LinkedHashMap();
        check.put("passed", passed);
        check.put("aiCognitiveDebt", debt);
        check.put("maxCognitiveDebt", options.maxCognitiveDebt());
        check.put("warningCount", warnings);
        check.put("failOnWarnings", options.failOnWarnings());
        StableIo.writeJson(options.outputDirectory().resolve("check.json"), check);
        if (!passed) {
            throw new IOException("AI knowledge quality gate failed: debt=" + debt + ", warnings=" + warnings);
        }
        return check;
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
    }

    private static Map envelope(String key, Object value) {
        Map map = new LinkedHashMap();
        map.put(key, value);
        return map;
    }
}
