package org.aiknowledge.core.javaspi;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record JavaKnowledgeRequest(
        Path repositoryRoot,
        Path sourceFile,
        String sourcePath,
        List modules,
        List sourceRoots,
        List testSourceRoots,
        Map buildMetadata,
        List classpathEntries,
        Map providerConfiguration) {
    public JavaKnowledgeRequest {
        repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        modules = List.copyOf(modules == null ? List.of() : modules);
        sourceRoots = List.copyOf(sourceRoots == null ? List.of() : sourceRoots);
        testSourceRoots = List.copyOf(testSourceRoots == null ? List.of() : testSourceRoots);
        buildMetadata = Map.copyOf(buildMetadata == null ? Map.of() : buildMetadata);
        classpathEntries = List.copyOf(classpathEntries == null ? List.of() : classpathEntries);
        providerConfiguration = Map.copyOf(providerConfiguration == null ? Map.of() : providerConfiguration);
    }
}
