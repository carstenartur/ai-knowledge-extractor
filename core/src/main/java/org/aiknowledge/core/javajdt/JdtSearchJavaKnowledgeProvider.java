package org.aiknowledge.core.javajdt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final Map<Path, SearchIndex> CACHE = new ConcurrentHashMap<>();
    private final JdtJavaKnowledgeProvider astProvider = new JdtJavaKnowledgeProvider();

    @Override
    public JavaKnowledgeResult extract(JavaKnowledgeRequest request) throws IOException {
        JavaKnowledgeResult base = astProvider.extract(request);
        if (!request.sourcePath().endsWith(".java")) return base;
        SearchIndex index = CACHE.compute(request.repositoryRoot(), (k, old) -> build(request));
        List<Object> relations = new ArrayList<>(base.relationFacts());
        relations.addAll(index.bySource().getOrDefault(request.sourcePath(), List.of()));
        List<Object> warnings = new ArrayList<>(base.warnings());
        warnings.addAll(index.warnings());
        return new JavaKnowledgeResult(base.typeFacts(), base.methodFacts(), base.fieldFacts(), base.testFacts(), base.packageFacts(), base.referenceFacts(), relations, base.classFacts(), warnings);
    }

    private static SearchIndex build(JavaKnowledgeRequest request) {
        try {
            List<TypeSource> types = discoverTypes(request);
            if (types.isEmpty()) return SearchIndex.empty();
            if (!Platform.isRunning()) return SearchIndex.warning("jdt-search-workspace-unavailable", "jdtMode=search requires an Eclipse workspace; emitted jdt-ast facts only.");
            IJavaProject[] projects = projectsForMode(request);
            if (projects.length == 0) return SearchIndex.warning("jdt-search-no-java-project", "No workspace Java project maps to the repository root; emitted jdt-ast facts only.");
            return search(request.repositoryRoot(), projects, types);
        } catch (Exception | LinkageError ex) {
            return SearchIndex.warning("jdt-search-failed", ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage()));
        }
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
        return new SearchIndex(Map.copyOf(bySource), List.of());
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

    private record TypeSource(String fqn, String sourcePath, String kind) {}
    private record SearchIndex(Map<String, List<Map<String, Object>>> bySource, List<Map<String, Object>> warnings) {
        static SearchIndex empty() { return new SearchIndex(Map.of(), List.of()); }
        static SearchIndex warning(String code, String message) { return new SearchIndex(Map.of(), List.of(JdtSearchJavaKnowledgeProvider.warning(code, message))); }
    }
}
