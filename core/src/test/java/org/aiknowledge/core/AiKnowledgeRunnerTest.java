package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiKnowledgeRunnerTest {
    @TempDir
    Path temp;

    @Test
    void generateWritesCoreArtifacts() throws Exception {
        Path project = temp.resolve("fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("src/test/java/example"));
        Files.createDirectories(project.resolve("docs"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/App.java"), "package example;\n\npublic class App {\n    public void run() {}\n}\n");
        Files.writeString(project.resolve("src/test/java/example/AppTest.java"), "package example;\n\nclass AppTest {\n    @org.junit.jupiter.api.Test\n    void run() {}\n}\n");
        Files.writeString(project.resolve("docs/design.md"), "# Design\n");

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        assertTrue(Files.isRegularFile(output.resolve("index.json")));
        assertTrue(Files.isRegularFile(output.resolve("modules.json")));
        assertTrue(Files.isRegularFile(output.resolve("classes.json")));
        assertTrue(Files.isRegularFile(output.resolve("tests.json")));
        assertTrue(Files.isRegularFile(output.resolve("docs.json")));
        assertTrue(Files.readString(output.resolve("index.json")).contains("schemaVersion"));
        assertTrue(Files.readString(output.resolve("classes.json")).contains("example.App"));
    }

    @Test
    void generateWritesEnrichedScannerMetadata() throws Exception {
        Path project = temp.resolve("scanner-fixture");
        Files.createDirectories(project.resolve("src/main/java/example/feature"));
        Files.createDirectories(project.resolve("src/test/java/example/feature"));
        Files.createDirectories(project.resolve("docs"));
        Files.writeString(project.resolve("build.gradle"), """
                plugins { id 'java' }
                implementation 'org.example:library:1.0'
                testImplementation project(':test-support')
                """);
        Files.writeString(project.resolve("src/main/java/example/feature/SearchStrategies.java"), """
                package example.feature;

                import example.shared.Helper;
                import java.util.List;

                public class SearchStrategies extends BaseSearch implements StrategyApi {
                    public void run() {}
                    protected String name() { return "search"; }
                }
                """);
        Files.writeString(project.resolve("src/test/java/example/feature/SearchStrategiesTest.java"), """
                package example.feature;

                import org.junit.jupiter.api.Tag;

                class SearchStrategiesTest {
                    @Tag("fast")
                    @org.junit.jupiter.api.Test
                    void run() {}
                }
                """);
        Files.writeString(project.resolve("docs/search-strategies.md"), "# Search Strategies\nSee [SearchStrategies](../src/main/java/example/feature/SearchStrategies.java).\n");

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String modules = Files.readString(output.resolve("modules.json"));
        String classes = Files.readString(output.resolve("classes.json"));
        String tests = Files.readString(output.resolve("tests.json"));
        String docs = Files.readString(output.resolve("docs.json"));
        String dependencies = Files.readString(output.resolve("dependencies.json"));
        String capabilities = Files.readString(output.resolve("capabilities.json"));
        String claims = Files.readString(output.resolve("claims.json"));

        assertTrue(modules.contains("sourceSets"));
        assertTrue(modules.contains("main/java"));
        assertTrue(modules.contains("externalDependencies"));
        assertTrue(modules.contains("projectDependencies"));
        assertTrue(classes.contains("kind"));
        assertTrue(classes.contains("imports"));
        assertTrue(classes.contains("superclass"));
        assertTrue(classes.contains("BaseSearch"));
        assertTrue(classes.contains("interfaces"));
        assertTrue(classes.contains("StrategyApi"));
        assertTrue(classes.contains("referencedProjectClasses"));
        assertTrue(classes.contains("example.shared.Helper"));
        assertTrue(tests.contains("testedClass"));
        assertTrue(tests.contains("example.feature.SearchStrategies"));
        assertTrue(tests.contains("tags"));
        assertTrue(tests.contains("fast"));
        assertTrue(docs.contains("links"));
        assertTrue(docs.contains("SearchStrategies"));
        assertTrue(dependencies.contains("\"buildSystem\":\"gradle\""));
        assertTrue(dependencies.contains("\"scope\":\"implementation\""));
        assertTrue(dependencies.contains("\"scope\":\"testImplementation\""));
        assertTrue(capabilities.contains("search-strategies"));
        assertTrue(capabilities.contains("implemented"));
        assertTrue(claims.contains("implementedBy"));
        assertTrue(claims.contains("verifiedBy"));
        assertTrue(claims.contains("documentedBy"));
    }

    @Test
    void generateScansMavenBuildFiles() throws Exception {
        Path project = temp.resolve("maven-fixture");
        Files.createDirectories(project.resolve("src/main/java/example/maven"));
        Files.writeString(project.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>maven-fixture</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.example</groupId>
                      <artifactId>sample-lib</artifactId>
                      <version>1.2.3</version>
                      <scope>runtime</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);
        Files.writeString(project.resolve("src/main/java/example/maven/MavenType.java"), "package example.maven;\npublic class MavenType {}\n");

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String modules = Files.readString(output.resolve("modules.json"));
        String dependencies = Files.readString(output.resolve("dependencies.json"));
        assertTrue(modules.contains("\"buildSystem\":\"maven\""));
        assertTrue(dependencies.contains("org.example:sample-lib:1.2.3"));
        assertTrue(dependencies.contains("\"scope\":\"runtime\""));
        assertTrue(dependencies.contains("\"buildSystem\":\"maven\""));
    }

    @Test
    void generateIsDeterministicAcrossRepeatedRuns() throws Exception {
        Path project = temp.resolve("deterministic-fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\nimplementation 'org.example:library:1.0'\n");
        Files.writeString(project.resolve("src/main/java/example/App.java"), "package example;\npublic class App {}\n");

        Path first = project.resolve("build/ai-knowledge-one");
        Path second = project.resolve("build/ai-knowledge-two");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, first));
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, second));

        for (String artifact : new String[] {"index.json", "modules.json", "classes.json", "tests.json", "docs.json", "dependencies.json", "capabilities.json", "claims.json"}) {
            assertEquals(Files.readString(first.resolve(artifact)), Files.readString(second.resolve(artifact)), artifact + " should be deterministic");
        }
    }

    @Test
    void generateScopesModulePackagesAndHandlesMalformedTag() throws Exception {
        Path project = temp.resolve("multi-module-fixture");
        Files.createDirectories(project.resolve("api/src/main/java/api/pkg"));
        Files.createDirectories(project.resolve("api/src/test/java/api/pkg"));
        Files.createDirectories(project.resolve("service/src/main/java/service/pkg"));
        Files.writeString(project.resolve("api/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("service/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("api/src/main/java/api/pkg/ApiType.java"), "package api.pkg;\npublic class ApiType {}\n");
        Files.writeString(project.resolve("api/src/main/java/DefaultType.java"), "public class DefaultType {}\n");
        Files.writeString(project.resolve("api/src/test/java/api/pkg/ApiTypeTest.java"), """
                package api.pkg;

                class ApiTypeTest {
                    @Tag(
                    @org.junit.jupiter.api.Test
                    void run() {}
                }
                """);
        Files.writeString(project.resolve("service/src/main/java/service/pkg/ServiceType.java"), "package service.pkg;\npublic class ServiceType {}\n");

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String modules = Files.readString(output.resolve("modules.json"));
        String tests = Files.readString(output.resolve("tests.json"));
        String apiModule = objectContaining(modules, "\"path\":\"api\"");
        String serviceModule = objectContaining(modules, "\"path\":\"service\"");

        assertTrue(apiModule.contains("api.pkg"));
        assertFalse(apiModule.contains("service.pkg"));
        assertFalse(apiModule.contains("\"mainPackages\":[\"\"]"));
        assertTrue(serviceModule.contains("service.pkg"));
        assertFalse(serviceModule.contains("api.pkg"));
        assertTrue(tests.contains("ApiTypeTest"));
    }

    @Test
    void generateMergesSeedListsAdditivelyAndHandlesQuoteEdges() throws Exception {
        Path project = temp.resolve("seed-fixture");
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("ai-knowledge/capabilities.seed.yaml"), """
                - id: seeded-capability
                  status: partial
                  classes: ['example.App']
                - id: seeded-capability
                  classes: ['example.App', 'example.Other']
                - id: quote-edge
                  status: '
                """);
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: seeded-claim
                  implementedBy: ['example.App']
                - id: seeded-claim
                  implementedBy: ['example.App', 'example.Other']
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String capabilities = Files.readString(output.resolve("capabilities.json"));
        String claims = Files.readString(output.resolve("claims.json"));
        assertTrue(capabilities.contains("seeded-capability"));
        assertTrue(capabilities.contains("quote-edge"));
        assertEquals(1, occurrences(capabilities, "example.App"));
        assertTrue(capabilities.contains("example.Other"));
        assertTrue(claims.contains("seeded-claim"));
        assertEquals(1, occurrences(claims, "example.App"));
        assertTrue(claims.contains("example.Other"));
    }

    @Test
    void capabilitySelectorsLinkEvidenceFromModulesTestsAndDiscovery() throws Exception {
        Path project = temp.resolve("selector-fixture");
        Files.createDirectories(project.resolve("search/src/main/java/de/regelsuche/search"));
        Files.createDirectories(project.resolve("search/src/test/java/de/regelsuche/search"));
        Files.createDirectories(project.resolve("docs/generated/discovery/complete-square"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.createDirectories(project.resolve(".github/workflows"));

        Files.writeString(project.resolve("search/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("search/src/main/java/de/regelsuche/search/RewriteSearch.java"),
                "package de.regelsuche.search;\npublic class RewriteSearch {}\n");
        Files.writeString(project.resolve("search/src/test/java/de/regelsuche/search/RewriteSearchTest.java"),
                "package de.regelsuche.search;\nclass RewriteSearchTest { @org.junit.jupiter.api.Test void run() {} }\n");
        Files.writeString(project.resolve("docs/generated/discovery/complete-square/evidence.json"), """
                {
                  "scenarioId": "complete-square-factorization",
                  "success": true,
                  "oracleStatus": "PROVED"
                }
                """);
        Files.writeString(project.resolve(".github/workflows/ci.yml"), "name: CI\non: [push]\njobs:\n  build:\n    runs-on: ubuntu-latest\n    steps:\n      - run: echo ok\n");
        Files.writeString(project.resolve("ai-knowledge/capabilities.seed.yaml"), """
                - id: rewrite-search
                  label: Rewrite Search
                  modules: [search]
                  packages: [de.regelsuche.search]
                  typePatterns: ['*Search*', '*Rewrite*']
                  testPatterns: ['*Search*Test']
                  docPatterns: ['docs/**/search*.md']
                  evidenceTypes: [discovery-evidence]
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String capabilities = Files.readString(output.resolve("capabilities.json"));

        // Selector-based linking must populate matched fields
        assertTrue(capabilities.contains("\"id\":\"rewrite-search\""), "id must be present");
        assertTrue(capabilities.contains("Rewrite Search"), "label from seed must be preserved");
        assertTrue(capabilities.contains("matchedModules"), "matchedModules field must be present");
        assertTrue(capabilities.contains("matchedPackages"), "matchedPackages field must be present");
        assertTrue(capabilities.contains("matchedTypes"), "matchedTypes field must be present");
        assertTrue(capabilities.contains("matchedTests"), "matchedTests field must be present");
        assertTrue(capabilities.contains("matchedEvidence"), "matchedEvidence field must be present");
        assertTrue(capabilities.contains("de.regelsuche.search.RewriteSearch"), "matched type must be listed");
        assertTrue(capabilities.contains("de.regelsuche.search.RewriteSearchTest"), "matched test must be listed");
        assertTrue(capabilities.contains("discovery-evidence"), "matched evidence type must be listed");

        // Status must reflect evidence (discovery-evidence → evidence-backed)
        assertTrue(capabilities.contains("evidence-backed"), "status must be evidence-backed when evidence is linked");

        // No fixed-ID fallback capabilities must appear when seeds are present
        assertFalse(capabilities.contains("equality-saturation"), "fixed fallback IDs must not appear when seeds are loaded");
    }

    @Test
    void capabilityLinkerAddsWarningWhenNoEvidenceFound() throws Exception {
        Path project = temp.resolve("no-evidence-fixture");
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("ai-knowledge/capabilities.seed.yaml"), """
                - id: unimplemented-feature
                  label: Unimplemented Feature
                  typePatterns: ['*Nonexistent*']
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String capabilities = Files.readString(output.resolve("capabilities.json"));
        assertTrue(capabilities.contains("\"status\":\"unknown\""), "status must be unknown when nothing matched");
        assertTrue(capabilities.contains("no-evidence-found"), "warning must be emitted when no evidence found");
    }


    @Test
    void optimizeDetectsMultipleSmellTypesAndRanksRecommendations() throws Exception {
        Path project = temp.resolve("smell-fixture");
        Files.createDirectories(project.resolve("src/main/java/alpha"));
        Files.createDirectories(project.resolve("src/main/java/beta"));
        Files.createDirectories(project.resolve("src/main/java/gamma"));
        Files.createDirectories(project.resolve("docs"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");

        // Concept Cycle: alpha imports beta and beta imports alpha
        Files.writeString(project.resolve("src/main/java/alpha/AlphaClass.java"),
                "package alpha;\nimport beta.BetaClass;\npublic class AlphaClass {}\n");
        Files.writeString(project.resolve("src/main/java/beta/BetaClass.java"),
                "package beta;\nimport alpha.AlphaClass;\npublic class BetaClass {}\n");

        // Scattered Capability + Weak Evidence: equality-saturation classes across three packages but no test
        Files.writeString(project.resolve("src/main/java/alpha/EqualitySaturation.java"),
                "package alpha;\npublic class EqualitySaturation {}\n");
        Files.writeString(project.resolve("src/main/java/beta/EqualitySaturationHelper.java"),
                "package beta;\npublic class EqualitySaturationHelper {}\n");
        Files.writeString(project.resolve("src/main/java/gamma/EqualitySaturationUtil.java"),
                "package gamma;\npublic class EqualitySaturationUtil {}\n");

        // Duplicate Knowledge: two docs with identical normalised title
        Files.writeString(project.resolve("docs/design.md"), "# Design Guide\n");
        Files.writeString(project.resolve("docs/design-2.md"), "# Design Guide\n");

        Path output = project.resolve("build/ai-knowledge");
        Map report = new AiKnowledgeRunner().optimize(ExtractionOptions.defaults(project, output));

        assertTrue(Files.isRegularFile(output.resolve("optimization.json")));
        assertTrue(Files.isRegularFile(output.resolve("optimization.html")));

        String json = Files.readString(output.resolve("optimization.json"));
        assertTrue(json.contains("\"before\""), "should contain before estimate");
        assertTrue(json.contains("\"afterEstimate\""), "should contain afterEstimate");
        assertTrue(json.contains("Hidden Concept"), "should detect Hidden Concept");
        assertTrue(json.contains("Concept Cycle"), "should detect Concept Cycle");
        assertTrue(json.contains("Duplicate Knowledge"), "should detect Duplicate Knowledge");
        assertTrue(json.contains("Scattered Capability"), "should detect Scattered Capability");
        assertTrue(json.contains("Weak Evidence"), "should detect Weak Evidence");

        // Verify at least five distinct smell types are present
        List smells = (List) report.get("smells");
        long distinctTypes = smells.stream()
                .map(s -> String.valueOf(((Map) s).get("type")))
                .distinct()
                .count();
        assertTrue(distinctTypes >= 5, "at least five distinct smell types must be detected, found: " + distinctTypes);

        // Verify recommendations are sorted by estimated token savings descending
        List recommendations = (List) report.get("recommendations");
        for (int i = 1; i < recommendations.size(); i++) {
            int prev = ((Number) ((Map) recommendations.get(i - 1)).get("estimatedTokenSavings")).intValue();
            int curr = ((Number) ((Map) recommendations.get(i)).get("estimatedTokenSavings")).intValue();
            assertTrue(prev >= curr, "recommendations must be sorted by savings descending at index " + i);
        }

        // Verify before/after token estimates are present and afterEstimate reflects savings
        Map before = (Map) report.get("before");
        Map after = (Map) report.get("afterEstimate");
        assertTrue(((Number) before.get("estimatedContextTokens")).intValue() > 0);
        assertTrue(((Number) after.get("estimatedTokenSavings")).intValue() > 0);
    }

    @Test
    void generateWritesReviewContextAndContextPacks() throws Exception {
        Path project = temp.resolve("review-ctx-fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("src/test/java/example"));
        Files.createDirectories(project.resolve("docs"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/SearchService.java"),
                "package example;\npublic class SearchService {}\n");
        Files.writeString(project.resolve("src/test/java/example/SearchServiceTest.java"),
                "package example;\nclass SearchServiceTest { @org.junit.jupiter.api.Test void run() {} }\n");
        Files.writeString(project.resolve("docs/search.md"), "# Search\n");
        Files.writeString(project.resolve("ai-knowledge/capabilities.seed.yaml"), """
                - id: search
                  label: Search Service
                  packages: [example]
                  typePatterns: ['*Search*']
                  testPatterns: ['*SearchServiceTest']
                  docPatterns: ['docs/search.md']
                """);
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: search
                  category: quality
                  verifiedBy: [example.SearchServiceTest]
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        // review-context.md exists and has required sections
        assertTrue(Files.isRegularFile(output.resolve("review-context.md")));
        String md = Files.readString(output.resolve("review-context.md"));
        assertTrue(md.contains("## Repository Overview"), "must have Repository Overview section");
        assertTrue(md.contains("## Module Graph"), "must have Module Graph section");
        assertTrue(md.contains("## Capability Overview"), "must have Capability Overview section");
        assertTrue(md.contains("## Architecture Claims"), "must have Architecture Claims section");
        assertTrue(md.contains("## Risk Areas"), "must have Risk Areas section");
        assertTrue(md.contains("## Suggested Context Packs"), "must have Suggested Context Packs section");
        assertTrue(md.contains("## Consumption Guide"), "must have Consumption Guide section");
        assertTrue(md.contains("search"), "capability id must appear in markdown");
        assertTrue(md.contains("Search Service"), "capability label must appear in markdown");

        // context-packs/index.json exists
        assertTrue(Files.isRegularFile(output.resolve("context-packs/index.json")));
        String packIndex = Files.readString(output.resolve("context-packs/index.json"));
        assertTrue(packIndex.contains("\"id\":\"search\""), "index must list search pack");
        assertTrue(packIndex.contains("tokenEstimate"), "index must include token estimate");
        assertTrue(packIndex.contains("intendedUse"), "index must include intended use");
        assertTrue(packIndex.contains("context-packs/search.json"), "index must reference pack file");

        // context-packs/search.json exists with expected structure
        assertTrue(Files.isRegularFile(output.resolve("context-packs/search.json")));
        String pack = Files.readString(output.resolve("context-packs/search.json"));
        assertTrue(pack.contains("\"id\":\"search\""), "pack must have id");
        assertTrue(pack.contains("\"label\":\"Search Service\""), "pack must have label");
        assertTrue(pack.contains("example.SearchService"), "pack must list matched type");
        assertTrue(pack.contains("example.SearchServiceTest"), "pack must list matched test");
        assertTrue(pack.contains("docs/search.md"), "pack must list matched doc");
        assertTrue(pack.contains("\"claims\""), "pack must have claims field");
        assertTrue(pack.contains("\"suggestedFiles\""), "pack must have suggestedFiles field");
    }

    @Test
    void generateContextPacksAreDeterministic() throws Exception {
        Path project = temp.resolve("deterministic-packs-fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/Alpha.java"),
                "package example;\npublic class Alpha {}\n");
        Files.writeString(project.resolve("ai-knowledge/capabilities.seed.yaml"), """
                - id: alpha-cap
                  label: Alpha Capability
                  packages: [example]
                """);

        Path first = project.resolve("build/ai-knowledge-one");
        Path second = project.resolve("build/ai-knowledge-two");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, first));
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, second));

        assertEquals(Files.readString(first.resolve("review-context.md")),
                Files.readString(second.resolve("review-context.md")),
                "review-context.md must be deterministic");
        assertEquals(Files.readString(first.resolve("context-packs/index.json")),
                Files.readString(second.resolve("context-packs/index.json")),
                "context-packs/index.json must be deterministic");
        assertEquals(Files.readString(first.resolve("context-packs/alpha-cap.json")),
                Files.readString(second.resolve("context-packs/alpha-cap.json")),
                "context-packs/alpha-cap.json must be deterministic");
    }

    @Test
    void generateContextPackLinksClaimsAndSuggestsFilesForRegelsucheCapability() throws Exception {
        Path project = temp.resolve("regelsuche-pack-fixture");
        Files.createDirectories(project.resolve("search/src/main/java/de/regelsuche/search"));
        Files.createDirectories(project.resolve("search/src/test/java/de/regelsuche/search"));
        Files.createDirectories(project.resolve("docs/generated/discovery/complete-square"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.createDirectories(project.resolve(".github/workflows"));

        Files.writeString(project.resolve("search/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("search/src/main/java/de/regelsuche/search/RewriteSearch.java"),
                "package de.regelsuche.search;\npublic class RewriteSearch {}\n");
        Files.writeString(project.resolve("search/src/test/java/de/regelsuche/search/RewriteSearchTest.java"),
                "package de.regelsuche.search;\nclass RewriteSearchTest { @org.junit.jupiter.api.Test void run() {} }\n");
        Files.writeString(project.resolve("docs/generated/discovery/complete-square/evidence.json"), """
                {"scenarioId":"complete-square","success":true,"oracleStatus":"PROVED"}
                """);
        Files.writeString(project.resolve(".github/workflows/ci.yml"),
                "name: CI\non: [push]\njobs:\n  build:\n    runs-on: ubuntu-latest\n    steps:\n      - run: echo ok\n");
        Files.writeString(project.resolve("ai-knowledge/capabilities.seed.yaml"), """
                - id: rewrite-search
                  label: Rewrite Search
                  modules: [search]
                  packages: [de.regelsuche.search]
                  typePatterns: ['*Search*']
                  testPatterns: ['*SearchTest']
                  evidenceTypes: [discovery-evidence]
                """);
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: search-correctness
                  category: quality
                  scopeModules: [search]
                  requiredEvidenceTypes: [discovery-evidence]
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        // Context pack for rewrite-search exists and is capability-centred
        assertTrue(Files.isRegularFile(output.resolve("context-packs/rewrite-search.json")));
        String pack = Files.readString(output.resolve("context-packs/rewrite-search.json"));
        assertTrue(pack.contains("de.regelsuche.search.RewriteSearch"), "matched type must be in pack");
        assertTrue(pack.contains("de.regelsuche.search.RewriteSearchTest"), "matched test must be in pack");
        assertTrue(pack.contains("complete-square/evidence.json"), "matched evidence path must be in pack");
        assertTrue(pack.contains("evidence-backed"), "status must be evidence-backed");

        // Claims relevant to the capability must be linked
        assertTrue(pack.contains("search-correctness"), "relevant claim must appear in pack");

        // suggestedFiles must be derived from matched types
        assertTrue(pack.contains("suggestedFiles"), "pack must list suggestedFiles");
        assertTrue(pack.contains("RewriteSearch.java"), "source file must be suggested");

        // review-context.md must mention the capability and its status
        String md = Files.readString(output.resolve("review-context.md"));
        assertTrue(md.contains("rewrite-search"), "capability id must appear in markdown");
        assertTrue(md.contains("evidence-backed"), "status must appear in markdown");
        assertTrue(md.contains("context-packs/rewrite-search.json"), "pack file reference must appear in markdown");
    }

    @Test
    void checkPassesWhenKnowledgeQualityGatesDisabled() throws Exception {
        Path project = temp.resolve("check-pass-fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/App.java"),
                "package example;\npublic class App {}\n");

        Path output = project.resolve("build/ai-knowledge");
        // With all quality gate options at their defaults (disabled), check must pass
        Map result = new AiKnowledgeRunner().check(ExtractionOptions.defaults(project, output));

        assertTrue(Boolean.TRUE.equals(result.get("passed")));
        String checkJson = Files.readString(output.resolve("check.json"));
        assertTrue(checkJson.contains("\"knowledgeQualityGates\""), "check.json must include knowledgeQualityGates");
        String knowledgeQualityGates = objectContaining(checkJson, "\"knowledgeQualityGates\"");
        assertTrue(knowledgeQualityGates.contains("\"passed\":true"), "quality gates must pass when disabled");
    }

    @Test
    void checkFailsWhenRequireCapabilityEvidenceIsSetAndCapabilityHasNoEvidence() throws Exception {
        Path project = temp.resolve("check-cap-evidence-fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/App.java"),
                "package example;\npublic class App {}\n");
        Files.writeString(project.resolve("ai-knowledge/capabilities.seed.yaml"), """
                - id: ghost-feature
                  label: Ghost Feature
                  typePatterns: ['*Nonexistent*']
                """);

        Path output = project.resolve("build/ai-knowledge");
        ExtractionOptions options = new ExtractionOptions(
                project, output,
                project.resolve("ai-knowledge"), project.resolve("ai-knowledge"),
                false, 100.0d, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                false, null,
                true, false, 0, Integer.MAX_VALUE);

        assertThrows(IOException.class, () -> new AiKnowledgeRunner().check(options));

        String checkJson = Files.readString(output.resolve("check.json"));
        assertTrue(checkJson.contains("\"passed\":false"), "check must fail when capability has no evidence");
        assertTrue(checkJson.contains("ghost-feature"), "violation must name the capability");
        assertTrue(checkJson.contains("requireCapabilityEvidence"), "gate name must appear in check.json");
    }

    @Test
    void checkPassesWhenRequireCapabilityEvidenceIsSetAndAllCapabilitiesLinked() throws Exception {
        Path project = temp.resolve("check-cap-evidence-pass-fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("src/test/java/example"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/SearchService.java"),
                "package example;\npublic class SearchService {}\n");
        Files.writeString(project.resolve("src/test/java/example/SearchServiceTest.java"),
                "package example;\nclass SearchServiceTest { @org.junit.jupiter.api.Test void run() {} }\n");
        Files.writeString(project.resolve("ai-knowledge/capabilities.seed.yaml"), """
                - id: search
                  label: Search Service
                  packages: [example]
                  typePatterns: ['*Search*']
                """);

        Path output = project.resolve("build/ai-knowledge");
        ExtractionOptions options = new ExtractionOptions(
                project, output,
                project.resolve("ai-knowledge"), project.resolve("ai-knowledge"),
                false, 100.0d, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                false, null,
                true, false, 0, Integer.MAX_VALUE);

        Map result = new AiKnowledgeRunner().check(options);
        assertTrue(Boolean.TRUE.equals(result.get("passed")));
        String checkJson = Files.readString(output.resolve("check.json"));
        assertTrue(checkJson.contains("requireCapabilityEvidence"), "gate must appear in output");
        assertTrue(objectContaining(checkJson, "requireCapabilityEvidence").contains("\"passed\":true"),
                "gate must pass when capability has evidence");
    }

    @Test
    void checkFailsWhenRequireClaimVerificationIsSetAndClaimIsUnverified() throws Exception {
        Path project = temp.resolve("check-claim-verify-fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/App.java"),
                "package example;\npublic class App {}\n");
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: open-claim
                  category: quality
                  description: This claim has no verifiable rules
                """);

        Path output = project.resolve("build/ai-knowledge");
        ExtractionOptions options = new ExtractionOptions(
                project, output,
                project.resolve("ai-knowledge"), project.resolve("ai-knowledge"),
                false, 100.0d, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                false, null,
                false, true, 0, Integer.MAX_VALUE);

        assertThrows(IOException.class, () -> new AiKnowledgeRunner().check(options));

        String checkJson = Files.readString(output.resolve("check.json"));
        assertTrue(checkJson.contains("\"passed\":false"), "check must fail for unverified claim");
        assertTrue(checkJson.contains("open-claim"), "violation must name the claim");
        assertTrue(checkJson.contains("requireClaimVerification"), "gate name must appear in check.json");
    }

    @Test
    void checkFailsWhenMinContextPackCountNotMet() throws Exception {
        Path project = temp.resolve("check-pack-count-fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/App.java"),
                "package example;\npublic class App {}\n");
        Files.writeString(project.resolve("ai-knowledge/capabilities.seed.yaml"), """
                - id: cap-one
                  label: Cap One
                  packages: [example]
                """);

        Path output = project.resolve("build/ai-knowledge");
        ExtractionOptions options = new ExtractionOptions(
                project, output,
                project.resolve("ai-knowledge"), project.resolve("ai-knowledge"),
                false, 100.0d, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                false, null,
                false, false, 3, Integer.MAX_VALUE);

        assertThrows(IOException.class, () -> new AiKnowledgeRunner().check(options));

        String checkJson = Files.readString(output.resolve("check.json"));
        assertTrue(checkJson.contains("\"passed\":false"), "check must fail when pack count is below minimum");
        assertTrue(checkJson.contains("minContextPackCount"), "gate name must appear in check.json");
        assertTrue(checkJson.contains("below required minimum 3"), "violation message must include threshold");
    }

    @Test
    void checkFailsWhenContextPackTokensExceedMaximum() throws Exception {
        Path project = temp.resolve("check-pack-tokens-fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("src/test/java/example"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/SearchService.java"),
                "package example;\npublic class SearchService {}\n");
        Files.writeString(project.resolve("src/test/java/example/SearchServiceTest.java"),
                "package example;\nclass SearchServiceTest { @org.junit.jupiter.api.Test void run() {} }\n");
        Files.writeString(project.resolve("ai-knowledge/capabilities.seed.yaml"), """
                - id: search
                  label: Search Service
                  packages: [example]
                  typePatterns: ['*Search*']
                """);

        Path output = project.resolve("build/ai-knowledge");
        // Set a very low max token limit to guarantee violation
        ExtractionOptions options = new ExtractionOptions(
                project, output,
                project.resolve("ai-knowledge"), project.resolve("ai-knowledge"),
                false, 100.0d, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                false, null,
                false, false, 0, 1);

        assertThrows(IOException.class, () -> new AiKnowledgeRunner().check(options));

        String checkJson = Files.readString(output.resolve("check.json"));
        assertTrue(checkJson.contains("\"passed\":false"), "check must fail when token limit is exceeded");
        assertTrue(checkJson.contains("maxContextPackTokens"), "gate name must appear in check.json");
        assertTrue(checkJson.contains("exceeds maximum 1"), "violation must include the threshold value");
    }

    @Test
    void checkIncludesKnowledgeQualityGatesSummaryInCheckJson() throws Exception {
        Path project = temp.resolve("check-gates-summary-fixture");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/App.java"),
                "package example;\npublic class App {}\n");

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().check(ExtractionOptions.defaults(project, output));

        String checkJson = Files.readString(output.resolve("check.json"));
        // knowledgeQualityGates section must always be present
        assertTrue(checkJson.contains("knowledgeQualityGates"), "check.json must contain knowledgeQualityGates");
        assertTrue(checkJson.contains("\"gates\""), "knowledgeQualityGates must contain gates list");
        // With defaults no gates are enabled, so gates list is empty but section passes
        assertTrue(objectContaining(checkJson, "\"knowledgeQualityGates\"").contains("\"passed\":true"),
                "gates must pass when no gates are enabled");
    }

    private static String objectContaining(String json, String marker) {
        int markerIndex = json.indexOf(marker);
        assertTrue(markerIndex >= 0, "Missing marker " + marker + " in " + json);
        int start = json.lastIndexOf('{', markerIndex);
        int end = json.indexOf('}', markerIndex);
        return json.substring(start, end + 1);
    }

    private static int occurrences(String text, String value) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }
}
