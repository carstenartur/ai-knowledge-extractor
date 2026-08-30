package org.aiknowledge.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Extracts deterministic npm-compatible module metadata from package.json. */
public final class PackageJsonMetadata {
    private static final List<String> DEPENDENCY_SCOPES = List.of(
            "dependencies",
            "devDependencies",
            "peerDependencies",
            "optionalDependencies");

    private PackageJsonMetadata() {
    }

    public static void enrich(
            Path root,
            Path packageJson,
            String path,
            Map module,
            RepositorySnapshot snapshot) throws IOException {
        Object parsed;
        try {
            parsed = StrictJsonReader.read(packageJson);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Cannot parse " + path + ": " + exception.getMessage(), exception);
        }
        if (!(parsed instanceof Map<?, ?> raw)) {
            throw new IOException(path + " must contain a JSON object");
        }
        Map<String, Object> document = copy(raw);
        String declaredName = text(document.get("name"));
        if (!declaredName.isBlank()) module.put("name", declaredName);
        String packageManager = text(document.get("packageManager"));
        if (!packageManager.isBlank()) module.put("packageManager", packageManager);
        module.put("language", "javascript-typescript");

        Object scriptsValue = document.get("scripts");
        if (scriptsValue instanceof Map<?, ?> scripts) {
            List<String> names = scripts.keySet().stream()
                    .map(String::valueOf)
                    .sorted()
                    .toList();
            if (!names.isEmpty()) module.put("scripts", names);
        }
        List<String> workspaces = workspaceValues(document.get("workspaces"));
        if (!workspaces.isEmpty()) module.put("workspaces", workspaces);

        for (String scope : DEPENDENCY_SCOPES) {
            Object dependenciesValue = document.get(scope);
            if (!(dependenciesValue instanceof Map<?, ?> dependencies)) continue;
            for (Map.Entry<?, ?> entry : dependencies.entrySet()) {
                String artifact = String.valueOf(entry.getKey());
                String version = text(entry.getValue());
                Map dep = new LinkedHashMap();
                dep.put("source", path);
                dep.put("module", text(module.get("name")));
                dep.put("notation", artifact + ":" + version);
                dep.put("artifact", artifact);
                dep.put("version", version);
                dep.put("scope", scope);
                dep.put("buildSystem", "npm");
                dep.put("ecosystem", "npm");
                snapshot.dependencies.add(dep);
            }
        }
    }

    private static List<String> workspaceValues(Object value) {
        Object effective = value;
        if (value instanceof Map<?, ?> map) effective = map.get("packages");
        if (!(effective instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String text = text(item);
            if (!text.isBlank()) result.add(text);
        }
        result.sort(String::compareTo);
        return List.copyOf(result);
    }

    private static Map<String, Object> copy(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
