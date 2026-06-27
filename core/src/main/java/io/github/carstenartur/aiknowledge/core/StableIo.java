package io.github.carstenartur.aiknowledge.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class StableIo {
    private StableIo() {
    }

    static void writeJson(Path path, Object value) throws IOException {
        writeText(path, JsonSupport.toJson(value) + "\n");
    }

    static void writeText(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    static String readUtf8(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
