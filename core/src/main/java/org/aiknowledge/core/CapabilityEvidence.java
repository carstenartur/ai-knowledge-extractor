package org.aiknowledge.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CapabilityEvidence {
    /**
     * Fixed capability IDs used by the fallback keyword-based inference path.
     * This path is active only when no seed capabilities have been loaded.
     * Prefer explicit capability selectors in seed files for new projects.
     */
    private static final String[] IDS = {"equality-saturation", "e-graph", "macro-rule-learning", "replay", "counterexample-search", "proof-bridge", "benchmark-report", "search-strategies", "assumption-handling", "rule-inventory"};

    private CapabilityEvidence() {
    }

    public static void addCapabilities(RepositorySnapshot snapshot) {
        for (String id : IDS) {
            List classes = evidence(snapshot.classes, id, "class");
            List tests = evidence(snapshot.tests, id, "testClass");
            List docs = evidence(snapshot.docs, id, "path");
            String status = status(classes, tests, docs);

            Map capability = new LinkedHashMap();
            capability.put("id", id);
            capability.put("status", status);
            capability.put("classes", classes);
            capability.put("tests", tests);
            capability.put("docs", docs);
            snapshot.capabilities.add(capability);

            Map claim = new LinkedHashMap();
            claim.put("id", id);
            claim.put("status", status);
            claim.put("implementedBy", classes);
            claim.put("verifiedBy", tests);
            claim.put("documentedBy", docs);
            snapshot.claims.add(claim);
        }
    }

    private static String status(List classes, List tests, List docs) {
        if (!classes.isEmpty() && !tests.isEmpty()) return "implemented";
        if (!classes.isEmpty() || !tests.isEmpty()) return "partial";
        if (!docs.isEmpty()) return "documented";
        return "unknown";
    }

    private static List evidence(List source, String id, String field) {
        List result = new ArrayList();
        String key = normalize(id);
        for (Object object : source) {
            Map map = (Map) object;
            if (normalize(map.toString()).contains(key)) result.add(map.getOrDefault(field, ""));
        }
        return result;
    }

    private static String normalize(String value) {
        return value.toLowerCase().replace("-", "").replace("_", "").replace(" ", "");
    }
}
