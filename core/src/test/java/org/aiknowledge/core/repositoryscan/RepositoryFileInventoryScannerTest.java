package org.aiknowledge.core.repositoryscan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryFileInventoryScannerTest {
    @TempDir
    Path temporaryDirectory;

    private final RepositoryFileInventoryScanner scanner =
            new RepositoryFileInventoryScanner();

    @Test
    void returnsDeterministicSourceInventoryWithoutGeneratedTrees() throws IOException {
        Path second = write("src/test/java/SecondTest.java", "class SecondTest {}");
        Path first = write("src/main/java/First.java", "class First {}");
        write("build/verification-venv/transient.pyc", "generated");
        write("target/classes/First.class", "generated");
        write("module/build/generated.txt", "generated");

        assertEquals(
                List.of(first, second),
                scanner.scan(temporaryDirectory));
        assertEquals(
                scanner.scan(temporaryDirectory),
                scanner.scan(temporaryDirectory));
    }

    @Test
    void doesNotEnterAnInaccessibleIgnoredBuildSubtree() throws IOException {
        requirePosixPermissions();
        Path source = write("src/main/java/Source.java", "class Source {}");
        Path build = temporaryDirectory.resolve("build");
        write("build/verification-venv/cache/transient.pyc", "generated");
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(build);

        Files.setPosixFilePermissions(build, Set.of());
        try {
            assumeFalse(
                    Files.isReadable(build),
                    "filesystem does not enforce the removed read permission");
            assertEquals(
                    List.of(source),
                    scanner.scan(temporaryDirectory));
        } finally {
            Files.setPosixFilePermissions(build, original);
        }
    }

    @Test
    void remainsFailClosedForAnInaccessibleSourceSubtree() throws IOException {
        requirePosixPermissions();
        Path source = temporaryDirectory.resolve("src");
        write("src/main/java/Source.java", "class Source {}");
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(source);

        Files.setPosixFilePermissions(source, Set.of());
        try {
            assumeFalse(
                    Files.isReadable(source),
                    "filesystem does not enforce the removed read permission");
            assertThrows(
                    IOException.class,
                    () -> scanner.scan(temporaryDirectory));
        } finally {
            Files.setPosixFilePermissions(source, original);
        }
    }

    private Path write(String relativePath, String content) throws IOException {
        Path file = temporaryDirectory.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private void requirePosixPermissions() throws IOException {
        assumeTrue(
                Files.getFileStore(temporaryDirectory)
                        .supportsFileAttributeView(PosixFileAttributeView.class),
                "test requires POSIX permissions");
    }
}
