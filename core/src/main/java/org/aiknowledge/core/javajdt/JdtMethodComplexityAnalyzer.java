package org.aiknowledge.core.javajdt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

/** Computes method-level Java complexity metrics from the JDT AST. */
final class JdtMethodComplexityAnalyzer {
    private final org.eclipse.jdt.core.dom.CompilationUnit unit;
    private final String typeName;
    private final String sourceFile;

    JdtMethodComplexityAnalyzer(org.eclipse.jdt.core.dom.CompilationUnit unit, String typeName, String sourceFile) {
        this.unit = unit;
        this.typeName = typeName;
        this.sourceFile = sourceFile;
    }

    List<Map<String, Object>> analyze(List<MethodDeclaration> methods) {
        List<Map<String, Object>> facts = new ArrayList<>();
        for (MethodDeclaration method : methods) {
            facts.add(analyze(method));
        }
        return List.copyOf(facts);
    }

    private Map<String, Object> analyze(MethodDeclaration method) {
        ComplexityVisitor visitor = new ComplexityVisitor(method.getName().getIdentifier());
        if (method.getBody() != null) {
            method.getBody().accept(visitor);
        }
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
        private final String methodName;
        private int nesting;
        private int maxNestingDepth;
        private int cyclomaticComplexity = 1;
        private int cognitiveComplexity;
        private int decisionPointCount;
        private final Map<String, Integer> decisionPointsByKind = new LinkedHashMap<>();

        ComplexityVisitor(String methodName) {
            this.methodName = methodName;
        }

        int cyclomaticComplexity() {
            return cyclomaticComplexity;
        }

        int cognitiveComplexity() {
            return cognitiveComplexity;
        }

        int maxNestingDepth() {
            return maxNestingDepth;
        }

        int decisionPointCount() {
            return decisionPointCount;
        }

        Map<String, Integer> decisionPointsByKind() {
            return new LinkedHashMap<>(decisionPointsByKind);
        }

        @Override
        public boolean visit(IfStatement node) {
            addDecision("if", cognitiveNestingForIf(node));
            enterNestedFlow();
            return true;
        }

        @Override
        public void endVisit(IfStatement node) {
            exitNestedFlow();
        }

        @Override
        public boolean visit(ForStatement node) {
            addNestedDecision("loop");
            return true;
        }

        @Override
        public void endVisit(ForStatement node) {
            exitNestedFlow();
        }

        @Override
        public boolean visit(EnhancedForStatement node) {
            addNestedDecision("loop");
            return true;
        }

        @Override
        public void endVisit(EnhancedForStatement node) {
            exitNestedFlow();
        }

        @Override
        public boolean visit(WhileStatement node) {
            addNestedDecision("loop");
            return true;
        }

        @Override
        public void endVisit(WhileStatement node) {
            exitNestedFlow();
        }

        @Override
        public boolean visit(DoStatement node) {
            addNestedDecision("loop");
            return true;
        }

        @Override
        public void endVisit(DoStatement node) {
            exitNestedFlow();
        }

        @Override
        public boolean visit(CatchClause node) {
            addNestedDecision("catch");
            return true;
        }

        @Override
        public void endVisit(CatchClause node) {
            exitNestedFlow();
        }

        @Override
        public boolean visit(SwitchStatement node) {
            addNestedDecision("switch");
            return true;
        }

        @Override
        public void endVisit(SwitchStatement node) {
            exitNestedFlow();
        }

        @Override
        public boolean visit(SwitchCase node) {
            addCyclomaticOnly("switchCase");
            return true;
        }

        @Override
        public boolean visit(ConditionalExpression node) {
            addNestedDecision("conditionalExpression");
            return true;
        }

        @Override
        public void endVisit(ConditionalExpression node) {
            exitNestedFlow();
        }

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
