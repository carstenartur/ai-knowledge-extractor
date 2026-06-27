package org.aiknowledge.maven;

import org.apache.maven.plugin.MojoExecutionException;

public final class BenchmarkGoal extends AbstractAiKnowledgeMojo {
    @Override
    public void execute() throws MojoExecutionException {
        try {
            runner().benchmark(options());
        } catch (Exception ex) {
            throw new MojoExecutionException("Benchmark failed", ex);
        }
    }
}
