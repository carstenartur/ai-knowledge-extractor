package org.aiknowledge.core.sourcespi;

import java.util.List;
import java.util.Map;

/** Language-neutral facts emitted by a source provider. */
public record SourceKnowledgeResult(
        List<Map<String, Object>> sourceUnitFacts,
        List<Map<String, Object>> symbolFacts,
        List<Map<String, Object>> relationFacts,
        List<Map<String, Object>> boundaryFacts,
        List<Map<String, Object>> warningFacts) {

    public SourceKnowledgeResult {
        sourceUnitFacts = immutable(sourceUnitFacts);
        symbolFacts = immutable(symbolFacts);
        relationFacts = immutable(relationFacts);
        boundaryFacts = immutable(boundaryFacts);
        warningFacts = immutable(warningFacts);
    }

    public static SourceKnowledgeResult empty() {
        return new SourceKnowledgeResult(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static List<Map<String, Object>> immutable(List<Map<String, Object>> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
