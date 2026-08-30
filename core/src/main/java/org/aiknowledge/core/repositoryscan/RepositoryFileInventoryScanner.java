package org.aiknowledge.core.repositoryscan;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Deterministic repository inventory excluding generated and dependency trees. */
public final class RepositoryFileInventoryScanner {
    private static final Set<String> IGNORED_SEGMENTS = Set.of(
            ".git", ".gradle", ".idea", ".vscode", "build", "target", "out", "bin",
            "node_modules", "dist", "coverage", ".next", ".nuxt", ".cache", ".parcel-cache");

    public List<Path> scan(Path root) throws IOException {
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

    public static boolean ignored(String path) {
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        for (String segment : normalized.split("/")) {
            if (IGNORED_SEGMENTS.contains(segment)) return true;
        }
        return normalized.endsWith(".min.js")
                || normalized.endsWith(".min.css")
                || normalized.endsWith(".map");
    }
}
