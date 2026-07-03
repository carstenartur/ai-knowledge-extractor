package org.aiknowledge.maven;

import org.apache.maven.plugin.MojoExecutionException;

public final class GenerateGoal extends AbstractAiKnowledgeMojo {
    @Override
    public void execute() throws MojoExecutionException {
        try (ScopedSystemProperties ignored = configureSystemProperties()) {
            runner().generate(options());
        } catch (Exception ex) {
            throw new MojoExecutionException("Generation failed", ex);
        }
    }
}
