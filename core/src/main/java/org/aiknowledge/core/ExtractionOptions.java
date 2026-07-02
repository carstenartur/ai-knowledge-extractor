package org.aiknowledge.core;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

/** Options shared by all deterministic AI knowledge tasks. */
public record ExtractionOptions(
        Path repositoryRoot,
        Path outputDirectory,
        Path seedDirectory,
        Path modelProfileDirectory,
        boolean failOnWarnings,
        double maxCognitiveDebt,
        double maxCognitiveDebtIncrease,
        double maxConceptRadiusIncrease,
        double maxContextTokenIncrease,
        boolean empiricalBenchmarkEnabled,
        Path empiricalBenchmarkFixtureFile,
        boolean requireCapabilityEvidence,
        boolean requireClaimVerification,
        int minContextPackCount,
        int maxContextPackTokens) {

    public ExtractionOptions(
            Path repositoryRoot,
            Path outputDirectory,
            Path seedDirectory,
            Path modelProfileDirectory,
            boolean failOnWarnings,
            double maxCognitiveDebt) {
        this(repositoryRoot, outputDirectory, seedDirectory, modelProfileDirectory, failOnWarnings, maxCognitiveDebt, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, false, null, false, false, 0, Integer.MAX_VALUE);
    }

    public ExtractionOptions(
            Path repositoryRoot,
            Path outputDirectory,
            Path seedDirectory,
            Path modelProfileDirectory,
            boolean failOnWarnings,
            double maxCognitiveDebt,
            double maxCognitiveDebtIncrease,
            double maxConceptRadiusIncrease,
            double maxContextTokenIncrease) {
        this(repositoryRoot, outputDirectory, seedDirectory, modelProfileDirectory, failOnWarnings, maxCognitiveDebt, maxCognitiveDebtIncrease, maxConceptRadiusIncrease, maxContextTokenIncrease, false, null, false, false, 0, Integer.MAX_VALUE);
    }

    public ExtractionOptions(
            Path repositoryRoot,
            Path outputDirectory,
            Path seedDirectory,
            Path modelProfileDirectory,
            boolean failOnWarnings,
            double maxCognitiveDebt,
            double maxCognitiveDebtIncrease,
            double maxConceptRadiusIncrease,
            double maxContextTokenIncrease,
            boolean empiricalBenchmarkEnabled,
            Path empiricalBenchmarkFixtureFile) {
        this(repositoryRoot, outputDirectory, seedDirectory, modelProfileDirectory, failOnWarnings, maxCognitiveDebt, maxCognitiveDebtIncrease, maxConceptRadiusIncrease, maxContextTokenIncrease, empiricalBenchmarkEnabled, empiricalBenchmarkFixtureFile, false, false, 0, Integer.MAX_VALUE);
    }

    public ExtractionOptions {
        repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot").toAbsolutePath().normalize();
        outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory").toAbsolutePath().normalize();
        seedDirectory = seedDirectory == null ? repositoryRoot.resolve("ai-knowledge") : seedDirectory.toAbsolutePath().normalize();
        modelProfileDirectory = modelProfileDirectory == null ? seedDirectory : modelProfileDirectory.toAbsolutePath().normalize();
        empiricalBenchmarkFixtureFile = empiricalBenchmarkFixtureFile == null
                ? seedDirectory.resolve("benchmark-fixtures.yaml")
                : empiricalBenchmarkFixtureFile.toAbsolutePath().normalize();
        maxCognitiveDebtIncrease = normalizeThreshold(maxCognitiveDebtIncrease);
        maxConceptRadiusIncrease = normalizeThreshold(maxConceptRadiusIncrease);
        maxContextTokenIncrease = normalizeThreshold(maxContextTokenIncrease);
        if (minContextPackCount < 0) minContextPackCount = 0;
        if (maxContextPackTokens < 0) maxContextPackTokens = Integer.MAX_VALUE;
    }

    public static ExtractionOptions defaults(Path repositoryRoot, Path outputDirectory) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        return new ExtractionOptions(root, outputDirectory, root.resolve("ai-knowledge"), root.resolve("ai-knowledge"), false, 100.0d);
    }

    public String reportPath(Path path) {
        Path normalized = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (normalized.startsWith(repositoryRoot)) {
            return repositoryRoot.relativize(normalized).toString().replace(File.separatorChar, '/');
        }
        return normalized.toString().replace(File.separatorChar, '/');
    }

    private static double normalizeThreshold(double value) {
        if (Double.isNaN(value) || value < 0.0d) return Double.MAX_VALUE;
        return value;
    }
}
