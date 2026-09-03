package org.aiknowledge.core.repositoryscan;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RepositoryFileInventoryScanner {
    public List<Path> scan(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory,
                    BasicFileAttributes attributes) {
                if (!directory.equals(root)
                        && ignoredDirectory(rel(root, directory))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(
                    Path file,
                    BasicFileAttributes attributes) {
                if (attributes.isRegularFile() && !ignored(rel(root, file))) {
                    result.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(
                    Path file,
                    IOException failure) throws IOException {
                String relative = rel(root, file);
                if (ignored(relative) || ignoredDirectory(relative)) {
                    return FileVisitResult.CONTINUE;
                }
                throw failure;
            }
        });
        result.sort(Comparator.comparing(file -> rel(root, file)));
        return List.copyOf(result);
    }

    public String rel(Path root, Path file) {
        return root.relativize(file).toString().replace(File.separatorChar, '/');
    }

    private static boolean ignoredDirectory(String path) {
        return ignored(path + "/");
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
