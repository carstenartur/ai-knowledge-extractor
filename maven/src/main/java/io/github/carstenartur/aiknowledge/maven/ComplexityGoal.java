package io.github.carstenartur.aiknowledge.maven;

public final class ComplexityGoal extends AbstractAiKnowledgeMojo {
    @Override
    public void execute() {
        runGoal("analyze");
    }
}
