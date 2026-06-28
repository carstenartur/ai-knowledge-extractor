package org.aiknowledge.core.repositoryscan;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class RepositoryFileInventoryScanner {
    public List scan(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(file -> !ignored(rel(root, file)))
                    .sorted(Comparator.comparing(file -> rel(root, file)))
                    .toList();
        }
    }

    public String rel(Path root, Path file) {
        return root.relativize(file).toString().replace(File.separatorChar, '/');
    }

    private static boolean ignored(String path) {
        return path.startsWith(".git/")
                || path.startsWith(".gradle/")
                || path.contains("/build/")
                || path.contains("/target/")
                || path.startsWith("build/")
                || path.startsWith("target/");
    }
}
