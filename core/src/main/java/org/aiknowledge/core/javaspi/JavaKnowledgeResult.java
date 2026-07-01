package org.aiknowledge.core.javaspi;

import java.util.List;

public record JavaKnowledgeResult(
        List typeFacts,
        List methodFacts,
        List fieldFacts,
        List testFacts,
        List packageFacts,
        List referenceFacts,
        List relationFacts,
        List classFacts,
        List warnings) {
    public JavaKnowledgeResult {
        typeFacts = List.copyOf(typeFacts == null ? List.of() : typeFacts);
        methodFacts = List.copyOf(methodFacts == null ? List.of() : methodFacts);
        fieldFacts = List.copyOf(fieldFacts == null ? List.of() : fieldFacts);
        testFacts = List.copyOf(testFacts == null ? List.of() : testFacts);
        packageFacts = List.copyOf(packageFacts == null ? List.of() : packageFacts);
        referenceFacts = List.copyOf(referenceFacts == null ? List.of() : referenceFacts);
        relationFacts = List.copyOf(relationFacts == null ? List.of() : relationFacts);
        classFacts = List.copyOf(classFacts == null ? List.of() : classFacts);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }

    public static JavaKnowledgeResult empty() {
        return new JavaKnowledgeResult(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
