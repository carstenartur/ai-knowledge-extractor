package org.aiknowledge.maven;

import org.apache.maven.plugin.MojoExecutionException;

public final class CheckGoal extends AbstractAiKnowledgeMojo {
    @Override
    public void execute() throws MojoExecutionException {
        try (ScopedSystemProperties ignored = configureSystemProperties()) {
            runner().check(options());
        } catch (Exception ex) {
            throw new MojoExecutionException("Quality gate failed", ex);
        }
    }
}
