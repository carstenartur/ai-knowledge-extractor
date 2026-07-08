package org.aiknowledge.gradle;

import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

public abstract class AiKnowledgeExtension {
    private final DirectoryProperty outputDirectory;
    private final DirectoryProperty docsOutputDirectory;
    private final DirectoryProperty seedDirectory;
    private final DirectoryProperty modelProfileDirectory;
    private final Property<Boolean> failOnWarnings;
    private final Property<Double> maxCognitiveDebt;
    private final Property<Double> maxCognitiveDebtIncrease;
    private final Property<Double> maxConceptRadiusIncrease;
    private final Property<Double> maxContextTokenIncrease;
    private final Property<Boolean> empiricalBenchmarkEnabled;
    private final RegularFileProperty empiricalBenchmarkFixtureFile;
    private final Property<Boolean> requireCapabilityEvidence;
    private final Property<Boolean> requireClaimVerification;
    private final Property<Integer> minContextPackCount;
    private final Property<Integer> maxContextPackTokens;
    private final Property<Integer> maxMethodCognitiveComplexity;
    private final Property<Integer> maxMethodCyclomaticComplexity;
    private final Property<Double> maxAverageMethodCognitiveComplexity;
    private final Property<Double> maxAverageMethodCyclomaticComplexity;
    private final Property<Integer> maxMethodsAboveCognitiveThreshold;
    private final Property<Integer> maxMethodsAboveCyclomaticThreshold;
    private final Property<String> javaProvider;
    private final Property<String> jdtMode;
    private final Property<String> jdtWorkspaceMode;
    private final Property<String> jdtSearchExecutionMode;
    private final Property<Boolean> jdtSearchFallbackToAst;
    private final Property<String> jdtWorkspaceDirectory;
    private final Property<Boolean> keepJdtWorkspace;

    @Inject
    public AiKnowledgeExtension(Project project) {
        ObjectFactory objects = project.getObjects();
        this.outputDirectory = objects.directoryProperty().convention(project.getLayout().getBuildDirectory().dir("ai-knowledge"));
        this.docsOutputDirectory = objects.directoryProperty().convention(project.getLayout().getProjectDirectory().dir("docs/ai-knowledge"));
        this.seedDirectory = objects.directoryProperty().convention(project.getLayout().getProjectDirectory().dir("ai-knowledge"));
        this.modelProfileDirectory = objects.directoryProperty().convention(project.getLayout().getProjectDirectory().dir("ai-knowledge"));
        this.failOnWarnings = objects.property(Boolean.class).convention(false);
        this.maxCognitiveDebt = objects.property(Double.class).convention(100.0d);
        this.maxCognitiveDebtIncrease = objects.property(Double.class).convention(Double.MAX_VALUE);
        this.maxConceptRadiusIncrease = objects.property(Double.class).convention(Double.MAX_VALUE);
        this.maxContextTokenIncrease = objects.property(Double.class).convention(Double.MAX_VALUE);
        this.empiricalBenchmarkEnabled = objects.property(Boolean.class).convention(false);
        this.empiricalBenchmarkFixtureFile = objects.fileProperty().convention(project.getLayout().getProjectDirectory().file("ai-knowledge/benchmark-fixtures.yaml"));
        this.requireCapabilityEvidence = objects.property(Boolean.class).convention(false);
        this.requireClaimVerification = objects.property(Boolean.class).convention(false);
        this.minContextPackCount = objects.property(Integer.class).convention(0);
        this.maxContextPackTokens = objects.property(Integer.class).convention(Integer.MAX_VALUE);
        this.maxMethodCognitiveComplexity = objects.property(Integer.class).convention(Integer.MAX_VALUE);
        this.maxMethodCyclomaticComplexity = objects.property(Integer.class).convention(Integer.MAX_VALUE);
        this.maxAverageMethodCognitiveComplexity = objects.property(Double.class).convention(Double.MAX_VALUE);
        this.maxAverageMethodCyclomaticComplexity = objects.property(Double.class).convention(Double.MAX_VALUE);
        this.maxMethodsAboveCognitiveThreshold = objects.property(Integer.class).convention(Integer.MAX_VALUE);
        this.maxMethodsAboveCyclomaticThreshold = objects.property(Integer.class).convention(Integer.MAX_VALUE);
        this.javaProvider = objects.property(String.class).convention(System.getProperty("aiknowledge.javaProvider", "basic"));
        this.jdtMode = objects.property(String.class).convention(System.getProperty("aiknowledge.jdt.mode", "ast"));
        this.jdtWorkspaceMode = objects.property(String.class).convention(System.getProperty("aiknowledge.jdt.workspace.mode", "create"));
        this.jdtSearchExecutionMode = objects.property(String.class).convention(System.getProperty("aiknowledge.jdt.search.execution.mode", "forked"));
        this.jdtSearchFallbackToAst = objects.property(Boolean.class).convention(Boolean.parseBoolean(System.getProperty("aiknowledge.jdt.search.fallback.to.ast", "true")));
        this.jdtWorkspaceDirectory = objects.property(String.class).convention(System.getProperty("aiknowledge.jdt.workspace.directory", ""));
        this.keepJdtWorkspace = objects.property(Boolean.class).convention(Boolean.parseBoolean(System.getProperty("aiknowledge.jdt.workspace.keep", "false")));
    }

    public DirectoryProperty getOutputDirectory() { return outputDirectory; }
    public DirectoryProperty getDocsOutputDirectory() { return docsOutputDirectory; }
    public DirectoryProperty getSeedDirectory() { return seedDirectory; }
    public DirectoryProperty getModelProfileDirectory() { return modelProfileDirectory; }
    public Property<Boolean> getFailOnWarnings() { return failOnWarnings; }
    public Property<Double> getMaxCognitiveDebt() { return maxCognitiveDebt; }
    public Property<Double> getMaxCognitiveDebtIncrease() { return maxCognitiveDebtIncrease; }
    public Property<Double> getMaxConceptRadiusIncrease() { return maxConceptRadiusIncrease; }
    public Property<Double> getMaxContextTokenIncrease() { return maxContextTokenIncrease; }
    public Property<Boolean> getEmpiricalBenchmarkEnabled() { return empiricalBenchmarkEnabled; }
    public RegularFileProperty getEmpiricalBenchmarkFixtureFile() { return empiricalBenchmarkFixtureFile; }
    public Property<Boolean> getRequireCapabilityEvidence() { return requireCapabilityEvidence; }
    public Property<Boolean> getRequireClaimVerification() { return requireClaimVerification; }
    public Property<Integer> getMinContextPackCount() { return minContextPackCount; }
    public Property<Integer> getMaxContextPackTokens() { return maxContextPackTokens; }
    public Property<Integer> getMaxMethodCognitiveComplexity() { return maxMethodCognitiveComplexity; }
    public Property<Integer> getMaxMethodCyclomaticComplexity() { return maxMethodCyclomaticComplexity; }
    public Property<Double> getMaxAverageMethodCognitiveComplexity() { return maxAverageMethodCognitiveComplexity; }
    public Property<Double> getMaxAverageMethodCyclomaticComplexity() { return maxAverageMethodCyclomaticComplexity; }
    public Property<Integer> getMaxMethodsAboveCognitiveThreshold() { return maxMethodsAboveCognitiveThreshold; }
    public Property<Integer> getMaxMethodsAboveCyclomaticThreshold() { return maxMethodsAboveCyclomaticThreshold; }
    public Property<String> getJavaProvider() { return javaProvider; }
    public Property<String> getJdtMode() { return jdtMode; }
    public Property<String> getJdtWorkspaceMode() { return jdtWorkspaceMode; }
    public Property<String> getJdtSearchExecutionMode() { return jdtSearchExecutionMode; }
    public Property<Boolean> getJdtSearchFallbackToAst() { return jdtSearchFallbackToAst; }
    public Property<String> getJdtWorkspaceDirectory() { return jdtWorkspaceDirectory; }
    public Property<Boolean> getKeepJdtWorkspace() { return keepJdtWorkspace; }
}
