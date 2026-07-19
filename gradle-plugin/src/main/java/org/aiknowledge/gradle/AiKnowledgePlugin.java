package org.aiknowledge.gradle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.aiknowledge.core.AiKnowledgeArtifactVerifier;
import org.aiknowledge.core.AiKnowledgeRunner;
import org.aiknowledge.core.ExtractionOptions;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.TaskProvider;

public final class AiKnowledgePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        AiKnowledgeExtension extension = project.getExtensions().create(
            "aiKnowledge", AiKnowledgeExtension.class, project);

        TaskProvider<Task> generate = runnerTask(
            project,
            extension,
            "generateAiKnowledgeIndex",
            "documentation",
            "Generates deterministic repository knowledge JSON files under build/ai-knowledge.",
            "generate");
        TaskProvider<Task> analyze = runnerTask(
            project,
            extension,
            "analyzeAiComplexity",
            "verification",
            "Generates AI cognitive complexity JSON and HTML reports.",
            "analyze");
        TaskProvider<Task> optimize = runnerTask(
            project,
            extension,
            "optimizeAiKnowledge",
            "verification",
            "Detects knowledge smells and writes ranked optimization suggestions.",
            "optimize");
        TaskProvider<Task> benchmark = runnerTask(
            project,
            extension,
            "benchmarkAiKnowledge",
            "verification",
            "Compares deterministic extraction profiles against model context budgets.",
            "benchmark");
        TaskProvider<Task> check = runnerTask(
            project,
            extension,
            "checkAiKnowledgeIndex",
            "verification",
            "Runs the AI knowledge quality gate and verifies its emitted artifacts.",
            "check");

        analyze.configure(task -> task.mustRunAfter(generate));
        optimize.configure(task -> task.mustRunAfter(analyze));
        benchmark.configure(task -> task.mustRunAfter(optimize));
        check.configure(task -> task.mustRunAfter(benchmark));

        TaskProvider<Task> aiKnowledgeCheck = runnerTask(
            project,
            extension,
            "aiKnowledgeCheck",
            "verification",
            "Runs the complete verified AI knowledge lifecycle from one repository snapshot.",
            "verify");

        TaskProvider<Task> verifyArtifacts = project.getTasks().register(
            "verifyAiKnowledgeArtifacts",
            task -> {
                task.setGroup("verification");
                task.setDescription(
                    "Verifies an existing complete AI knowledge artifact set without regenerating it.");
                task.doLast(ignored -> requireValid(
                    new AiKnowledgeArtifactVerifier().verifyCompleteLifecycle(
                        extension.getOutputDirectory().get().getAsFile().toPath())));
            });
        verifyArtifacts.configure(task -> task.mustRunAfter(aiKnowledgeCheck));

        project.getTasks().register("publishAiKnowledgeIndex", task -> {
            task.setGroup("documentation");
            task.setDescription(
                "Copies generated AI knowledge artifacts to docs/ai-knowledge.");
            task.dependsOn(generate);
            task.doLast(ignored -> copy(
                extension.getOutputDirectory().get().getAsFile().toPath(),
                extension.getDocsOutputDirectory().get().getAsFile().toPath()));
        });
    }

    private static TaskProvider<Task> runnerTask(
        Project project,
        AiKnowledgeExtension extension,
        String name,
        String group,
        String description,
        String mode
    ) {
        return project.getTasks().register(name, task -> {
            task.setGroup(group);
            task.setDescription(description);
            task.doLast(ignored -> run(project, extension, mode));
        });
    }

    private static void run(
        Project project,
        AiKnowledgeExtension extension,
        String mode
    ) {
        String previousJavaProvider = System.getProperty("aiknowledge.javaProvider");
        String previousJdtMode = System.getProperty("aiknowledge.jdt.mode");
        String previousJdtWorkspaceMode = System.getProperty(
            "aiknowledge.jdt.workspace.mode");
        String previousJdtSearchExecutionMode = System.getProperty(
            "aiknowledge.jdt.search.execution.mode");
        String previousJdtSearchFallbackToAst = System.getProperty(
            "aiknowledge.jdt.search.fallback.to.ast");
        String previousJdtWorkspaceDirectory = System.getProperty(
            "aiknowledge.jdt.workspace.directory");
        String previousKeepJdtWorkspace = System.getProperty(
            "aiknowledge.jdt.workspace.keep");
        try {
            String javaProvider = extension.getJavaProvider().get();
            String jdtMode = extension.getJdtMode().get();
            String jdtWorkspaceMode = extension.getJdtWorkspaceMode().get();
            String jdtSearchExecutionMode = extension.getJdtSearchExecutionMode().get();
            boolean jdtSearchFallbackToAst =
                extension.getJdtSearchFallbackToAst().get();
            String jdtWorkspaceDirectory =
                extension.getJdtWorkspaceDirectory().get();
            boolean keepJdtWorkspace = extension.getKeepJdtWorkspace().get();
            if (!javaProvider.isBlank()) {
                System.setProperty("aiknowledge.javaProvider", javaProvider);
            }
            if (!jdtMode.isBlank()) {
                System.setProperty("aiknowledge.jdt.mode", jdtMode);
            }
            if (!jdtWorkspaceMode.isBlank()) {
                System.setProperty(
                    "aiknowledge.jdt.workspace.mode", jdtWorkspaceMode);
            }
            if (!jdtSearchExecutionMode.isBlank()) {
                System.setProperty(
                    "aiknowledge.jdt.search.execution.mode",
                    jdtSearchExecutionMode);
            }
            System.setProperty(
                "aiknowledge.jdt.search.fallback.to.ast",
                String.valueOf(jdtSearchFallbackToAst));
            if (!jdtWorkspaceDirectory.isBlank()) {
                System.setProperty(
                    "aiknowledge.jdt.workspace.directory",
                    jdtWorkspaceDirectory);
            }
            System.setProperty(
                "aiknowledge.jdt.workspace.keep",
                String.valueOf(keepJdtWorkspace));

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
                extension.getMaxContextPackTokens().get(),
                extension.getMaxMethodCognitiveComplexity().get(),
                extension.getMaxMethodCyclomaticComplexity().get(),
                extension.getMaxAverageMethodCognitiveComplexity().get(),
                extension.getMaxAverageMethodCyclomaticComplexity().get(),
                extension.getMaxMethodsAboveCognitiveThreshold().get(),
                extension.getMaxMethodsAboveCyclomaticThreshold().get(),
                List.of());
            List<Path> classpathEntries = resolveClasspath(project);
            if (!classpathEntries.isEmpty()) {
                options = options.withClasspathEntries(classpathEntries);
            }
            switch (mode) {
                case "generate" -> runner.generate(options);
                case "analyze" -> runner.analyze(options);
                case "optimize" -> runner.optimize(options);
                case "benchmark" -> runner.benchmark(options);
                case "check" -> runner.check(options);
                case "verify" -> runner.verify(options);
                default -> throw new IllegalArgumentException(mode);
            }
        } catch (Exception exception) {
            throw new GradleException("AI knowledge task failed", exception);
        } finally {
            restoreProperty("aiknowledge.javaProvider", previousJavaProvider);
            restoreProperty("aiknowledge.jdt.mode", previousJdtMode);
            restoreProperty(
                "aiknowledge.jdt.workspace.mode",
                previousJdtWorkspaceMode);
            restoreProperty(
                "aiknowledge.jdt.search.execution.mode",
                previousJdtSearchExecutionMode);
            restoreProperty(
                "aiknowledge.jdt.search.fallback.to.ast",
                previousJdtSearchFallbackToAst);
            restoreProperty(
                "aiknowledge.jdt.workspace.directory",
                previousJdtWorkspaceDirectory);
            restoreProperty(
                "aiknowledge.jdt.workspace.keep",
                previousKeepJdtWorkspace);
        }
    }

    private static void requireValid(
            AiKnowledgeArtifactVerifier.VerificationReport report) {
        if (!report.passed()) {
            throw new GradleException(
                "AI knowledge artifact verification failed: "
                    + String.join("; ", report.errors()));
        }
    }

    private static List<Path> resolveClasspath(Project project) {
        List<Path> entries = new ArrayList<>();
        Configuration config = project.getConfigurations().findByName(
            "compileClasspath");
        if (config == null || !config.isCanBeResolved()) {
            return List.of();
        }
        try {
            config.forEach(file -> {
                if (file.exists()) {
                    entries.add(file.toPath());
                }
            });
        } catch (Exception exception) {
            project.getLogger().debug(
                "ai-knowledge: could not resolve compileClasspath; classpath will be empty",
                exception);
        }
        return List.copyOf(entries);
    }

    private static void copy(Path source, Path target) {
        try {
            Files.createDirectories(target);
            try (var stream = Files.walk(source)) {
                for (Path path : stream.filter(Files::isRegularFile).toList()) {
                    Path destination = target.resolve(
                        source.relativize(path).toString());
                    Files.createDirectories(destination.getParent());
                    Files.copy(
                        path,
                        destination,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception exception) {
            throw new GradleException(
                "Could not publish AI knowledge index", exception);
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
