package fixture;

import java.util.List;
import java.util.Map;
import org.aiknowledge.core.sourcespi.SourceFactContract;
import org.aiknowledge.core.sourcespi.SourceKnowledgeProvider;
import org.aiknowledge.core.sourcespi.SourceKnowledgeRequest;
import org.aiknowledge.core.sourcespi.SourceKnowledgeResult;

/** Independent binary fixture: depends only on the published core API. */
public class TextProvider implements SourceKnowledgeProvider {
    public String id() { return "example-text"; }
    public String contractVersion() { return "1.1"; }
    public boolean supports(String path) { return path.endsWith(".txt"); }
    public SourceKnowledgeResult extract(SourceKnowledgeRequest request) {
        String id = "text:" + request.sourcePath();
        return new SourceKnowledgeResult(List.of(Map.of(
                SourceFactContract.ID, id,
                SourceFactContract.KIND, "document",
                SourceFactContract.LANGUAGE, "text",
                "exampleAdditive", true)), List.of(), List.of(Map.of(
                SourceFactContract.KIND, "EXAMPLE_REFERENCES_TEXT",
                SourceFactContract.SOURCE, id,
                SourceFactContract.TARGET, "external:text-format")), List.of(), List.of());
    }
}
