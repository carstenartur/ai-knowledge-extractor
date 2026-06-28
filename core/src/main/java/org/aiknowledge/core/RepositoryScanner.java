package org.aiknowledge.core;

import java.io.File;
import java.io.IOException;

final class RepositoryScanner {
    private final KnowledgeExtractionPipeline pipeline;

    RepositoryScanner() {
        this(new KnowledgeExtractionPipeline());
    }

    RepositoryScanner(KnowledgeExtractionPipeline pipeline) {
        this.pipeline = pipeline;
    }

    RepositorySnapshot scan(ExtractionOptions options) throws IOException {
        return pipeline.extract(options);
    }
}
