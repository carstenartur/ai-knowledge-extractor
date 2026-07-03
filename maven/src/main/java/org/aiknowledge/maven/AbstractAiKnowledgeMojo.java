package org.aiknowledge.maven;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    @Parameter(defaultValue = "false")
    protected boolean requireCapabilityEvidence;
    @Parameter(defaultValue = "false")
    protected boolean requireClaimVerification;
    @Parameter(defaultValue = "0")
    protected int minContextPackCount;
    @Parameter(defaultValue = "2147483647")
    protected int maxContextPackTokens;
    @Parameter(defaultValue = "basic")
    protected String javaProvider;
    @Parameter(defaultValue = "ast")
    protected String jdtMode;
    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true)
    protected List<String> compileClasspathElements;

    protected final ExtractionOptions options() {
        if (javaProvider != null && !javaProvider.isBlank()) System.setProperty("aiknowledge.javaProvider", javaProvider);
        if (jdtMode != null && !jdtMode.isBlank()) System.setProperty("aiknowledge.jdt.mode", jdtMode);

        ExtractionOptions base = new ExtractionOptions(
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
                empiricalBenchmarkFixtureFile != null ? empiricalBenchmarkFixtureFile.toPath() : null,
                requireCapabilityEvidence,
                requireClaimVerification,
                minContextPackCount,
                maxContextPackTokens);
        List<Path> classpathPaths = resolveClasspath();
        return classpathPaths.isEmpty() ? base : base.withClasspathEntries(classpathPaths);
    }

    protected final AiKnowledgeRunner runner() {
        return new AiKnowledgeRunner();
    }

    private List<Path> resolveClasspath() {
        if (compileClasspathElements == null) return List.of();
        List<Path> paths = new ArrayList<>();
        for (String element : compileClasspathElements) {
            if (element == null || element.isBlank()) continue;
            File file = new File(element);
            if (file.exists()) paths.add(file.toPath());
        }
        return List.copyOf(paths);
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
