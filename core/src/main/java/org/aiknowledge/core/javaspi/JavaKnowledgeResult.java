package org.aiknowledge.core.javaspi;

import java.util.List;

public record JavaKnowledgeResult(
        List typeFacts,
        List methodFacts,
        List testFacts,
        List packageFacts,
        List referenceFacts,
        List classFacts,
        List warnings) {
    public JavaKnowledgeResult {
        typeFacts = List.copyOf(typeFacts == null ? List.of() : typeFacts);
        methodFacts = List.copyOf(methodFacts == null ? List.of() : methodFacts);
        testFacts = List.copyOf(testFacts == null ? List.of() : testFacts);
        packageFacts = List.copyOf(packageFacts == null ? List.of() : packageFacts);
        referenceFacts = List.copyOf(referenceFacts == null ? List.of() : referenceFacts);
        classFacts = List.copyOf(classFacts == null ? List.of() : classFacts);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }

    public static JavaKnowledgeResult empty() {
        return new JavaKnowledgeResult(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
