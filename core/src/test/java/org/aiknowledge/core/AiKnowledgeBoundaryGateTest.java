package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.aiknowledge.core.analysis.BoundaryGateOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiKnowledgeBoundaryGateTest {
    @TempDir Path root;

    @Test void failedGateStillWritesActionableJsonAndHtmlAndStableTrendMetrics() throws Exception {
        Files.writeString(root.resolve("client.ts"), "export async function load(url: string) { return await fetch(url); }\n");
        Path output = root.resolve("build/ai-knowledge");
        var options = ExtractionOptions.defaults(root, output).withBoundaryGates(
                new BoundaryGateOptions(null, null, null, 0, null, null, null, false));
        assertEquals(0, options.withClasspathEntries(List.of()).boundaryGates().maxBoundaryDynamicCalls());
        assertThrows(java.io.IOException.class, () -> new AiKnowledgeRunner().check(options));
        Map<?, ?> check = (Map<?, ?>) StrictJsonReader.read(output.resolve("check.json"));
        assertEquals(false, check.get("passed"));
        assertTrue(check.get("boundaryQualityGate").toString().contains("client.ts"));
        assertTrue(Files.readString(output.resolve("check.html")).contains("boundaryDynamicCalls"));
        Map<?, ?> snapshot = (Map<?, ?>) StrictJsonReader.read(output.resolve("metrics-snapshot.json"));
        assertEquals(1, ((Number) snapshot.get("boundaryDynamicCalls")).intValue());
        assertEquals("1.0", snapshot.get("boundaryScoringModelVersion"));
        assertEquals(false, ((Map<?, ?>) check.get("boundaryAnalysis")).get("versionControlHistoryUsed"));
    }
}
