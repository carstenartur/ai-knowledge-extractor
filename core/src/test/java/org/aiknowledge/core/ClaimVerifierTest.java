package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@code ClaimVerifier} using Regelsuche-like architectural claims.
 */
class ClaimVerifierTest {
    @TempDir
    Path temp;

    // ---------- no-infrastructure-in-core claim ----------

    @Test
    void noInfrastructureInCore_detectsHibernateImportViolation() throws Exception {
        Path project = temp.resolve("regelsuche-arch");
        Files.createDirectories(project.resolve("core/src/main/java/de/regelsuche/core"));
        Files.createDirectories(project.resolve("infra/src/main/java/de/regelsuche/infra"));
        Files.createDirectories(project.resolve("ai-knowledge"));

        Files.writeString(project.resolve("core/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("infra/build.gradle"), "plugins { id 'java' }\ndependencies { implementation 'org.hibernate:hibernate-core:5.4' }\n");
        // Core class with forbidden infrastructure import
        Files.writeString(project.resolve("core/src/main/java/de/regelsuche/core/CoreService.java"), """
                package de.regelsuche.core;

                import org.hibernate.Session;

                public class CoreService {
                    public void run() {}
                }
                """);
        Files.writeString(project.resolve("infra/src/main/java/de/regelsuche/infra/InfraService.java"), """
                package de.regelsuche.infra;

                import org.hibernate.Session;

                public class InfraService {
                    public void run() {}
                }
                """);
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: no-infrastructure-in-core
                  category: architecture
                  description: Core must not reference infrastructure frameworks
                  scopeModules: [core]
                  forbiddenReferences: [org.hibernate, jakarta.persistence, org.springframework]
                  severity: error
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String claims = Files.readString(output.resolve("claims.json"));

        assertTrue(claims.contains("\"id\":\"no-infrastructure-in-core\""), "claim id must be present");
        assertTrue(claims.contains("\"status\":\"failed\""), "status must be failed");
        assertTrue(claims.contains("forbidden-reference:org.hibernate"), "violation must name forbidden prefix");
        assertTrue(claims.contains("CoreService"), "violation must identify violating class");
        assertTrue(claims.contains("\"severity\":\"error\""), "severity must be preserved");
    }

    @Test
    void noInfrastructureInCore_passesWhenCoreHasNoForbiddenImports() throws Exception {
        Path project = temp.resolve("clean-core");
        Files.createDirectories(project.resolve("core/src/main/java/de/regelsuche/core"));
        Files.createDirectories(project.resolve("ai-knowledge"));

        Files.writeString(project.resolve("core/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("core/src/main/java/de/regelsuche/core/CoreLogic.java"), """
                package de.regelsuche.core;

                import java.util.List;

                public class CoreLogic {
                    public void run() {}
                }
                """);
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: no-infrastructure-in-core
                  category: architecture
                  scopeModules: [core]
                  forbiddenReferences: [org.hibernate, jakarta.persistence, org.springframework]
                  severity: error
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String claims = Files.readString(output.resolve("claims.json"));
        assertTrue(claims.contains("\"status\":\"passed\""), "status must be passed when no violations exist");
        assertFalse(claims.contains("violations"), "no violations field when claim passes");
    }

    // ---------- hibernate-isolated claim ----------

    @Test
    void hibernateIsolated_detectsHibernateDependencyInNonInfraModule() throws Exception {
        Path project = temp.resolve("hibernate-isolated");
        Files.createDirectories(project.resolve("core/src/main/java/de/regelsuche/core"));
        Files.createDirectories(project.resolve("ai-knowledge"));

        // Core module erroneously declares a Hibernate dependency
        Files.writeString(project.resolve("core/build.gradle"), """
                plugins { id 'java' }
                implementation 'org.hibernate:hibernate-core:5.4'
                """);
        Files.writeString(project.resolve("core/src/main/java/de/regelsuche/core/CoreClass.java"),
                "package de.regelsuche.core;\npublic class CoreClass {}\n");
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: hibernate-isolated
                  category: architecture
                  description: Hibernate must only appear in infrastructure modules
                  scopeModules: [core]
                  forbiddenDependencies: [org.hibernate]
                  severity: error
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String claims = Files.readString(output.resolve("claims.json"));
        assertTrue(claims.contains("\"id\":\"hibernate-isolated\""), "claim id must be present");
        assertTrue(claims.contains("\"status\":\"failed\""), "status must be failed");
        assertTrue(claims.contains("forbidden-dependency:org.hibernate"), "violation must name forbidden dependency");
        assertTrue(claims.contains("module:core"), "violation must identify the module");
    }

    @Test
    void hibernateIsolated_passesWhenCoreHasNoHibernateDependency() throws Exception {
        Path project = temp.resolve("hibernate-clean");
        Files.createDirectories(project.resolve("core/src/main/java/de/regelsuche/core"));
        Files.createDirectories(project.resolve("ai-knowledge"));

        Files.writeString(project.resolve("core/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("core/src/main/java/de/regelsuche/core/CoreClass.java"),
                "package de.regelsuche.core;\npublic class CoreClass {}\n");
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: hibernate-isolated
                  scopeModules: [core]
                  forbiddenDependencies: [org.hibernate]
                  severity: error
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String claims = Files.readString(output.resolve("claims.json"));
        assertTrue(claims.contains("\"status\":\"passed\""), "status must be passed when no violations");
    }

    // ---------- verifiedBy matching ----------

    @Test
    void verifiedBy_marksPassedWhenTestExists() throws Exception {
        Path project = temp.resolve("verified-by-pass");
        Files.createDirectories(project.resolve("core/src/main/java/de/regelsuche/core"));
        Files.createDirectories(project.resolve("core/src/test/java/de/regelsuche/core"));
        Files.createDirectories(project.resolve("ai-knowledge"));

        Files.writeString(project.resolve("core/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("core/src/main/java/de/regelsuche/core/CoreRule.java"),
                "package de.regelsuche.core;\npublic class CoreRule {}\n");
        Files.writeString(project.resolve("core/src/test/java/de/regelsuche/core/ArchitectureBoundariesTest.java"), """
                package de.regelsuche.core;

                class ArchitectureBoundariesTest {
                    @org.junit.jupiter.api.Test
                    void coreHasNoBoundaryViolations() {}
                }
                """);
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: architecture-boundaries
                  category: architecture
                  forbiddenReferences: [org.hibernate]
                  verifiedBy: [de.regelsuche.core.ArchitectureBoundariesTest]
                  severity: warning
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String claims = Files.readString(output.resolve("claims.json"));
        assertTrue(claims.contains("\"status\":\"passed\""), "status must be passed");
        assertTrue(claims.contains("matchedVerifiedBy"), "matchedVerifiedBy must be present");
        assertTrue(claims.contains("de.regelsuche.core.ArchitectureBoundariesTest"), "matched test class must be listed");
    }

    @Test
    void verifiedBy_failsWhenTestDoesNotExist() throws Exception {
        Path project = temp.resolve("verified-by-fail");
        Files.createDirectories(project.resolve("core/src/main/java/de/regelsuche/core"));
        Files.createDirectories(project.resolve("ai-knowledge"));

        Files.writeString(project.resolve("core/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("core/src/main/java/de/regelsuche/core/CoreLogic.java"),
                "package de.regelsuche.core;\npublic class CoreLogic {}\n");
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: deterministic-discovery
                  category: quality
                  forbiddenReferences: [org.mockito]
                  verifiedBy: [de.regelsuche.docs.GalleryConsistencyTest]
                  severity: warning
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String claims = Files.readString(output.resolve("claims.json"));
        assertTrue(claims.contains("\"status\":\"failed\""), "status must be failed when test missing");
        assertTrue(claims.contains("missing-verifier-test:de.regelsuche.docs.GalleryConsistencyTest"), "must report missing verifier test");
    }

    // ---------- requiredTests ----------

    @Test
    void requiredTests_failsWhenRequiredTestMissing() throws Exception {
        Path project = temp.resolve("required-test-fail");
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("ai-knowledge"));

        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/example/App.java"),
                "package example;\npublic class App {}\n");
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: app-tested
                  requiredTests: [example.AppTest]
                  severity: error
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String claims = Files.readString(output.resolve("claims.json"));
        assertTrue(claims.contains("\"status\":\"failed\""), "status must be failed");
        assertTrue(claims.contains("missing-required-test:example.AppTest"), "must report missing required test");
    }

    // ---------- unverified claims ----------

    @Test
    void claimWithNoRuleFields_markedUnverified() throws Exception {
        Path project = temp.resolve("unverified");
        Files.createDirectories(project.resolve("ai-knowledge"));

        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: prose-claim
                  category: quality
                  description: This is a prose-only claim with no rule fields
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String claims = Files.readString(output.resolve("claims.json"));
        assertTrue(claims.contains("\"id\":\"prose-claim\""), "claim id must be present");
        assertTrue(claims.contains("\"status\":\"unverified\""), "status must be unverified");
    }

    // ---------- checkAiKnowledgeIndex integration ----------

    @Test
    void checkFailsWhenErrorSeverityClaimFails() throws Exception {
        Path project = temp.resolve("check-fail");
        Files.createDirectories(project.resolve("core/src/main/java/de/regelsuche/core"));
        Files.createDirectories(project.resolve("ai-knowledge"));

        Files.writeString(project.resolve("core/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("core/src/main/java/de/regelsuche/core/CoreBadClass.java"), """
                package de.regelsuche.core;

                import org.hibernate.Session;

                public class CoreBadClass {}
                """);
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: no-hibernate-in-core
                  scopeModules: [core]
                  forbiddenReferences: [org.hibernate]
                  severity: error
                """);

        Path output = project.resolve("build/ai-knowledge");
        ExtractionOptions options = ExtractionOptions.defaults(project, output);
        boolean threw = false;
        try {
            new AiKnowledgeRunner().check(options);
        } catch (Exception ex) {
            threw = true;
            assertTrue(ex.getMessage().contains("claimFailures=1"), "error message must include claimFailures count");
        }
        assertTrue(threw, "check must throw when error-severity claim fails");

        String checkJson = Files.readString(output.resolve("check.json"));
        assertTrue(checkJson.contains("\"passed\":false"), "check.json must record passed=false");
        assertTrue(checkJson.contains("\"claimFailureCount\":1"), "check.json must include claimFailureCount");
    }

    @Test
    void checkPassesAndIncludesClaimFailureCountZeroWhenNoClaimsFail() throws Exception {
        Path project = temp.resolve("check-pass");
        Files.createDirectories(project.resolve("core/src/main/java/de/regelsuche/core"));
        Files.createDirectories(project.resolve("ai-knowledge"));

        Files.writeString(project.resolve("core/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("core/src/main/java/de/regelsuche/core/CleanClass.java"), """
                package de.regelsuche.core;

                import java.util.List;

                public class CleanClass {}
                """);
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: no-hibernate-in-core
                  scopeModules: [core]
                  forbiddenReferences: [org.hibernate]
                  severity: error
                """);

        Path output = project.resolve("build/ai-knowledge");
        Map result = new AiKnowledgeRunner().check(ExtractionOptions.defaults(project, output));

        assertEquals(0, ((Number) result.get("claimFailureCount")).intValue(), "claimFailureCount must be 0");
        assertEquals(true, result.get("passed"), "check must pass");

        String checkJson = Files.readString(output.resolve("check.json"));
        assertTrue(checkJson.contains("\"claimFailureCount\":0"), "check.json must include claimFailureCount:0");
    }

    // ---------- mustBeAcyclic ----------

    @Test
    void mustBeAcyclic_detectsImportCycle() throws Exception {
        Path project = temp.resolve("acyclic-fail");
        Files.createDirectories(project.resolve("src/main/java/de/cycle"));
        Files.createDirectories(project.resolve("ai-knowledge"));

        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("src/main/java/de/cycle/Alpha.java"), """
                package de.cycle;
                import de.cycle.Beta;
                public class Alpha {}
                """);
        Files.writeString(project.resolve("src/main/java/de/cycle/Beta.java"), """
                package de.cycle;
                import de.cycle.Alpha;
                public class Beta {}
                """);
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: no-cycles-in-core
                  mustBeAcyclic: true
                  severity: warning
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String claims = Files.readString(output.resolve("claims.json"));
        assertTrue(claims.contains("\"status\":\"failed\""), "status must be failed when cycle detected");
        assertTrue(claims.contains("import-cycle:"), "violations must include import-cycle prefix");
    }

    // ---------- requiredEvidenceTypes ----------

    @Test
    void requiredEvidenceTypes_failsWhenEvidenceMissing() throws Exception {
        Path project = temp.resolve("evidence-missing");
        Files.createDirectories(project.resolve("ai-knowledge"));

        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: evidence-backed-search
                  requiredEvidenceTypes: [discovery-evidence]
                  severity: warning
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String claims = Files.readString(output.resolve("claims.json"));
        assertTrue(claims.contains("\"status\":\"failed\""), "status must be failed");
        assertTrue(claims.contains("missing-evidence-type:discovery-evidence"), "must report missing evidence type");
    }

    // ---------- verificationEvidence ----------

    @Test
    void passedClaim_includesVerificationEvidence() throws Exception {
        Path project = temp.resolve("verification-evidence");
        Files.createDirectories(project.resolve("core/src/main/java/de/regelsuche/core"));
        Files.createDirectories(project.resolve("core/src/test/java/de/regelsuche/core"));
        Files.createDirectories(project.resolve("ai-knowledge"));

        Files.writeString(project.resolve("core/build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("core/src/main/java/de/regelsuche/core/Logic.java"),
                "package de.regelsuche.core;\npublic class Logic {}\n");
        Files.writeString(project.resolve("core/src/test/java/de/regelsuche/core/ArchTest.java"), """
                package de.regelsuche.core;
                class ArchTest { @org.junit.jupiter.api.Test void run() {} }
                """);
        Files.writeString(project.resolve("ai-knowledge/claims.seed.yaml"), """
                - id: scope-evidence-claim
                  scopeModules: [core]
                  forbiddenReferences: [org.hibernate]
                  verifiedBy: [de.regelsuche.core.ArchTest]
                  severity: error
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String claims = Files.readString(output.resolve("claims.json"));
        assertTrue(claims.contains("\"status\":\"passed\""), "status must be passed");
        assertTrue(claims.contains("verificationEvidence"), "verificationEvidence must be present");
        assertTrue(claims.contains("module:core"), "verificationEvidence must list scoped module");
        assertTrue(claims.contains("test:de.regelsuche.core.ArchTest"), "verificationEvidence must list matched test");
    }
}
