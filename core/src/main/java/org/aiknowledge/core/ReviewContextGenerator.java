package org.aiknowledge.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates {@code review-context.md} and {@code context-packs/*.json} from a repository snapshot.
 *
 * <p>Output files written relative to the configured output directory:
 * <ul>
 *   <li>{@code review-context.md} – human and AI readable markdown summary of the repository.</li>
 *   <li>{@code context-packs/&lt;id&gt;.json} – one capability-centred context pack per capability.</li>
 *   <li>{@code context-packs/index.json} – index of all context packs with token estimates.</li>
 * </ul>
 */
final class ReviewContextGenerator {

    private ReviewContextGenerator() {}

    static void generate(ExtractionOptions options, RepositorySnapshot snapshot) throws IOException {
        Path outputDir = options.outputDirectory();
        Path packDir = outputDir.resolve("context-packs");
        Files.createDirectories(packDir);

        Map<String, String> typeToSourceFile = buildTypeToSourceFile(snapshot);

        List<Map> packIndex = new ArrayList<>();
        for (Object obj : snapshot.capabilities) {
            if (!(obj instanceof Map cap)) continue;
            String id = String.valueOf(cap.getOrDefault("id", ""));
            if (id.isBlank()) continue;

            Map pack = buildContextPack(id, cap, snapshot, typeToSourceFile);
            String packJson = JsonSupport.toJson(pack) + "\n";
            StableIo.writeText(packDir.resolve(id + ".json"), packJson);

            packIndex.add(buildIndexEntry(id, cap, packJson));
        }

        Map indexWrapper = new LinkedHashMap();
        indexWrapper.put("contextPacks", packIndex);
        StableIo.writeJson(packDir.resolve("index.json"), indexWrapper);

        StableIo.writeText(outputDir.resolve("review-context.md"),
                buildMarkdown(options, snapshot, packIndex));
    }

    // ── Context pack construction ───────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map buildContextPack(String id, Map cap, RepositorySnapshot snapshot,
            Map<String, String> typeToSourceFile) {
        String label = String.valueOf(cap.getOrDefault("label", id));
        String status = String.valueOf(cap.getOrDefault("status", "unknown"));

        List<String> modules = asList(cap.get("matchedModules"));
        List<String> types = asList(cap.get("matchedTypes"));
        if (types.isEmpty()) types = asList(cap.get("classes"));
        List<String> tests = asList(cap.get("matchedTests"));
        if (tests.isEmpty()) tests = asList(cap.get("tests"));
        List<String> docs = asList(cap.get("matchedDocs"));
        if (docs.isEmpty()) docs = asList(cap.get("docs"));
        List<String> evidence = asList(cap.get("matchedEvidence"));
        List<String> warnings = asList(cap.get("warnings"));

        List<Map> claims = findRelevantClaims(id, modules, types, tests, snapshot);

        List<String> suggestedFiles = buildSuggestedFiles(types, docs, typeToSourceFile);

        Map pack = new LinkedHashMap();
        pack.put("id", id);
        pack.put("label", label);
        pack.put("status", status);
        if (!warnings.isEmpty()) pack.put("warnings", warnings);
        pack.put("modules", modules);
        pack.put("types", types);
        pack.put("tests", tests);
        pack.put("docs", docs);
        pack.put("evidence", evidence);
        pack.put("claims", buildClaimSummaries(claims));
        pack.put("suggestedFiles", suggestedFiles);
        return pack;
    }

    private static List<String> buildSuggestedFiles(List<String> types, List<String> docs,
            Map<String, String> typeToSourceFile) {
        List<String> files = new ArrayList<>();
        for (String fqn : types) {
            String src = typeToSourceFile.get(fqn);
            if (src != null && !files.contains(src)) files.add(src);
        }
        for (String doc : docs) {
            if (!files.contains(doc)) files.add(doc);
        }
        return files;
    }

    private static List<Map> buildClaimSummaries(List<Map> claims) {
        List<Map> result = new ArrayList<>();
        for (Map claim : claims) {
            Map summary = new LinkedHashMap();
            summary.put("id", claim.getOrDefault("id", ""));
            if (claim.containsKey("category")) summary.put("category", claim.get("category"));
            summary.put("status", claim.getOrDefault("status", "unverified"));
            if (claim.containsKey("violations")) summary.put("violations", claim.get("violations"));
            result.add(summary);
        }
        return result;
    }

    private static Map buildIndexEntry(String id, Map cap, String packJson) {
        String label = String.valueOf(cap.getOrDefault("label", id));
        String status = String.valueOf(cap.getOrDefault("status", "unknown"));
        int tokenEstimate = packJson.length() / 4;

        Map entry = new LinkedHashMap();
        entry.put("id", id);
        entry.put("label", label);
        entry.put("status", status);
        entry.put("tokenEstimate", tokenEstimate);
        entry.put("file", "context-packs/" + id + ".json");
        entry.put("intendedUse", intendedUse(status));
        return entry;
    }

    private static String intendedUse(String status) {
        return switch (status) {
            case "evidence-backed" -> "Review evidence and assertions for this capability";
            case "implemented-and-tested" -> "Code review with implementation and test evidence";
            case "implemented" -> "Review implementation – tests may be missing";
            case "partial" -> "Tests exist but implementation may be incomplete";
            case "documented" -> "Documentation-focused review; implementation evidence may be missing";
            case "unknown" -> "No evidence found – investigate this capability";
            default -> "General capability review";
        };
    }

    // ── Claim relevance ─────────────────────────────────────────────────────

    private static List<Map> findRelevantClaims(String capId, List<String> modules,
            List<String> types, List<String> tests, RepositorySnapshot snapshot) {
        Set<String> moduleSet = new LinkedHashSet<>(modules);
        Set<String> typeSet = new LinkedHashSet<>(types);
        Set<String> testSet = new LinkedHashSet<>(tests);

        List<Map> result = new ArrayList<>();
        for (Object obj : snapshot.claims) {
            if (!(obj instanceof Map claim)) continue;
            if (isRelevant(capId, moduleSet, typeSet, testSet, claim)) result.add(claim);
        }
        return result;
    }

    private static boolean isRelevant(String capId, Set<String> moduleSet, Set<String> typeSet,
            Set<String> testSet, Map claim) {
        String claimId = String.valueOf(claim.getOrDefault("id", ""));
        if (capId.equals(claimId)) return true;

        List<String> scopeModules = asList(claim.get("scopeModules"));
        if (!scopeModules.isEmpty() && !moduleSet.isEmpty()) {
            for (String sm : scopeModules) {
                if (moduleSet.contains(sm)) return true;
            }
        }

        for (String vb : asList(claim.get("verifiedBy"))) {
            if (testSet.contains(vb)) return true;
        }
        for (String ib : asList(claim.get("implementedBy"))) {
            if (typeSet.contains(ib)) return true;
        }
        return false;
    }

    // ── Lookup helpers ───────────────────────────────────────────────────────

    private static Map<String, String> buildTypeToSourceFile(RepositorySnapshot snapshot) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Object obj : snapshot.classes) {
            if (!(obj instanceof Map cls)) continue;
            String fqn = String.valueOf(cls.getOrDefault("class", ""));
            String src = String.valueOf(cls.getOrDefault("sourceFile", ""));
            if (!fqn.isBlank() && !src.isBlank()) map.put(fqn, src);
        }
        for (Object obj : snapshot.tests) {
            if (!(obj instanceof Map tst)) continue;
            String fqn = String.valueOf(tst.getOrDefault("testClass", ""));
            String src = String.valueOf(tst.getOrDefault("sourceFile", ""));
            if (!fqn.isBlank() && !src.isBlank()) map.put(fqn, src);
        }
        return map;
    }

    // ── Markdown generation ──────────────────────────────────────────────────

    private static String buildMarkdown(ExtractionOptions options, RepositorySnapshot snapshot,
            List<Map> packIndex) {
        StringBuilder md = new StringBuilder();

        appendRepositoryOverview(md, options, snapshot);
        appendModuleGraph(md, snapshot);
        appendCapabilityOverview(md, snapshot);
        appendArchitectureClaims(md, snapshot);
        appendRiskAreas(md, snapshot);
        appendSuggestedContextPacks(md, packIndex);
        appendConsumptionGuide(md);

        return md.toString();
    }

    private static void appendRepositoryOverview(StringBuilder md, ExtractionOptions options,
            RepositorySnapshot snapshot) {
        Map counts = (Map) snapshot.index.getOrDefault("counts", Map.of());
        md.append("# AI Knowledge Review Context\n\n");
        md.append("## Repository Overview\n\n");
        md.append("- **Repository**: ").append(snapshot.index.getOrDefault("repository", options.repositoryRoot().getFileName())).append("\n");
        md.append("- **Generation Mode**: ").append(snapshot.index.getOrDefault("generationMode", "deterministic-static")).append("\n");
        md.append("- **Schema Version**: ").append(snapshot.index.getOrDefault("schemaVersion", 1)).append("\n");
        md.append("- **Modules**: ").append(counts.getOrDefault("modules", 0));
        md.append(" | **Classes**: ").append(counts.getOrDefault("classes", 0));
        md.append(" | **Tests**: ").append(counts.getOrDefault("tests", 0));
        md.append(" | **Documents**: ").append(counts.getOrDefault("docs", 0)).append("\n");
        md.append("- **Capabilities**: ").append(counts.getOrDefault("capabilities", 0));
        md.append(" | **Claims**: ").append(counts.getOrDefault("claims", 0));
        md.append(" | **Evidence**: ").append(counts.getOrDefault("evidence", 0)).append("\n\n");
    }

    private static void appendModuleGraph(StringBuilder md, RepositorySnapshot snapshot) {
        md.append("## Module Graph\n\n");
        if (snapshot.modules.isEmpty()) {
            md.append("_No modules detected._\n\n");
            return;
        }
        md.append("| Module | Build System | Main Packages | Project Dependencies |\n");
        md.append("|--------|-------------|---------------|---------------------|\n");
        for (Object obj : snapshot.modules) {
            if (!(obj instanceof Map module)) continue;
            String name = String.valueOf(module.getOrDefault("name", ""));
            String buildSystem = String.valueOf(module.getOrDefault("buildSystem", ""));
            String packages = String.join(", ", asList(module.get("mainPackages")));
            String projectDeps = String.join(", ", asList(module.get("projectDependencies")));
            md.append("| ").append(name)
                    .append(" | ").append(buildSystem)
                    .append(" | ").append(packages.isEmpty() ? "_none_" : packages)
                    .append(" | ").append(projectDeps.isEmpty() ? "_none_" : projectDeps)
                    .append(" |\n");
        }
        md.append("\n");
    }

    private static void appendCapabilityOverview(StringBuilder md, RepositorySnapshot snapshot) {
        md.append("## Capability Overview\n\n");
        if (snapshot.capabilities.isEmpty()) {
            md.append("_No capabilities detected._\n\n");
            return;
        }
        md.append("| ID | Label | Status | Types | Tests | Evidence |\n");
        md.append("|----|-------|--------|-------|-------|----------|\n");
        for (Object obj : snapshot.capabilities) {
            if (!(obj instanceof Map cap)) continue;
            String id = String.valueOf(cap.getOrDefault("id", ""));
            String label = String.valueOf(cap.getOrDefault("label", id));
            String status = String.valueOf(cap.getOrDefault("status", "unknown"));
            List<String> types = asList(cap.get("matchedTypes"));
            if (types.isEmpty()) types = asList(cap.get("classes"));
            List<String> tests = asList(cap.get("matchedTests"));
            if (tests.isEmpty()) tests = asList(cap.get("tests"));
            int evidenceCount = asList(cap.get("matchedEvidence")).size();
            md.append("| ").append(id)
                    .append(" | ").append(label)
                    .append(" | ").append(status)
                    .append(" | ").append(types.size())
                    .append(" | ").append(tests.size())
                    .append(" | ").append(evidenceCount)
                    .append(" |\n");
        }
        md.append("\n");
    }

    private static void appendArchitectureClaims(StringBuilder md, RepositorySnapshot snapshot) {
        md.append("## Architecture Claims\n\n");
        if (snapshot.claims.isEmpty()) {
            md.append("_No claims defined._\n\n");
            return;
        }
        md.append("| ID | Category | Status | Violations |\n");
        md.append("|----|----------|--------|------------|\n");
        for (Object obj : snapshot.claims) {
            if (!(obj instanceof Map claim)) continue;
            String id = String.valueOf(claim.getOrDefault("id", ""));
            String category = String.valueOf(claim.getOrDefault("category", ""));
            String status = String.valueOf(claim.getOrDefault("status", "unverified"));
            List<String> violations = asList(claim.get("violations"));
            String violationSummary = violations.isEmpty() ? "_none_" : violations.size() + " violation(s)";
            md.append("| ").append(id)
                    .append(" | ").append(category.isEmpty() ? "_none_" : category)
                    .append(" | ").append(status)
                    .append(" | ").append(violationSummary)
                    .append(" |\n");
        }
        md.append("\n");
    }

    private static void appendRiskAreas(StringBuilder md, RepositorySnapshot snapshot) {
        md.append("## Risk Areas\n\n");

        List<String> weakCapabilities = new ArrayList<>();
        for (Object obj : snapshot.capabilities) {
            if (!(obj instanceof Map cap)) continue;
            String status = String.valueOf(cap.getOrDefault("status", "unknown"));
            List<String> warnings = asList(cap.get("warnings"));
            if ("unknown".equals(status) || !warnings.isEmpty()) {
                String id = String.valueOf(cap.getOrDefault("id", ""));
                String label = String.valueOf(cap.getOrDefault("label", id));
                weakCapabilities.add("**" + id + "** (" + label + "): status=" + status
                        + (warnings.isEmpty() ? "" : ", warnings: " + String.join(", ", warnings)));
            }
        }

        List<String> failedClaims = new ArrayList<>();
        for (Object obj : snapshot.claims) {
            if (!(obj instanceof Map claim)) continue;
            String status = String.valueOf(claim.getOrDefault("status", ""));
            if ("failed".equals(status)) {
                String id = String.valueOf(claim.getOrDefault("id", ""));
                List<String> violations = asList(claim.get("violations"));
                failedClaims.add("**" + id + "**: " + violations.size() + " violation(s)");
            }
        }

        md.append("### Weakly Evidenced Capabilities\n\n");
        if (weakCapabilities.isEmpty()) {
            md.append("_None._\n\n");
        } else {
            for (String item : weakCapabilities) md.append("- ").append(item).append("\n");
            md.append("\n");
        }

        md.append("### Failed Claims\n\n");
        if (failedClaims.isEmpty()) {
            md.append("_None._\n\n");
        } else {
            for (String item : failedClaims) md.append("- ").append(item).append("\n");
            md.append("\n");
        }
    }

    private static void appendSuggestedContextPacks(StringBuilder md, List<Map> packIndex) {
        md.append("## Suggested Context Packs\n\n");
        if (packIndex.isEmpty()) {
            md.append("_No context packs generated._\n\n");
            return;
        }
        md.append("| ID | File | Status | Token Estimate | Intended Use |\n");
        md.append("|----|------|--------|----------------|--------------|\n");
        for (Map entry : packIndex) {
            md.append("| ").append(entry.getOrDefault("id", ""))
                    .append(" | `").append(entry.getOrDefault("file", ""))
                    .append("` | ").append(entry.getOrDefault("status", ""))
                    .append(" | ~").append(entry.getOrDefault("tokenEstimate", 0))
                    .append(" | ").append(entry.getOrDefault("intendedUse", ""))
                    .append(" |\n");
        }
        md.append("\n");
        md.append("The full index is at `context-packs/index.json`.\n\n");
    }

    private static void appendConsumptionGuide(StringBuilder md) {
        md.append("## Consumption Guide\n\n");
        md.append("**CI workflow**: Run `generateAiKnowledgeIndex` (Gradle) or `ai-knowledge:generate` (Maven) ");
        md.append("and commit the `review-context.md` to track capability health over time.\n\n");
        md.append("**AI assistant**: Load `review-context.md` for an overview, then load a specific ");
        md.append("`context-packs/<capability-id>.json` for targeted review, bug-fixing or feature work.\n\n");
        md.append("**Architecture review**: Cross-reference the _Architecture Claims_ table with ");
        md.append("the _Risk Areas_ section to identify gaps that need attention.\n");
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static List<String> asList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                String s = String.valueOf(item);
                if (!s.isBlank() && !"null".equals(s)) result.add(s);
            }
            return result;
        }
        if (value instanceof String s && !s.isBlank() && !"null".equals(s)) return List.of(s);
        return List.of();
    }
}
