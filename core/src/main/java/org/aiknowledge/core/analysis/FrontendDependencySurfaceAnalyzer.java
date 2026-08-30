package org.aiknowledge.core.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.aiknowledge.core.RepositorySnapshot;

/** Evaluates declared and actually imported JavaScript runtime dependencies. */
public final class FrontendDependencySurfaceAnalyzer {
    private static final Set<String> RUNTIME_SCOPES =
            Set.of("dependencies", "peerDependencies", "optionalDependencies");

    private FrontendDependencySurfaceAnalyzer() {
    }

    public static Map<String, Object> analyze(RepositorySnapshot snapshot) {
        Map<String, Set<String>> declaredByScope = declaredByScope(snapshot.dependencies);
        Set<String> declaredRuntime = new LinkedHashSet<>();
        for (String scope : RUNTIME_SCOPES) {
            declaredRuntime.addAll(declaredByScope.getOrDefault(scope, Set.of()));
        }
        Set<String> declaredDev = declaredByScope.getOrDefault("devDependencies", Set.of());

        Set<String> runtimeImports = new LinkedHashSet<>();
        Set<String> typeOnlyImports = new LinkedHashSet<>();
        Set<String> boundaryFiles = boundaryFiles(snapshot.boundaries);
        Set<String> boundaryRuntimeImports = new LinkedHashSet<>();
        for (Object value : snapshot.relations) {
            if (!(value instanceof Map<?, ?> relation)) continue;
            if (!"SOURCE_UNIT_IMPORTS_MODULE".equals(text(relation.get("kind")))) continue;
            if (!Boolean.TRUE.equals(relation.get("external"))) continue;
            String packageName = text(relation.get("packageName"));
            if (packageName.isBlank()) continue;
            boolean runtime = Boolean.TRUE.equals(relation.get("runtime"));
            if (runtime) {
                runtimeImports.add(packageName);
                if (boundaryFiles.contains(text(relation.get("sourceFile")))) {
                    boundaryRuntimeImports.add(packageName);
                }
            } else {
                typeOnlyImports.add(packageName);
            }
        }

        Set<String> undeclaredRuntimeImports = difference(runtimeImports, union(declaredRuntime, declaredDev));
        Set<String> runtimeImportsDeclaredOnlyAsDev = intersection(runtimeImports, declaredDev);
        runtimeImportsDeclaredOnlyAsDev.removeAll(declaredRuntime);
        Set<String> unusedDeclaredRuntime = difference(declaredRuntime, runtimeImports);
        Set<String> clientMechanisms = clientMechanisms(snapshot.boundaries);

        int score = clamp(
                boundaryRuntimeImports.size() * 8
                        + Math.max(0, clientMechanisms.size() - 1) * 20
                        + runtimeImportsDeclaredOnlyAsDev.size() * 15
                        + undeclaredRuntimeImports.size() * 12);

        List<Map<String, Object>> findings = new ArrayList<>();
        if (clientMechanisms.size() > 1) {
            findings.add(finding(
                    "MIXED_CLIENT_MECHANISMS",
                    "medium",
                    "Frontend boundary code uses " + clientMechanisms.size()
                            + " client mechanisms: " + String.join(", ", sorted(clientMechanisms)) + ".",
                    "Consolidate boundary access behind one typed client or adapter where practical."));
        }
        if (!runtimeImportsDeclaredOnlyAsDev.isEmpty()) {
            findings.add(finding(
                    "RUNTIME_IMPORT_DECLARED_AS_DEV_ONLY",
                    "high",
                    "Runtime imports are declared only as development dependencies: "
                            + String.join(", ", sorted(runtimeImportsDeclaredOnlyAsDev)) + ".",
                    "Move runtime packages to dependencies, peerDependencies, or optionalDependencies as appropriate."));
        }
        if (!undeclaredRuntimeImports.isEmpty()) {
            findings.add(finding(
                    "UNDECLARED_RUNTIME_IMPORT",
                    "high",
                    "Runtime imports are not declared in package.json: "
                            + String.join(", ", sorted(undeclaredRuntimeImports)) + ".",
                    "Declare direct runtime dependencies explicitly instead of relying on transitive installation."));
        }

        Map<String, Object> declared = new LinkedHashMap<>();
        declaredByScope.forEach((scope, packages) -> declared.put(scope, sorted(packages)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("score", score);
        result.put("rating", rating(score));
        result.put("declaredPackagesByScope", declared);
        result.put("declaredRuntimePackageCount", declaredRuntime.size());
        result.put("runtimeImportedPackages", sorted(runtimeImports));
        result.put("typeOnlyImportedPackages", sorted(typeOnlyImports));
        result.put("boundaryRuntimeImportedPackages", sorted(boundaryRuntimeImports));
        result.put("unusedDeclaredRuntimePackages", sorted(unusedDeclaredRuntime));
        result.put("runtimeImportsDeclaredOnlyAsDev", sorted(runtimeImportsDeclaredOnlyAsDev));
        result.put("undeclaredRuntimeImports", sorted(undeclaredRuntimeImports));
        result.put("clientMechanisms", sorted(clientMechanisms));
        result.put("findings", findings);
        result.put("method", "declared-vs-imported-runtime-dependency-surface-v1");
        result.put("notes", List.of(
                "Unused development dependencies do not increase the boundary score.",
                "Type-only imports remain visible but do not count as runtime dependency surface.",
                "The score reflects observable source dependencies, not package download size or vulnerability risk."));
        return result;
    }

    private static Map<String, Set<String>> declaredByScope(List dependencies) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Object value : dependencies) {
            if (!(value instanceof Map<?, ?> dependency)) continue;
            if (!"npm".equals(text(dependency.get("ecosystem")))) continue;
            String scope = text(dependency.get("scope"));
            String artifact = text(dependency.get("artifact"));
            if (scope.isBlank() || artifact.isBlank()) continue;
            result.computeIfAbsent(scope, ignored -> new LinkedHashSet<>()).add(artifact);
        }
        return result;
    }

    private static Set<String> boundaryFiles(List boundaries) {
        Set<String> result = new LinkedHashSet<>();
        for (Object value : boundaries) {
            if (value instanceof Map<?, ?> boundary
                    && "client-call".equals(text(boundary.get("kind")))) {
                String sourceFile = text(boundary.get("sourceFile"));
                if (!sourceFile.isBlank()) result.add(sourceFile);
            }
        }
        return result;
    }

    private static Set<String> clientMechanisms(List boundaries) {
        Set<String> result = new LinkedHashSet<>();
        for (Object value : boundaries) {
            if (!(value instanceof Map<?, ?> boundary)
                    || !"client-call".equals(text(boundary.get("kind")))) continue;
            String client = text(boundary.get("client"));
            if (!client.isBlank()) result.add(client.toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private static Map<String, Object> finding(
            String code, String severity, String message, String recommendation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("severity", severity);
        result.put("message", message);
        result.put("recommendation", recommendation);
        return result;
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> result = new LinkedHashSet<>(first);
        result.addAll(second);
        return result;
    }

    private static Set<String> intersection(Set<String> first, Set<String> second) {
        Set<String> result = new LinkedHashSet<>(first);
        result.retainAll(second);
        return result;
    }

    private static Set<String> difference(Set<String> first, Set<String> second) {
        Set<String> result = new LinkedHashSet<>(first);
        result.removeAll(second);
        return result;
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String rating(int score) {
        if (score >= 75) return "very-high";
        if (score >= 50) return "high";
        if (score >= 25) return "moderate";
        return "low";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
