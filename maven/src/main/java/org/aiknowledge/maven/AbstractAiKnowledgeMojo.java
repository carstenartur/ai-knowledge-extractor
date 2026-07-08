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
    @Parameter(defaultValue = "2147483647")
    protected int maxMethodCognitiveComplexity;
    @Parameter(defaultValue = "2147483647")
    protected int maxMethodCyclomaticComplexity;
    @Parameter(defaultValue = "1.7976931348623157E308")
    protected double maxAverageMethodCognitiveComplexity;
    @Parameter(defaultValue = "1.7976931348623157E308")
    protected double maxAverageMethodCyclomaticComplexity;
    @Parameter(defaultValue = "2147483647")
    protected int maxMethodsAboveCognitiveThreshold;
    @Parameter(defaultValue = "2147483647")
    protected int maxMethodsAboveCyclomaticThreshold;
    @Parameter(defaultValue = "basic")
    protected String javaProvider;
    @Parameter(defaultValue = "ast")
    protected String jdtMode;
    @Parameter(defaultValue = "forked")
    protected String jdtSearchExecutionMode;
    @Parameter(defaultValue = "true")
    protected boolean jdtSearchFallbackToAst;
    @Parameter(defaultValue = "create")
    protected String jdtWorkspaceMode;
    @Parameter(defaultValue = "${project.build.directory}/ai-knowledge/jdt-workspace")
    protected File jdtWorkspaceDirectory;
    @Parameter(defaultValue = "false")
    protected boolean keepJdtWorkspace;
    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true)
    protected List<String> compileClasspathElements;

    protected final ExtractionOptions options() {
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
                maxContextPackTokens,
                systemInt("aiKnowledge.maxMethodCognitiveComplexity", maxMethodCognitiveComplexity),
                systemInt("aiKnowledge.maxMethodCyclomaticComplexity", maxMethodCyclomaticComplexity),
                systemDouble("aiKnowledge.maxAverageMethodCognitiveComplexity", maxAverageMethodCognitiveComplexity),
                systemDouble("aiKnowledge.maxAverageMethodCyclomaticComplexity", maxAverageMethodCyclomaticComplexity),
                systemInt("aiKnowledge.maxMethodsAboveCognitiveThreshold", maxMethodsAboveCognitiveThreshold),
                systemInt("aiKnowledge.maxMethodsAboveCyclomaticThreshold", maxMethodsAboveCyclomaticThreshold),
                List.of());
        List<Path> classpathPaths = resolveClasspath();
        return classpathPaths.isEmpty() ? base : base.withClasspathEntries(classpathPaths);
    }

    protected final AiKnowledgeRunner runner() {
        return new AiKnowledgeRunner();
    }

    protected final ScopedSystemProperties configureSystemProperties() {
        return new ScopedSystemProperties(javaProvider, jdtMode, jdtSearchExecutionMode, jdtSearchFallbackToAst, jdtWorkspaceMode, jdtWorkspaceDirectory, keepJdtWorkspace);
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

    private int systemInt(String key, int fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            getLog().warn("Ignoring invalid integer value for -D" + key + ": " + value);
            return fallback;
        }
    }

    protected static final class ScopedSystemProperties implements AutoCloseable {
        private final String previousJavaProvider = System.getProperty("aiknowledge.javaProvider");
        private final String previousJdtMode = System.getProperty("aiknowledge.jdt.mode");
        private final String previousJdtSearchExecutionMode = System.getProperty("aiknowledge.jdt.search.execution.mode");
        private final String previousJdtSearchFallbackToAst = System.getProperty("aiknowledge.jdt.search.fallback.to.ast");
        private final String previousJdtWorkspaceMode = System.getProperty("aiknowledge.jdt.workspace.mode");
        private final String previousJdtWorkspaceDirectory = System.getProperty("aiknowledge.jdt.workspace.directory");
        private final String previousKeepJdtWorkspace = System.getProperty("aiknowledge.jdt.workspace.keep");

        private ScopedSystemProperties(
                String javaProvider,
                String jdtMode,
                String jdtSearchExecutionMode,
                boolean jdtSearchFallbackToAst,
                String jdtWorkspaceMode,
                File jdtWorkspaceDirectory,
                boolean keepJdtWorkspace) {
            if (javaProvider != null && !javaProvider.isBlank()) System.setProperty("aiknowledge.javaProvider", javaProvider);
            if (jdtMode != null && !jdtMode.isBlank()) System.setProperty("aiknowledge.jdt.mode", jdtMode);
            if (jdtSearchExecutionMode != null && !jdtSearchExecutionMode.isBlank()) {
                System.setProperty("aiknowledge.jdt.search.execution.mode", jdtSearchExecutionMode);
            }
            System.setProperty("aiknowledge.jdt.search.fallback.to.ast", String.valueOf(jdtSearchFallbackToAst));
            if (jdtWorkspaceMode != null && !jdtWorkspaceMode.isBlank()) System.setProperty("aiknowledge.jdt.workspace.mode", jdtWorkspaceMode);
            if (jdtWorkspaceDirectory != null) System.setProperty("aiknowledge.jdt.workspace.directory", jdtWorkspaceDirectory.getAbsolutePath());
            System.setProperty("aiknowledge.jdt.workspace.keep", String.valueOf(keepJdtWorkspace));
        }

        @Override
        public void close() {
            restore("aiknowledge.javaProvider", previousJavaProvider);
            restore("aiknowledge.jdt.mode", previousJdtMode);
            restore("aiknowledge.jdt.search.execution.mode", previousJdtSearchExecutionMode);
            restore("aiknowledge.jdt.search.fallback.to.ast", previousJdtSearchFallbackToAst);
            restore("aiknowledge.jdt.workspace.mode", previousJdtWorkspaceMode);
            restore("aiknowledge.jdt.workspace.directory", previousJdtWorkspaceDirectory);
            restore("aiknowledge.jdt.workspace.keep", previousKeepJdtWorkspace);
        }

        private static void restore(String key, String previousValue) {
            if (previousValue == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previousValue);
            }
        }
    }
}
