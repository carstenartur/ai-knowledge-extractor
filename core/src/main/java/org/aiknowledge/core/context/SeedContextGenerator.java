package org.aiknowledge.core.context;

import java.io.IOException;
import org.aiknowledge.core.ExtractionOptions;
import org.aiknowledge.core.RepositorySnapshot;
import org.aiknowledge.core.SeedSupport;

public final class SeedContextGenerator {
    public void generate(ExtractionOptions options, RepositorySnapshot snapshot) throws IOException {
        SeedSupport.mergeSeeds(options, snapshot);
    }
}
