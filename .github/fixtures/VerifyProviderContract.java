import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.aiknowledge.core.AiKnowledgeRunner;
import org.aiknowledge.core.ExtractionOptions;
import org.aiknowledge.core.sourcespi.SourceFactContract;

/** Binary consumer of the public artifact; provider classes are never on the main classpath. */
class VerifyProviderContract {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("provider-contract-");
        try {
            Path source = root.resolve("repository");
            Files.createDirectories(source);
            Files.writeString(source.resolve("notes.txt"), "example");
            URL jar = Path.of(args[0]).toUri().toURL();
            try (var loader = new URLClassLoader(new URL[]{jar}, AiKnowledgeRunner.class.getClassLoader())) {
                var runner = new AiKnowledgeRunner(loader);
                var options = ExtractionOptions.defaults(source, root.resolve("valid"));
                runner.verify(options);
                String facts = Files.readString(root.resolve("valid/source-units.json"));
                if (!facts.contains("example-text") || !facts.contains("exampleAdditive")) {
                    throw new AssertionError("Independent additive-minor provider was not loaded");
                }
                String before = facts;
                runner.verify(options);
                if (!before.equals(Files.readString(root.resolve("valid/source-units.json")))) {
                    throw new AssertionError("Provider output was not deterministic");
                }
            }
            reject(root, source, jar, "fixture.IncompatibleTextProvider", "fact contract 2.0");
            reject(root, source, jar, "fixture.DuplicateTextProvider", "Duplicate source provider id");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }
    private static void reject(Path root, Path source, URL jar, String type, String diagnostic) throws Exception {
        Path registration = root.resolve(type);
        Path service = registration.resolve("META-INF/services/org.aiknowledge.core.sourcespi.SourceKnowledgeProvider");
        Files.createDirectories(service.getParent());
        Files.writeString(service, type + "\n");
        Path output = root.resolve(type + "-output");
        Files.createDirectories(output);
        Files.writeString(output.resolve("index.json"), "retained");
        try (var loader = new URLClassLoader(new URL[]{registration.toUri().toURL(), jar},
                AiKnowledgeRunner.class.getClassLoader())) {
            try {
                new AiKnowledgeRunner(loader).generate(ExtractionOptions.defaults(source, output));
                throw new AssertionError("Invalid provider was accepted: " + type);
            } catch (SourceFactContract.ContractException expected) {
                if (!expected.getMessage().contains(diagnostic)) throw expected;
            }
        }
        try (var files = Files.list(output)) {
            if (files.count() != 1 || !Files.readString(output.resolve("index.json")).equals("retained")) {
                throw new AssertionError("Contract failure changed retained artifacts");
            }
        }
    }
}
