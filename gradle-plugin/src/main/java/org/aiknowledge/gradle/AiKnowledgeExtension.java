package org.aiknowledge.gradle;

import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

public abstract class AiKnowledgeExtension {
    private final DirectoryProperty outputDirectory;
    private final DirectoryProperty docsOutputDirectory;
    private final DirectoryProperty seedDirectory;
    private final DirectoryProperty modelProfileDirectory;
    private final Property<Boolean> failOnWarnings;
    private final Property<Double> maxCognitiveDebt;

    @Inject
    public AiKnowledgeExtension(Project project) {
        ObjectFactory objects = project.getObjects();
        this.outputDirectory = objects.directoryProperty().convention(project.getLayout().getBuildDirectory().dir("ai-knowledge"));
        this.docsOutputDirectory = objects.directoryProperty().convention(project.getLayout().getProjectDirectory().dir("docs/ai-knowledge"));
        this.seedDirectory = objects.directoryProperty().convention(project.getLayout().getProjectDirectory().dir("ai-knowledge"));
        this.modelProfileDirectory = objects.directoryProperty().convention(project.getLayout().getProjectDirectory().dir("ai-knowledge"));
        this.failOnWarnings = objects.property(Boolean.class).convention(false);
        this.maxCognitiveDebt = objects.property(Double.class).convention(100.0d);
    }

    public DirectoryProperty getOutputDirectory() { return outputDirectory; }
    public DirectoryProperty getDocsOutputDirectory() { return docsOutputDirectory; }
    public DirectoryProperty getSeedDirectory() { return seedDirectory; }
    public DirectoryProperty getModelProfileDirectory() { return modelProfileDirectory; }
    public Property<Boolean> getFailOnWarnings() { return failOnWarnings; }
    public Property<Double> getMaxCognitiveDebt() { return maxCognitiveDebt; }
}
