package io.github.carstenartur.aiknowledge.maven;

import io.github.carstenartur.aiknowledge.core.AiKnowledgeRunner;

public final class GenerateGoal extends AbstractAiKnowledgeMojo {
    @Override
    public void execute() throws org.apache.maven.plugin.MojoExecutionException {
        try {
            new AiKnowledgeRunner().generate(options());
        } catch (Exception ex) {
            throw new org.apache.maven.plugin.MojoExecutionException("Generation failed", ex);
        }
    }
}
