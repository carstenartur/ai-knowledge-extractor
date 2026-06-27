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

    protected final ExtractionOptions options() {
        return new ExtractionOptions(
                basedir.toPath(),
                outputDirectory.toPath(),
                seedDirectory.toPath(),
                modelProfileDirectory.toPath(),
                failOnWarnings,
                maxCognitiveDebt,
                maxCognitiveDebtIncrease,
                maxConceptRadiusIncrease,
                maxContextTokenIncrease);
    }

    protected final AiKnowledgeRunner runner() {
        return new AiKnowledgeRunner();
    }
}
