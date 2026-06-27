package org.aiknowledge.maven;

import org.apache.maven.plugin.MojoExecutionException;

public final class AnalyzeGoal extends AbstractAiKnowledgeMojo {
    @Override
    public void execute() throws MojoExecutionException {
        try {
            runner().analyze(options());
        } catch (Exception ex) {
            throw new MojoExecutionException("Analysis failed", ex);
        }
    }
}
