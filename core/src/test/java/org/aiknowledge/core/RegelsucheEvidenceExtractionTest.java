package org.aiknowledge.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegelsucheEvidenceExtractionTest {
    @TempDir
    Path temp;

    @Test
    void generatesJsonSeedsAndProjectEvidence() throws Exception {
        Path project = temp.resolve("regelsuche-like");
        Files.createDirectories(project.resolve("ai-knowledge"));
        Files.createDirectories(project.resolve("docs/generated/discovery/complete-square"));
        Files.createDirectories(project.resolve("app/src/jmh/java/de/regelsuche/benchmark"));
        Files.createDirectories(project.resolve(".github/workflows"));

        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(project.resolve("ai-knowledge/capabilities.json"), """
                [
                  {
                    "id": "rewrite-search",
                    "label": "Rewrite Search",
                    "classes": ["de.regelsuche.search.RewriteSearch"]
                  }
                ]
                """);
        Files.writeString(project.resolve("ai-knowledge/claims.json"), """
                [
                  {
                    "id": "deterministic-discovery",
                    "category": "quality",
                    "verifiedBy": ["de.regelsuche.docs.GalleryConsistencyTest"]
                  }
                ]
                """);
        Files.writeString(project.resolve("docs/generated/discovery/complete-square/evidence.json"), """
                {
                  "scenarioId": "complete-square-factorization",
                  "inputExpression": "a^2+2ab+b^2",
                  "targetExpression": "(a+b)^2",
                  "success": true,
                  "promotionEligible": true,
                  "oracleStatus": "PROVED",
                  "nodeCount": 12,
                  "edgeCount": 20,
                  "bridgeRulesUsed": ["complete-square"],
                  "learnedMacros": ["macro-a"],
                  "reusedMacros": ["macro-a"]
                }
                """);
        Files.writeString(project.resolve("app/src/jmh/java/de/regelsuche/benchmark/CoreBenchmarks.java"), "package de.regelsuche.benchmark; public class CoreBenchmarks {}\n");
        Files.writeString(project.resolve(".github/workflows/ci.yml"), """
                name: CI
                on: [push]
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);

        Path output = project.resolve("build/ai-knowledge");
        new AiKnowledgeRunner().generate(ExtractionOptions.defaults(project, output));

        String index = Files.readString(output.resolve("index.json"));
        String capabilities = Files.readString(output.resolve("capabilities.json"));
        String claims = Files.readString(output.resolve("claims.json"));
        String evidence = Files.readString(output.resolve("evidence.json"));

        assertTrue(index.contains("\"evidence\":3"));
        assertTrue(capabilities.contains("Rewrite Search"));
        assertTrue(claims.contains("deterministic-discovery"));
        assertTrue(evidence.contains("discovery-evidence"));
        assertTrue(evidence.contains("complete-square-factorization"));
        assertTrue(evidence.contains("benchmark-source"));
        assertTrue(evidence.contains("github-workflow"));
        assertTrue(evidence.contains("\"jobCount\":1"));
    }
}
