package org.aiknowledge.core.sourcespi;

import java.io.IOException;

/**
 * Extensible source-language and source-tooling analysis contract.
 *
 * <p>Multiple providers may contribute facts for the same source file. This
 * permits a general language provider and focused framework or project-system
 * providers to coexist without exposing parser-specific AST objects.</p>
 */
public interface SourceKnowledgeProvider {
    String id();

    boolean supports(String sourcePath);

    SourceKnowledgeResult extract(SourceKnowledgeRequest request) throws IOException;

    default int priority() {
        return 0;
    }
}
