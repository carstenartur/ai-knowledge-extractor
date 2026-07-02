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
}
