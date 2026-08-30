package org.aiknowledge.core.sourcespi;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Input shared by language and tooling providers. */
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
        sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        modules = modules == null ? List.of() : List.copyOf(modules);
        buildMetadata = buildMetadata == null ? Map.of() : Map.copyOf(buildMetadata);
        providerConfiguration = providerConfiguration == null
                ? Map.of()
                : Map.copyOf(providerConfiguration);
    }
}
