package org.aiknowledge.core.javascript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.aiknowledge.core.analysis.BoundaryPath;
import org.aiknowledge.core.analysis.ComplexityModel;
import org.aiknowledge.core.sourcespi.SourceKnowledgeProvider;
import org.aiknowledge.core.sourcespi.SourceKnowledgeRequest;
import org.aiknowledge.core.sourcespi.SourceKnowledgeResult;

/**
 * Dependency-free JavaScript and TypeScript structural analysis.
 *
 * <p>The provider deliberately emits confidence and accuracy metadata. It can
 * later be complemented or replaced by a compiler-API or tree-sitter provider
 * without changing the language-neutral downstream model.</p>
 */
public final class JavaScriptTypeScriptKnowledgeProvider implements SourceKnowledgeProvider {
    public static final String PROVIDER_ID = "javascript-typescript-structural";

    private static final Set<String> EXTENSIONS =
            Set.of(".js", ".jsx", ".mjs", ".cjs", ".ts", ".tsx");
    private static final Set<String> CONTROL_NAMES =
            Set.of("if", "for", "while", "switch", "catch", "with", "function", "return", "throw", "new");

    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "(?m)^\\s*import\\s+(type\\s+)?(?:[^'\"\\n;]*?\\s+from\\s+)?(['\"])([^'\"]+)\\2");
    private static final Pattern EXPORT_FROM_PATTERN = Pattern.compile(
            "(?m)^\\s*export\\s+(type\\s+)?[^'\"\\n;]*?\\s+from\\s+(['\"])([^'\"]+)\\2");
    private static final Pattern REQUIRE_PATTERN = Pattern.compile(
            "\\brequire\\s*\\(\\s*(['\"])([^'\"]+)\\1\\s*\\)");
    private static final Pattern DYNAMIC_IMPORT_PATTERN = Pattern.compile(
            "\\bimport\\s*\\(\\s*(['\"])([^'\"]+)\\1\\s*\\)");
    private static final Pattern DECLARATION_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:export\\s+(?:default\\s+)?)?(?:declare\\s+)?(?:abstract\\s+)?"
                    + "(class|interface|type|enum)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern EXPORTED_NAME_PATTERN = Pattern.compile(
            "(?m)^\\s*export\\s+(?:default\\s+)?(?:declare\\s+)?(?:async\\s+)?(?:abstract\\s+)?"
                    + "(?:function|class|interface|type|enum|const|let|var)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern EXPORT_LIST_PATTERN =
            Pattern.compile("(?m)^\\s*export\\s*\\{([^}]*)}");

    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:export\\s+(?:default\\s+)?)?(?:async\\s+)?function\\s+"
                    + "([A-Za-z_$][\\w$]*)\\s*\\([^)]*\\)\\s*(?::[^\\{\\n]+)?\\{");
    private static final Pattern ARROW_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:export\\s+)?(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)"
                    + "\\s*(?::[^=\\n]+)?=\\s*(?:async\\s*)?(?:\\([^\\n]*?\\)|[A-Za-z_$][\\w$]*)"
                    + "\\s*(?::[^=\\n]+)?=>\\s*\\{");
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:(?:public|private|protected|static|async|readonly|abstract|override|get|set)\\s+)*"
                    + "([A-Za-z_$][\\w$]*)\\s*\\([^;{}\\n]*\\)\\s*(?::[^\\{\\n]+)?\\{");

    private static final Pattern FETCH_CALL = Pattern.compile("\\bfetch\\s*\\(");
    private static final Pattern AXIOS_METHOD_CALL = Pattern.compile(
            "\\baxios\\s*\\.\\s*(get|post|put|patch|delete|head|options)\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AXIOS_CONFIG_CALL = Pattern.compile("\\baxios\\s*\\(\\s*\\{");
    private static final Pattern STREAM_CALL = Pattern.compile(
            "\\bnew\\s+(WebSocket|EventSource)\\s*\\(");
    private static final Pattern GRAPHQL_OPERATION = Pattern.compile(
            "(?s)\\b(?:gql|graphql)\\s*`\\s*(query|mutation|subscription)\\s+([A-Za-z_][\\w]*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HTTP_METHOD_OPTION = Pattern.compile(
            "\\bmethod\\s*:\\s*['\"]([A-Za-z]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern AXIOS_URL_OPTION = Pattern.compile(
            "\\burl\\s*:\\s*([^,}]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATUS_INTERPRETATION = Pattern.compile(
            "\\b(status|statusCode|state|phase|code)\\b\\s*(?:===|==|!==|!=|<=|>=|<|>)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BACKEND_STATE_LITERAL = Pattern.compile(
            "\\b(?:status|statusCode|state|phase|code)\\b\\s*(?:===|==|!==|!=)\\s*['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPLEXITY_TOKEN = Pattern.compile(
            "\\b(?:if|else|for|while|do|catch|switch|case|default)\\b|&&|\\|\\||[{}?]");

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public boolean supports(String sourcePath) {
        String lower = sourcePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".d.ts")) return true;
        return EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public SourceKnowledgeResult extract(SourceKnowledgeRequest request) throws IOException {
        String source = Files.readString(request.sourceFile(), StandardCharsets.UTF_8);
        String commentsMasked = maskComments(source);
        String codeMasked = maskStringsAndComments(source);
        String language = language(request.sourcePath());
        boolean test = isTest(request.sourcePath());
        String module = moduleFor(request.modules(), request.sourcePath());
        String sourceUnitId = "source:" + request.sourcePath();
        String namespace = namespace(request.sourcePath());

        List<ImportReference> imports = imports(commentsMasked, source);
        Set<String> exports = exports(commentsMasked);
        List<CallableRange> callables = callables(
                codeMasked, source, sourceUnitId, language, request.sourcePath(), module);
        List<Map<String, Object>> symbols = declarations(
                codeMasked, request.sourcePath(), sourceUnitId, namespace, module, language, exports);
        for (CallableRange callable : callables) symbols.add(callable.fact());
        symbols.sort(Comparator.comparing(value -> String.valueOf(value.get("id"))));

        Map<String, Object> unit = new LinkedHashMap<>();
        unit.put("id", sourceUnitId);
        unit.put("name", request.sourcePath());
        unit.put("simpleName", sourceBaseName(request.sourcePath()));
        unit.put("class", request.sourcePath());
        unit.put("namespace", namespace);
        unit.put("package", namespace);
        unit.put("kind", test ? "test-module" : unitKind(request.sourcePath(), exports));
        unit.put("language", language);
        unit.put("sourceFile", request.sourcePath());
        unit.put("module", module);
        unit.put("lineCount", lineCount(source));
        unit.put("test", test);
        unit.put("imports", imports.stream().map(ImportReference::specifier).distinct().sorted().toList());
        unit.put("runtimeImports", imports.stream().filter(ImportReference::runtime)
                .map(ImportReference::specifier).distinct().sorted().toList());
        unit.put("typeOnlyImports", imports.stream().filter(reference -> !reference.runtime())
                .map(ImportReference::specifier).distinct().sorted().toList());
        unit.put("exports", exports.stream().sorted().toList());
        unit.put("methodFacts", callables.stream().map(CallableRange::fact).toList());
        unit.put("provider", id());
        unit.put("confidence", "syntactic-structural");

        List<Map<String, Object>> relations = importRelations(
                imports, sourceUnitId, request.sourcePath(), language);
        for (Map<String, Object> symbol : symbols) {
            relations.add(relation(
                    "SOURCE_UNIT_DECLARES_SYMBOL",
                    sourceUnitId,
                    String.valueOf(symbol.get("id")),
                    request.sourcePath(),
                    language));
        }

        List<Map<String, Object>> boundaries = boundaries(
                source,
                commentsMasked,
                request.sourcePath(),
                sourceUnitId,
                module,
                language,
                imports,
                callables,
                relations);

        List<Map<String, Object>> warnings = new ArrayList<>();
        if (request.sourcePath().toLowerCase(Locale.ROOT).endsWith(".d.ts")) {
            warnings.add(warning(request.sourcePath(),
                    "javascript-declaration-file",
                    "Declaration file analyzed as API structure; executable complexity is intentionally absent."));
        }
        if (commentsMasked.contains("eval(") || commentsMasked.contains("new Function(")) {
            warnings.add(warning(request.sourcePath(),
                    "javascript-dynamic-code",
                    "Dynamic code construction limits static dependency and call-boundary resolution."));
        }

        return new SourceKnowledgeResult(
                List.of(unit), symbols, relations, boundaries, warnings);
    }

    private static List<ImportReference> imports(String source, String original) {
        List<ImportReference> values = new ArrayList<>();
        collectImports(values, IMPORT_PATTERN, source, original, "import", false, 1, 3);
        collectImports(values, EXPORT_FROM_PATTERN, source, original, "re-export", false, 1, 3);

        Matcher require = REQUIRE_PATTERN.matcher(source);
        while (require.find()) {
            values.add(new ImportReference(
                    require.group(2), true, false, "require", lineAt(original, require.start())));
        }
        Matcher dynamic = DYNAMIC_IMPORT_PATTERN.matcher(source);
        while (dynamic.find()) {
            values.add(new ImportReference(
                    dynamic.group(2), true, true, "dynamic-import", lineAt(original, dynamic.start())));
        }
        values.sort(Comparator.comparing(ImportReference::specifier)
                .thenComparing(ImportReference::kind)
                .thenComparingInt(ImportReference::line));
        return List.copyOf(values);
    }

    private static void collectImports(
            List<ImportReference> target,
            Pattern pattern,
            String source,
            String original,
            String kind,
            boolean dynamic,
            int typeGroup,
            int specifierGroup) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            boolean typeOnly = matcher.group(typeGroup) != null;
            target.add(new ImportReference(
                    matcher.group(specifierGroup), !typeOnly, dynamic, kind, lineAt(original, matcher.start())));
        }
    }

    private static List<Map<String, Object>> importRelations(
            List<ImportReference> imports,
            String sourceUnitId,
            String sourcePath,
            String language) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ImportReference reference : imports) {
            Map<String, Object> relation = relation(
                    "SOURCE_UNIT_IMPORTS_MODULE",
                    sourceUnitId,
                    reference.specifier(),
                    sourcePath,
                    language);
            relation.put("line", reference.line());
            relation.put("importKind", reference.kind());
            relation.put("runtime", reference.runtime());
            relation.put("typeOnly", !reference.runtime());
            relation.put("dynamic", reference.dynamic());
            relation.put("external", isExternal(reference.specifier()));
            relation.put("packageName", packageName(reference.specifier()));
            result.add(relation);
        }
        return result;
    }

    private static List<Map<String, Object>> declarations(
            String masked,
            String sourcePath,
            String sourceUnitId,
            String namespace,
            String module,
            String language,
            Set<String> exports) {
        List<Map<String, Object>> result = new ArrayList<>();
        Matcher matcher = DECLARATION_PATTERN.matcher(masked);
        while (matcher.find()) {
            String kind = matcher.group(1).toLowerCase(Locale.ROOT);
            String name = matcher.group(2);
            Map<String, Object> symbol = new LinkedHashMap<>();
            symbol.put("id", sourceUnitId + "#" + name);
            symbol.put("name", name);
            symbol.put("qualifiedName", sourceUnitId + "#" + name);
            symbol.put("kind", kind);
            symbol.put("namespace", namespace);
            symbol.put("language", language);
            symbol.put("sourceFile", sourcePath);
            symbol.put("module", module);
            symbol.put("line", lineAt(masked, matcher.start(2)));
            symbol.put("exported", exports.contains(name));
            symbol.put("provider", PROVIDER_ID);
            symbol.put("confidence", "syntactic-structural");
            result.add(symbol);
        }
        return result;
    }

    private static List<CallableRange> callables(
            String masked,
            String source,
            String sourceUnitId,
            String language,
            String sourcePath,
            String module) {
        List<CallableRange> result = new ArrayList<>();
        Set<Integer> seenBodies = new LinkedHashSet<>();
        addCallables(FUNCTION_PATTERN, "function", masked, source, sourceUnitId,
                language, sourcePath, module, result, seenBodies);
        addCallables(ARROW_PATTERN, "arrow-function", masked, source, sourceUnitId,
                language, sourcePath, module, result, seenBodies);
        addCallables(METHOD_PATTERN, "method", masked, source, sourceUnitId,
                language, sourcePath, module, result, seenBodies);
        result.sort(Comparator.comparingInt(CallableRange::start));
        return result;
    }

    private static void addCallables(
            Pattern pattern,
            String callableKind,
            String masked,
            String source,
            String sourceUnitId,
            String language,
            String sourcePath,
            String module,
            List<CallableRange> target,
            Set<Integer> seenBodies) {
        Matcher matcher = pattern.matcher(masked);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (CONTROL_NAMES.contains(name)) continue;
            int bodyStart = masked.lastIndexOf('{', matcher.end() - 1);
            if (bodyStart < matcher.start() || !seenBodies.add(bodyStart)) continue;
            int bodyEnd = matchingBrace(masked, bodyStart);
            if (bodyEnd < 0) bodyEnd = source.length() - 1;
            Complexity complexity = complexity(masked.substring(
                    bodyStart, Math.min(masked.length(), bodyEnd + 1)));
            int line = lineAt(source, matcher.start(1));
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("id", sourceUnitId + "#" + name + "@" + line);
            fact.put("name", name);
            fact.put("qualifiedName", sourceUnitId + "#" + name);
            fact.put("kind", "callable");
            fact.put("callableKind", callableKind);
            fact.put("role", callableRole(name));
            fact.put("language", language);
            fact.put("sourceFile", sourcePath);
            fact.put("module", module);
            fact.put("line", line);
            fact.put("endLine", lineAt(source, bodyEnd));
            fact.put("lineCount", Math.max(1, lineAt(source, bodyEnd) - line + 1));
            fact.put("cyclomaticComplexity", complexity.cyclomatic());
            fact.put("cognitiveComplexity", complexity.cognitive());
            fact.put("maxNestingDepth", complexity.maxNesting());
            fact.put("decisionPointCount", complexity.decisionCount());
            fact.put("decisionPointsByKind", complexity.byKind());
            fact.put("complexityProvider", PROVIDER_ID);
            fact.put("complexityAccuracy", "token-structural");
            fact.put("complexityModel", ComplexityModel.ID);
            fact.put("provider", PROVIDER_ID);
            fact.put("confidence", "syntactic-structural");
            target.add(new CallableRange(matcher.start(), bodyStart, bodyEnd, fact));
        }
    }

    private static Complexity complexity(String body) {
        Matcher matcher = COMPLEXITY_TOKEN.matcher(body);
        int cyclomatic = 1;
        int cognitive = 0;
        int flowNesting = 0;
        int maxNesting = 0;
        int decisions = 0;
        boolean pendingFlow = false;
        String previousWord = "";
        Deque<Boolean> flowBlocks = new ArrayDeque<>();
        Map<String, Integer> byKind = new LinkedHashMap<>();
        while (matcher.find()) {
            String token = matcher.group();
            if ("{".equals(token)) {
                flowBlocks.push(pendingFlow);
                if (pendingFlow) {
                    flowNesting++;
                    maxNesting = Math.max(maxNesting, flowNesting);
                }
                pendingFlow = false;
                continue;
            }
            if ("}".equals(token)) {
                if (!flowBlocks.isEmpty() && flowBlocks.pop()) {
                    flowNesting = Math.max(0, flowNesting - 1);
                }
                pendingFlow = false;
                continue;
            }
            if ("&&".equals(token) || "||".equals(token)) {
                cyclomatic++;
                cognitive++;
                decisions++;
                increment(byKind, "booleanOperator");
                continue;
            }
            if ("?".equals(token)) {
                int end = matcher.end();
                int start = matcher.start();
                if ((end < body.length() && (body.charAt(end) == '.' || body.charAt(end) == '?'))
                        || (start > 0 && body.charAt(start - 1) == '?')) {
                    continue;
                }
                cyclomatic++;
                cognitive += 1 + flowNesting;
                decisions++;
                increment(byKind, "conditionalExpression");
                continue;
            }
            String word = token.toLowerCase(Locale.ROOT);
            if (Set.of("if", "for", "while", "do", "catch", "switch").contains(word)) {
                cyclomatic++;
                int effectiveNesting = "if".equals(word) && "else".equals(previousWord)
                        ? Math.max(0, flowNesting - 1)
                        : flowNesting;
                cognitive += 1 + effectiveNesting;
                decisions++;
                increment(byKind, word);
                pendingFlow = true;
            } else if ("case".equals(word) || "default".equals(word)) {
                cyclomatic++;
                decisions++;
                increment(byKind, "switchCase");
            }
            previousWord = word;
        }
        return new Complexity(cyclomatic, cognitive, maxNesting, decisions, Map.copyOf(byKind));
    }

    private static List<Map<String, Object>> boundaries(
            String source,
            String commentsMasked,
            String sourcePath,
            String sourceUnitId,
            String module,
            String language,
            List<ImportReference> imports,
            List<CallableRange> callables,
            List<Map<String, Object>> relations) {
        List<Map<String, Object>> result = new ArrayList<>();
        int sequence = 0;

        Matcher fetchMatcher = FETCH_CALL.matcher(commentsMasked);
        while (fetchMatcher.find()) {
            int open = commentsMasked.indexOf('(', fetchMatcher.start());
            Argument argument = firstArgument(source, open + 1);
            String callText = callText(source, open);
            String method = optionMethod(callText, "GET");
            result.add(clientCall(source, sourcePath, sourceUnitId, module, language,
                    imports, callables, fetchMatcher.start(), ++sequence,
                    "fetch", "http", method, argument.expression(), relations));
        }

        Matcher axiosMatcher = AXIOS_METHOD_CALL.matcher(commentsMasked);
        while (axiosMatcher.find()) {
            int open = commentsMasked.indexOf('(', axiosMatcher.start());
            Argument argument = firstArgument(source, open + 1);
            result.add(clientCall(source, sourcePath, sourceUnitId, module, language,
                    imports, callables, axiosMatcher.start(), ++sequence,
                    "axios", "http", axiosMatcher.group(1), argument.expression(), relations));
        }

        Matcher axiosConfig = AXIOS_CONFIG_CALL.matcher(commentsMasked);
        while (axiosConfig.find()) {
            int open = commentsMasked.indexOf('(', axiosConfig.start());
            String callText = callText(source, open);
            Matcher url = AXIOS_URL_OPTION.matcher(callText);
            if (!url.find()) continue;
            result.add(clientCall(source, sourcePath, sourceUnitId, module, language,
                    imports, callables, axiosConfig.start(), ++sequence,
                    "axios", "http", optionMethod(callText, "GET"), url.group(1), relations));
        }

        Matcher streams = STREAM_CALL.matcher(commentsMasked);
        while (streams.find()) {
            int open = commentsMasked.indexOf('(', streams.start());
            Argument argument = firstArgument(source, open + 1);
            boolean webSocket = "WebSocket".equals(streams.group(1));
            result.add(clientCall(source, sourcePath, sourceUnitId, module, language,
                    imports, callables, streams.start(), ++sequence,
                    streams.group(1), webSocket ? "websocket" : "server-sent-events",
                    webSocket ? "CONNECT" : "SUBSCRIBE", argument.expression(), relations));
        }

        Matcher graphql = GRAPHQL_OPERATION.matcher(commentsMasked);
        while (graphql.find()) {
            result.add(clientCall(source, sourcePath, sourceUnitId, module, language,
                    imports, callables, graphql.start(), ++sequence,
                    "graphql", "graphql", graphql.group(1), quote(graphql.group(2)), relations));
        }

        result.sort(Comparator
                .comparing((Map<String, Object> value) -> String.valueOf(value.get("sourceFile")))
                .thenComparingInt(value -> ((Number) value.getOrDefault("line", 0)).intValue())
                .thenComparing(value -> String.valueOf(value.get("id"))));
        return result;
    }

    private static Map<String, Object> clientCall(
            String source,
            String sourcePath,
            String sourceUnitId,
            String module,
            String language,
            List<ImportReference> imports,
            List<CallableRange> callables,
            int offset,
            int sequence,
            String client,
            String protocol,
            String method,
            String pathExpression,
            List<Map<String, Object>> relations) {
        CallableRange owner = owner(callables, offset);
        String callableId = owner == null
                ? sourceUnitId + "#<module>"
                : String.valueOf(owner.fact().get("id"));
        String body = owner == null
                ? source
                : source.substring(owner.bodyStart(), Math.min(source.length(), owner.bodyEnd() + 1));
        PathExpression path = pathExpression(pathExpression);
        String normalized = "graphql".equals(protocol)
                ? "/" + path.value()
                : BoundaryPath.normalize(path.value());
        String normalizedMethod = BoundaryPath.normalizeMethod(method);
        String id = "client:" + sourcePath + ":" + lineAt(source, offset) + ":" + sequence;

        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("id", id);
        fact.put("kind", "client-call");
        fact.put("protocol", protocol);
        fact.put("method", normalizedMethod);
        fact.put("path", path.value());
        fact.put("normalizedPath", normalized);
        fact.put("pathExpressionKind", path.kind());
        fact.put("literalPath", path.literal());
        fact.put("client", client);
        fact.put("callable", callableId);
        fact.put("sourceUnit", sourceUnitId);
        fact.put("sourceFile", sourcePath);
        fact.put("module", module);
        fact.put("language", language);
        fact.put("line", lineAt(source, offset));
        fact.put("awaited", precededByAwait(source, offset));
        fact.put("errorHandling", errorHandling(body));
        fact.put("errorBranchCount", errorBranchCount(body));
        fact.put("modelTransformationCount", modelTransformationCount(body));
        fact.put("backendStateInterpretationCount", count(STATUS_INTERPRETATION, body));
        fact.put("backendStateLiterals", backendStateLiterals(body));
        fact.put("contractSource", generatedClient(imports)
                ? "generated-client"
                : "handwritten-client");
        fact.put("provider", PROVIDER_ID);
        fact.put("confidence", path.literal() ? "syntactic-structural" : "low");

        relations.add(relation(
                "CALLABLE_CALLS_BOUNDARY",
                callableId,
                normalizedMethod + " " + normalized,
                sourcePath,
                language));
        return fact;
    }

    private static PathExpression pathExpression(String expression) {
        if (expression == null) return new PathExpression(BoundaryPath.DYNAMIC, "dynamic", false);
        String value = expression.trim();
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return new PathExpression(value.substring(1, value.length() - 1), "literal", true);
        }
        if (value.length() >= 2 && value.startsWith("`") && value.endsWith("`")) {
            String template = value.substring(1, value.length() - 1)
                    .replaceAll("\\$\\{[^}]+}", "{}");
            return new PathExpression(template, "template", true);
        }
        return new PathExpression(BoundaryPath.DYNAMIC, "dynamic", false);
    }

    private static Argument firstArgument(String source, int start) {
        int position = start;
        int paren = 0;
        int brace = 0;
        int bracket = 0;
        char quote = 0;
        boolean escaped = false;
        for (; position < source.length(); position++) {
            char current = source.charAt(position);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                quote = current;
                continue;
            }
            if (current == '(') paren++;
            else if (current == ')') {
                if (paren == 0 && brace == 0 && bracket == 0) break;
                paren--;
            } else if (current == '{') brace++;
            else if (current == '}') brace--;
            else if (current == '[') bracket++;
            else if (current == ']') bracket--;
            else if (current == ',' && paren == 0 && brace == 0 && bracket == 0) break;
        }
        return new Argument(source.substring(Math.min(start, source.length()), Math.min(position, source.length())).trim(), position);
    }

    private static String callText(String source, int openParen) {
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int index = openParen; index < source.length(); index++) {
            char current = source.charAt(index);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == quote) quote = 0;
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                quote = current;
                continue;
            }
            if (current == '(') depth++;
            else if (current == ')' && --depth == 0) {
                return source.substring(openParen, index + 1);
            }
        }
        return source.substring(openParen, Math.min(source.length(), openParen + 2000));
    }

    private static String optionMethod(String value, String fallback) {
        Matcher matcher = HTTP_METHOD_OPTION.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static CallableRange owner(List<CallableRange> callables, int offset) {
        CallableRange selected = null;
        for (CallableRange candidate : callables) {
            if (offset < candidate.start() || offset > candidate.bodyEnd()) continue;
            if (selected == null
                    || candidate.bodyEnd() - candidate.start() < selected.bodyEnd() - selected.start()) {
                selected = candidate;
            }
        }
        return selected;
    }

    private static boolean precededByAwait(String source, int offset) {
        int start = Math.max(0, offset - 80);
        return source.substring(start, offset).matches("(?s).*\\bawait\\s*$");
    }

    private static String errorHandling(String body) {
        boolean catches = Pattern.compile("\\bcatch\\b|\\.catch\\s*\\(").matcher(body).find();
        boolean status = STATUS_INTERPRETATION.matcher(body).find()
                || Pattern.compile("\\.ok\\b").matcher(body).find();
        if (catches && status) return "catch-and-status";
        if (catches) return "catch";
        if (status) return "status";
        return "none";
    }

    private static int errorBranchCount(String body) {
        return count(Pattern.compile("\\bcatch\\b|\\.catch\\s*\\("), body)
                + count(STATUS_INTERPRETATION, body)
                + count(Pattern.compile("!\\s*[A-Za-z_$][\\w$]*\\.ok\\b"), body);
    }

    private static int modelTransformationCount(String body) {
        int result = 0;
        if (Pattern.compile("\\.(?:map|reduce|flatMap|filter)\\s*\\(").matcher(body).find()) result++;
        if (Pattern.compile("\\breturn\\s*\\{").matcher(body).find()) result++;
        if (Pattern.compile(
                "\\b(?:fromDto|toViewModel|mapResponse|normalizeResponse|deserialize)\\b",
                Pattern.CASE_INSENSITIVE).matcher(body).find()) result++;
        return result;
    }

    private static List<String> backendStateLiterals(String body) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Matcher matcher = BACKEND_STATE_LITERAL.matcher(body);
        while (matcher.find()) result.add(matcher.group(1));
        return List.copyOf(result);
    }

    private static boolean generatedClient(List<ImportReference> imports) {
        return imports.stream()
                .map(ImportReference::specifier)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains("generated")
                        || value.contains("openapi")
                        || value.contains("api-client"));
    }

    private static Set<String> exports(String source) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Matcher named = EXPORTED_NAME_PATTERN.matcher(source);
        while (named.find()) result.add(named.group(1));
        Matcher listed = EXPORT_LIST_PATTERN.matcher(source);
        while (listed.find()) {
            for (String part : listed.group(1).split(",")) {
                String value = part.trim();
                if (value.isEmpty()) continue;
                String[] alias = value.split("\\s+as\\s+");
                result.add(alias[alias.length - 1].trim());
            }
        }
        return result;
    }

    private static String maskComments(String source) {
        StringBuilder result = new StringBuilder(source.length());
        boolean lineComment = false;
        boolean blockComment = false;
        boolean string = false;
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : 0;
            if (lineComment) {
                if (current == '\n') {
                    lineComment = false;
                    result.append('\n');
                } else result.append(' ');
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    result.append("  ");
                    index++;
                    blockComment = false;
                } else result.append(current == '\n' ? '\n' : ' ');
                continue;
            }
            if (string) {
                result.append(current);
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == quote) string = false;
                continue;
            }
            if (current == '/' && next == '/') {
                result.append("  ");
                index++;
                lineComment = true;
            } else if (current == '/' && next == '*') {
                result.append("  ");
                index++;
                blockComment = true;
            } else {
                result.append(current);
                if (current == '\'' || current == '"' || current == '`') {
                    string = true;
                    quote = current;
                }
            }
        }
        return result.toString();
    }

    private static String maskStringsAndComments(String source) {
        String commentsMasked = maskComments(source);
        StringBuilder result = new StringBuilder(commentsMasked.length());
        boolean string = false;
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < commentsMasked.length(); index++) {
            char current = commentsMasked.charAt(index);
            if (string) {
                result.append(current == '\n' ? '\n' : ' ');
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == quote) string = false;
            } else if (current == '\'' || current == '"' || current == '`') {
                string = true;
                quote = current;
                result.append(' ');
            } else result.append(current);
        }
        return result.toString();
    }

    private static int matchingBrace(String source, int start) {
        int depth = 0;
        for (int index = start; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return index;
        }
        return -1;
    }

    private static Map<String, Object> relation(
            String kind,
            String source,
            String target,
            String sourceFile,
            String language) {
        Map<String, Object> relation = new LinkedHashMap<>();
        relation.put("kind", kind);
        relation.put("source", source);
        relation.put("target", target);
        relation.put("sourceFile", sourceFile);
        relation.put("language", language);
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

    private static boolean isExternal(String specifier) {
        return !(specifier.startsWith(".") || specifier.startsWith("/") || specifier.startsWith("#"));
    }

    private static String packageName(String specifier) {
        if (!isExternal(specifier)) return "";
        String value = specifier.startsWith("node:") ? specifier.substring("node:".length()) : specifier;
        if (value.startsWith("@")) {
            String[] parts = value.split("/");
            return parts.length >= 2 ? parts[0] + "/" + parts[1] : value;
        }
        int slash = value.indexOf('/');
        return slash < 0 ? value : value.substring(0, slash);
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

    private static String sourceBaseName(String sourcePath) {
        String normalized = sourcePath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String fileName = slash < 0 ? normalized : normalized.substring(slash + 1);
        if (fileName.endsWith(".d.ts")) return fileName.substring(0, fileName.length() - 5);
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    private static String namespace(String sourcePath) {
        String normalized = sourcePath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? "" : normalized.substring(0, slash).replace('/', '.');
    }

    private static String language(String sourcePath) {
        String lower = sourcePath.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ts") || lower.endsWith(".tsx") || lower.endsWith(".d.ts")
                ? "typescript"
                : "javascript";
    }

    private static boolean isTest(String sourcePath) {
        String normalized = sourcePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/__tests__/")
                || normalized.contains("/test/")
                || normalized.contains("/tests/")
                || normalized.matches(".*\\.(?:test|spec)\\.[cm]?[jt]sx?$");
    }

    private static String unitKind(String sourcePath, Set<String> exports) {
        String lower = sourcePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".tsx") || lower.endsWith(".jsx")) return "ui-module";
        if (lower.contains("service") || lower.contains("client") || lower.contains("api")) {
            return "service-module";
        }
        return exports.isEmpty() ? "module" : "library-module";
    }

    private static String callableRole(String name) {
        if (name.startsWith("use") && name.length() > 3 && Character.isUpperCase(name.charAt(3))) {
            return "hook";
        }
        if (!name.isEmpty() && Character.isUpperCase(name.charAt(0))) {
            return "component-or-constructor";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("fetch") || lower.contains("load") || lower.contains("request")) {
            return "boundary-orchestration";
        }
        return "callable";
    }

    private static int count(Pattern pattern, String value) {
        int result = 0;
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) result++;
        return result;
    }

    private static void increment(Map<String, Integer> values, String key) {
        values.put(key, values.getOrDefault(key, 0) + 1);
    }

    private static int lineAt(String source, int offset) {
        int line = 1;
        int end = Math.max(0, Math.min(offset, source.length()));
        for (int index = 0; index < end; index++) {
            if (source.charAt(index) == '\n') line++;
        }
        return line;
    }

    private static int lineCount(String source) {
        return source.isEmpty() ? 0 : lineAt(source, source.length());
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record ImportReference(
            String specifier,
            boolean runtime,
            boolean dynamic,
            String kind,
            int line) {
    }

    private record CallableRange(
            int start,
            int bodyStart,
            int bodyEnd,
            Map<String, Object> fact) {
    }

    private record Complexity(
            int cyclomatic,
            int cognitive,
            int maxNesting,
            int decisionCount,
            Map<String, Integer> byKind) {
    }

    private record Argument(String expression, int end) {
    }

    private record PathExpression(String value, String kind, boolean literal) {
    }
}
