package org.aiknowledge.core.sourcespi;

import java.util.List;
import java.util.Map;

/** Language-neutral facts emitted by a source provider. */
public record SourceKnowledgeResult(
        List<Map<String, Object>> sourceUnitFacts,
        List<Map<String, Object>> symbolFacts,
        List<Map<String, Object>> callableFacts,
        List<Map<String, Object>> relationFacts,
        List<Map<String, Object>> boundaryFacts,
        List<Map<String, Object>> warnings) {
    public SourceKnowledgeResult {
        sourceUnitFacts = copy(sourceUnitFacts);
        symbolFacts = copy(symbolFacts);
        callableFacts = copy(callableFacts);
        relationFacts = copy(relationFacts);
        boundaryFacts = copy(boundaryFacts);
        warnings = copy(warnings);
    }

    public static SourceKnowledgeResult empty() {
        return new SourceKnowledgeResult(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static List<Map<String, Object>> copy(List<Map<String, Object>> values) {
        return List.copyOf(values == null ? List.of() : values);
    }
}
