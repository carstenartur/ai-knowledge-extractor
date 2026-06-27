package org.aiknowledge.core;

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
        double maxContextTokenIncrease) {

    public ExtractionOptions(
            Path repositoryRoot,
            Path outputDirectory,
            Path seedDirectory,
            Path modelProfileDirectory,
            boolean failOnWarnings,
            double maxCognitiveDebt) {
        this(repositoryRoot, outputDirectory, seedDirectory, modelProfileDirectory, failOnWarnings, maxCognitiveDebt, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
    }

    public ExtractionOptions {
        repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot").toAbsolutePath().normalize();
        outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory").toAbsolutePath().normalize();
        seedDirectory = seedDirectory == null ? repositoryRoot.resolve("ai-knowledge") : seedDirectory.toAbsolutePath().normalize();
        modelProfileDirectory = modelProfileDirectory == null ? seedDirectory : modelProfileDirectory.toAbsolutePath().normalize();
        maxCognitiveDebtIncrease = normalizeThreshold(maxCognitiveDebtIncrease);
        maxConceptRadiusIncrease = normalizeThreshold(maxConceptRadiusIncrease);
        maxContextTokenIncrease = normalizeThreshold(maxContextTokenIncrease);
    }

    public static ExtractionOptions defaults(Path repositoryRoot, Path outputDirectory) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        return new ExtractionOptions(root, outputDirectory, root.resolve("ai-knowledge"), root.resolve("ai-knowledge"), false, 100.0d);
    }

    private static double normalizeThreshold(double value) {
        if (Double.isNaN(value) || value < 0.0d) return Double.MAX_VALUE;
        return value;
    }
}
