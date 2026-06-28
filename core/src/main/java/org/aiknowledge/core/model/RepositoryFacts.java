package org.aiknowledge.core.model;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.aiknowledge.core.RepositorySnapshot;

public final class RepositoryFacts {
    private RepositoryFacts() {}

    public static void populateIndex(Path root, RepositorySnapshot snapshot) {
        Map counts = new LinkedHashMap();
        counts.put("modules", snapshot.modules.size());
        counts.put("classes", snapshot.classes.size());
        counts.put("tests", snapshot.tests.size());
        counts.put("docs", snapshot.docs.size());
        counts.put("dependencies", snapshot.dependencies.size());
        counts.put("capabilities", snapshot.capabilities.size());
        counts.put("claims", snapshot.claims.size());
        counts.put("evidence", snapshot.evidence.size());
        snapshot.index.put("schemaVersion", 1);
        snapshot.index.put("repository", root.getFileName().toString());
        snapshot.index.put("generationMode", "deterministic-static");
        snapshot.index.put("counts", counts);
    }
}
