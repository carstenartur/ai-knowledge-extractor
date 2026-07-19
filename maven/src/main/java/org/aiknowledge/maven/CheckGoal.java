package org.aiknowledge.maven;

import org.apache.maven.plugin.MojoExecutionException;

public final class CheckGoal extends AbstractAiKnowledgeMojo {
    @Override
    public void execute() throws MojoExecutionException {
        try (ScopedSystemProperties ignored = configureSystemProperties()) {
            runner().verify(options());
        } catch (Exception exception) {
            throw new MojoExecutionException(
                "AI knowledge lifecycle verification failed", exception);
        }
    }
}
