package org.aiknowledge.core.javaspi;

import java.io.IOException;
import java.nio.file.Path;
import org.aiknowledge.core.RepositorySnapshot;

public interface JavaKnowledgeProvider {
    boolean supports(String path);

    void extract(Path root, Path file, String path, RepositorySnapshot snapshot) throws IOException;
}
