package org.aiknowledge.core.sourcespi;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SourceFactContractTest {
    @Test void preservesAdditiveFactsAndNormalizesProvenance() {
        SourceFactContract.requireCompatible("example", "1.99");
        var fields = new LinkedHashMap<String, Object>(Map.of("kind", "EXTERNAL_KIND",
                "source", "s", "target", "external:t", "newField", List.of(Map.of("future", true))));
        var result = SourceFactContract.normalize(new SourceKnowledgeResult(List.of(), List.of(),
                List.of(fields), List.of(), List.of()), "example", "src/a.txt");
        fields.put("newField", false);
        var fact = result.relationFacts().get(0);
        assertEquals("EXTERNAL_KIND", fact.get("kind"));
        assertEquals(List.of(Map.of("future", true)), fact.get("newField"));
        assertEquals("example", fact.get("provider"));
        assertEquals("provider-defined", fact.get("confidence"));
        assertThrows(UnsupportedOperationException.class, () -> fact.put("x", true));
    }
    @Test void rejectsIncompatibleVersionsAndMalformedFacts() {
        for (String version : List.of("2.0", "0.1", "1", "1.-1", "bogus")) {
            assertThrows(SourceFactContract.ContractException.class,
                    () -> SourceFactContract.requireCompatible("example", version));
        }
        var missing = new SourceKnowledgeResult(List.of(Map.of("kind", "file")), null, null, null, null);
        assertThrows(SourceFactContract.ContractException.class,
                () -> SourceFactContract.normalize(missing, "example", "src/a.txt"));
        var invalid = new SourceKnowledgeResult(List.of(Map.of("kind", "file", "id", "s",
                "language", "text", "ast", new Object())), null, null, null, null);
        assertThrows(SourceFactContract.ContractException.class,
                () -> SourceFactContract.normalize(invalid, "example", "src/a.txt"));
    }
    @Test void rejectsDuplicateIdsIndependentlyOfOrder() {
        var a = provider("same", "1.0");
        var b = provider("same", "1.1");
        var first = assertThrows(SourceFactContract.ContractException.class,
                () -> SourceProviderRegistry.validate(List.of(a, b)));
        var second = assertThrows(SourceFactContract.ContractException.class,
                () -> SourceProviderRegistry.validate(List.of(b, a)));
        assertEquals(first.getMessage(), second.getMessage());
        assertTrue(first.getMessage().contains("Duplicate source provider id"));
        assertThrows(SourceFactContract.ContractException.class,
                () -> SourceProviderRegistry.validate(List.of(provider("other", "2.0"))));
    }
    private SourceKnowledgeProvider provider(String id, String version) {
        return new SourceKnowledgeProvider() {
            public String id() { return id; }
            public String contractVersion() { return version; }
            public boolean supports(String path) { return true; }
            public SourceKnowledgeResult extract(SourceKnowledgeRequest request) { return SourceKnowledgeResult.empty(); }
        };
    }
}
