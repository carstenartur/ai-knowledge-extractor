package org.aiknowledge.maven;

import java.io.File;
import org.aiknowledge.core.AiKnowledgeRunner;
import org.aiknowledge.core.ExtractionOptions;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractAiKnowledgeMojo extends org.apache.maven.plugin.AbstractMojo {
    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    protected File basedir;
    @Parameter(defaultValue = "${project.build.directory}/ai-knowledge")
    protected File outputDirectory;
    @Parameter(defaultValue = "${project.basedir}/ai-knowledge")
    protected File seedDirectory;
    @Parameter(defaultValue = "${project.basedir}/ai-knowledge")
    protected File modelProfileDirectory;
    @Parameter(defaultValue = "false")
    protected boolean failOnWarnings;
    @Parameter(defaultValue = "100.0")
    protected double maxCognitiveDebt;
    @Parameter(defaultValue = "1.7976931348623157E308")
    protected double maxCognitiveDebtIncrease;
    @Parameter(defaultValue = "1.7976931348623157E308")
    protected double maxConceptRadiusIncrease;
    @Parameter(defaultValue = "1.7976931348623157E308")
    protected double maxContextTokenIncrease;
    @Parameter(defaultValue = "false")
    protected boolean empiricalBenchmarkEnabled;
    @Parameter(defaultValue = "${project.basedir}/ai-knowledge/benchmark-fixtures.yaml")
    protected File empiricalBenchmarkFixtureFile;

    protected final ExtractionOptions options() {
        return new ExtractionOptions(
                basedir.toPath(),
                outputDirectory.toPath(),
                seedDirectory.toPath(),
                modelProfileDirectory.toPath(),
                failOnWarnings,
                systemDouble("aiKnowledge.maxCognitiveDebt", maxCognitiveDebt),
                systemDouble("aiKnowledge.maxCognitiveDebtIncrease", maxCognitiveDebtIncrease),
                systemDouble("aiKnowledge.maxConceptRadiusIncrease", maxConceptRadiusIncrease),
                systemDouble("aiKnowledge.maxContextTokenIncrease", maxContextTokenIncrease),
                empiricalBenchmarkEnabled,
                empiricalBenchmarkFixtureFile.toPath());
    }

    protected final AiKnowledgeRunner runner() {
        return new AiKnowledgeRunner();
    }

    private double systemDouble(String key, double fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            getLog().warn("Ignoring invalid numeric value for -D" + key + ": " + value);
            return fallback;
        }
    }
}
