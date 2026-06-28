package org.aiknowledge.core.javaspi;

import java.io.IOException;

public interface JavaKnowledgeProvider {
    JavaKnowledgeResult extract(JavaKnowledgeRequest request) throws IOException;
}
