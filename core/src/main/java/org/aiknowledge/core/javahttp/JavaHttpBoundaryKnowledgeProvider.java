package org.aiknowledge.core.javahttp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.aiknowledge.core.analysis.BoundaryPath;
import org.aiknowledge.core.sourcespi.SourceKnowledgeProvider;
import org.aiknowledge.core.sourcespi.SourceKnowledgeRequest;
import org.aiknowledge.core.sourcespi.SourceKnowledgeResult;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;

/** JDT-backed extraction of Spring and JAX-RS HTTP endpoint contracts. */
public final class JavaHttpBoundaryKnowledgeProvider implements SourceKnowledgeProvider {
    public static final String PROVIDER_ID = "java-jdt-http-boundary";

    private static final Map<String, String> SPRING_METHODS = Map.of(
            "GetMapping", "GET",
            "PostMapping", "POST",
            "PutMapping", "PUT",
            "PatchMapping", "PATCH",
            "DeleteMapping", "DELETE");
    private static final Set<String> JAX_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final Pattern REQUEST_METHOD =
            Pattern.compile("RequestMethod\\.([A-Z]+)");

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public boolean supports(String sourcePath) {
        return sourcePath.toLowerCase(Locale.ROOT).endsWith(".java");
    }

    @Override
    public int priority() {
        return 90;
    }

    @Override
    @SuppressWarnings("deprecation")
    public SourceKnowledgeResult extract(SourceKnowledgeRequest request) throws IOException {
        String source = Files.readString(request.sourceFile(), StandardCharsets.UTF_8);
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setResolveBindings(false);
        Map<String, String> options = new LinkedHashMap<>(JavaCore.getOptions());
        JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
        parser.setCompilerOptions(options);
        CompilationUnit unit = (CompilationUnit) parser.createAST(null);

        String packageName = unit.getPackage() == null
                ? ""
                : unit.getPackage().getName().getFullyQualifiedName();
        String module = moduleFor(request.modules(), request.sourcePath());
        List<Map<String, Object>> boundaries = new ArrayList<>();
        List<Map<String, Object>> relations = new ArrayList<>();
        for (Object declaration : unit.types()) {
            if (declaration instanceof AbstractTypeDeclaration type) {
                analyzeType(type, packageName, "", request.sourcePath(), module,
                        unit, boundaries, relations);
            }
        }

        List<Map<String, Object>> warnings = new ArrayList<>();
        if (source.contains("RouterFunctions")
                || source.contains("RequestPredicates")
                || source.contains(".route(")) {
            warnings.add(warning(request.sourcePath(),
                    "java-functional-route-unresolved",
                    "Functional Java routes were detected; annotation extraction does not resolve their full route graph."));
        }
        return new SourceKnowledgeResult(
                List.of(), List.of(), relations, boundaries, warnings);
    }

    private static void analyzeType(
            AbstractTypeDeclaration type,
            String packageName,
            String enclosingName,
            String sourcePath,
            String module,
            CompilationUnit unit,
            List<Map<String, Object>> boundaries,
            List<Map<String, Object>> relations) {
        String simpleName = type.getName().getIdentifier();
        String localName = enclosingName.isEmpty()
                ? simpleName
                : enclosingName + "." + simpleName;
        String qualifiedName = packageName.isEmpty()
                ? localName
                : packageName + "." + localName;
        Map<String, List<PathValue>> basePaths = basePaths(type.modifiers());

        for (Object declaration : type.bodyDeclarations()) {
            if (declaration instanceof MethodDeclaration method) {
                for (Mapping mapping : methodMappings(method.modifiers())) {
                    List<PathValue> bases = basePaths.getOrDefault(
                            mapping.framework(), List.of(new PathValue("", true)));
                    for (PathValue base : bases) {
                        String path = combine(base.value(), mapping.path());
                        boolean literal = base.literal() && mapping.literalPath();
                        String normalized = literal
                                ? BoundaryPath.normalize(path)
                                : BoundaryPath.DYNAMIC;
                        String methodName = method.getName().getIdentifier();
                        String callable = qualifiedName + "#" + methodName;
                        int line = Math.max(1, unit.getLineNumber(method.getStartPosition()));
                        String httpMethod = BoundaryPath.normalizeMethod(mapping.method());
                        String endpointId = "endpoint:" + httpMethod + ":" + normalized
                                + ":" + qualifiedName + "#" + methodName;

                        Map<String, Object> fact = new LinkedHashMap<>();
                        fact.put("id", endpointId);
                        fact.put("kind", "server-endpoint");
                        fact.put("protocol", "http");
                        fact.put("method", httpMethod);
                        fact.put("path", path);
                        fact.put("normalizedPath", normalized);
                        fact.put("literalPath", literal);
                        fact.put("framework", mapping.framework());
                        fact.put("declaringType", qualifiedName);
                        fact.put("callable", callable);
                        fact.put("sourceFile", sourcePath);
                        fact.put("module", module);
                        fact.put("language", "java");
                        fact.put("line", line);
                        fact.put("provider", PROVIDER_ID);
                        fact.put("confidence", literal ? "jdt-syntactic" : "partial-expression");
                        boundaries.add(fact);

                        Map<String, Object> relation = relation(
                                "CALLABLE_EXPOSES_BOUNDARY",
                                callable,
                                httpMethod + " " + normalized,
                                sourcePath);
                        relations.add(relation);
                    }
                }
            } else if (declaration instanceof AbstractTypeDeclaration nested) {
                analyzeType(nested, packageName, localName, sourcePath, module,
                        unit, boundaries, relations);
            }
        }
    }

    private static Map<String, List<PathValue>> basePaths(List<?> modifiers) {
        Map<String, List<PathValue>> result = new LinkedHashMap<>();
        for (Object modifier : modifiers) {
            if (!(modifier instanceof Annotation annotation)) continue;
            String name = simpleName(annotation);
            if ("RequestMapping".equals(name)) {
                result.put("spring", defaultPath(paths(annotation)));
            } else if ("Path".equals(name)) {
                result.put("jax-rs", defaultPath(paths(annotation)));
            }
        }
        return result;
    }

    private static List<Mapping> methodMappings(List<?> modifiers) {
        List<Mapping> result = new ArrayList<>();
        List<String> jaxMethods = new ArrayList<>();
        List<PathValue> jaxPaths = List.of(new PathValue("", true));
        for (Object modifier : modifiers) {
            if (!(modifier instanceof Annotation annotation)) continue;
            String name = simpleName(annotation);
            String springMethod = SPRING_METHODS.get(name);
            if (springMethod != null) {
                for (PathValue path : defaultPath(paths(annotation))) {
                    result.add(new Mapping("spring", springMethod, path.value(), path.literal()));
                }
            } else if ("RequestMapping".equals(name)) {
                List<String> methods = requestMethods(annotation);
                if (methods.isEmpty()) methods = List.of("*");
                for (String method : methods) {
                    for (PathValue path : defaultPath(paths(annotation))) {
                        result.add(new Mapping("spring", method, path.value(), path.literal()));
                    }
                }
            } else if (JAX_METHODS.contains(name.toUpperCase(Locale.ROOT))) {
                jaxMethods.add(name.toUpperCase(Locale.ROOT));
            } else if ("Path".equals(name)) {
                jaxPaths = defaultPath(paths(annotation));
            }
        }
        for (String method : jaxMethods) {
            for (PathValue path : jaxPaths) {
                result.add(new Mapping("jax-rs", method, path.value(), path.literal()));
            }
        }
        return result;
    }

    private static List<String> requestMethods(Annotation annotation) {
        Expression expression = member(annotation, "method");
        if (expression == null) return List.of();
        LinkedHashSet<String> methods = new LinkedHashSet<>();
        Matcher matcher = REQUEST_METHOD.matcher(expression.toString());
        while (matcher.find()) methods.add(matcher.group(1));
        return List.copyOf(methods);
    }

    private static List<PathValue> paths(Annotation annotation) {
        Expression expression = member(annotation, "path");
        if (expression == null) expression = member(annotation, "value");
        if (expression == null && annotation instanceof SingleMemberAnnotation single) {
            expression = single.getValue();
        }
        if (expression == null) return List.of();
        List<PathValue> result = new ArrayList<>();
        collectPaths(expression, result);
        return List.copyOf(result);
    }

    private static void collectPaths(Expression expression, List<PathValue> target) {
        if (expression instanceof StringLiteral literal) {
            target.add(new PathValue(literal.getLiteralValue(), true));
        } else if (expression instanceof ArrayInitializer array) {
            for (Object value : array.expressions()) {
                if (value instanceof Expression nested) collectPaths(nested, target);
            }
        } else {
            target.add(new PathValue(expression.toString(), false));
        }
    }

    private static Expression member(Annotation annotation, String name) {
        if (annotation instanceof SingleMemberAnnotation single && "value".equals(name)) {
            return single.getValue();
        }
        if (annotation instanceof NormalAnnotation normal) {
            for (Object value : normal.values()) {
                MemberValuePair pair = (MemberValuePair) value;
                if (name.equals(pair.getName().getIdentifier())) return pair.getValue();
            }
        }
        if (annotation instanceof MarkerAnnotation) return null;
        return null;
    }

    private static List<PathValue> defaultPath(List<PathValue> values) {
        return values.isEmpty() ? List.of(new PathValue("", true)) : values;
    }

    private static String simpleName(Annotation annotation) {
        String name = annotation.getTypeName().getFullyQualifiedName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    private static String combine(String base, String path) {
        String left = base == null ? "" : base.trim();
        String right = path == null ? "" : path.trim();
        if (left.isEmpty() && right.isEmpty()) return "/";
        String result = ("/" + left + "/" + right).replaceAll("/{2,}", "/");
        return result.length() > 1 && result.endsWith("/")
                ? result.substring(0, result.length() - 1)
                : result;
    }

    private static String moduleFor(List<?> modules, String sourcePath) {
        String best = "";
        int bestLength = -1;
        for (Object value : modules) {
            if (!(value instanceof Map<?, ?> module)) continue;
            String path = text(module.get("path")).replace('\\', '/');
            String prefix = path.isBlank() ? "" : path + "/";
            if (sourcePath.startsWith(prefix) && prefix.length() > bestLength) {
                best = text(module.get("name"));
                bestLength = prefix.length();
            }
        }
        return best;
    }

    private static Map<String, Object> relation(
            String kind, String source, String target, String sourceFile) {
        Map<String, Object> relation = new LinkedHashMap<>();
        relation.put("kind", kind);
        relation.put("source", source);
        relation.put("target", target);
        relation.put("sourceFile", sourceFile);
        relation.put("language", "java");
        relation.put("provider", PROVIDER_ID);
        return relation;
    }

    private static Map<String, Object> warning(String sourceFile, String code, String message) {
        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("code", code);
        warning.put("message", message);
        warning.put("sourceFile", sourceFile);
        warning.put("provider", PROVIDER_ID);
        return warning;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record Mapping(
            String framework,
            String method,
            String path,
            boolean literalPath) {
    }

    private record PathValue(String value, boolean literal) {
    }
}
