package org.aiknowledge.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.ServiceLoader;
import org.aiknowledge.core.context.SeedContextGenerator;
import org.aiknowledge.core.javabasic.BasicJavaKnowledgeProvider;
import org.aiknowledge.core.javaspi.JavaKnowledgeProvider;
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
        for (Path file : inventoryScanner.scan(root)) {
            String path = inventoryScanner.rel(root, file);
            moduleScanner.extract(root, file, path, snapshot);
            if (javaKnowledgeProvider.supports(path)) javaKnowledgeProvider.extract(root, file, path, snapshot);
            markdownScanner.extract(file, path, snapshot);
            evidenceScanner.extract(root, file, path, snapshot);
        }
        BuildMetadata.enrichModules(root, snapshot);
        capabilityLinker.link(snapshot);
        seedContextGenerator.generate(options, snapshot);
        RepositoryFacts.populateIndex(root, snapshot);
        return snapshot;
    }
}
