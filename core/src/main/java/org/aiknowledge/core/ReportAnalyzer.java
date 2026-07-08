package org.aiknowledge.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ReportAnalyzer {
    Map complexity(ExtractionOptions options, RepositorySnapshot snapshot) throws IOException {
        int classes = snapshot.classes.size();
        int tests = snapshot.tests.size();
        int docs = snapshot.docs.size();
        int dependencies = snapshot.dependencies.size();
        int capabilities = snapshot.capabilities.size();
        int estimatedTokens = classes * 260 + tests * 140 + docs * 180 + dependencies * 35 + capabilities * 80;
        double density = capabilities == 0 ? 0.0d : (classes + tests + docs) / (double) capabilities;
        double contextDebt = Math.max(0.0d, estimatedTokens / 1000.0d + density - tests / 10.0d);
        List profileMetrics = ModelProfileSupport.profileMetrics(options, estimatedTokens);
        List warnings = new ArrayList();
        for (Object item : profileMetrics) {
            Map profile = (Map) item;
            Object profileWarnings = profile.get("warnings");
            if (profileWarnings instanceof List list && !list.isEmpty()) warnings.add("Profile " + profile.get("id") + ": " + list.get(0));
        }
        if (density > 20.0d) warnings.add("Knowledge density is low: many files are needed per capability.");
        if (tests < Math.max(1, classes / 4)) warnings.add("Test evidence appears weak compared with production classes.");
        Map report = new LinkedHashMap();
        report.put("schemaVersion", 1);
        report.put("estimatedContextTokens", estimatedTokens);
        report.put("conceptRadius", Math.max(1, classes / Math.max(1, capabilities)));
        report.put("dependencyRadius", dependencies);
        report.put("knowledgeDensity", density);
        report.put("contextLocality", Math.max(0.0d, 1.0d - density / 100.0d));
        report.put("compressionRatio", estimatedTokens == 0 ? 1.0d : Math.min(1.0d, 12000.0d / estimatedTokens));
        report.put("aiCognitiveComplexity", estimatedTokens / 1000.0d + dependencies * 0.5d);
        report.put("aiContextDebt", contextDebt);
        report.put("aiCognitiveDebt", contextDebt);
        report.put("codeComplexity", codeComplexity(snapshot));
        report.put("warningCount", warnings.size());
        report.put("warnings", warnings);
        report.put("modelProfiles", profileMetrics);
        report.put("thresholds", thresholds(options));
        return report;
    }

    Map optimization(ExtractionOptions options, RepositorySnapshot snapshot, Map complexity) {
        List smells = new ArrayList();
        for (Object item : snapshot.capabilities) {
            Map cap = (Map) item;
            if ("unknown".equals(cap.get("status"))) smells.add(smell("Hidden Concept", cap.get("id"), 300));
        }
        for (Object item : snapshot.capabilities) {
            Map cap = (Map) item;
            if ("partial".equals(cap.get("status"))) smells.add(smell("Weak Evidence", cap.get("id"), 200));
        }
        for (Object item : snapshot.capabilities) {
            Map cap = (Map) item;
            List classes = (List) cap.getOrDefault("classes", new ArrayList());
            if (classes.size() > 5) smells.add(smell("Large Concept", cap.get("id"), (classes.size() - 5) * 200));
        }
        for (Object item : snapshot.capabilities) {
            Map cap = (Map) item;
            List classes = (List) cap.getOrDefault("classes", new ArrayList());
            Map pkgs = new LinkedHashMap();
            for (Object cls : classes) {
                String pkg = packageOf(String.valueOf(cls));
                if (!pkg.isBlank()) pkgs.put(pkg, Boolean.TRUE);
            }
            if (pkgs.size() >= 3) smells.add(smell("Scattered Capability", cap.get("id"), pkgs.size() * 150));
        }
        Map packageDeps = new LinkedHashMap();
        for (Object item : snapshot.classes) {
            Map cls = (Map) item;
            String pkg = String.valueOf(cls.getOrDefault("package", ""));
            if (pkg.isBlank()) continue;
            List refs = (List) cls.getOrDefault("referencedProjectClasses", new ArrayList());
            for (Object ref : refs) {
                String depPkg = packageOf(String.valueOf(ref));
                if (!depPkg.isBlank() && !depPkg.equals(pkg)) {
                    if (!packageDeps.containsKey(pkg)) packageDeps.put(pkg, new ArrayList());
                    List deps = (List) packageDeps.get(pkg);
                    if (!deps.contains(depPkg)) deps.add(depPkg);
                }
            }
        }
        List cyclesSeen = new ArrayList();
        for (Object pkgKey : packageDeps.keySet()) {
            String pkg = String.valueOf(pkgKey);
            for (Object depKey : (List) packageDeps.get(pkg)) {
                String dep = String.valueOf(depKey);
                List reverseDeps = (List) packageDeps.getOrDefault(dep, new ArrayList());
                if (reverseDeps.contains(pkg)) {
                    String pair = pkg.compareTo(dep) < 0 ? pkg + " <-> " + dep : dep + " <-> " + pkg;
                    if (!cyclesSeen.contains(pair)) {
                        cyclesSeen.add(pair);
                        smells.add(smell("Concept Cycle", pair, 400));
                    }
                }
            }
        }
        Map normTitleCount = new LinkedHashMap();
        for (Object item : snapshot.docs) {
            Map doc = (Map) item;
            String norm = normalizeTitle(String.valueOf(doc.getOrDefault("title", "")));
            if (!norm.isBlank()) normTitleCount.put(norm, ((Integer) normTitleCount.getOrDefault(norm, 0)) + 1);
        }
        for (Object norm : normTitleCount.keySet()) {
            int count = (Integer) normTitleCount.get(norm);
            if (count > 1) smells.add(smell("Duplicate Knowledge", norm, (count - 1) * 100));
        }
        if (snapshot.classes.size() > 80) smells.add(smell("Oversized Context Cluster", "repository", snapshot.classes.size() * 20));
        smells.sort((a, b) -> Integer.compare(
                ((Number) ((Map) b).get("estimatedTokenSavings")).intValue(),
                ((Number) ((Map) a).get("estimatedTokenSavings")).intValue()));
        List recommendations = new ArrayList();
        for (Object object : smells) {
            Map s = (Map) object;
            Map r = new LinkedHashMap();
            r.put("title", "Reduce " + s.get("type") + " around " + s.get("subject"));
            r.put("action", actionFor(String.valueOf(s.get("type"))));
            r.put("estimatedTokenSavings", s.get("estimatedTokenSavings"));
            recommendations.add(r);
        }
        Map report = new LinkedHashMap();
        report.put("schemaVersion", 1);
        report.put("smells", smells);
        report.put("recommendations", recommendations);
        report.put("before", complexity);
        report.put("afterEstimate", after(complexity, smells));
        return report;
    }

    Map benchmark(ExtractionOptions options, RepositorySnapshot snapshot, Map complexity) throws IOException {
        int base = ((Number) complexity.get("estimatedContextTokens")).intValue();
        List results = new ArrayList();
        for (Object item : ModelProfileSupport.profileMetrics(options, base)) {
            Map profile = (Map) item;
            Map result = new LinkedHashMap();
            result.put("profile", profile.get("id"));
            result.put("estimatedTokens", profile.get("estimatedCompressedTokens"));
            result.put("rawTokens", profile.get("estimatedRawTokens"));
            result.put("compressionRatio", profile.get("targetCompressionRatio"));
            result.put("rawFitsPracticalBudget", profile.get("fitsPracticalBudget"));
            result.put("rawFitsHardLimit", profile.get("fitsHardLimit"));
            result.put("compressedFitsPracticalBudget", profile.get("compressedFitsPracticalBudget"));
            result.put("compressedFitsHardLimit", profile.get("compressedFitsHardLimit"));
            String budgetRisk = budgetRisk(profile);
            String missingContextRisk = missingContextRisk(profile);
            result.put("budgetRisk", budgetRisk);
            result.put("missingContextRisk", missingContextRisk);
            result.put("risk", maxRisk(budgetRisk, missingContextRisk));
            results.add(result);
        }
        Map report = new LinkedHashMap();
        report.put("schemaVersion", 1);
        report.put("method", "deterministic-preflight");
        report.put("results", results);
        report.put("recommendedProfile", recommended(results));
        report.put("empirical", EmpiricalBenchmarkSupport.report(options));
        return report;
    }

    private static Map codeComplexity(RepositorySnapshot snapshot) {
        List<Map> methods = complexityMethodFacts(snapshot);
        int totalCyclomatic = sum(methods, "cyclomaticComplexity");
        int totalCognitive = sum(methods, "cognitiveComplexity");
        Map report = new LinkedHashMap();
        report.put("methodCount", methods.size());
        report.put("totalCyclomaticComplexity", totalCyclomatic);
        report.put("maxMethodCyclomaticComplexity", max(methods, "cyclomaticComplexity"));
        report.put("averageMethodCyclomaticComplexity", average(totalCyclomatic, methods.size()));
        report.put("totalCognitiveComplexity", totalCognitive);
        report.put("maxMethodCognitiveComplexity", max(methods, "cognitiveComplexity"));
        report.put("averageMethodCognitiveComplexity", average(totalCognitive, methods.size()));
        report.put("methodsAboveCyclomaticThreshold", countAbove(methods, "cyclomaticComplexity", 10));
        report.put("methodsAboveCognitiveThreshold", countAbove(methods, "cognitiveComplexity", 15));
        report.put("topCyclomaticMethods", topMethods(methods, "cyclomaticComplexity"));
        report.put("topCognitiveMethods", topMethods(methods, "cognitiveComplexity"));
        return report;
    }

    private static List<Map> complexityMethodFacts(RepositorySnapshot snapshot) {
        List<Map> methods = new ArrayList();
        collectComplexityMethodFacts(methods, snapshot.classes);
        collectComplexityMethodFacts(methods, snapshot.tests);
        return methods;
    }

    private static void collectComplexityMethodFacts(List<Map> methods, List facts) {
        for (Object item : facts) {
            if (!(item instanceof Map fact)) continue;
            Object methodFacts = fact.get("methodFacts");
            if (!(methodFacts instanceof List list)) continue;
            for (Object method : list) {
                if (method instanceof Map methodFact && methodFact.containsKey("cognitiveComplexity")) methods.add(methodFact);
            }
        }
    }

    private static List<Map> topMethods(List<Map> methods, String metric) {
        return methods.stream()
                .sorted(Comparator.comparingInt((Map method) -> number(method, metric)).reversed()
                        .thenComparing(method -> String.valueOf(method.get("type")))
                        .thenComparing(method -> String.valueOf(method.get("signature"))))
                .limit(10)
                .map(ReportAnalyzer::compactMethod)
                .toList();
    }

    private static Map compactMethod(Map method) {
        Map compact = new LinkedHashMap();
        compact.put("type", method.get("type"));
        compact.put("signature", method.get("signature"));
        compact.put("sourceFile", method.get("sourceFile"));
        if (method.containsKey("startLine")) compact.put("startLine", method.get("startLine"));
        if (method.containsKey("endLine")) compact.put("endLine", method.get("endLine"));
        compact.put("cyclomaticComplexity", method.get("cyclomaticComplexity"));
        compact.put("cognitiveComplexity", method.get("cognitiveComplexity"));
        compact.put("maxNestingDepth", method.get("maxNestingDepth"));
        return compact;
    }

    private static int sum(List<Map> methods, String metric) { return methods.stream().mapToInt(method -> number(method, metric)).sum(); }
    private static int max(List<Map> methods, String metric) { return methods.stream().mapToInt(method -> number(method, metric)).max().orElse(0); }
    private static int countAbove(List<Map> methods, String metric, int threshold) { return (int) methods.stream().filter(method -> number(method, metric) > threshold).count(); }
    private static double average(int total, int count) { return count == 0 ? 0.0d : Math.round((total / (double) count) * 100.0d) / 100.0d; }
    private static int number(Map method, String metric) { Object value = method.get(metric); return value instanceof Number number ? number.intValue() : 0; }

    private static Map thresholds(ExtractionOptions options) {
        Map thresholds = new LinkedHashMap();
        thresholds.put("maxCognitiveDebt", options.maxCognitiveDebt());
        thresholds.put("maxMethodCognitiveComplexity", options.maxMethodCognitiveComplexity());
        thresholds.put("maxMethodCyclomaticComplexity", options.maxMethodCyclomaticComplexity());
        thresholds.put("maxAverageMethodCognitiveComplexity", options.maxAverageMethodCognitiveComplexity());
        thresholds.put("maxAverageMethodCyclomaticComplexity", options.maxAverageMethodCyclomaticComplexity());
        thresholds.put("maxMethodsAboveCognitiveThreshold", options.maxMethodsAboveCognitiveThreshold());
        thresholds.put("maxMethodsAboveCyclomaticThreshold", options.maxMethodsAboveCyclomaticThreshold());
        thresholds.put("failOnWarnings", options.failOnWarnings());
        thresholds.put("modelProfileDirectory", options.reportPath(options.modelProfileDirectory()));
        return thresholds;
    }

    private static String budgetRisk(Map profile) { if (Boolean.FALSE.equals(profile.get("compressedFitsHardLimit"))) return "high"; if (Boolean.FALSE.equals(profile.get("compressedFitsPracticalBudget"))) return "medium"; return "low"; }
    private static String missingContextRisk(Map profile) { double ratio = ((Number) profile.getOrDefault("targetCompressionRatio", 1.0d)).doubleValue(); if (ratio <= 0.4d) return "high"; if (ratio <= 0.7d) return "medium"; return "low"; }
    private static String maxRisk(String left, String right) { return rank(left) >= rank(right) ? left : right; }
    private static int rank(String risk) { return switch (risk) { case "high" -> 3; case "medium" -> 2; default -> 1; }; }
    private static Object recommended(List results) { for (Object item : results) { Map result = (Map) item; if ("review".equals(result.get("profile"))) return "review"; } for (Object item : results) { Map result = (Map) item; if ("low".equals(result.get("risk"))) return result.get("profile"); } return results.isEmpty() ? "none" : ((Map) results.get(0)).get("profile"); }
    private static String packageOf(String fqn) { int last = fqn.lastIndexOf('.'); return last < 0 ? "" : fqn.substring(0, last); }
    private static String normalizeTitle(String title) { return title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    private static String actionFor(String type) { return switch (type) { case "Large Concept" -> "Split the concept into smaller, more focused modules or packages."; case "Hidden Concept" -> "Add implementation classes and tests to reveal and verify the capability."; case "Concept Cycle" -> "Introduce an abstraction layer or extract shared code to break the cycle."; case "Scattered Capability" -> "Consolidate capability classes into a single cohesive package."; case "Weak Evidence" -> "Add test coverage and implementation to strengthen the evidence for this capability."; case "Duplicate Knowledge" -> "Consolidate duplicate documentation into a single authoritative source."; case "Oversized Context Cluster" -> "Modularize the repository by splitting large clusters into smaller bounded contexts."; default -> "Improve locality, documentation and test evidence."; }; }
    private static Map smell(String type, Object subject, int savings) { Map s = new LinkedHashMap(); s.put("type", type); s.put("subject", subject); s.put("estimatedTokenSavings", savings); return s; }
    private static Map after(Map before, List smells) { int savings = 0; for (Object item : smells) savings += ((Number) ((Map) item).get("estimatedTokenSavings")).intValue(); Map after = new LinkedHashMap(); int tokens = ((Number) before.get("estimatedContextTokens")).intValue(); after.put("estimatedContextTokens", Math.max(0, tokens - savings)); after.put("estimatedTokenSavings", savings); return after; }
    static String html(String title, Map report) { return "<!doctype html><html><head><meta charset=\"utf-8\"><title>" + title + "</title></head><body><h1>" + title + "</h1><pre>" + escape(JsonSupport.toJson(report)) + "</pre></body></html>\n"; }
    private static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
