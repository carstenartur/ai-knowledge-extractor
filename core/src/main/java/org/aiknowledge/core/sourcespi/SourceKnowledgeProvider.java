package org.aiknowledge.core.sourcespi;

import java.io.IOException;

/**
 * Extensible source-language and source-tooling analysis contract.
 *
 * <p>Providers emit language-neutral facts and must not expose parser-specific AST objects.
 * Multiple providers may contribute complementary facts for the same source file.</p>
 */
public interface SourceKnowledgeProvider {
    String id();

    boolean supports(String sourcePath);

    SourceKnowledgeResult extract(SourceKnowledgeRequest request) throws IOException;

    default int priority() {
        return 0;
    }
}
