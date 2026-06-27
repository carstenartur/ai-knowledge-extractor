package io.github.carstenartur.aiknowledge.maven;

import io.github.carstenartur.aiknowledge.core.ExtractionOptions;
import java.io.File;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;

abstract class AbstractAiKnowledgeMojo extends AbstractMojo {
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

    protected final ExtractionOptions options() {
        return new ExtractionOptions(
                basedir.toPath(),
                outputDirectory.toPath(),
                seedDirectory.toPath(),
                modelProfileDirectory.toPath(),
                failOnWarnings,
                maxCognitiveDebt);
    }
}
