package org.aiknowledge.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SeedSupport {
    private SeedSupport() {}

    public static void mergeSeeds(ExtractionOptions options, RepositorySnapshot snapshot) throws IOException {
        Path seedDir = options.seedDirectory();
        if (seedDir == null || !Files.isDirectory(seedDir)) return;
        mergeAll(seedDir, "capabilities", snapshot.capabilities);
        mergeAll(seedDir, "claims", snapshot.claims);
    }

    private static void mergeAll(Path seedDir, String name, List target) throws IOException {
        merge(seedDir.resolve(name + ".seed.yaml"), target);
        merge(seedDir.resolve(name + ".seed.yml"), target);
        merge(seedDir.resolve(name + ".yaml"), target);
        merge(seedDir.resolve(name + ".yml"), target);
        merge(seedDir.resolve(name + ".json"), target);
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
        String name = file.getFileName().toString();
        if (name.endsWith(".json")) return parseJsonSeed(Files.readString(file));
        return parseYamlSeed(file);
    }

    /**
     * Parses the deliberately small seed dialect: a top-level YAML sequence of
     * mappings whose values are scalars, inline lists, or indented block lists.
     */
    private static List<Map> parseYamlSeed(Path file) throws IOException {
        List<Map> result = new ArrayList<>();
        Map current = null;
        String pendingListKey = null;
        int recordIndent = -1;
        for (String raw : Files.readAllLines(file)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int indent = leadingWhitespace(raw);

            boolean startsRecord = line.startsWith("- ")
                    && (current == null || indent <= recordIndent);
            if (startsRecord) {
                current = new LinkedHashMap();
                result.add(current);
                recordIndent = indent;
                pendingListKey = null;
                String firstField = line.substring(2).trim();
                if (!firstField.isEmpty()) pendingListKey = putYamlField(current, firstField);
                continue;
            }

            if (current == null) continue;
            if (line.startsWith("- ")) {
                if (pendingListKey != null && current.get(pendingListKey) instanceof List values) {
                    String item = line.substring(2).trim();
                    if (!item.isEmpty()) values.add(stripQuotes(item));
                }
                continue;
            }
            if (!line.contains(":")) continue;
            pendingListKey = putYamlField(current, line);
        }
        return result;
    }

    private static String putYamlField(Map current, String line) {
        int idx = line.indexOf(':');
        if (idx < 0) return null;
        String key = line.substring(0, idx).trim();
        String value = line.substring(idx + 1).trim();
        if (key.isEmpty()) return null;
        if (value.isEmpty()) {
            current.put(key, new ArrayList<>());
            return key;
        }
        current.put(key, parseValue(value));
        return null;
    }

    private static int leadingWhitespace(String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) index++;
        return index;
    }

    private static List<Map> parseJsonSeed(String text) {
        List<Map> result = new ArrayList<>();
        for (String object : objectBodies(text)) {
            Map map = new LinkedHashMap();
            for (String field : splitTopLevel(object, ',')) {
                int colon = topLevelColon(field);
                if (colon < 0) continue;
                String key = stripQuotes(field.substring(0, colon).trim());
                String value = field.substring(colon + 1).trim();
                map.put(key, parseValue(value));
            }
            if (!map.isEmpty()) result.add(map);
        }
        return result;
    }

    private static List<String> objectBodies(String text) {
        List<String> bodies = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') {
                if (depth == 0) start = i + 1;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) bodies.add(text.substring(start, i));
            }
        }
        return bodies;
    }

    private static int topLevelColon(String text) {
        boolean inString = false;
        boolean escaped = false;
        int bracketDepth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '[') bracketDepth++;
            else if (c == ']') bracketDepth--;
            else if (c == ':' && bracketDepth == 0) return i;
        }
        return -1;
    }

    private static List<String> splitTopLevel(String text, char separator) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int bracketDepth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '[') bracketDepth++;
            else if (c == ']') bracketDepth--;
            else if (c == separator && bracketDepth == 0) {
                parts.add(text.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(text.substring(start).trim());
        return parts;
    }

    private static Object parseValue(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            List list = new ArrayList();
            String body = trimmed.substring(1, trimmed.length() - 1).trim();
            if (!body.isEmpty()) for (String part : splitTopLevel(body, ',')) list.add(stripQuotes(part.trim()));
            return list;
        }
        return stripQuotes(trimmed);
    }

    private static String stripQuotes(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) return unescapeJson(trimmed.substring(1, trimmed.length() - 1));
        if (trimmed.length() >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'")) return trimmed.substring(1, trimmed.length() - 1);
        return trimmed;
    }

    private static String unescapeJson(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) { sb.append(c); i++; continue; }
            char next = s.charAt(i + 1);
            switch (next) {
                case '"': sb.append('"'); i += 2; break;
                case '\\': sb.append('\\'); i += 2; break;
                case '/': sb.append('/'); i += 2; break;
                case 'n': sb.append('\n'); i += 2; break;
                case 'r': sb.append('\r'); i += 2; break;
                case 't': sb.append('\t'); i += 2; break;
                case 'b': sb.append('\b'); i += 2; break;
                case 'f': sb.append('\f'); i += 2; break;
                case 'u':
                    if (i + 5 < s.length()) {
                        String hex = s.substring(i + 2, i + 6);
                        try { sb.append((char) Integer.parseInt(hex, 16)); i += 6; break; } catch (NumberFormatException ignored) {}
                    }
                    sb.append(c); i++;
                    break;
                default: sb.append(c); i++; break;
            }
        }
        return sb.toString();
    }
}
