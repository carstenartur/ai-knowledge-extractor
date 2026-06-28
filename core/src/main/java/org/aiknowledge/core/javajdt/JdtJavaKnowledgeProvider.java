package org.aiknowledge.core.javajdt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.aiknowledge.core.javaspi.JavaKnowledgeRequest;
import org.aiknowledge.core.javaspi.JavaKnowledgeProvider;
import org.aiknowledge.core.javaspi.JavaKnowledgeResult;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchPattern;

public final class JdtJavaKnowledgeProvider implements JavaKnowledgeProvider {
    private static final Map<Path, ProjectIndex> INDEX_CACHE = new ConcurrentHashMap<>();

    @Override
    public JavaKnowledgeResult extract(JavaKnowledgeRequest request) throws IOException {
        if (!request.sourcePath().endsWith(".java")) return JavaKnowledgeResult.empty();
        ProjectIndex index = INDEX_CACHE.compute(request.repositoryRoot(), (key, existing) -> {
            try {
                return buildIndex(request);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });
        SourceFacts sourceFacts = index.bySourcePath().get(request.sourcePath());
        if (sourceFacts == null) return JavaKnowledgeResult.empty();
        String typeName = sourceFacts.typeName();
        String modulePath = modulePath(request.modules(), request.sourcePath());
        String sourceFolder = sourceFolder(request, request.sourceFile());
        boolean test = sourceFacts.test();
        List<String> implementations = index.implementationsByInterface().getOrDefault(typeName, List.of());
        List<String> referencedBy = index.referencesToType().getOrDefault(typeName, List.of());
        List<String> references = index.referencesByType().getOrDefault(typeName, List.of());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(test ? "testClass" : "class", typeName);
        data.put("sourceFile", request.sourcePath());
        data.put("package", sourceFacts.packageName());
        data.put("sourceFolder", sourceFolder);
        data.put("module", modulePath);
        data.put("kind", sourceFacts.kind());
        data.put("imports", sourceFacts.imports());
        data.put("superclass", sourceFacts.superclassName());
        data.put("interfaces", sourceFacts.interfaces());
        data.put("lineCount", sourceFacts.lineCount());
        data.put("methodCount", sourceFacts.methods().size());
        if (!implementations.isEmpty()) data.put("implementations", implementations);
        if (!referencedBy.isEmpty()) data.put("referencedBy", referencedBy);
        if (test) {
            data.put("testMethods", sourceFacts.methods());
            List<String> referencedProductionTypes = references.stream()
                    .filter(reference -> !Boolean.TRUE.equals(index.testTypes().get(reference)))
                    .toList();
            data.put("referencedProductionTypes", referencedProductionTypes);
            data.put("testedClass", inferTestedClass(sourceFacts, referencedProductionTypes));
            data.put("tags", sourceFacts.tags());
        } else {
            data.put("publicApiMethods", sourceFacts.methods());
            data.put("referencedProjectClasses", references);
        }

        List<Map<String, Object>> classFacts = test ? List.of() : List.of(data);
        List<Map<String, Object>> testFacts = test ? List.of(data) : List.of();
        List<Map<String, Object>> typeFacts = List.of(typeFact(sourceFacts, request.sourcePath(), sourceFolder, modulePath));
        List<Map<String, Object>> methodFacts = methodFacts(typeName, sourceFacts.methods(), sourceFacts.constructors());
        List<Map<String, Object>> packageFacts = sourceFacts.packageName().isBlank()
                ? List.of()
                : List.of(packageFact(sourceFacts.packageName(), request.sourcePath(), sourceFolder, modulePath));
        List<Map<String, Object>> referenceFacts = referenceFacts(typeName, references, referencedBy);
        List<Map<String, Object>> warnings = new ArrayList<>(sourceFacts.warnings());
        if (request.classpathEntries().isEmpty()) warnings.add(warning(
                "jdt-classpath-incomplete",
                "JDT provider was configured without explicit classpath entries; bindings may be incomplete."));
        return new JavaKnowledgeResult(typeFacts, methodFacts, testFacts, packageFacts, referenceFacts, classFacts, warnings);
    }

    private static ProjectIndex buildIndex(JavaKnowledgeRequest request) throws IOException {
        List<Path> files = javaFiles(request.sourceRoots(), request.testSourceRoots());
        Map<String, SourceFacts> byPath = new LinkedHashMap<>();
        Map<String, Boolean> testTypes = new LinkedHashMap<>();
        Map<String, Set<String>> knownTypesBySimple = new LinkedHashMap<>();
        for (Path file : files) {
            SourceFacts facts = parseSource(request, file);
            if (facts == null) continue;
            byPath.put(facts.sourcePath(), facts);
            testTypes.put(facts.typeName(), facts.test());
            knownTypesBySimple.computeIfAbsent(facts.simpleName(), ignored -> new LinkedHashSet<>()).add(facts.typeName());
        }

        Map<String, Set<String>> implementationsByInterface = new LinkedHashMap<>();
        Map<String, Set<String>> referencesByType = new LinkedHashMap<>();
        Map<String, Set<String>> referencesToType = new LinkedHashMap<>();
        for (SourceFacts facts : byPath.values()) {
            Set<String> resolvedReferences = new LinkedHashSet<>();
            for (String reference : facts.references()) {
                String resolved = resolveType(reference, facts.packageName(), knownTypesBySimple);
                if (resolved != null && !resolved.equals(facts.typeName())) resolvedReferences.add(resolved);
            }
            referencesByType.put(facts.typeName(), resolvedReferences);
            for (String target : resolvedReferences) {
                referencesToType.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(facts.typeName());
            }
            if ("class".equals(facts.kind()) || "record".equals(facts.kind())) {
                for (String iface : facts.interfaces()) {
                    String resolvedInterface = resolveType(iface, facts.packageName(), knownTypesBySimple);
                    if (resolvedInterface != null) {
                        implementationsByInterface.computeIfAbsent(resolvedInterface, ignored -> new LinkedHashSet<>()).add(facts.typeName());
                    }
                }
            }
        }
        return new ProjectIndex(
                byPath,
                toListMap(implementationsByInterface),
                toListMap(referencesByType),
                toListMap(referencesToType),
                testTypes);
    }

    private static Map<String, List<String>> toListMap(Map<String, Set<String>> source) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            map.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return map;
    }

    private static String resolveType(String reference, String packageName, Map<String, Set<String>> knownTypesBySimple) {
        String candidate = reference.trim();
        if (candidate.isBlank()) return null;
        if (knownTypesBySimple.containsKey(candidate)) {
            Set<String> matches = knownTypesBySimple.get(candidate);
            return matches.size() == 1 ? matches.iterator().next() : null;
        }
        int lastDot = candidate.lastIndexOf('.');
        String simple = lastDot >= 0 ? candidate.substring(lastDot + 1) : candidate;
        if (knownTypesBySimple.containsKey(simple)) {
            Set<String> matches = knownTypesBySimple.get(simple);
            if (matches.size() == 1) return matches.iterator().next();
            String packageQualified = packageName.isBlank() ? simple : packageName + "." + simple;
            if (matches.contains(packageQualified)) return packageQualified;
        }
        return null;
    }

    private static List<Path> javaFiles(List<?> sourceRoots, List<?> testSourceRoots) throws IOException {
        Set<Path> files = new LinkedHashSet<>();
        for (Path root : mergeRoots(sourceRoots, testSourceRoots)) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(path -> path.toString().endsWith(".java"))
                        .sorted()
                        .forEach(files::add);
            }
        }
        return List.copyOf(files);
    }

    private static List<Path> mergeRoots(List<?> sourceRoots, List<?> testSourceRoots) {
        List<Path> roots = new ArrayList<>();
        appendPathRoots(roots, sourceRoots);
        appendPathRoots(roots, testSourceRoots);
        return roots;
    }

    private static void appendPathRoots(List<Path> roots, List<?> candidates) {
        for (Object candidate : candidates) {
            if (candidate instanceof Path path) roots.add(path);
        }
    }

    private static SourceFacts parseSource(JavaKnowledgeRequest request, Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setBindingsRecovery(true);
        parser.setResolveBindings(true);
        parser.setUnitName(file.getFileName().toString());
        parser.setCompilerOptions(compilerOptions());
        parser.setEnvironment(
                stringArray(request.classpathEntries()),
                stringArray(mergeRoots(request.sourceRoots(), request.testSourceRoots())),
                null,
                true);
        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        List<?> unitTypes = unit.types();
        if (unitTypes.isEmpty()) return null;

        Object firstType = unitTypes.get(0);
        String simpleName = simpleName(firstType);
        if (simpleName.isBlank()) return null;
        String packageName = unit.getPackage() == null ? "" : unit.getPackage().getName().getFullyQualifiedName();
        String typeName = packageName.isBlank() ? simpleName : packageName + "." + simpleName;
        List<String> methods = methods(firstType);
        List<String> constructors = constructors(firstType);
        List<String> interfaces = interfaces(firstType);
        List<String> imports = imports(unit);
        Set<String> references = new LinkedHashSet<>(imports);
        collectTypeReferences(unit, references);
        List<Map<String, Object>> warnings = new ArrayList<>();
        if (hasBindingProblems(unit)) warnings.add(warning("jdt-bindings-incomplete", "JDT reported unresolved bindings in this source file."));
        boolean test = isTestSource(request, file, simpleName);
        return new SourceFacts(
                request.repositoryRoot().relativize(file).toString().replace('\\', '/'),
                typeName,
                simpleName,
                packageName,
                kind(firstType),
                superclass(firstType),
                interfaces,
                methods,
                constructors,
                imports,
                List.copyOf(references),
                source.split("\\R", -1).length,
                test,
                tags(source),
                List.copyOf(warnings));
    }

    private static Map<String, String> compilerOptions() {
        Map<String, String> options = new LinkedHashMap<>(JavaCore.getOptions());
        JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
        return options;
    }

    private static boolean isTestSource(JavaKnowledgeRequest request, Path file, String simpleName) {
        for (Object rootObject : request.testSourceRoots()) {
            if (!(rootObject instanceof Path root)) continue;
            if (file.normalize().startsWith(root.normalize())) return true;
        }
        String relative = request.repositoryRoot().relativize(file).toString().replace('\\', '/');
        if (relative.contains("/src/test/")) return true;
        return simpleName.endsWith("Test");
    }

    private static String kind(Object declaration) {
        if (declaration instanceof TypeDeclaration type && type.isInterface()) return "interface";
        if (declaration instanceof RecordDeclaration) return "record";
        if (declaration instanceof EnumDeclaration) return "enum";
        if (declaration instanceof AnnotationTypeDeclaration) return "annotation";
        return "class";
    }

    private static String simpleName(Object declaration) {
        if (declaration instanceof TypeDeclaration type) return type.getName().getIdentifier();
        if (declaration instanceof RecordDeclaration record) return record.getName().getIdentifier();
        if (declaration instanceof EnumDeclaration enumDeclaration) return enumDeclaration.getName().getIdentifier();
        if (declaration instanceof AnnotationTypeDeclaration annotation) return annotation.getName().getIdentifier();
        return "";
    }

    private static String superclass(Object declaration) {
        if (declaration instanceof TypeDeclaration type) {
            Type superclassType = type.getSuperclassType();
            return superclassType == null ? "" : superclassType.toString();
        }
        return "";
    }

    private static List<String> interfaces(Object declaration) {
        List<String> interfaces = new ArrayList<>();
        List<?> source = declaration instanceof TypeDeclaration type ? type.superInterfaceTypes()
                : declaration instanceof RecordDeclaration record ? record.superInterfaceTypes()
                : List.of();
        for (Object object : source) {
            interfaces.add(String.valueOf(object));
        }
        return List.copyOf(interfaces);
    }

    private static List<String> methods(Object declaration) {
        List<String> methods = new ArrayList<>();
        for (MethodDeclaration method : methodDeclarations(declaration)) {
            if (method.isConstructor()) continue;
            methods.add(signature(method));
        }
        return List.copyOf(methods);
    }

    private static List<String> constructors(Object declaration) {
        List<String> constructors = new ArrayList<>();
        for (MethodDeclaration method : methodDeclarations(declaration)) {
            if (!method.isConstructor()) continue;
            constructors.add(signature(method));
        }
        return List.copyOf(constructors);
    }

    private static List<MethodDeclaration> methodDeclarations(Object declaration) {
        List<MethodDeclaration> methods = new ArrayList<>();
        List<?> body = declaration instanceof TypeDeclaration type ? type.bodyDeclarations()
                : declaration instanceof RecordDeclaration record ? record.bodyDeclarations()
                : declaration instanceof EnumDeclaration enumDeclaration ? enumDeclaration.bodyDeclarations()
                : declaration instanceof AnnotationTypeDeclaration annotation ? annotation.bodyDeclarations()
                : List.of();
        for (Object object : body) {
            if (object instanceof MethodDeclaration method) methods.add(method);
        }
        return methods;
    }

    private static String signature(MethodDeclaration method) {
        StringBuilder signature = new StringBuilder();
        for (Object modifier : method.modifiers()) {
            String value = String.valueOf(modifier).trim();
            if (!value.isEmpty() && !value.startsWith("@")) signature.append(value).append(' ');
        }
        if (!method.isConstructor()) signature.append(method.getReturnType2()).append(' ');
        signature.append(method.getName());
        signature.append('(');
        List<?> parameters = method.parameters();
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) signature.append(", ");
            signature.append(parameters.get(i));
        }
        signature.append(')');
        return signature.toString().replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }

    private static List<String> imports(CompilationUnit unit) {
        List<String> imports = new ArrayList<>();
        for (Object object : unit.imports()) {
            if (object instanceof ImportDeclaration declaration) imports.add(declaration.getName().getFullyQualifiedName());
        }
        return List.copyOf(imports);
    }

    private static void collectTypeReferences(CompilationUnit unit, Collection<String> references) {
        unit.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleType node) {
                references.add(node.getName().getFullyQualifiedName());
                return true;
            }

            @Override
            public boolean visit(QualifiedType node) {
                references.add(node.getName().getFullyQualifiedName());
                return true;
            }

            @Override
            public boolean visit(NameQualifiedType node) {
                references.add(node.getName().getFullyQualifiedName());
                return true;
            }
        });
    }

    private static boolean hasBindingProblems(CompilationUnit unit) {
        return Stream.of(unit.getProblems()).anyMatch(problem -> problem != null && problem.isError());
    }

    private static String[] stringArray(List<?> values) {
        String[] array = new String[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = String.valueOf(values.get(i));
        }
        return array;
    }

    private static List<String> tags(String source) {
        List<String> tags = new ArrayList<>();
        for (String line : source.split("\\R")) {
            String text = line.trim();
            if (!text.startsWith("@Tag(")) continue;
            int end = text.indexOf(')');
            if (end < "@Tag(".length()) continue;
            String value = text.substring("@Tag(".length(), end).replace("\"", "").replace("'", "").trim();
            if (!value.isBlank()) tags.add(value);
        }
        return List.copyOf(tags);
    }

    private static String inferTestedClass(SourceFacts sourceFacts, List<String> referencedProductionTypes) {
        if (!referencedProductionTypes.isEmpty()) return referencedProductionTypes.get(0);
        if (!sourceFacts.simpleName().endsWith("Test")) return "";
        String base = sourceFacts.simpleName().substring(0, sourceFacts.simpleName().length() - 4);
        return sourceFacts.packageName().isBlank() ? base : sourceFacts.packageName() + "." + base;
    }

    private static String modulePath(List<?> modules, String sourcePath) {
        for (Object object : modules) {
            if (!(object instanceof Map<?, ?> module)) continue;
            Object pathObject = module.get("path");
            String path = pathObject == null ? "" : String.valueOf(pathObject).replace('\\', '/');
            if (path.isBlank()) continue;
            if (sourcePath.startsWith(path + "/")) return path;
        }
        return "";
    }

    private static String sourceFolder(JavaKnowledgeRequest request, Path sourceFile) {
        for (Object rootObject : mergeRoots(request.sourceRoots(), request.testSourceRoots())) {
            if (!(rootObject instanceof Path root)) continue;
            if (!sourceFile.normalize().startsWith(root.normalize())) continue;
            return request.repositoryRoot().relativize(root).toString().replace('\\', '/');
        }
        return "";
    }

    private static Map<String, Object> typeFact(SourceFacts sourceFacts, String sourcePath, String sourceFolder, String modulePath) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("name", sourceFacts.typeName());
        fact.put("simpleName", sourceFacts.simpleName());
        fact.put("package", sourceFacts.packageName());
        fact.put("sourceFile", sourcePath);
        fact.put("sourceFolder", sourceFolder);
        fact.put("module", modulePath);
        fact.put("kind", sourceFacts.kind());
        fact.put("superclass", sourceFacts.superclassName());
        fact.put("interfaces", sourceFacts.interfaces());
        fact.put("test", sourceFacts.test());
        return fact;
    }

    private static List<Map<String, Object>> methodFacts(String typeName, List<String> methods, List<String> constructors) {
        List<Map<String, Object>> facts = new ArrayList<>();
        for (String constructor : constructors) {
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("type", typeName);
            fact.put("signature", constructor);
            fact.put("constructor", true);
            facts.add(fact);
        }
        for (String method : methods) {
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("type", typeName);
            fact.put("signature", method);
            facts.add(fact);
        }
        return facts;
    }

    private static Map<String, Object> packageFact(String pkg, String sourcePath, String sourceFolder, String modulePath) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("package", pkg);
        fact.put("sourceFile", sourcePath);
        fact.put("sourceFolder", sourceFolder);
        fact.put("module", modulePath);
        return fact;
    }

    private static List<Map<String, Object>> referenceFacts(String typeName, List<String> references, List<String> referencedBy) {
        List<Map<String, Object>> facts = new ArrayList<>();
        for (String reference : references) {
            SearchPattern pattern = SearchPattern.createPattern(reference, IJavaSearchConstants.TYPE, IJavaSearchConstants.REFERENCES, SearchPattern.R_EXACT_MATCH);
            if (pattern == null) continue;
            if ((pattern.getMatchRule() & SearchPattern.R_EXACT_MATCH) == 0) continue;
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("type", typeName);
            fact.put("reference", reference);
            fact.put("search", "jdt-type-reference-exact");
            facts.add(fact);
        }
        for (String reference : referencedBy) {
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("type", typeName);
            fact.put("referencedBy", reference);
            facts.add(fact);
        }
        return facts;
    }

    private static Map<String, Object> warning(String code, String message) {
        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("code", code);
        warning.put("message", message);
        return warning;
    }

    private record SourceFacts(
            String sourcePath,
            String typeName,
            String simpleName,
            String packageName,
            String kind,
            String superclassName,
            List<String> interfaces,
            List<String> methods,
            List<String> constructors,
            List<String> imports,
            List<String> references,
            int lineCount,
            boolean test,
            List<String> tags,
            List<Map<String, Object>> warnings) {
    }

    private record ProjectIndex(
            Map<String, SourceFacts> bySourcePath,
            Map<String, List<String>> implementationsByInterface,
            Map<String, List<String>> referencesByType,
            Map<String, List<String>> referencesToType,
            Map<String, Boolean> testTypes) {
    }
}
