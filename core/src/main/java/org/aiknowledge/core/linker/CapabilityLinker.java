package org.aiknowledge.core.linker;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aiknowledge.core.CapabilityEvidence;
import org.aiknowledge.core.RepositorySnapshot;

public final class CapabilityLinker {

    /**
     * Links capabilities to evidence from the repository snapshot.
     * When seed capabilities are present, selector fields (modules, packages,
     * typePatterns, testPatterns, docPatterns, evidenceTypes) are used to match
     * repository facts and populate matched* fields and a computed status.
     * When no seed capabilities are present, falls back to fixed-ID keyword inference
     * via {@link CapabilityEvidence}.
     */
    public void link(RepositorySnapshot snapshot) {
        if (snapshot.capabilities.isEmpty()) {
            // Fallback: keyword-based capability inference for well-known fixed IDs.
            CapabilityEvidence.addCapabilities(snapshot);
            return;
        }
        for (Object obj : snapshot.capabilities) {
            if (obj instanceof Map cap) {
                linkCapability(cap, snapshot);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void linkCapability(Map cap, RepositorySnapshot snapshot) {
        List<String> moduleSelectors = asList(cap.get("modules"));
        List<String> packageSelectors = asList(cap.get("packages"));
        List<String> typePatterns = asList(cap.get("typePatterns"));
        List<String> testPatterns = asList(cap.get("testPatterns"));
        List<String> docPatterns = asList(cap.get("docPatterns"));
        List<String> evidenceTypes = asList(cap.get("evidenceTypes"));

        List<String> matchedModules = matchByField(moduleSelectors, snapshot.modules, "name");
        List<String> matchedPackages = matchPackages(packageSelectors, snapshot.classes);
        List<String> matchedTypes = matchTypes(typePatterns, packageSelectors, snapshot.classes);
        List<String> matchedTests = matchTests(testPatterns, packageSelectors, snapshot.tests);
        List<String> matchedDocs = matchDocs(docPatterns, snapshot.docs);
        List<String> matchedEvidence = matchEvidence(evidenceTypes, snapshot.evidence);

        cap.put("matchedModules", matchedModules);
        cap.put("matchedPackages", matchedPackages);
        cap.put("matchedTypes", matchedTypes);
        cap.put("matchedTests", matchedTests);
        cap.put("matchedDocs", matchedDocs);
        cap.put("matchedEvidence", matchedEvidence);
        String status = computeStatus(matchedTypes, matchedTests, matchedDocs, matchedEvidence);
        cap.put("status", status);

        if ("unknown".equals(status)) {
            List<String> existing = asList(cap.get("warnings"));
            List<String> warnings = new ArrayList<>(existing);
            warnings.add("no-evidence-found");
            cap.put("warnings", warnings);
        }
    }

    private static List<String> matchByField(List<String> selectors, List items, String field) {
        if (selectors.isEmpty()) return List.of();
        Set<String> selectorSet = new LinkedHashSet<>(selectors);
        List<String> result = new ArrayList<>();
        for (Object obj : items) {
            if (obj instanceof Map map) {
                String value = String.valueOf(map.getOrDefault(field, ""));
                if (selectorSet.contains(value) && !result.contains(value)) result.add(value);
            }
        }
        return result;
    }

    private static List<String> matchPackages(List<String> packageSelectors, List classes) {
        if (packageSelectors.isEmpty()) return List.of();
        Set<String> selectorSet = new LinkedHashSet<>(packageSelectors);
        Set<String> matched = new LinkedHashSet<>();
        for (Object obj : classes) {
            if (obj instanceof Map map) {
                String pkg = String.valueOf(map.getOrDefault("package", ""));
                if (selectorSet.contains(pkg)) matched.add(pkg);
            }
        }
        return new ArrayList<>(matched);
    }

    private static List<String> matchTypes(List<String> patterns, List<String> packageSelectors, List classes) {
        Set<String> pkgSet = new LinkedHashSet<>(packageSelectors);
        List<String> result = new ArrayList<>();
        for (Object obj : classes) {
            if (!(obj instanceof Map map)) continue;
            String fqn = String.valueOf(map.getOrDefault("class", ""));
            String pkg = String.valueOf(map.getOrDefault("package", ""));
            boolean inPackage = !pkgSet.isEmpty() && pkgSet.contains(pkg);
            boolean matchesPattern = matchesAnyGlob(patterns, simpleName(fqn));
            if ((inPackage || matchesPattern) && !result.contains(fqn)) result.add(fqn);
        }
        return result;
    }

    private static List<String> matchTests(List<String> patterns, List<String> packageSelectors, List tests) {
        Set<String> pkgSet = new LinkedHashSet<>(packageSelectors);
        List<String> result = new ArrayList<>();
        for (Object obj : tests) {
            if (!(obj instanceof Map map)) continue;
            String fqn = String.valueOf(map.getOrDefault("testClass", ""));
            String pkg = String.valueOf(map.getOrDefault("package", ""));
            boolean inPackage = !pkgSet.isEmpty() && pkgSet.contains(pkg);
            boolean matchesPattern = matchesAnyGlob(patterns, simpleName(fqn));
            if ((inPackage || matchesPattern) && !result.contains(fqn)) result.add(fqn);
        }
        return result;
    }

    private static List<String> matchDocs(List<String> patterns, List docs) {
        if (patterns.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (Object obj : docs) {
            if (obj instanceof Map map) {
                String path = String.valueOf(map.getOrDefault("path", ""));
                if (matchesAnyGlob(patterns, path) && !result.contains(path)) result.add(path);
            }
        }
        return result;
    }

    private static List<String> matchEvidence(List<String> evidenceTypes, List evidence) {
        if (evidenceTypes.isEmpty()) return List.of();
        Set<String> typeSet = new LinkedHashSet<>(evidenceTypes);
        List<String> result = new ArrayList<>();
        for (Object obj : evidence) {
            if (obj instanceof Map map) {
                String type = String.valueOf(map.getOrDefault("type", ""));
                if (typeSet.contains(type)) {
                    String path = String.valueOf(map.getOrDefault("path", type));
                    if (!result.contains(path)) result.add(path);
                }
            }
        }
        return result;
    }

    private static String computeStatus(List<String> types, List<String> tests, List<String> docs, List<String> evidence) {
        if (!evidence.isEmpty()) return "evidence-backed";
        if (!types.isEmpty() && !tests.isEmpty()) return "implemented-and-tested";
        if (!types.isEmpty()) return "implemented";
        if (!tests.isEmpty()) return "partial";
        if (!docs.isEmpty()) return "documented";
        return "unknown";
    }

    private static boolean matchesAnyGlob(List<String> patterns, String value) {
        for (String pattern : patterns) {
            if (globMatches(pattern, value)) return true;
        }
        return false;
    }

    static boolean globMatches(String pattern, String value) {
        return matchAt(pattern, 0, value, 0);
    }

    private static boolean matchAt(String p, int pi, String v, int vi) {
        while (pi < p.length()) {
            char c = p.charAt(pi);
            if (c == '*') {
                boolean dstar = pi + 1 < p.length() && p.charAt(pi + 1) == '*';
                int next = pi + (dstar ? 2 : 1);
                // For **, skip an optional following / so docs/**/x matches docs/x
                if (dstar && next < p.length() && p.charAt(next) == '/') next++;
                for (int i = vi; i <= v.length(); i++) {
                    if (!dstar && i > vi && v.charAt(i - 1) == '/') break;
                    if (matchAt(p, next, v, i)) return true;
                }
                return false;
            }
            if (vi >= v.length() || c != v.charAt(vi)) return false;
            pi++;
            vi++;
        }
        return vi == v.length();
    }

    private static String simpleName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                String s = String.valueOf(item);
                if (!s.isEmpty() && !"null".equals(s)) result.add(s);
            }
            return result;
        }
        if (value instanceof String s && !s.isEmpty() && !"null".equals(s)) return List.of(s);
        return List.of();
    }
}
