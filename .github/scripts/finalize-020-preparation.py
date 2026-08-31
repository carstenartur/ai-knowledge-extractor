#!/usr/bin/env python3
"""Materialize and adapt the 0.2.0 release-hardening changes on the preparation branch."""

from __future__ import annotations

from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
MATERIALIZER = Path("/tmp/apply-release-hardening.py")


def read(path: str | Path) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str | Path, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        if new in text:
            return text
        raise SystemExit(f"{label} marker missing")
    return text.replace(old, new, 1)


def run(*args: str) -> None:
    subprocess.run(args, cwd=ROOT, check=True)


def adapt_materializer() -> None:
    source = MATERIALIZER.read_text(encoding="utf-8")
    replacements = {
        'update("core/src/main/java/org/aiknowledge/core/KnowledgeExtractionPipeline.java", patch_pipeline)':
            'print("deferred KnowledgeExtractionPipeline patch for current source model")',
        "JavaScriptKnowledgeProvider": "JavaScriptTypeScriptKnowledgeProvider",
        ".callableFacts()": ".symbolFacts()",
    }
    for old, new in replacements.items():
        if old not in source:
            raise SystemExit(f"materializer compatibility marker missing: {old}")
        source = source.replace(old, new)
    MATERIALIZER.write_text(source, encoding="utf-8")


def write_result_contract() -> None:
    write(
        "core/src/main/java/org/aiknowledge/core/sourcespi/SourceKnowledgeResult.java",
        """package org.aiknowledge.core.sourcespi;

import java.util.List;
import java.util.Map;

/**
 * Language-neutral JSON-compatible facts emitted by a source provider.
 *
 * <p>Callables are symbols with {@code kind=callable}; parser-specific AST objects never cross
 * this contract. Empty categories are represented by empty lists. Recoverable limitations are
 * emitted through {@code warningFacts}; fatal I/O failures follow the shared source error policy.</p>
 */
public record SourceKnowledgeResult(
        List<Map<String, Object>> sourceUnitFacts,
        List<Map<String, Object>> symbolFacts,
        List<Map<String, Object>> relationFacts,
        List<Map<String, Object>> boundaryFacts,
        List<Map<String, Object>> warningFacts) {

    public SourceKnowledgeResult {
        sourceUnitFacts = immutable(sourceUnitFacts);
        symbolFacts = immutable(symbolFacts);
        relationFacts = immutable(relationFacts);
        boundaryFacts = immutable(boundaryFacts);
        warningFacts = immutable(warningFacts);
    }

    public static SourceKnowledgeResult empty() {
        return new SourceKnowledgeResult(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static List<Map<String, Object>> immutable(List<Map<String, Object>> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
""",
    )


def patch_pipeline() -> None:
    path = "core/src/main/java/org/aiknowledge/core/KnowledgeExtractionPipeline.java"
    pipeline = read(path)

    import_line = "import org.aiknowledge.core.sourcespi.SourceAnalysisConfiguration;"
    if import_line not in pipeline:
        marker = "import org.aiknowledge.core.sourcespi.SourceKnowledgeProvider;"
        pipeline = replace_once(
            pipeline,
            marker,
            import_line + "\n" + marker,
            "source-provider import",
        )

    field = (
        "    private final SourceAnalysisConfiguration sourceAnalysisConfiguration = "
        "SourceAnalysisConfiguration.fromSystemProperties();\n"
    )
    if "private final SourceAnalysisConfiguration sourceAnalysisConfiguration" not in pipeline:
        marker = "    private final List<SourceKnowledgeProvider> sourceKnowledgeProviders;\n"
        pipeline = replace_once(pipeline, marker, marker + field, "source-provider field")

    old_loop = """                if (!provider.supports(path)) continue;
                SourceKnowledgeResult result = provider.extract(new SourceKnowledgeRequest(
                        root,
                        file,
                        path,
                        snapshot.modules,
                        buildMetadata,
                        Map.of()));
                recordSourceFacts(snapshot, provider, path, result);"""
    new_loop = """                if (!sourceAnalysisConfiguration.providerEnabled(provider.id())
                        || !provider.supports(path)
                        || !sourceAnalysisConfiguration.acceptsSource(file, path)) {
                    continue;
                }
                try {
                    SourceKnowledgeResult result = provider.extract(new SourceKnowledgeRequest(
                            root,
                            file,
                            path,
                            snapshot.modules,
                            buildMetadata,
                            Map.of()));
                    recordSourceFacts(snapshot, provider, path, result);
                } catch (Exception exception) {
                    handleSourceProviderFailure(snapshot, provider, path, exception);
                }"""
    if old_loop in pipeline:
        pipeline = pipeline.replace(old_loop, new_loop, 1)
    elif new_loop not in pipeline:
        raise SystemExit("current provider loop marker missing")

    if "private void handleSourceProviderFailure(" not in pipeline:
        marker = "    private static void recordJavaFacts(RepositorySnapshot snapshot) {"
        handler = """    private void handleSourceProviderFailure(
            RepositorySnapshot snapshot,
            SourceKnowledgeProvider provider,
            String sourcePath,
            Exception exception) throws IOException {
        if (sourceAnalysisConfiguration.errorPolicy() == SourceAnalysisConfiguration.ErrorPolicy.FAIL) {
            if (exception instanceof IOException ioException) throw ioException;
            throw new IOException("Source provider " + provider.id() + " failed for " + sourcePath, exception);
        }
        if (sourceAnalysisConfiguration.errorPolicy() == SourceAnalysisConfiguration.ErrorPolicy.WARN) {
            Map<String, Object> warning = new LinkedHashMap<>();
            warning.put("provider", provider.id());
            warning.put("sourceFile", sourcePath);
            warning.put("code", "source-provider-failure");
            warning.put("message", exception.getClass().getSimpleName() + ": "
                    + String.valueOf(exception.getMessage()));
            snapshot.warnings.add(warning);
        }
    }

"""
        pipeline = replace_once(pipeline, marker, handler + marker, "recordJavaFacts")

    evidence = "        snapshot.evidence.add(sourceAnalysisConfiguration.asEvidence());\n"
    if evidence not in pipeline:
        marker = "        RepositoryFacts.populateIndex(root, snapshot);"
        pipeline = replace_once(pipeline, marker, evidence + marker, "repository index")

    write(path, pipeline)


def patch_provider_documentation() -> None:
    path = "docs/provider-spi.md"
    docs = read(path)
    old = (
        "- `symbolFacts`: types, fields, values and declarations;\n"
        "- `callableFacts`: functions, methods, hooks or equivalent executable symbols;"
    )
    new = (
        "- `symbolFacts`: types, fields, values, declarations and executable symbols with "
        "`kind=callable`;"
    )
    if old in docs:
        docs = docs.replace(old, new, 1)
    write(path, docs)


def set_candidate_version() -> None:
    gradle = ROOT / "gradle.properties"
    lines = [
        line
        for line in gradle.read_text(encoding="utf-8").splitlines()
        if not line.startswith("projectVersion=")
    ]
    lines.append("projectVersion=0.2.0-SNAPSHOT")
    gradle.write_text("\n".join(lines) + "\n", encoding="utf-8")

    write(
        "release.properties",
        "# Managed by the audited release workflow.\nnext.release.version=0.2.0\n",
    )

    plugin_path = "maven/src/main/resources/META-INF/maven/plugin.xml"
    plugin = read(plugin_path)
    plugin, count = re.subn(
        r"(<version>)[^<]+(</version>)",
        r"\g<1>0.2.0-SNAPSHOT\g<2>",
        plugin,
        count=1,
    )
    if count != 1:
        raise SystemExit("Maven plugin descriptor version marker missing")
    write(plugin_path, plugin)

    run("python3", ".github/scripts/update-release-metadata.py", "0.2.0-SNAPSHOT")


def main() -> None:
    if not MATERIALIZER.is_file():
        raise SystemExit(f"materializer not found: {MATERIALIZER}")
    adapt_materializer()
    run("python3", str(MATERIALIZER))
    write_result_contract()
    patch_pipeline()
    patch_provider_documentation()
    set_candidate_version()


if __name__ == "__main__":
    main()
