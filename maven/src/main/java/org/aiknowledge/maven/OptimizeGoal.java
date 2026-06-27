package org.aiknowledge.maven;

import org.apache.maven.plugin.MojoExecutionException;

public final class OptimizeGoal extends AbstractAiKnowledgeMojo {
    @Override
    public void execute() throws MojoExecutionException {
        try {
            runner().optimize(options());
        } catch (Exception ex) {
            throw new MojoExecutionException("Optimization failed", ex);
        }
    }
}
