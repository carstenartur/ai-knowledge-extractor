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
import java.util.Set;
import org.aiknowledge.core.context.SeedContextGenerator;
import org.aiknowledge.core.javabasic.BasicJavaKnowledgeProvider;
import org.aiknowledge.core.javahttp.JavaHttpBoundaryKnowledgeProvider;
import org.aiknowledge.core.javascript.JavaScriptTypeScriptKnowledgeProvider;
import org.aiknowledge.core.javaspi.JavaKnowledgeProvider;
import org.aiknowledge.core.javaspi.JavaKnowledgeRequest;
import org.aiknowledge.core.javaspi.JavaKnowledgeResult;
import org.aiknowledge.core.linker.CapabilityLinker;
import org.aiknowledge.core.linker.ClaimVerifier;
import org.aiknowledge.core.model.RepositoryFacts;
import org.aiknowledge.core.repositoryscan.BuildModuleScanner;
import org.aiknowledge.core.repositoryscan.MarkdownDocumentScanner;
import org.aiknowledge.core.repositoryscan.RepositoryEvidenceScanner;
import org.aiknowledge.core.repositoryscan.RepositoryFileInventoryScanner;
import org.aiknowledge.core.sourcespi.SourceAnalysisConfiguration;
import org.aiknowledge.core.sourcespi.SourceKnowledgeProvider;
import org.aiknowledge.core.sourcespi.SourceKnowledgeRequest;
import org.aiknowledge.core.sourcespi.SourceKnowledgeResult;

final class KnowledgeExtractionPipeline {
    private final RepositoryFileInventoryScanner inventoryScanner;
    private final BuildModuleScanner moduleScanner;
    private final MarkdownDocumentScanner markdownScanner;
    private final RepositoryEvidenceScanner evidenceScanner;
    private final JavaKnowledgeProvider javaKnowledgeProvider;
    private final List<SourceKnowledgeProvider> sourceKnowledgeProviders;
    private final SourceAnalysisConfiguration sourceAnalysisConfiguration = SourceAnalysisConfiguration.fromSystemProperties();
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
                loadSourceKnowledgeProviders(),
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
            List<SourceKnowledgeProvider> sourceKnowledgeProviders,
            CapabilityLinker capabilityLinker,
            ClaimVerifier claimVerifier,
            SeedContextGenerator seedContextGenerator,
            CodeComplexityAnalyzer codeComplexityAnalyzer) {
        this.inventoryScanner = inventoryScanner;
        this.moduleScanner = moduleScanner;
        this.markdownScanner = markdownScanner;
        this.evidenceScanner = evidenceScanner;
        this.javaKnowledgeProvider = Objects.requireNonNull(javaKnowledgeProvider, "javaKnowledgeProvider");
        this.sourceKnowledgeProviders = List.copyOf(sourceKnowledgeProviders);
        this.capabilityLinker = capabilityLinker;
        this.claimVerifier = claimVerifier;
        this.seedContextGenerator = seedContextGenerator;
        this.codeComplexityAnalyzer = codeComplexityAnalyzer;
    }

    private static JavaKnowledgeProvider loadJavaKnowledgeProvider() {
        String configuredProvider = System.getProperty("aiknowledge.javaProvider", "basic").trim();
        String jdtMode = System.getProperty("aiknowledge.jdt.mode", "ast").trim();
        if (configuredProvider.isBlank() || "basic".equalsIgnoreCase(configuredProvider)) {
            return new BasicJavaKnowledgeProvider();
        }
        if (("jdt".equalsIgnoreCase(configuredProvider) && "search".equalsIgnoreCase(jdtMode))
                || "jdt-search".equalsIgnoreCase(configuredProvider)
                || "jdtsearch".equalsIgnoreCase(configuredProvider)) {
            return new org.aiknowledge.core.javajdt.JdtSearchJavaKnowledgeProvider();
        }
        List<ServiceLoader.Provider<JavaKnowledgeProvider>> providers =
                ServiceLoader.load(JavaKnowledgeProvider.class).stream()
                        .sorted(Comparator.comparing(provider -> provider.type().getName()))
                        .toList();
        for (ServiceLoader.Provider<JavaKnowledgeProvider> provider : providers) {
            Class<? extends JavaKnowledgeProvider> type = provider.type();
            if (matchesProvider(configuredProvider, type.getName())
                    || matchesProvider(configuredProvider, type.getSimpleName())) {
                return provider.get();
            }
        }
        return new BasicJavaKnowledgeProvider();
    }

    private static List<SourceKnowledgeProvider> loadSourceKnowledgeProviders() {
        Map<String, SourceKnowledgeProvider> providers = new LinkedHashMap<>();
        for (SourceKnowledgeProvider provider : List.of(
                new JavaScriptTypeScriptKnowledgeProvider(),
                new JavaHttpBoundaryKnowledgeProvider())) {
            providers.put(provider.id(), provider);
        }
        for (SourceKnowledgeProvider provider : ServiceLoader.load(SourceKnowledgeProvider.class)) {
            providers.put(provider.id(), provider);
        }
        return providers.values().stream()
                .sorted(Comparator.comparingInt(SourceKnowledgeProvider::priority).reversed()
                        .thenComparing(SourceKnowledgeProvider::id))
                .toList();
    }

    private static boolean matchesProvider(String configuredProvider, String candidate) {
        String normalizedConfigured = configuredProvider.toLowerCase();
        String normalizedCandidate = candidate.toLowerCase();
        String jdtMode = System.getProperty("aiknowledge.jdt.mode", "ast").trim().toLowerCase();
        if (normalizedConfigured.equals(normalizedCandidate)) return true;
        if (normalizedConfigured.equals("basic")) {
            return normalizedCandidate.contains("basicjavaknowledgeprovider");
        }
        if (normalizedConfigured.equals("jdt-search") || normalizedConfigured.equals("jdtsearch")) {
            return normalizedCandidate.contains("jdtsearchjavaknowledgeprovider");
        }
        if (normalizedConfigured.equals("jdt")) {
            if ("search".equals(jdtMode)) {
                return normalizedCandidate.contains("jdtsearchjavaknowledgeprovider");
            }
            return normalizedCandidate.contains("jdtjavaknowledgeprovider")
                    && !normalizedCandidate.contains("jdtsearchjavaknowledgeprovider");
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

        recordJavaFacts(snapshot);
        for (Path file : files) {
            String path = inventoryScanner.rel(root, file);
            for (SourceKnowledgeProvider provider : sourceKnowledgeProviders) {
                if (!sourceAnalysisConfiguration.providerEnabled(provider.id())
                        || !provider.supports(path)
                        || !sourceAnalysisConfiguration.acceptsSource(file, path)) {
                    continue;
                }
                try {
                    SourceKnowledgeResult result = provider.extract(new SourceKnowledgeRequest(
                            root,
                            file,
                            path,
                            snapshot.modules,
                            buildMetadata,
                            Map.of()));
                    recordSourceFacts(snapshot, provider, path, result);
                } catch (Exception exception) {
                    handleSourceProviderFailure(snapshot, provider, path, exception);
                }
            }
        }

        BuildMetadata.enrichModules(root, snapshot);
        seedContextGenerator.generate(options, snapshot);
        capabilityLinker.link(snapshot);
        claimVerifier.verify(snapshot);
        snapshot.evidence.add(sourceAnalysisConfiguration.asEvidence());
        RepositoryFacts.populateIndex(root, snapshot);
        return snapshot;
    }

    private void handleSourceProviderFailure(
            RepositorySnapshot snapshot,
            SourceKnowledgeProvider provider,
            String sourcePath,
            Exception exception) throws IOException {
        if (sourceAnalysisConfiguration.errorPolicy() == SourceAnalysisConfiguration.ErrorPolicy.FAIL) {
            if (exception instanceof IOException ioException) throw ioException;
            throw new IOException("Source provider " + provider.id() + " failed for " + sourcePath, exception);
        }
        if (sourceAnalysisConfiguration.errorPolicy() == SourceAnalysisConfiguration.ErrorPolicy.WARN) {
            Map<String, Object> warning = new LinkedHashMap<>();
            warning.put("provider", provider.id());
            warning.put("sourceFile", sourcePath);
            warning.put("code", "source-provider-failure");
            warning.put("message", exception.getClass().getSimpleName() + ": "
                    + String.valueOf(exception.getMessage()));
            snapshot.warnings.add(warning);
        }
    }

    private static void recordJavaFacts(RepositorySnapshot snapshot) {
        Set<String> unitIds = new LinkedHashSet<>();
        Set<String> symbolIds = new LinkedHashSet<>();
        Set<String> relationIds = new LinkedHashSet<>();
        addJavaFacts(snapshot, snapshot.classes, false, unitIds, symbolIds, relationIds);
        addJavaFacts(snapshot, snapshot.tests, true, unitIds, symbolIds, relationIds);
    }

    private static void addJavaFacts(
            RepositorySnapshot snapshot,
            List facts,
            boolean test,
            Set<String> unitIds,
            Set<String> symbolIds,
            Set<String> relationIds) {
        for (Object value : facts) {
            if (!(value instanceof Map<?, ?> raw)) continue;
            Map<String, Object> fact = copy(raw);
            String name = text(fact.get(test ? "testClass" : "class"));
            if (name.isBlank()) name = text(fact.get("name"));
            String sourceFile = text(fact.get("sourceFile"));
            String id = "source:" + sourceFile + "#" + name;
            if (unitIds.add(id)) {
                Map<String, Object> unit = new LinkedHashMap<>();
                unit.put("id", id);
                unit.put("name", name);
                unit.put("class", name);
                unit.put("namespace", text(fact.get("package")));
                unit.put("package", text(fact.get("package")));
                unit.put("kind", test ? "test-type-unit" : "type-unit");
                unit.put("language", "java");
                unit.put("sourceFile", sourceFile);
                unit.put("module", text(fact.get("module")));
                unit.put("lineCount", fact.getOrDefault("lineCount", 0));
                unit.put("test", test);
                unit.put("provider", "configured-java-provider");
                unit.put("confidence", "syntactic");
                snapshot.sourceUnits.add(unit);
            }
            appendNestedSymbols(snapshot, fact.get("typeFacts"), "type", sourceFile, symbolIds);
            appendNestedSymbols(snapshot, fact.get("methodFacts"), "callable", sourceFile, symbolIds);
            appendNestedSymbols(snapshot, fact.get("fieldFacts"), "field", sourceFile, symbolIds);
            appendNestedRelations(snapshot, fact.get("relationFacts"), sourceFile, relationIds);
            if (fact.get("warnings") instanceof List<?> warnings) {
                for (Object warning : warnings) {
                    Map<String, Object> warningFact = new LinkedHashMap<>();
                    warningFact.put("provider", "configured-java-provider");
                    warningFact.put("sourceFile", sourceFile);
                    if (warning instanceof Map<?, ?> warningMap) warningFact.putAll(copy(warningMap));
                    else warningFact.put("message", text(warning));
                    snapshot.warnings.add(warningFact);
                }
            }
        }
    }

    private static void appendNestedSymbols(
            RepositorySnapshot snapshot,
            Object nested,
            String defaultKind,
            String sourceFile,
            Set<String> seen) {
        if (!(nested instanceof List<?> list)) return;
        for (Object value : list) {
            if (!(value instanceof Map<?, ?> raw)) continue;
            Map<String, Object> symbol = copy(raw);
            symbol.putIfAbsent("kind", defaultKind);
            symbol.putIfAbsent("language", "java");
            symbol.putIfAbsent("sourceFile", sourceFile);
            symbol.putIfAbsent("provider", "configured-java-provider");
            String id = symbolId(symbol, defaultKind);
            symbol.putIfAbsent("id", id);
            if (seen.add(id)) snapshot.symbols.add(symbol);
        }
    }

    private static String symbolId(Map<String, Object> symbol, String kind) {
        String explicit = text(symbol.get("id"));
        if (!explicit.isBlank()) return explicit;
        if ("callable".equals(kind)) {
            return text(symbol.get("type")) + "#" + text(symbol.get("signature"));
        }
        if ("field".equals(kind)) {
            return text(symbol.get("declaringType")) + "." + text(symbol.get("name"));
        }
        String name = text(symbol.get("name"));
        return name.isBlank() ? symbol.toString() : name;
    }

    private static void appendNestedRelations(
            RepositorySnapshot snapshot,
            Object nested,
            String sourceFile,
            Set<String> seen) {
        if (!(nested instanceof List<?> list)) return;
        for (Object value : list) {
            if (!(value instanceof Map<?, ?> raw)) continue;
            Map<String, Object> relation = copy(raw);
            relation.putIfAbsent("language", "java");
            relation.putIfAbsent("sourceFile", sourceFile);
            relation.putIfAbsent("provider", "configured-java-provider");
            String id = text(relation.get("kind")) + "|" + text(relation.get("source"))
                    + "|" + text(relation.get("target")) + "|" + sourceFile;
            if (seen.add(id)) snapshot.relations.add(relation);
        }
    }

    private static void recordSourceFacts(
            RepositorySnapshot snapshot,
            SourceKnowledgeProvider provider,
            String sourcePath,
            SourceKnowledgeResult result) {
        appendFacts(snapshot.sourceUnits, result.sourceUnitFacts(), provider.id(), sourcePath);
        appendFacts(snapshot.symbols, result.symbolFacts(), provider.id(), sourcePath);
        appendFacts(snapshot.relations, result.relationFacts(), provider.id(), sourcePath);
        appendFacts(snapshot.boundaries, result.boundaryFacts(), provider.id(), sourcePath);
        appendFacts(snapshot.warnings, result.warningFacts(), provider.id(), sourcePath);
        for (Map<String, Object> unit : result.sourceUnitFacts()) {
            if (!Boolean.TRUE.equals(unit.get("test"))) continue;
            Map<String, Object> test = copy(unit);
            test.put("testClass", text(unit.get("name")));
            test.putIfAbsent("package", text(unit.get("namespace")));
            snapshot.tests.add(test);
        }
    }

    private static void appendFacts(
            List target,
            List<Map<String, Object>> facts,
            String provider,
            String sourcePath) {
        for (Map<String, Object> raw : facts) {
            Map<String, Object> fact = copy(raw);
            fact.putIfAbsent("sourceFile", sourcePath);
            fact.putIfAbsent("provider", provider);
            target.add(fact);
        }
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

    private static List<Map<String, Object>> enrichFacts(
            List<?> facts,
            JavaKnowledgeResult result) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Object factObject : facts) {
            if (!(factObject instanceof Map<?, ?> sourceFact)) continue;
            Map<String, Object> fact = copy(sourceFact);
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

    private static Map<String, Object> copy(Map<?, ?> raw) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record JavaSourceUnit(Path file, String path) {
    }
}
