package org.aiknowledge.core.linker;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aiknowledge.core.RepositorySnapshot;

/**
 * Verifies architecture and quality claims against repository facts.
 *
 * <p>Claims that declare at least one rule field are evaluated against the snapshot:
 * imports, module dependencies, tests and evidence. Verified claims receive a
 * {@code status} of {@code passed} or {@code failed} with {@code violations} details.
 * Claims without rule fields are tagged {@code unverified} when they have no prior status.
 *
 * <p>Supported rule fields:
 * <ul>
 *   <li>{@code scopeModules} – limit checks to classes in these modules</li>
 *   <li>{@code forbiddenReferences} – package/class prefixes that must not appear in imports</li>
 *   <li>{@code forbiddenImports} – package/class prefixes that must not appear in imports (alias)</li>
 *   <li>{@code forbiddenDependencies} – substrings that must not appear in external dependency notations</li>
 *   <li>{@code allowedDependencies} – if provided, only dependencies matching at least one entry are allowed</li>
 *   <li>{@code allowedTargetModules} – project dependency targets not in this list are violations</li>
 *   <li>{@code verifiedBy} – test class names that must exist to prove the claim</li>
 *   <li>{@code requiredTests} – test class names that must exist</li>
 *   <li>{@code requiredEvidenceTypes} – evidence type values that must exist in evidence.json</li>
 *   <li>{@code requiredDocs} – glob patterns that must match at least one document path</li>
 *   <li>{@code mustBeAcyclic} – when {@code true}, import cycles among scoped classes are violations</li>
 * </ul>
 */
public final class ClaimVerifier {

    private static final String PROJECT_DEP_PREFIX = "project(";
    private static final String[] PROJECT_DEP_QUOTE_PREFIXES = {"':", "\":"};
    /**
     * Verifies all claims in the snapshot and tags each with a computed {@code status},
     * {@code violations}, {@code verificationEvidence} and {@code matchedVerifiedBy} as applicable.
     *
     * @return the count of claims that failed with {@code severity=error}
     */
    public int verify(RepositorySnapshot snapshot) {
        int errorFailures = 0;
        for (Object obj : snapshot.claims) {
            if (obj instanceof Map claim) {
                verifyClaim(claim, snapshot);
                if ("failed".equals(claim.get("status")) && "error".equals(claim.getOrDefault("severity", ""))) {
                    errorFailures++;
                }
            }
        }
        return errorFailures;
    }

    /** Returns the number of claims that have {@code status=failed} and {@code severity=error}. */
    public static int countErrorFailures(List claims) {
        int count = 0;
        for (Object obj : claims) {
            if (obj instanceof Map claim) {
                if ("failed".equals(claim.get("status")) && "error".equals(claim.getOrDefault("severity", ""))) {
                    count++;
                }
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private static void verifyClaim(Map claim, RepositorySnapshot snapshot) {
        List<String> forbiddenRefs = asList(claim.get("forbiddenReferences"));
        List<String> forbiddenImps = asList(claim.get("forbiddenImports"));
        List<String> forbiddenDeps = asList(claim.get("forbiddenDependencies"));
        List<String> allowedDeps = asListOrNull(claim, "allowedDependencies");
        List<String> allowedTargets = asListOrNull(claim, "allowedTargetModules");
        List<String> requiredTests = asList(claim.get("requiredTests"));
        List<String> requiredEvidenceTypes = asList(claim.get("requiredEvidenceTypes"));
        List<String> requiredDocs = asList(claim.get("requiredDocs"));
        boolean mustBeAcyclic = "true".equalsIgnoreCase(String.valueOf(claim.getOrDefault("mustBeAcyclic", "false")));

        boolean hasStructuralRules = !forbiddenRefs.isEmpty() || !forbiddenImps.isEmpty()
                || !forbiddenDeps.isEmpty() || allowedDeps != null || allowedTargets != null
                || !requiredTests.isEmpty() || !requiredEvidenceTypes.isEmpty()
                || !requiredDocs.isEmpty() || mustBeAcyclic;

        if (!hasStructuralRules) {
            if (!claim.containsKey("status")) {
                claim.put("status", "unverified");
            }
            return;
        }

        // Also treat verifiedBy as a rule when structural rules are present
        List<String> verifiedBy = asList(claim.get("verifiedBy"));
        List<String> scopeModules = asList(claim.get("scopeModules"));

        List<String> violations = new ArrayList<>();
        List<String> verificationEvidence = new ArrayList<>();

        List<Map> scopedModuleEntries = resolveScopedModules(scopeModules, snapshot);
        List<Map> scopedClasses = resolveScopedClasses(scopeModules, scopedModuleEntries, snapshot);

        for (Map m : scopedModuleEntries) {
            String name = String.valueOf(m.getOrDefault("name", ""));
            if (!name.isBlank()) verificationEvidence.add("module:" + name);
        }

        // Check forbiddenReferences and forbiddenImports
        for (Map cls : scopedClasses) {
            String className = String.valueOf(cls.getOrDefault("class", ""));
            if (className.isBlank()) continue;
            String sourceFile = String.valueOf(cls.getOrDefault("sourceFile", ""));
            List<String> imports = asList(cls.get("imports"));
            for (String imp : imports) {
                for (String forbidden : forbiddenRefs) {
                    if (imp.startsWith(forbidden)) {
                        violations.add("forbidden-reference:" + forbidden + " in " + className + " (" + sourceFile + ")");
                    }
                }
                for (String forbidden : forbiddenImps) {
                    if (imp.startsWith(forbidden)) {
                        violations.add("forbidden-import:" + forbidden + " in " + className + " (" + sourceFile + ")");
                    }
                }
            }
        }

        // Check forbiddenDependencies in module external dependencies
        for (Map module : scopedModuleEntries) {
            String moduleName = String.valueOf(module.getOrDefault("name", ""));
            List<String> externalDeps = asList(module.get("externalDependencies"));
            for (String dep : externalDeps) {
                for (String forbidden : forbiddenDeps) {
                    if (dep.contains(forbidden)) {
                        violations.add("forbidden-dependency:" + forbidden + " in module:" + moduleName + " (" + dep + ")");
                    }
                }
                if (allowedDeps != null) {
                    boolean allowed = false;
                    for (String allowedDep : allowedDeps) {
                        if (dep.contains(allowedDep)) { allowed = true; break; }
                    }
                    if (!allowed) {
                        violations.add("disallowed-dependency:" + dep + " in module:" + moduleName);
                    }
                }
            }
        }

        // Check allowedTargetModules in module project dependencies
        if (allowedTargets != null) {
            Set<String> allowedSet = new LinkedHashSet<>(allowedTargets);
            for (Map module : scopedModuleEntries) {
                String moduleName = String.valueOf(module.getOrDefault("name", ""));
                List<String> projectDeps = asList(module.get("projectDependencies"));
                for (String dep : projectDeps) {
                    String projectName = extractProjectName(dep);
                    if (projectName != null && !allowedSet.contains(projectName)) {
                        violations.add("disallowed-target-module:" + projectName + " from module:" + moduleName);
                    }
                }
            }
        }

        // Check verifiedBy (test classes must exist in tests.json)
        List<String> matchedVerifiedBy = new ArrayList<>();
        if (!verifiedBy.isEmpty()) {
            Set<String> existingTests = buildTestNameSet(snapshot.tests);
            for (String testName : verifiedBy) {
                if (existingTests.contains(testName)) {
                    matchedVerifiedBy.add(testName);
                    verificationEvidence.add("test:" + testName);
                } else {
                    violations.add("missing-verifier-test:" + testName);
                }
            }
            if (!matchedVerifiedBy.isEmpty()) {
                claim.put("matchedVerifiedBy", matchedVerifiedBy);
            }
        }

        // Check requiredTests (test classes that must exist)
        if (!requiredTests.isEmpty()) {
            Set<String> existingTests = buildTestNameSet(snapshot.tests);
            for (String testName : requiredTests) {
                if (existingTests.contains(testName)) {
                    verificationEvidence.add("test:" + testName);
                } else {
                    violations.add("missing-required-test:" + testName);
                }
            }
        }

        // Check requiredEvidenceTypes
        if (!requiredEvidenceTypes.isEmpty()) {
            Set<String> evidenceTypeSet = new LinkedHashSet<>();
            for (Object ev : snapshot.evidence) {
                if (ev instanceof Map evMap) {
                    evidenceTypeSet.add(String.valueOf(evMap.getOrDefault("type", "")));
                }
            }
            for (String required : requiredEvidenceTypes) {
                if (evidenceTypeSet.contains(required)) {
                    verificationEvidence.add("evidence-type:" + required);
                } else {
                    violations.add("missing-evidence-type:" + required);
                }
            }
        }

        // Check requiredDocs
        if (!requiredDocs.isEmpty()) {
            for (String pattern : requiredDocs) {
                boolean found = false;
                for (Object doc : snapshot.docs) {
                    if (doc instanceof Map docMap) {
                        String path = String.valueOf(docMap.getOrDefault("path", ""));
                        if (CapabilityLinker.globMatches(pattern, path)) {
                            found = true;
                            verificationEvidence.add("doc:" + path);
                            break;
                        }
                    }
                }
                if (!found) {
                    violations.add("missing-required-doc:" + pattern);
                }
            }
        }

        // Check mustBeAcyclic
        if (mustBeAcyclic) {
            List<String> cycles = detectImportCycles(scopedClasses);
            violations.addAll(cycles);
        }

        claim.put("status", violations.isEmpty() ? "passed" : "failed");
        if (!violations.isEmpty()) {
            claim.put("violations", violations);
        }
        if (!verificationEvidence.isEmpty()) {
            claim.put("verificationEvidence", verificationEvidence);
        }
    }

    private static Set<String> buildTestNameSet(List tests) {
        Set<String> result = new LinkedHashSet<>();
        for (Object obj : tests) {
            if (obj instanceof Map test) {
                String testClass = String.valueOf(test.getOrDefault("testClass", ""));
                if (!testClass.isBlank()) {
                    // Add both the fully-qualified name and the simple name so that
                    // verifiedBy/requiredTests entries may be written either way.
                    result.add(testClass);
                    int dot = testClass.lastIndexOf('.');
                    if (dot >= 0) result.add(testClass.substring(dot + 1));
                }
            }
        }
        return result;
    }

    private static List<Map> resolveScopedModules(List<String> scopeModules, RepositorySnapshot snapshot) {
        if (scopeModules.isEmpty()) {
            List<Map> all = new ArrayList<>();
            for (Object obj : snapshot.modules) if (obj instanceof Map m) all.add(m);
            return all;
        }
        Set<String> names = new LinkedHashSet<>(scopeModules);
        List<Map> result = new ArrayList<>();
        for (Object obj : snapshot.modules) {
            if (obj instanceof Map module) {
                String name = String.valueOf(module.getOrDefault("name", ""));
                if (names.contains(name)) result.add(module);
            }
        }
        return result;
    }

    private static List<Map> resolveScopedClasses(List<String> scopeModules, List<Map> scopedModuleEntries, RepositorySnapshot snapshot) {
        if (scopeModules.isEmpty()) {
            List<Map> all = new ArrayList<>();
            for (Object obj : snapshot.classes) if (obj instanceof Map m) all.add(m);
            return all;
        }
        Set<String> scopedPackages = new LinkedHashSet<>();
        for (Map module : scopedModuleEntries) {
            for (String pkg : asList(module.get("mainPackages"))) {
                scopedPackages.add(pkg);
            }
        }
        List<Map> result = new ArrayList<>();
        for (Object obj : snapshot.classes) {
            if (obj instanceof Map cls) {
                String pkg = String.valueOf(cls.getOrDefault("package", ""));
                if (scopedPackages.contains(pkg)) result.add(cls);
            }
        }
        return result;
    }

    private static List<String> detectImportCycles(List<Map> classes) {
        Map<String, Set<String>> graph = new java.util.LinkedHashMap<>();
        Set<String> classSet = new LinkedHashSet<>();
        for (Map cls : classes) {
            String name = String.valueOf(cls.getOrDefault("class", ""));
            if (name.isBlank()) continue;
            classSet.add(name);
            Set<String> imports = new LinkedHashSet<>(asList(cls.get("imports")));
            graph.put(name, imports);
        }
        List<String> cycles = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        for (String cls : graph.keySet()) {
            if (!visited.contains(cls)) {
                detectCyclesDfs(cls, graph, classSet, visited, new LinkedHashSet<>(), new ArrayList<>(), cycles);
            }
        }
        return cycles.stream().distinct().toList();
    }

    private static void detectCyclesDfs(
            String cls,
            Map<String, Set<String>> graph,
            Set<String> classSet,
            Set<String> visited,
            Set<String> inStack,
            List<String> path,
            List<String> cycles) {
        visited.add(cls);
        inStack.add(cls);
        List<String> currentPath = new ArrayList<>(path);
        currentPath.add(cls);
        for (String dep : graph.getOrDefault(cls, Set.of())) {
            if (!classSet.contains(dep)) continue;
            if (!visited.contains(dep)) {
                detectCyclesDfs(dep, graph, classSet, visited, inStack, currentPath, cycles);
            } else if (inStack.contains(dep)) {
                int start = currentPath.indexOf(dep);
                if (start >= 0) {
                    List<String> cycle = new ArrayList<>(currentPath.subList(start, currentPath.size()));
                    cycle.add(dep);
                    cycles.add("import-cycle:" + String.join(" -> ", cycle));
                }
            }
        }
        inStack.remove(cls);
    }

    private static String extractProjectName(String notation) {
        for (String quote : PROJECT_DEP_QUOTE_PREFIXES) {
            int start = notation.indexOf(PROJECT_DEP_PREFIX + quote);
            if (start >= 0) {
                int nameStart = start + PROJECT_DEP_PREFIX.length() + quote.length();
                char closingQuote = quote.charAt(0);
                int end = notation.indexOf(closingQuote + ")", nameStart);
                if (end >= 0) return notation.substring(nameStart, end);
            }
        }
        return null;
    }

    private static List<String> asListOrNull(Map claim, String key) {
        if (!claim.containsKey(key)) return null;
        return asList(claim.get(key));
    }

    @SuppressWarnings("unchecked")
    private static List<String> asList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                String s = String.valueOf(item);
                if (!s.isBlank() && !"null".equals(s)) result.add(s);
            }
            return result;
        }
        if (value instanceof String s && !s.isBlank() && !"null".equals(s)) return List.of(s);
        return List.of();
    }
}
