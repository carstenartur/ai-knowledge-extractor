package org.aiknowledge.core.analysis;

import java.util.Locale;

/** Shared route-template normalization for client and server providers. */
public final class BoundaryPath {
    public static final String DYNAMIC = "<dynamic>";

    private BoundaryPath() {
    }

    public static String normalize(String rawPath) {
        if (rawPath == null) return "";
        String value = rawPath.trim();
        if (value.isEmpty()) return "/";
        if (DYNAMIC.equals(value)) return DYNAMIC;

        value = value.replace("\\/", "/");
        value = value.replaceAll("(?i)^https?://[^/]+", "");
        value = value.replaceAll("\\$\\{[^}]+}", "{}");
        value = value.replaceAll("\\{[^}]+}", "{}");
        value = value.replaceAll("(?<=/):[A-Za-z_$][\\w$-]*", "{}");
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int fragment = value.indexOf('#');
        if (fragment >= 0) value = value.substring(0, fragment);
        value = value.replaceAll("/{2,}", "/");
        if (!value.startsWith("/")) value = "/" + value;
        if (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static String normalizeMethod(Object value) {
        String method = value == null ? "" : String.valueOf(value).trim();
        return method.isEmpty() ? "*" : method.toUpperCase(Locale.ROOT);
    }
}
