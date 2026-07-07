package org.aiknowledge.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.aiknowledge.core.linker.ClaimVerifier;
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
    private final ClaimVerifier claimVerifier;
    private final SeedContextGenerator seedContextGenerator;
    private final CodeComplexityAnalyzer codeComplexityAnalyzer;

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
                new ClaimVerifier(),
                new SeedContextGenerator(),
                new CodeComplexityAnalyzer());
    }

    private KnowledgeExtractionPipeline(
            RepositoryFileInventoryScanner inventoryScanner,
            BuildModuleScanner moduleScanner,
            MarkdownDocumentScanner markdownScanner,
            RepositoryEvidenceScanner evidenceScanner,
            JavaKnowledgeProvider javaKnowledgeProvider,
            CapabilityLinker capabilityLinker,
            ClaimVerifier claimVerifier,
            SeedContextGenerator seedContextGenerator,
            CodeComplexityAnalyzer codeComplexityAnalyzer) {
        this.inventoryScanner = inventoryScanner;
        this.moduleScanner = moduleScanner;
        this.markdownScanner = markdownScanner;
        this.evidenceScanner = evidenceScanner;
        this.javaKnowledgeProvider = Objects.requireNonNull(javaKnowledgeProvider, "javaKnowledgeProvider");
        this.capabilityLinker = capabilityLinker;
        this.claimVerifier = claimVerifier;
        this.seedContextGenerator = seedContextGenerator;
        this.codeComplexityAnalyzer = codeComplexityAnalyzer;
    }

    private static JavaKnowledgeProvider loadJavaKnowledgeProvider() {
        String configuredProvider = System.getProperty("aiknowledge.javaProvider", "basic").trim();
        String jdtMode = System.getProperty("aiknowledge.jdt.mode", "ast").trim();
        if (configuredProvider.isBlank() || "basic".equalsIgnoreCase(configuredProvider)) return new BasicJavaKnowledgeProvider();
        if (("jdt".equalsIgnoreCase(configuredProvider) && "search".equalsIgnoreCase(jdtMode))
                || "jdt-search".equalsIgnoreCase(configuredProvider)
                || "jdtsearch".equalsIgnoreCase(configuredProvider)) {
            return new org.aiknowledge.core.javajdt.JdtSearchJavaKnowledgeProvider();
        }
        List<ServiceLoader.Provider<JavaKnowledgeProvider>> providers = ServiceLoader.load(JavaKnowledgeProvider.class).stream()
                .sorted(Comparator.comparing(provider -> provider.type().getName()))
                .toList();
        for (ServiceLoader.Provider<JavaKnowledgeProvider> provider : providers) {
            Class<? extends JavaKnowledgeProvider> type = provider.type();
            if (matchesProvider(configuredProvider, type.getName()) || matchesProvider(configuredProvider, type.getSimpleName())) return provider.get();
        }
        return new BasicJavaKnowledgeProvider();
    }

    private static boolean matchesProvider(String configuredProvider, String candidate) {
        String normalizedConfigured = configuredProvider.toLowerCase();
        String normalizedCandidate = candidate.toLowerCase();
        String jdtMode = System.getProperty("aiknowledge.jdt.mode", "ast").trim().toLowerCase();
        if (normalizedConfigured.equals(normalizedCandidate)) return true;
        if (normalizedConfigured.equals("basic")) return normalizedCandidate.contains("basicjavaknowledgeprovider");
        if (normalizedConfigured.equals("jdt-search") || normalizedConfigured.equals("jdtsearch")) return normalizedCandidate.contains("jdtsearchjavaknowledgeprovider");
        if (normalizedConfigured.equals("jdt")) {
            if ("search".equals(jdtMode)) return normalizedCandidate.contains("jdtsearchjavaknowledgeprovider");
            return normalizedCandidate.contains("jdtjavaknowledgeprovider") && !normalizedCandidate.contains("jdtsearchjavaknowledgeprovider");
        }
        return false;
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
        List<Path> sourceRoots = sourceRoots(root, snapshot.modules);
        List<Path> testSourceRoots = testSourceRoots(root, snapshot.modules);
        for (JavaSourceUnit source : javaSources) {
            JavaKnowledgeRequest request = new JavaKnowledgeRequest(
                    root,
                    source.file(),
                    source.path(),
                    snapshot.modules,
                    sourceRoots,
                    testSourceRoots,
                    buildMetadata,
                    options.classpathEntries(),
                    Map.of(
                            "javaProvider", System.getProperty("aiknowledge.javaProvider", "basic"),
                            "jdtMode", System.getProperty("aiknowledge.jdt.mode", "ast")));
            JavaKnowledgeResult result = javaKnowledgeProvider.extract(request);
            result = codeComplexityAnalyzer.enrich(request, result);
            snapshot.classes.addAll(enrichFacts(result.classFacts(), result));
            snapshot.tests.addAll(enrichFacts(result.testFacts(), result));
        }
        BuildMetadata.enrichModules(root, snapshot);
        seedContextGenerator.generate(options, snapshot);
        capabilityLinker.link(snapshot);
        claimVerifier.verify(snapshot);
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

    private static List<Map<String, Object>> enrichFacts(List<?> facts, JavaKnowledgeResult result) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Object factObject : facts) {
            if (!(factObject instanceof Map<?, ?> sourceFact)) continue;
            Map<String, Object> fact = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : sourceFact.entrySet()) {
                if (entry.getKey() == null) continue;
                fact.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            if (!result.typeFacts().isEmpty()) fact.put("typeFacts", result.typeFacts());
            if (!result.methodFacts().isEmpty()) fact.put("methodFacts", result.methodFacts());
            if (!result.fieldFacts().isEmpty()) fact.put("fieldFacts", result.fieldFacts());
            if (!result.packageFacts().isEmpty()) fact.put("packageFacts", result.packageFacts());
            if (!result.referenceFacts().isEmpty()) fact.put("referenceFacts", result.referenceFacts());
            if (!result.relationFacts().isEmpty()) fact.put("relationFacts", result.relationFacts());
            if (!result.warnings().isEmpty()) fact.put("warnings", result.warnings());
            enriched.add(fact);
        }
        return enriched;
    }

    private record JavaSourceUnit(Path file, String path) {}
}
