package org.aiknowledge.core.sourcespi;

import java.io.IOException;

/**
 * Extensible source-language and source-tooling analysis contract.
 *
 * <h2>Lifecycle and concurrency</h2>
 * <p>A provider instance is created once per extraction pipeline and may analyse many files. The
 * current pipeline invokes providers sequentially, but implementations must be stateless,
 * thread-safe, or internally synchronized so that future parallel extraction does not change the
 * result.</p>
 *
 * <h2>Determinism</h2>
 * <p>For identical request bytes and configuration a provider must emit equivalent facts with
 * stable identifiers and ordering. Providers must not add timestamps, random identifiers,
 * absolute checkout paths, network responses, Git history, or environment-dependent data.</p>
 *
 * <h2>Composition</h2>
 * <p>More than one provider may support a file. A general language provider and focused framework
 * providers can therefore contribute complementary facts. Parser-specific AST objects must never
 * cross this SPI; downstream analysis consumes only {@link SourceKnowledgeResult}.</p>
 */
public interface SourceKnowledgeProvider {
    /** Stable provider identifier used in configuration, evidence and diagnostics. */
    String id();

    /** Cheap path-level capability check; this method must not read the file. */
    boolean supports(String sourcePath);

    /** Extracts deterministic facts or throws an {@link IOException} for an unreadable source. */
    SourceKnowledgeResult extract(SourceKnowledgeRequest request) throws IOException;

    /**
     * Ordering hint for composable providers. Higher priorities run first; equal priorities are
     * ordered by {@link #id()}.
     */
    default int priority() {
        return 0;
    }
}
