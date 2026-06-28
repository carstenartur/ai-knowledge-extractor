package org.aiknowledge.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import org.aiknowledge.core.context.SeedContextGenerator;
import org.aiknowledge.core.javabasic.BasicJavaKnowledgeProvider;
import org.aiknowledge.core.javaspi.JavaKnowledgeRequest;
import org.aiknowledge.core.javaspi.JavaKnowledgeProvider;
import org.aiknowledge.core.javaspi.JavaKnowledgeResult;
import org.aiknowledge.core.linker.CapabilityLinker;
import org.aiknowledge.core.model.RepositoryFacts;
import org.aiknowledge.core.repositoryscan.BuildModuleScanner;
import org.aiknowledge.core.repositoryscan.MarkdownDocumentScanner;
import org.aiknowledge.core.repositoryscan.RepositoryEvidenceScanner;
import org.aiknowledge.core.repositoryscan.RepositoryFileInventoryScanner;

final class KnowledgeExtractionPipeline {
    private final RepositoryFileInventoryScanner inventoryScanner;
    private final BuildModuleScanner moduleScanner;
    private final MarkdownDocumentScanner markdownScanner;
    private final RepositoryEvidenceScanner evidenceScanner;
    private final JavaKnowledgeProvider javaKnowledgeProvider;
    private final CapabilityLinker capabilityLinker;
    private final SeedContextGenerator seedContextGenerator;

    KnowledgeExtractionPipeline() {
        this(loadJavaKnowledgeProvider());
    }

    KnowledgeExtractionPipeline(JavaKnowledgeProvider javaKnowledgeProvider) {
        this(
                new RepositoryFileInventoryScanner(),
                new BuildModuleScanner(),
                new MarkdownDocumentScanner(),
                new RepositoryEvidenceScanner(),
                javaKnowledgeProvider,
                new CapabilityLinker(),
                new SeedContextGenerator());
    }

    private KnowledgeExtractionPipeline(
            RepositoryFileInventoryScanner inventoryScanner,
            BuildModuleScanner moduleScanner,
            MarkdownDocumentScanner markdownScanner,
            RepositoryEvidenceScanner evidenceScanner,
            JavaKnowledgeProvider javaKnowledgeProvider,
            CapabilityLinker capabilityLinker,
            SeedContextGenerator seedContextGenerator) {
        this.inventoryScanner = inventoryScanner;
        this.moduleScanner = moduleScanner;
        this.markdownScanner = markdownScanner;
        this.evidenceScanner = evidenceScanner;
        this.javaKnowledgeProvider = Objects.requireNonNull(javaKnowledgeProvider, "javaKnowledgeProvider");
        this.capabilityLinker = capabilityLinker;
        this.seedContextGenerator = seedContextGenerator;
    }

    private static JavaKnowledgeProvider loadJavaKnowledgeProvider() {
        return ServiceLoader.load(JavaKnowledgeProvider.class).findFirst().orElseGet(BasicJavaKnowledgeProvider::new);
    }

    RepositorySnapshot extract(ExtractionOptions options) throws IOException {
        Path root = options.repositoryRoot();
        RepositorySnapshot snapshot = new RepositorySnapshot();
        List<JavaSourceUnit> javaSources = new ArrayList<>();
        List<Path> files = inventoryScanner.scan(root);
        for (Path file : files) {
            String path = inventoryScanner.rel(root, file);
            moduleScanner.extract(root, file, path, snapshot);
            markdownScanner.extract(file, path, snapshot);
            evidenceScanner.extract(root, file, path, snapshot);
            if (path.endsWith(".java")) javaSources.add(new JavaSourceUnit(file, path));
        }
        Map buildMetadata = Map.of("modules", List.copyOf(snapshot.modules));
        for (JavaSourceUnit source : javaSources) {
            JavaKnowledgeResult result = javaKnowledgeProvider.extract(new JavaKnowledgeRequest(
                    root,
                    source.file(),
                    source.path(),
                    snapshot.modules,
                    sourceRoots(root, snapshot.modules),
                    testSourceRoots(root, snapshot.modules),
                    buildMetadata,
                    List.of(),
                    Map.of()));
            snapshot.classes.addAll(result.classFacts());
            snapshot.tests.addAll(result.testFacts());
        }
        BuildMetadata.enrichModules(root, snapshot);
        capabilityLinker.link(snapshot);
        seedContextGenerator.generate(options, snapshot);
        RepositoryFacts.populateIndex(root, snapshot);
        return snapshot;
    }

    private static List<Path> sourceRoots(Path root, List modules) {
        return roots(root, modules, "main/java");
    }

    private static List<Path> testSourceRoots(Path root, List modules) {
        return roots(root, modules, "test/java");
    }

    private static List<Path> roots(Path root, List modules, String sourceSet) {
        List<Path> roots = new ArrayList<>();
        for (Object object : modules) {
            Map module = (Map) object;
            String modulePath = String.valueOf(module.get("path"));
            Path moduleRoot = modulePath.isBlank() ? root : root.resolve(modulePath);
            Path candidate = moduleRoot.resolve("src").resolve(sourceSet).normalize();
            if (candidate.toFile().isDirectory()) roots.add(candidate);
        }
        if (roots.isEmpty()) {
            Path fallback = root.resolve("src").resolve(sourceSet).normalize();
            if (fallback.toFile().isDirectory()) roots.add(fallback);
        }
        return roots;
    }

    private record JavaSourceUnit(Path file, String path) {
    }
}
