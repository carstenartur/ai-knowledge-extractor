package org.aiknowledge.core.sourcespi;

import java.util.List;
import java.util.Map;

/**
 * Language-neutral JSON-compatible facts emitted by a source provider.
 *
 * <p>Callables are symbols with {@code kind=callable}; parser-specific AST objects never cross
 * this contract. Empty categories are represented by empty lists. Recoverable limitations are
 * emitted through {@code warningFacts}; fatal I/O failures follow the shared source error policy.</p>
 */
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
