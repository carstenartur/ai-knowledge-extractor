package org.aiknowledge.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.aiknowledge.core.javaspi.JavaKnowledgeRequest;
import org.aiknowledge.core.javaspi.JavaKnowledgeResult;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.WhileStatement;

/** Adds JDT AST based McCabe and Cognitive Complexity metrics to Java method facts. */
final class CodeComplexityAnalyzer {
    JavaKnowledgeResult enrich(JavaKnowledgeRequest request, JavaKnowledgeResult result) throws IOException {
        if (!request.sourcePath().endsWith(".java")) return result;
        List<Map<String, Object>> complexityFacts = methodComplexityFacts(request);
        if (complexityFacts.isEmpty()) return result;
        return new JavaKnowledgeResult(
                result.typeFacts(),
                mergeMethodFacts(result.methodFacts(), complexityFacts),
                result.fieldFacts(),
                result.testFacts(),
                result.packageFacts(),
                result.referenceFacts(),
                result.relationFacts(),
                result.classFacts(),
                result.warnings());
    }

    private static List<Map<String, Object>> mergeMethodFacts(List<?> existing, List<Map<String, Object>> complexityFacts) {
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (Object item : existing) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> copy = copyOf(map);
            byKey.put(methodKey(copy), copy);
        }
        for (Map<String, Object> complexity : complexityFacts) {
            String key = methodKey(complexity);
            Map<String, Object> target = byKey.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            target.putAll(complexity);
        }
        return new ArrayList<>(byKey.values());
    }

    private static List<Map<String, Object>> methodComplexityFacts(JavaKnowledgeRequest request) throws IOException {
        String source = Files.readString(request.sourceFile(), StandardCharsets.UTF_8);
        CompilationUnit unit = parse(source);
        List<?> types = unit.types();
        if (types.isEmpty()) return List.of();
        String packageName = unit.getPackage() == null ? "" : unit.getPackage().getName().getFullyQualifiedName();
        String sourcePath = request.sourcePath();
        List<Map<String, Object>> facts = new ArrayList<>();
        for (Object typeObj : types) {
            String simpleName = simpleName(typeObj);
            if (simpleName.isBlank()) continue;
            String typeName = packageName.isBlank() ? simpleName : packageName + "." + simpleName;
            for (MethodDeclaration method : methodDeclarations(typeObj)) {
                facts.add(analyzeMethod(unit, typeName, sourcePath, method));
            }
        }
        return List.copyOf(facts);
    }

    private static CompilationUnit parse(String source) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        Map<String, String> options = new LinkedHashMap<>(JavaCore.getOptions());
        JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
        parser.setCompilerOptions(options);
        return (CompilationUnit) parser.createAST(null);
    }

    private static Map<String, Object> analyzeMethod(
            CompilationUnit unit, String typeName, String sourceFile, MethodDeclaration method) {
        ComplexityVisitor visitor = new ComplexityVisitor();
        if (method.getBody() != null) method.getBody().accept(visitor);
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("type", typeName);
        fact.put("signature", signature(method));
        if (method.isConstructor()) fact.put("constructor", true);
        fact.put("sourceFile", sourceFile);
        int startLine = unit.getLineNumber(method.getStartPosition());
        int endLine = unit.getLineNumber(method.getStartPosition() + Math.max(0, method.getLength() - 1));
        if (startLine > 0) fact.put("startLine", startLine);
        if (endLine > 0) fact.put("endLine", endLine);
        if (startLine > 0 && endLine >= startLine) fact.put("lineCount", endLine - startLine + 1);
        fact.put("cyclomaticComplexity", visitor.cyclomaticComplexity());
        fact.put("cognitiveComplexity", visitor.cognitiveComplexity());
        fact.put("maxNestingDepth", visitor.maxNestingDepth());
        fact.put("decisionPointCount", visitor.decisionPointCount());
        fact.put("decisionPointsByKind", visitor.decisionPointsByKind());
        fact.put("complexityProvider", "jdt-ast");
        fact.put("complexityAccuracy", "ast");
        return fact;
    }

    private static String methodKey(Map<String, Object> fact) {
        String sig = String.valueOf(fact.getOrDefault("signature", ""));
        int parenIndex = sig.indexOf('(');
        String normalizedSig = parenIndex >= 0 ? sig.substring(0, parenIndex).trim() : sig;
        return String.valueOf(fact.getOrDefault("type", "")) + "#" + normalizedSig;
    }

    private static Map<String, Object> copyOf(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    private static String simpleName(Object declaration) {
        if (declaration instanceof TypeDeclaration type) return type.getName().getIdentifier();
        if (declaration instanceof RecordDeclaration record) return record.getName().getIdentifier();
        if (declaration instanceof EnumDeclaration enumDeclaration) return enumDeclaration.getName().getIdentifier();
        if (declaration instanceof AnnotationTypeDeclaration annotation) return annotation.getName().getIdentifier();
        return "";
    }

    private static List<MethodDeclaration> methodDeclarations(Object declaration) {
        List<MethodDeclaration> methods = new ArrayList<>();
        for (Object object : bodyDeclarations(declaration)) {
            if (object instanceof MethodDeclaration method) methods.add(method);
        }
        return methods;
    }

    private static List<?> bodyDeclarations(Object declaration) {
        if (declaration instanceof TypeDeclaration type) return type.bodyDeclarations();
        if (declaration instanceof RecordDeclaration record) return record.bodyDeclarations();
        if (declaration instanceof EnumDeclaration enumDeclaration) return enumDeclaration.bodyDeclarations();
        if (declaration instanceof AnnotationTypeDeclaration annotation) return annotation.bodyDeclarations();
        return List.of();
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

    private static final class ComplexityVisitor extends ASTVisitor {
        private int nesting;
        private int maxNestingDepth;
        private int cyclomaticComplexity = 1;
        private int cognitiveComplexity;
        private int decisionPointCount;
        private final Map<String, Integer> decisionPointsByKind = new LinkedHashMap<>();

        int cyclomaticComplexity() { return cyclomaticComplexity; }
        int cognitiveComplexity() { return cognitiveComplexity; }
        int maxNestingDepth() { return maxNestingDepth; }
        int decisionPointCount() { return decisionPointCount; }
        Map<String, Integer> decisionPointsByKind() { return new LinkedHashMap<>(decisionPointsByKind); }

        @Override
        public boolean visit(IfStatement node) {
            addDecision("if", cognitiveNestingForIf(node));
            enterNestedFlow();
            return true;
        }

        @Override
        public void endVisit(IfStatement node) { exitNestedFlow(); }

        @Override
        public boolean visit(ForStatement node) { addNestedDecision("loop"); return true; }
        @Override
        public void endVisit(ForStatement node) { exitNestedFlow(); }

        @Override
        public boolean visit(EnhancedForStatement node) { addNestedDecision("loop"); return true; }
        @Override
        public void endVisit(EnhancedForStatement node) { exitNestedFlow(); }

        @Override
        public boolean visit(WhileStatement node) { addNestedDecision("loop"); return true; }
        @Override
        public void endVisit(WhileStatement node) { exitNestedFlow(); }

        @Override
        public boolean visit(DoStatement node) { addNestedDecision("loop"); return true; }
        @Override
        public void endVisit(DoStatement node) { exitNestedFlow(); }

        @Override
        public boolean visit(CatchClause node) { addNestedDecision("catch"); return true; }
        @Override
        public void endVisit(CatchClause node) { exitNestedFlow(); }

        @Override
        public boolean visit(SwitchStatement node) { addNestedDecision("switch"); return true; }
        @Override
        public void endVisit(SwitchStatement node) { exitNestedFlow(); }

        @Override
        public boolean visit(SwitchCase node) { addCyclomaticOnly("switchCase"); return true; }

        @Override
        public boolean visit(ConditionalExpression node) { addNestedDecision("conditionalExpression"); return true; }
        @Override
        public void endVisit(ConditionalExpression node) { exitNestedFlow(); }

        @Override
        public boolean visit(InfixExpression node) {
            if (isBooleanDecision(node.getOperator())) {
                addCyclomaticOnly("booleanOperator");
                cognitiveComplexity++;
            }
            return true;
        }

        private int cognitiveNestingForIf(IfStatement node) {
            ASTNode parent = node.getParent();
            if (parent instanceof IfStatement ifParent && ifParent.getElseStatement() == node) {
                return Math.max(0, nesting - 1);
            }
            return nesting;
        }

        private void addNestedDecision(String kind) {
            addDecision(kind, nesting);
            enterNestedFlow();
        }

        private void addDecision(String kind, int nestingPenalty) {
            addCyclomaticOnly(kind);
            cognitiveComplexity += 1 + Math.max(0, nestingPenalty);
        }

        private void addCyclomaticOnly(String kind) {
            cyclomaticComplexity++;
            decisionPointCount++;
            decisionPointsByKind.put(kind, decisionPointsByKind.getOrDefault(kind, 0) + 1);
        }

        private void enterNestedFlow() {
            nesting++;
            maxNestingDepth = Math.max(maxNestingDepth, nesting);
        }

        private void exitNestedFlow() {
            nesting = Math.max(0, nesting - 1);
        }

        private static boolean isBooleanDecision(InfixExpression.Operator operator) {
            return InfixExpression.Operator.CONDITIONAL_AND.equals(operator)
                    || InfixExpression.Operator.CONDITIONAL_OR.equals(operator);
        }
    }
}
