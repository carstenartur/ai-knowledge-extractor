package org.aiknowledge.gradle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.aiknowledge.core.AiKnowledgeRunner;
import org.aiknowledge.core.ExtractionOptions;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

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
        String previousJavaProvider = System.getProperty("aiknowledge.javaProvider");
        String previousJdtMode = System.getProperty("aiknowledge.jdt.mode");
        String previousJdtWorkspaceMode = System.getProperty("aiknowledge.jdt.workspace.mode");
        try {
            String javaProvider = extension.getJavaProvider().get();
            String jdtMode = extension.getJdtMode().get();
            String jdtWorkspaceMode = extension.getJdtWorkspaceMode().get();
            if (!javaProvider.isBlank()) System.setProperty("aiknowledge.javaProvider", javaProvider);
            if (!jdtMode.isBlank()) System.setProperty("aiknowledge.jdt.mode", jdtMode);
            if (!jdtWorkspaceMode.isBlank()) System.setProperty("aiknowledge.jdt.workspace.mode", jdtWorkspaceMode);

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
            List<Path> classpathEntries = resolveClasspath(project);
            if (!classpathEntries.isEmpty()) options = options.withClasspathEntries(classpathEntries);
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
        } finally {
            restoreProperty("aiknowledge.javaProvider", previousJavaProvider);
            restoreProperty("aiknowledge.jdt.mode", previousJdtMode);
            restoreProperty("aiknowledge.jdt.workspace.mode", previousJdtWorkspaceMode);
        }
    }

    private static List<Path> resolveClasspath(Project project) {
        List<Path> entries = new ArrayList<>();
        Configuration config = project.getConfigurations().findByName("compileClasspath");
        if (config == null || !config.isCanBeResolved()) return List.of();
        try {
            config.forEach(file -> {
                if (file.exists()) entries.add(file.toPath());
            });
        } catch (Exception e) {
            project.getLogger().debug("ai-knowledge: could not resolve compileClasspath; classpath will be empty", e);
        }
        return List.copyOf(entries);
    }

    private static void copy(Path source, Path target) {
        try {
            Files.createDirectories(target);
            try (var stream = Files.walk(source)) {
                for (Path path : stream.filter(Files::RegularFile).toList()) {
                    Path dest = target.resolve(source.relativize(path).toString());
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("Could not publish AI knowledge index", ex);
        }
    }

    private static void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }
}
