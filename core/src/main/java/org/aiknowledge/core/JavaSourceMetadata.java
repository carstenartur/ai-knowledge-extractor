package org.aiknowledge.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JavaSourceMetadata {
    private JavaSourceMetadata() {
    }

    static void enrich(Map data, String source, String simpleName, boolean test) {
        data.put("kind", kind(source));
        data.put("imports", imports(source));
        data.put("superclass", superclass(source));
        data.put("interfaces", interfaces(source));
        if (test) {
            data.put("testedClass", testedClass(String.valueOf(data.get("package")), simpleName));
            data.put("tags", tags(source));
        } else {
            data.put("referencedProjectClasses", referencedProjectClasses((List) data.get("imports")));
        }
    }

    private static String kind(String source) {
        String text = " " + source.replace('\n', ' ').replace('\r', ' ') + " ";
        if (text.contains(" interface ")) return "interface";
        if (text.contains(" record ")) return "record";
        if (text.contains(" enum ")) return "enum";
        return "class";
    }

    private static List imports(String source) {
        List result = new ArrayList();
        for (String line : source.split("\\R")) {
            String text = line.trim();
            if (text.startsWith("import ")) result.add(text.substring("import ".length()).replace("static ", "").replace(";", "").trim());
        }
        return result;
    }

    private static String superclass(String source) {
        String text = source.replace('\n', ' ').replace('\r', ' ');
        int idx = text.indexOf(" extends ");
        if (idx < 0) return "";
        String tail = text.substring(idx + " extends ".length()).trim();
        return firstTypeName(tail);
    }

    private static List interfaces(String source) {
        List result = new ArrayList();
        String text = source.replace('\n', ' ').replace('\r', ' ');
        int idx = text.indexOf(" implements ");
        if (idx < 0) return result;
        String tail = text.substring(idx + " implements ".length());
        int brace = tail.indexOf('{');
        if (brace >= 0) tail = tail.substring(0, brace);
        for (String part : tail.split(",")) {
            String name = firstTypeName(part.trim());
            if (!name.isBlank()) result.add(name);
        }
        return result;
    }

    private static String testedClass(String packageName, String simpleName) {
        if (!simpleName.endsWith("Test") || simpleName.length() <= 4) return "";
        String base = simpleName.substring(0, simpleName.length() - 4);
        return packageName == null || packageName.isBlank() ? base : packageName + "." + base;
    }

    private static List tags(String source) {
        List result = new ArrayList();
        for (String line : source.split("\\R")) {
            String text = line.trim();
            if (text.startsWith("@Tag(")) {
                int end = text.indexOf(')');
                if (end > "@Tag(".length()) {
                    String value = text.substring("@Tag(".length(), end).replace("\"", "").replace("'", "").trim();
                    if (!value.isBlank()) result.add(value);
                }
            }
        }
        return result;
    }

    private static List referencedProjectClasses(List imports) {
        List result = new ArrayList();
        for (Object object : imports) {
            String value = String.valueOf(object);
            if (!value.startsWith("java.") && !value.startsWith("javax.") && !result.contains(value)) result.add(value);
        }
        return result;
    }

    private static String firstTypeName(String text) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isJavaIdentifierPart(c) || c == '.' || c == '_') builder.append(c); else break;
        }
        return builder.toString();
    }
}
