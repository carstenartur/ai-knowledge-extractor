package org.aiknowledge.core;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
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
        int maxContextPackTokens,
        int maxMethodCognitiveComplexity,
        int maxMethodCyclomaticComplexity,
        double maxAverageMethodCognitiveComplexity,
        double maxAverageMethodCyclomaticComplexity,
        int maxMethodsAboveCognitiveThreshold,
        int maxMethodsAboveCyclomaticThreshold,
        List<Path> classpathEntries) {

    public ExtractionOptions(
            Path repositoryRoot,
            Path outputDirectory,
            Path seedDirectory,
            Path modelProfileDirectory,
            boolean failOnWarnings,
            double maxCognitiveDebt) {
        this(repositoryRoot, outputDirectory, seedDirectory, modelProfileDirectory,
                failOnWarnings, maxCognitiveDebt,
                Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                false, null, false, false, 0, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, List.of());
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
        this(repositoryRoot, outputDirectory, seedDirectory, modelProfileDirectory,
                failOnWarnings, maxCognitiveDebt,
                maxCognitiveDebtIncrease, maxConceptRadiusIncrease, maxContextTokenIncrease,
                false, null, false, false, 0, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, List.of());
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
        this(repositoryRoot, outputDirectory, seedDirectory, modelProfileDirectory,
                failOnWarnings, maxCognitiveDebt,
                maxCognitiveDebtIncrease, maxConceptRadiusIncrease, maxContextTokenIncrease,
                empiricalBenchmarkEnabled, empiricalBenchmarkFixtureFile,
                false, false, 0, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, List.of());
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
            Path empiricalBenchmarkFixtureFile,
            boolean requireCapabilityEvidence,
            boolean requireClaimVerification,
            int minContextPackCount,
            int maxContextPackTokens) {
        this(repositoryRoot, outputDirectory, seedDirectory, modelProfileDirectory,
                failOnWarnings, maxCognitiveDebt,
                maxCognitiveDebtIncrease, maxConceptRadiusIncrease, maxContextTokenIncrease,
                empiricalBenchmarkEnabled, empiricalBenchmarkFixtureFile,
                requireCapabilityEvidence, requireClaimVerification, minContextPackCount, maxContextPackTokens,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, List.of());
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
        maxAverageMethodCognitiveComplexity = normalizeThreshold(maxAverageMethodCognitiveComplexity);
        maxAverageMethodCyclomaticComplexity = normalizeThreshold(maxAverageMethodCyclomaticComplexity);
        if (minContextPackCount < 0) minContextPackCount = 0;
        if (maxContextPackTokens < 0) maxContextPackTokens = Integer.MAX_VALUE;
        maxMethodCognitiveComplexity = normalizeIntThreshold(maxMethodCognitiveComplexity);
        maxMethodCyclomaticComplexity = normalizeIntThreshold(maxMethodCyclomaticComplexity);
        maxMethodsAboveCognitiveThreshold = normalizeIntThreshold(maxMethodsAboveCognitiveThreshold);
        maxMethodsAboveCyclomaticThreshold = normalizeIntThreshold(maxMethodsAboveCyclomaticThreshold);
        classpathEntries = List.copyOf(classpathEntries == null ? List.of() : classpathEntries);
    }

    public ExtractionOptions withClasspathEntries(List<Path> newClasspathEntries) {
        return new ExtractionOptions(
                repositoryRoot, outputDirectory, seedDirectory, modelProfileDirectory,
                failOnWarnings, maxCognitiveDebt, maxCognitiveDebtIncrease,
                maxConceptRadiusIncrease, maxContextTokenIncrease,
                empiricalBenchmarkEnabled, empiricalBenchmarkFixtureFile,
                requireCapabilityEvidence, requireClaimVerification,
                minContextPackCount, maxContextPackTokens,
                maxMethodCognitiveComplexity, maxMethodCyclomaticComplexity,
                maxAverageMethodCognitiveComplexity, maxAverageMethodCyclomaticComplexity,
                maxMethodsAboveCognitiveThreshold, maxMethodsAboveCyclomaticThreshold,
                newClasspathEntries);
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

    private static int normalizeIntThreshold(int value) {
        return value < 0 ? Integer.MAX_VALUE : value;
    }
}
