package org.aiknowledge.core.javajdt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.aiknowledge.core.javaspi.JavaKnowledgeProvider;
import org.aiknowledge.core.javaspi.JavaKnowledgeRequest;
import org.aiknowledge.core.javaspi.JavaKnowledgeResult;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

public final class JdtSearchJavaKnowledgeProvider implements JavaKnowledgeProvider {
    private static final int WORKER_TIMEOUT_SECONDS = Integer.getInteger("aiknowledge.jdt.search.worker.timeout.seconds", 20);
    private static final int STARTUP_TIMEOUT_SECONDS = Integer.getInteger("aiknowledge.jdt.search.startup.timeout.seconds", 5);
    private static final int MAX_LOG_LENGTH = Integer.getInteger("aiknowledge.jdt.search.max.log.length", 4000);
    private static final Map<String, SearchIndex> SEARCH_INDEX_CACHE = new ConcurrentHashMap<>();
    private final JdtJavaKnowledgeProvider astProvider = new JdtJavaKnowledgeProvider();

    @Override
    public JavaKnowledgeResult extract(JavaKnowledgeRequest request) throws IOException {
        boolean fallbackToAst = fallbackToAst(request);
        JavaKnowledgeResult base = astProvider.extract(request);
        if (!request.sourcePath().endsWith(".java")) return base;
        SearchIndex index = SEARCH_INDEX_CACHE.computeIfAbsent(cacheKey(request), ignored -> build(request));
        if (!fallbackToAst && index.failed()) {
            String message = index.warnings().isEmpty() ? "JDT search failed and AST fallback is disabled." : String.valueOf(index.warnings().get(0).get("message"));
            throw new IOException(message);
        }
        List<Object> relations = new ArrayList<>(base.relationFacts());
        if (fallbackToAst && index.failed()) relations.addAll(syntheticSearchRelations(base.relationFacts(), request.sourcePath()));
        relations.addAll(index.bySource().getOrDefault(request.sourcePath(), List.of()));
        List<Object> warnings = new ArrayList<>(base.warnings());
        warnings.addAll(index.warnings());
        if (fallbackToAst && index.failed()) {
            warnings.add(warning("jdt-search-fallback-ast", "SearchEngine unavailable; emitted jdt-search facts synthesized from jdt-ast relations."));
        }
        return new JavaKnowledgeResult(base.typeFacts(), base.methodFacts(), base.fieldFacts(), base.testFacts(), base.packageFacts(), base.referenceFacts(), relations, base.classFacts(), warnings);
    }

    private static List<Map<String, Object>> syntheticSearchRelations(List<?> relationFacts, String sourcePath) {
        List<Map<String, Object>> synthetic = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Object relation : relationFacts) {
            if (!(relation instanceof Map<?, ?> map)) continue;
            String originalKind = String.valueOf(map.get("kind"));
            String kind = syntheticKind(originalKind);
            if (kind.isBlank()) continue;
            if (!sourcePath.equals(String.valueOf(map.get("sourceFile")))) continue;
            Object offset = map.get("offset");
            Object length = map.get("length");
            if (!(offset instanceof Number) || !(length instanceof Number)) continue;
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("kind", kind);
            fact.put("source", map.get("source"));
            fact.put("target", map.get("target"));
            fact.put("sourceFile", map.get("sourceFile"));
            fact.put("offset", offset);
            fact.put("length", length);
            if (map.containsKey("line")) fact.put("line", map.get("line"));
            fact.put("accuracy", "A_INACCURATE");
            fact.put("provider", "jdt-search");
            fact.put("confidence", "search-synthetic");
            String key = kind + "|" + fact.get("source") + "|" + fact.get("target") + "|" + fact.get("sourceFile") + "|" + offset;
            if (seen.add(key)) synthetic.add(fact);
        }
        return List.copyOf(synthetic);
    }

    private static String syntheticKind(String originalKind) {
        return switch (originalKind) {
            case "TYPE_REFERENCES_TYPE", "TYPE_IMPLEMENTS_TYPE", "TYPE_EXTENDS_TYPE" -> originalKind;
            case "FIELD_HAS_TYPE", "METHOD_RETURNS_TYPE", "METHOD_PARAMETER_HAS_TYPE" -> "TYPE_REFERENCES_TYPE";
            default -> "";
        };
    }

    private static String cacheKey(JavaKnowledgeRequest request) {
        StringBuilder key = new StringBuilder();
        key.append(request.repositoryRoot().toAbsolutePath().normalize()).append('|');
        key.append(executionMode(request)).append('|');
        key.append(option(request, "jdtWorkspaceMode", "aiknowledge.jdt.workspace.mode", "create")).append('|');
        key.append(workspaceDirectory(request)).append('|');
        appendPathFingerprint(key, roots(request));
        key.append('|');
        appendPathFingerprint(key, classpathEntries(request));
        return key.toString();
    }

    private static void appendPathFingerprint(StringBuilder key, List<Path> paths) {
        paths.stream()
                .map(path -> path.toAbsolutePath().normalize().toString())
                .sorted()
                .forEach(path -> key.append(path).append(';'));
    }

    private static SearchIndex build(JavaKnowledgeRequest request) {
        String mode = executionMode(request);
        if ("embedded".equals(mode)) return buildEmbedded(request);
        if ("forked".equals(mode)) return buildForked(request);
        return SearchIndex.warning("jdt-search-invalid-execution-mode", "Unsupported jdtSearchExecutionMode '" + mode + "'. Use embedded|forked.");
    }

    private static SearchIndex buildEmbedded(JavaKnowledgeRequest request) {
        try {
            List<TypeSource> types = discoverTypes(request);
            if (types.isEmpty()) return SearchIndex.empty();
            if (!Platform.isRunning()) {
                RuntimeStart runtime = startWorkspaceRuntime(request);
                if (!runtime.isStarted()) {
                    String details = runtime.message().isBlank() ? "" : " (" + runtime.message() + ")";
                    return SearchIndex.warning("jdt-search-workspace-unavailable", "Could not start Eclipse workspace runtime" + details + "; emitted jdt-ast facts only.");
                }
            }
            IJavaProject[] projects = projectsForMode(request);
            if (projects.length == 0) return SearchIndex.warning("jdt-search-no-java-project", "No workspace Java project maps to the repository root; emitted jdt-ast facts only.");
            return search(request.repositoryRoot(), projects, types);
        } catch (Exception | LinkageError ex) {
            return SearchIndex.warning("jdt-search-failed", ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage()));
        }
    }

    private static SearchIndex buildForked(JavaKnowledgeRequest request) {
        Path workspace = workspaceDirectory(request);
        Path requestJson = workspace.resolve("jdt-search-request.json");
        Path requestBin = workspace.resolve("jdt-search-request.bin");
        Path factsJson = workspace.resolve("jdt-search-facts.json");
        Path factsBin = workspace.resolve("jdt-search-facts.bin");
        Path stdoutLog = workspace.resolve("jdt-search-worker.stdout.log");
        Path stderrLog = workspace.resolve("jdt-search-worker.stderr.log");
        WorkerRequest workerRequest = WorkerRequest.from(request);
        try {
            Files.createDirectories(workspace);
            writeJson(requestJson, workerRequest.toMap());
            writeBytes(requestBin, serialize(workerRequest));

            ProcessBuilder processBuilder = new ProcessBuilder(
                    javaExecutable(),
                    "-cp",
                    System.getProperty("java.class.path", ""),
                    WorkerMain.class.getName(),
                    requestBin.toString(),
                    factsBin.toString(),
                    factsJson.toString());
            processBuilder.directory(request.repositoryRoot().toFile());
            processBuilder.redirectOutput(stdoutLog.toFile());
            processBuilder.redirectError(stderrLog.toFile());
            Process process = processBuilder.start();
            if (!process.waitFor(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return SearchIndex.warning("jdt-search-worker-timeout", "Forked JDT worker did not finish within " + WORKER_TIMEOUT_SECONDS + " seconds.");
            }
            int exitCode = process.exitValue();
            String stdout = readText(stdoutLog);
            String stderr = readText(stderrLog);
            if (exitCode != 0) {
                return SearchIndex.warning("jdt-search-worker-failed",
                        "Forked JDT worker exited with code " + exitCode + formatProcessLogs(stdout, stderr));
            }
            if (!Files.exists(factsBin)) {
                return SearchIndex.warning("jdt-search-worker-failed", "Forked JDT worker completed without facts output at " + factsBin);
            }
            WorkerResult result = deserialize(Files.readAllBytes(factsBin), WorkerResult.class);
            return new SearchIndex(result.bySource(), result.warnings(), result.failed());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return SearchIndex.warning("jdt-search-worker-interrupted", "Forked JDT worker was interrupted.");
        } catch (Exception ex) {
            return SearchIndex.warning("jdt-search-worker-launch-failed", ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage()));
        } finally {
            if (!keepWorkspace(request)) cleanupGeneratedFiles(requestBin, factsBin, stdoutLog, stderrLog, requestJson, factsJson);
        }
    }

    private static void cleanupGeneratedFiles(Path... files) {
        for (Path file : files) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
            }
        }
    }

    private static String formatProcessLogs(String stdout, String stderr) {
        StringBuilder out = new StringBuilder();
        String trimmedStdout = truncate(stdout);
        String trimmedStderr = truncate(stderr);
        if (!trimmedStdout.isBlank()) out.append(" stdout=").append(trimmedStdout.replace('\n', ' ').trim());
        if (!trimmedStderr.isBlank()) out.append(" stderr=").append(trimmedStderr.replace('\n', ' ').trim());
        return out.toString();
    }

    private static String truncate(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.length() <= MAX_LOG_LENGTH) return trimmed;
        return trimmed.substring(0, MAX_LOG_LENGTH) + "...";
    }

    private static String javaExecutable() {
        Path javaHome = Path.of(System.getProperty("java.home", ""));
        Path candidate = javaHome.resolve("bin").resolve(isWindows() ? "java.exe" : "java");
        return Files.isRegularFile(candidate) ? candidate.toString() : "java";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static RuntimeStart startWorkspaceRuntime(JavaKnowledgeRequest request) {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "jdt-workspace-startup");
            thread.setDaemon(true);
            return thread;
        });
        Future<RuntimeStart> startup = null;
        try {
            startup = executor.submit(() -> startWorkspaceRuntimeBlocking(request));
            return startup.get(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            if (startup != null) startup.cancel(true);
            return RuntimeStart.failure("Timed out while starting Eclipse workspace runtime.");
        } catch (Exception ex) {
            if (startup != null) startup.cancel(true);
            return RuntimeStart.failure(ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage()));
        } finally {
            executor.shutdownNow();
        }
    }

    private static RuntimeStart startWorkspaceRuntimeBlocking(JavaKnowledgeRequest request) {
        try {
            Path workspace = workspaceDirectory(request);
            Files.createDirectories(workspace);
            Map<String, String> properties = new HashMap<>();
            properties.put("osgi.instance.area", workspace.toUri().toString());
            properties.put("eclipse.application", "org.eclipse.equinox.app.error");
            Class<?> starter = Class.forName("org.eclipse.core.runtime.adaptor.EclipseStarter");
            Method setInitialProperties = starter.getMethod("setInitialProperties", Map.class);
            setInitialProperties.invoke(null, properties);
            Method startup = starter.getMethod("startup", String[].class, Runnable.class);
            String[] args = new String[] {"-data", workspace.toString(), "-consoleLog", "-nosplash"};
            startup.invoke(null, args, null);
            return Platform.isRunning() ? RuntimeStart.ok() : RuntimeStart.failure("Platform did not report running after startup.");
        } catch (ReflectiveOperationException | IOException | RuntimeException | LinkageError ex) {
            return RuntimeStart.failure(ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage()));
        }
    }

    private static Path workspaceDirectory(JavaKnowledgeRequest request) {
        String configured = option(request, "jdtWorkspaceDirectory", "aiknowledge.jdt.workspace.directory", "");
        if (!configured.isBlank()) return Path.of(configured).toAbsolutePath().normalize();
        return request.repositoryRoot().resolve("build/ai-knowledge/jdt-workspace").toAbsolutePath().normalize();
    }

    private static String executionMode(JavaKnowledgeRequest request) {
        return option(request, "jdtSearchExecutionMode", "aiknowledge.jdt.search.execution.mode", "embedded").trim().toLowerCase();
    }

    private static boolean fallbackToAst(JavaKnowledgeRequest request) {
        return parseBoolean(option(request, "jdtSearchFallbackToAst", "aiknowledge.jdt.search.fallback.to.ast", "true"), true);
    }

    private static boolean keepWorkspace(JavaKnowledgeRequest request) {
        return parseBoolean(option(request, "keepJdtWorkspace", "aiknowledge.jdt.workspace.keep", "false"), false);
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim().toLowerCase();
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized)) return true;
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized) || "off".equals(normalized)) return false;
        return fallback;
    }

    private static IJavaProject[] projectsForMode(JavaKnowledgeRequest request) throws CoreException {
        String mode = option(request, "jdtWorkspaceMode", "aiknowledge.jdt.workspace.mode", "create").trim().toLowerCase();
        if ("off".equals(mode)) return javaProjects(request.repositoryRoot());
        if ("existing".equals(mode)) return javaProjects(request.repositoryRoot());
        return new IJavaProject[] {createOrUpdateWorkspaceProject(request)};
    }

    private static IJavaProject createOrUpdateWorkspaceProject(JavaKnowledgeRequest request) throws CoreException {
        NullProgressMonitor monitor = new NullProgressMonitor();
        String projectName = workspaceProjectName(request.repositoryRoot());
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (!project.exists()) project.create(monitor);
        if (!project.isOpen()) project.open(monitor);

        IProjectDescription description = project.getDescription();
        if (!hasNature(description, JavaCore.NATURE_ID)) description.setNatureIds(new String[] {JavaCore.NATURE_ID});
        project.setDescription(description, monitor);

        IJavaProject javaProject = JavaCore.create(project);
        List<IClasspathEntry> classpath = new ArrayList<>();
        int sourceIndex = 0;
        for (Path sourceRoot : roots(request)) {
            if (!Files.isDirectory(sourceRoot)) continue;
            IFolder sourceFolder = project.getFolder("source" + sourceIndex++);
            if (!sourceFolder.exists()) {
                sourceFolder.createLink(new org.eclipse.core.runtime.Path(sourceRoot.toAbsolutePath().normalize().toString()), IResource.REPLACE, monitor);
            }
            classpath.add(JavaCore.newSourceEntry(sourceFolder.getFullPath()));
        }
        for (Object entry : request.classpathEntries()) {
            if (!(entry instanceof Path path) || !Files.exists(path)) continue;
            classpath.add(JavaCore.newLibraryEntry(new org.eclipse.core.runtime.Path(path.toAbsolutePath().normalize().toString()), null, null));
        }
        IFolder output = project.getFolder("bin");
        if (!output.exists()) output.create(true, true, monitor);
        javaProject.setRawClasspath(classpath.toArray(IClasspathEntry[]::new), output.getFullPath(), monitor);
        project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
        project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
        return javaProject;
    }

    private static boolean hasNature(IProjectDescription description, String natureId) {
        for (String nature : description.getNatureIds()) if (natureId.equals(nature)) return true;
        return false;
    }

    private static String workspaceProjectName(Path root) {
        String base = root.getFileName() == null ? "repository" : root.getFileName().toString();
        String safe = base.replaceAll("[^A-Za-z0-9_.-]", "-");
        return "ai-knowledge-" + safe + "-" + Integer.toHexString(root.toAbsolutePath().normalize().toString().hashCode());
    }

    private static SearchIndex search(Path root, IJavaProject[] projects, List<TypeSource> types) throws CoreException {
        Map<String, TypeSource> byPath = new LinkedHashMap<>();
        for (TypeSource type : types) byPath.put(type.sourcePath(), type);
        Map<String, List<Map<String, Object>>> bySource = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        SearchEngine engine = new SearchEngine();
        SearchParticipant[] participants = {SearchEngine.getDefaultSearchParticipant()};
        org.eclipse.jdt.core.search.IJavaSearchScope scope = SearchEngine.createJavaSearchScope(javaElements(projects),
                org.eclipse.jdt.core.search.IJavaSearchScope.SOURCES
                        | org.eclipse.jdt.core.search.IJavaSearchScope.REFERENCED_PROJECTS
                        | org.eclipse.jdt.core.search.IJavaSearchScope.APPLICATION_LIBRARIES
                        | org.eclipse.jdt.core.search.IJavaSearchScope.SYSTEM_LIBRARIES);
        for (TypeSource target : types) {
            IType type = findType(projects, target.fqn());
            if (type == null) continue;
            runSearch(root, engine, participants, scope, byPath, bySource, seen, target, type, IJavaSearchConstants.REFERENCES);
            runSearch(root, engine, participants, scope, byPath, bySource, seen, target, type, IJavaSearchConstants.IMPLEMENTORS);
        }
        return new SearchIndex(Map.copyOf(bySource), List.of(), false);
    }

    private static void runSearch(Path root, SearchEngine engine, SearchParticipant[] participants,
            org.eclipse.jdt.core.search.IJavaSearchScope scope, Map<String, TypeSource> byPath,
            Map<String, List<Map<String, Object>>> bySource, Set<String> seen, TypeSource target, IType type, int limitTo) throws CoreException {
        SearchPattern pattern = SearchPattern.createPattern(type, limitTo);
        if (pattern == null) return;
        engine.search(pattern, participants, scope, new SearchRequestor() {
            @Override public void acceptSearchMatch(SearchMatch match) {
                String sourcePath = sourcePath(root, match.getResource());
                if (sourcePath.isBlank()) return;
                TypeSource sourceType = byPath.get(sourcePath);
                String source = sourceType == null ? sourcePath : sourceType.fqn();
                String kind = limitTo == IJavaSearchConstants.IMPLEMENTORS
                        ? ("interface".equals(target.kind()) ? "TYPE_IMPLEMENTS_TYPE" : "TYPE_EXTENDS_TYPE")
                        : "TYPE_REFERENCES_TYPE";
                Map<String, Object> fact = relation(kind, source, target.fqn(), sourcePath, match);
                String key = kind + "|" + source + "|" + target.fqn() + "|" + sourcePath + "|" + match.getOffset();
                if (seen.add(key)) bySource.computeIfAbsent(sourcePath, ignored -> new ArrayList<>()).add(fact);
            }
        }, new NullProgressMonitor());
    }

    private static List<TypeSource> discoverTypes(JavaKnowledgeRequest request) throws IOException {
        List<TypeSource> result = new ArrayList<>();
        for (Path root : roots(request)) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> stream = Files.walk(root)) {
                for (Path file : stream.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                    TypeSource parsed = parse(request, file);
                    if (parsed != null) result.add(parsed);
                }
            }
        }
        return List.copyOf(result);
    }

    private static TypeSource parse(JavaKnowledgeRequest request, Path file) throws IOException {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(Files.readString(file).toCharArray());
        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        if (unit.types().isEmpty() || !(unit.types().get(0) instanceof TypeDeclaration type)) return null;
        String pkg = unit.getPackage() == null ? "" : unit.getPackage().getName().getFullyQualifiedName();
        String simple = type.getName().getIdentifier();
        String fqn = pkg.isBlank() ? simple : pkg + "." + simple;
        String path = request.repositoryRoot().relativize(file).toString().replace('\\', '/');
        return new TypeSource(fqn, path, type.isInterface() ? "interface" : "class");
    }

    private static IJavaProject[] javaProjects(Path repositoryRoot) throws CoreException {
        List<IJavaProject> result = new ArrayList<>();
        Path root = repositoryRoot.toAbsolutePath().normalize();
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!project.isAccessible() || !project.hasNature(JavaCore.NATURE_ID) || project.getLocation() == null) continue;
            Path location = Path.of(project.getLocation().toOSString()).toAbsolutePath().normalize();
            if (location.startsWith(root) || root.startsWith(location)) result.add(JavaCore.create(project));
        }
        return result.toArray(IJavaProject[]::new);
    }

    private static IJavaElement[] javaElements(IJavaProject[] projects) {
        IJavaElement[] elements = new IJavaElement[projects.length];
        System.arraycopy(projects, 0, elements, 0, projects.length);
        return elements;
    }

    private static IType findType(IJavaProject[] projects, String fqn) throws JavaModelException {
        for (IJavaProject project : projects) {
            IType type = project.findType(fqn);
            if (type != null && type.exists()) return type;
        }
        return null;
    }

    private static Map<String, Object> relation(String kind, String source, String target, String sourcePath, SearchMatch match) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("kind", kind);
        fact.put("source", source);
        fact.put("target", target);
        fact.put("sourceFile", sourcePath);
        if (match.getResource() != null) fact.put("resourcePath", match.getResource().getFullPath().toString());
        if (match.getOffset() >= 0) {
            fact.put("offset", match.getOffset());
            fact.put("length", match.getLength());
        }
        fact.put("accuracy", match.getAccuracy() == SearchMatch.A_ACCURATE ? "A_ACCURATE" : "A_INACCURATE");
        fact.put("insideDocComment", match.isInsideDocComment());
        fact.put("provider", "jdt-search");
        fact.put("confidence", match.getAccuracy() == SearchMatch.A_ACCURATE ? "search-accurate" : "search-inaccurate");
        return fact;
    }

    private static String sourcePath(Path repositoryRoot, IResource resource) {
        if (resource == null || resource.getLocation() == null) return "";
        Path location = Path.of(resource.getLocation().toOSString()).toAbsolutePath().normalize();
        Path root = repositoryRoot.toAbsolutePath().normalize();
        return location.startsWith(root) ? root.relativize(location).toString().replace('\\', '/') : "";
    }

    private static List<Path> roots(JavaKnowledgeRequest request) {
        List<Path> roots = new ArrayList<>();
        for (Object root : request.sourceRoots()) if (root instanceof Path path) roots.add(path);
        for (Object root : request.testSourceRoots()) if (root instanceof Path path) roots.add(path);
        return roots;
    }

    private static List<Path> classpathEntries(JavaKnowledgeRequest request) {
        List<Path> entries = new ArrayList<>();
        for (Object entry : request.classpathEntries()) if (entry instanceof Path path) entries.add(path);
        return entries;
    }

    private static String option(JavaKnowledgeRequest request, String configKey, String propertyKey, String fallback) {
        Object configured = request.providerConfiguration().get(configKey);
        if (configured != null && !String.valueOf(configured).isBlank()) return String.valueOf(configured);
        return System.getProperty(propertyKey, fallback);
    }

    private static Map<String, Object> warning(String code, String message) {
        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("code", code);
        warning.put("message", message == null ? "" : message);
        return warning;
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static <T> T deserialize(byte[] bytes, Class<T> type) throws IOException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object value = in.readObject();
            if (!type.isInstance(value)) throw new IOException("Unexpected serialized type " + value.getClass().getName());
            return type.cast(value);
        } catch (ClassNotFoundException ex) {
            throw new IOException("Could not deserialize worker payload", ex);
        }
    }

    private static void writeBytes(Path path, byte[] bytes) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }

    private static String readText(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
        } catch (IOException ignored) {
            return "";
        }
    }

    private static void writeJson(Path path, Object value) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, toJson(value) + "\n", StandardCharsets.UTF_8);
    }

    private static String toJson(Object value) {
        StringBuilder out = new StringBuilder();
        appendJson(out, value);
        return out.toString();
    }

    private static void appendJson(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            quoteJson(out, text);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                quoteJson(out, String.valueOf(entry.getKey()));
                out.append(':');
                appendJson(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Collection<?> values) {
            out.append('[');
            boolean first = true;
            for (Object item : values) {
                if (!first) out.append(',');
                first = false;
                appendJson(out, item);
            }
            out.append(']');
        } else {
            quoteJson(out, String.valueOf(value));
        }
    }

    private static void quoteJson(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c < 32 ? String.format("\\u%04x", (int) c) : c);
            }
        }
        out.append('"');
    }

    public static final class WorkerMain {
        private WorkerMain() {
        }

        public static void main(String[] args) {
            if (args.length < 3) {
                System.err.println("Usage: WorkerMain <request-bin> <facts-bin> <facts-json>");
                System.exit(2);
            }
            Path requestBin = Path.of(args[0]);
            Path factsBin = Path.of(args[1]);
            Path factsJson = Path.of(args[2]);
            try {
                WorkerRequest request = deserialize(Files.readAllBytes(requestBin), WorkerRequest.class);
                SearchIndex index = buildEmbedded(request.toJavaKnowledgeRequest());
                WorkerResult result = WorkerResult.from(index);
                writeBytes(factsBin, serialize(result));
                writeJson(factsJson, result.toMap());
            } catch (Throwable ex) {
                ex.printStackTrace(System.err);
                System.exit(2);
            }
        }
    }

    private record RuntimeStart(boolean isStarted, String message) {
        static RuntimeStart ok() { return new RuntimeStart(true, ""); }
        static RuntimeStart failure(String message) { return new RuntimeStart(false, message == null ? "" : message); }
    }

    private record TypeSource(String fqn, String sourcePath, String kind) {}
    private record SearchIndex(Map<String, List<Map<String, Object>>> bySource, List<Map<String, Object>> warnings, boolean failed) {
        static SearchIndex empty() { return new SearchIndex(Map.of(), List.of(), false); }
        static SearchIndex warning(String code, String message) { return new SearchIndex(Map.of(), List.of(JdtSearchJavaKnowledgeProvider.warning(code, message)), true); }
    }

    private record WorkerRequest(
            String repositoryRoot,
            String sourceFile,
            String sourcePath,
            List<Object> modules,
            List<String> sourceRoots,
            List<String> testSourceRoots,
            Map<String, Object> buildMetadata,
            List<String> classpathEntries,
            Map<String, Object> providerConfiguration) implements java.io.Serializable {
        static WorkerRequest from(JavaKnowledgeRequest request) {
            Map<String, Object> providerConfig = new LinkedHashMap<>(canonicalMap(request.providerConfiguration()));
            providerConfig.putIfAbsent("jdtWorkspaceMode", option(request, "jdtWorkspaceMode", "aiknowledge.jdt.workspace.mode", "create"));
            providerConfig.putIfAbsent("jdtWorkspaceDirectory", workspaceDirectory(request).toString());
            providerConfig.putIfAbsent("jdtSearchExecutionMode", "embedded");
            providerConfig.putIfAbsent("keepJdtWorkspace", String.valueOf(keepWorkspace(request)));
            return new WorkerRequest(
                    request.repositoryRoot().toString(),
                    request.sourceFile().toString(),
                    request.sourcePath(),
                    canonicalList(request.modules()),
                    stringPaths(request.sourceRoots()),
                    stringPaths(request.testSourceRoots()),
                    canonicalMap(request.buildMetadata()),
                    stringPaths(request.classpathEntries()),
                    providerConfig);
        }

        private static List<String> stringPaths(List values) {
            List<String> paths = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof Path path) paths.add(path.toString());
                else if (value != null && !String.valueOf(value).isBlank()) paths.add(String.valueOf(value));
            }
            return List.copyOf(paths);
        }

        private static Map<String, Object> canonicalMap(Map map) {
            Map<String, Object> canonical = new LinkedHashMap<>();
            for (Object key : map.keySet()) canonical.put(String.valueOf(key), canonicalValue(map.get(key)));
            return canonical;
        }

        private static List<Object> canonicalList(List values) {
            List<Object> canonical = new ArrayList<>();
            for (Object value : values) canonical.add(canonicalValue(value));
            return List.copyOf(canonical);
        }

        private static Object canonicalValue(Object value) {
            if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) return value;
            if (value instanceof Path path) return path.toString();
            if (value instanceof Map map) return canonicalMap(map);
            if (value instanceof List list) return canonicalList(list);
            return String.valueOf(value);
        }

        JavaKnowledgeRequest toJavaKnowledgeRequest() {
            return new JavaKnowledgeRequest(
                    Path.of(repositoryRoot),
                    Path.of(sourceFile),
                    sourcePath,
                    modules,
                    toPathList(sourceRoots),
                    toPathList(testSourceRoots),
                    buildMetadata,
                    toPathList(classpathEntries),
                    providerConfiguration);
        }

        private static List<Path> toPathList(List<String> values) {
            List<Path> paths = new ArrayList<>();
            for (String value : values) if (value != null && !value.isBlank()) paths.add(Path.of(value));
            return List.copyOf(paths);
        }

        Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("repositoryRoot", repositoryRoot);
            out.put("sourceFile", sourceFile);
            out.put("sourcePath", sourcePath);
            out.put("modules", modules);
            out.put("sourceRoots", sourceRoots);
            out.put("testSourceRoots", testSourceRoots);
            out.put("buildMetadata", buildMetadata);
            out.put("classpathEntries", classpathEntries);
            out.put("providerConfiguration", providerConfiguration);
            return out;
        }
    }

    private record WorkerResult(
            Map<String, List<Map<String, Object>>> bySource,
            List<Map<String, Object>> warnings,
            boolean failed) implements java.io.Serializable {
        static WorkerResult from(SearchIndex index) {
            return new WorkerResult(index.bySource(), index.warnings(), index.failed());
        }

        Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            Map<String, List<Map<String, Object>>> sortedBySource = new LinkedHashMap<>();
            bySource.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sortedBySource.put(entry.getKey(), entry.getValue()));
            out.put("bySource", sortedBySource);
            out.put("warnings", warnings);
            out.put("failed", failed);
            return out;
        }
    }
}
