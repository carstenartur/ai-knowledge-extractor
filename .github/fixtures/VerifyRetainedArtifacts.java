import java.nio.file.Path;
import org.aiknowledge.core.AiKnowledgeArtifactVerifier;

/** Runs the released verifier on retained outputs, without generating any files. */
class VerifyRetainedArtifacts {
    public static void main(String[] args) throws Exception {
        var report = new AiKnowledgeArtifactVerifier().verifyCompleteLifecycle(Path.of(args[0]));
        if (!report.passed()) throw new IllegalStateException(String.join("; ", report.errors()));
    }
}
