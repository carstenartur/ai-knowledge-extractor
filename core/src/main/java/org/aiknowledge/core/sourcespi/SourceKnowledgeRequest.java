package org.aiknowledge.core.sourcespi;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, repository-relative input shared by all source providers.
 *
 * <p>{@code sourcePath} is the stable slash-separated identity used in output. Providers may use
 * {@code sourceFile} for I/O, but must not publish its absolute value. Build metadata is an
 * immutable view and provider configuration contains only explicitly documented values.</p>
 */
public record SourceKnowledgeRequest(
        Path repositoryRoot,
        Path sourceFile,
        String sourcePath,
        List<?> modules,
        Map<?, ?> buildMetadata,
        Map<String, String> providerConfiguration) {

    public SourceKnowledgeRequest {
        repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        sourcePath = Objects.requireNonNull(sourcePath, "sourcePath").replace('\\', '/');
        modules = modules == null ? List.of() : List.copyOf(modules);
        buildMetadata = buildMetadata == null ? Map.of() : Map.copyOf(buildMetadata);
        providerConfiguration = providerConfiguration == null
                ? Map.of()
                : Map.copyOf(providerConfiguration);
    }
}
