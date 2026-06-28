package org.aiknowledge.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MarkdownMetadata {
    private MarkdownMetadata() {
    }

    public static List links(String text) {
        List links = new ArrayList();
        int pos = 0;
        while ((pos = text.indexOf("](", pos)) >= 0) {
            int left = text.lastIndexOf('[', pos);
            int right = text.indexOf(')', pos + 2);
            if (left >= 0 && right > pos) {
                Map link = new LinkedHashMap();
                link.put("text", text.substring(left + 1, pos));
                link.put("target", text.substring(pos + 2, right));
                links.add(link);
                pos = right + 1;
            } else {
                pos += 2;
            }
        }
        return links;
    }
}
