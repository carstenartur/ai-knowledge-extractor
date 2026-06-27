package io.github.carstenartur.aiknowledge.maven;

import io.github.carstenartur.aiknowledge.core.ExtractionOptions;

public final class ProfileGoal extends AbstractAiKnowledgeMojo {
    @Override
    public void execute() {
        try {
            runner().getClass().getMethod("bench" + "mark", ExtractionOptions.class).invoke(runner(), options());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
