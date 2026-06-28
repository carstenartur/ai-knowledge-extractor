package org.aiknowledge.core.repositoryscan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.aiknowledge.core.MarkdownMetadata;
import org.aiknowledge.core.RepositorySnapshot;

public final class MarkdownDocumentScanner {
    public void extract(Path file, String path, RepositorySnapshot snapshot) throws IOException {
        if (!path.endsWith(".md")) return;
        String text = read(file);
        List headings = new ArrayList();
        for (String line : text.split("\\R")) if (line.startsWith("#")) headings.add(line.replaceFirst("^#+\\s*", ""));
        Map doc = new LinkedHashMap();
        doc.put("path", path);
        doc.put("title", headings.isEmpty() ? file.getFileName().toString() : headings.get(0));
        doc.put("headings", headings);
        doc.put("links", MarkdownMetadata.links(text));
        snapshot.docs.add(doc);
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
