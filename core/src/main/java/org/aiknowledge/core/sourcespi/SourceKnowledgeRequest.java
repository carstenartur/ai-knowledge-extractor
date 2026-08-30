package org.aiknowledge.core.sourcespi;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Input shared by source-language and tooling providers. */
public record SourceKnowledgeRequest(
        Path repositoryRoot,
        Path sourceFile,
        String sourcePath,
        List modules,
        Map buildMetadata,
        Map<String, String> providerConfiguration) {
    public SourceKnowledgeRequest {
        repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        modules = List.copyOf(modules == null ? List.of() : modules);
        buildMetadata = Map.copyOf(buildMetadata == null ? Map.of() : buildMetadata);
        providerConfiguration = Map.copyOf(providerConfiguration == null ? Map.of() : providerConfiguration);
    }
}
