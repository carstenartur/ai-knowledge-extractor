package io.github.carstenartur.aiknowledge.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SeedSupport {
    private SeedSupport() {}

    static void mergeSeeds(ExtractionOptions options, RepositorySnapshot snapshot) throws IOException {
        Path seedDir = options.seedDirectory();
        if (seedDir == null || !Files.isDirectory(seedDir)) return;
        merge(seedDir.resolve("capabilities.seed.yaml"), "capabilities", snapshot.capabilities);
        merge(seedDir.resolve("claims.seed.yaml"), "claims", snapshot.claims);
    }

    private static void merge(Path file, String key, List target) throws IOException {
        if (!Files.isRegularFile(file)) return;
        for (Map item : parse(file)) {
            if (!item.containsKey("id")) continue;
            Object existing = find(target, String.valueOf(item.get("id")));
            if (existing instanceof Map map) {
                map.putAll(item);
            } else {
                target.add(item);
            }
        }
    }

    private static Object find(List items, String id) {
        for (Object object : items) {
            if (object instanceof Map map && id.equals(String.valueOf(map.get("id")))) return map;
        }
        return null;
    }

    private static List<Map> parse(Path file) throws IOException {
        List<Map> result = new ArrayList<>();
        Map current = null;
        for (String raw : Files.readAllLines(file)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("- ")) {
                current = new LinkedHashMap();
                result.add(current);
                line = line.substring(2).trim();
            }
            if (current == null || !line.contains(":")) continue;
            int idx = line.indexOf(':');
            String name = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            current.put(name, parseValue(value));
        }
        return result;
    }

    private static Object parseValue(String value) {
        if (value.startsWith("[") && value.endsWith("]")) {
            List list = new ArrayList();
            String body = value.substring(1, value.length() - 1).trim();
            if (!body.isEmpty()) {
                for (String part : body.split(",")) list.add(part.trim());
            }
            return list;
        }
        return value;
    }
}
