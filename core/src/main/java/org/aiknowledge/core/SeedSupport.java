package org.aiknowledge.core;

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
        merge(seedDir.resolve("capabilities.seed.yaml"), snapshot.capabilities);
        merge(seedDir.resolve("claims.seed.yaml"), snapshot.claims);
    }

    private static void merge(Path file, List target) throws IOException {
        if (!Files.isRegularFile(file)) return;
        for (Map item : parse(file)) {
            if (!item.containsKey("id")) continue;
            Object existing = find(target, String.valueOf(item.get("id")));
            if (existing instanceof Map map) mergeMap(map, item); else target.add(item);
        }
    }

    private static void mergeMap(Map target, Map source) {
        for (Object object : source.entrySet()) {
            Map.Entry entry = (Map.Entry) object;
            Object oldValue = target.get(entry.getKey());
            Object newValue = entry.getValue();
            if (oldValue instanceof List oldList && newValue instanceof List newList) {
                for (Object value : newList) if (!oldList.contains(value)) oldList.add(value);
            } else {
                target.put(entry.getKey(), newValue);
            }
        }
    }

    private static Object find(List items, String id) {
        for (Object object : items) if (object instanceof Map map && id.equals(String.valueOf(map.get("id")))) return map;
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
            current.put(line.substring(0, idx).trim(), parseValue(line.substring(idx + 1).trim()));
        }
        return result;
    }

    private static Object parseValue(String value) {
        String trimmed = stripQuotes(value.trim());
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            List list = new ArrayList();
            String body = trimmed.substring(1, trimmed.length() - 1).trim();
            if (!body.isEmpty()) for (String part : body.split(",")) list.add(stripQuotes(part.trim()));
            return list;
        }
        return trimmed;
    }

    private static String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) return value.substring(1, value.length() - 1);
        return value;
    }
}
