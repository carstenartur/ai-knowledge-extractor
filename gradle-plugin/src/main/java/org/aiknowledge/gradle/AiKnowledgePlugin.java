package org.aiknowledge.gradle;

import java.nio.file.Files;
import java.nio.file.Path;
import org.aiknowledge.core.AiKnowledgeRunner;
import org.aiknowledge.core.ExtractionOptions;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public final class AiKnowledgePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        AiKnowledgeExtension extension = project.getExtensions().create("aiKnowledge", AiKnowledgeExtension.class, project);

        project.getTasks().register("generateAiKnowledgeIndex", task -> {
            task.setGroup("documentation");
            task.setDescription("Generates deterministic repository knowledge JSON files under build/ai-knowledge.");
            task.doLast(t -> run(project, extension, "generate"));
        });
        project.getTasks().register("analyzeAiComplexity", task -> {
            task.setGroup("verification");
            task.setDescription("Generates AI cognitive complexity JSON and HTML reports.");
            task.doLast(t -> run(project, extension, "analyze"));
        });
        project.getTasks().register("optimizeAiKnowledge", task -> {
            task.setGroup("verification");
            task.setDescription("Detects knowledge smells and writes ranked optimization suggestions.");
            task.doLast(t -> run(project, extension, "optimize"));
        });
        project.getTasks().register("benchmarkAiKnowledge", task -> {
            task.setGroup("verification");
            task.setDescription("Compares deterministic extraction profiles against model context budgets.");
            task.doLast(t -> run(project, extension, "benchmark"));
        });
        project.getTasks().register("checkAiKnowledgeIndex", task -> {
            task.setGroup("verification");
            task.setDescription("Runs the AI knowledge quality gate.");
            task.doLast(t -> run(project, extension, "check"));
        });
        project.getTasks().register("publishAiKnowledgeIndex", task -> {
            task.setGroup("documentation");
            task.setDescription("Copies generated AI knowledge artifacts to docs/ai-knowledge.");
            task.dependsOn("generateAiKnowledgeIndex");
            task.doLast(t -> copy(extension.getOutputDirectory().get().getAsFile().toPath(), extension.getDocsOutputDirectory().get().getAsFile().toPath()));
        });
    }

    private static void run(Project project, AiKnowledgeExtension extension, String mode) {
        try {
            AiKnowledgeRunner runner = new AiKnowledgeRunner();
            ExtractionOptions options = new ExtractionOptions(
                    project.getRootDir().toPath(),
                    extension.getOutputDirectory().get().getAsFile().toPath(),
                    extension.getSeedDirectory().get().getAsFile().toPath(),
                    extension.getModelProfileDirectory().get().getAsFile().toPath(),
                    extension.getFailOnWarnings().get(),
                    extension.getMaxCognitiveDebt().get(),
                    extension.getMaxCognitiveDebtIncrease().get(),
                    extension.getMaxConceptRadiusIncrease().get(),
                    extension.getMaxContextTokenIncrease().get(),
                    extension.getEmpiricalBenchmarkEnabled().get(),
                    extension.getEmpiricalBenchmarkFixtureFile().get().getAsFile().toPath(),
                    extension.getRequireCapabilityEvidence().get(),
                    extension.getRequireClaimVerification().get(),
                    extension.getMinContextPackCount().get(),
                    extension.getMaxContextPackTokens().get());
            switch (mode) {
                case "generate" -> runner.generate(options);
                case "analyze" -> runner.analyze(options);
                case "optimize" -> runner.optimize(options);
                case "benchmark" -> runner.benchmark(options);
                case "check" -> runner.check(options);
                default -> throw new IllegalArgumentException(mode);
            }
        } catch (Exception ex) {
            throw new RuntimeException("AI knowledge task failed", ex);
        }
    }

    private static void copy(Path source, Path target) {
        try {
            Files.createDirectories(target);
            try (var stream = Files.walk(source)) {
                for (Path path : stream.filter(Files::isRegularFile).toList()) {
                    Path dest = target.resolve(source.relativize(path).toString());
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("Could not publish AI knowledge index", ex);
        }
    }
}
