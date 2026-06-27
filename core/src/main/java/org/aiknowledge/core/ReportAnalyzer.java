package org.aiknowledge.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        report.put("aiCognitiveDebt", Math.max(0.0d, estimatedTokens / 1000.0d + density - tests / 10.0d));
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
            Object status = cap.get("status");
            if ("unknown".equals(status)) smells.add(smell("Hidden Concept", cap.get("id"), 300));
        }
        if (snapshot.classes.size() > 80) smells.add(smell("Oversized Context Cluster", "repository", snapshot.classes.size() * 20));
        if (snapshot.docs.size() > 30) smells.add(smell("Duplicate Documentation", "docs", snapshot.docs.size() * 25));
        List recommendations = new ArrayList();
        for (Object object : smells) {
            Map s = (Map) object;
            Map r = new LinkedHashMap();
            r.put("title", "Reduce " + s.get("type") + " around " + s.get("subject"));
            r.put("action", "Improve locality, documentation and test evidence.");
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
            result.put("risk", risk(profile));
            results.add(result);
        }
        Map report = new LinkedHashMap();
        report.put("schemaVersion", 1);
        report.put("method", "deterministic-preflight");
        report.put("results", results);
        report.put("recommendedProfile", recommended(results));
        return report;
    }

    private static Map thresholds(ExtractionOptions options) {
        Map thresholds = new LinkedHashMap();
        thresholds.put("maxCognitiveDebt", options.maxCognitiveDebt());
        thresholds.put("failOnWarnings", options.failOnWarnings());
        thresholds.put("modelProfileDirectory", options.modelProfileDirectory().toString());
        return thresholds;
    }

    private static String risk(Map profile) {
        if (Boolean.FALSE.equals(profile.get("compressedFitsHardLimit"))) return "high";
        if (Boolean.FALSE.equals(profile.get("compressedFitsPracticalBudget"))) return "medium";
        return "low";
    }

    private static Object recommended(List results) {
        for (Object item : results) {
            Map result = (Map) item;
            if ("low".equals(result.get("risk"))) return result.get("profile");
        }
        return results.isEmpty() ? "none" : ((Map) results.get(0)).get("profile");
    }

    private static Map smell(String type, Object subject, int savings) { Map s = new LinkedHashMap(); s.put("type", type); s.put("subject", subject); s.put("estimatedTokenSavings", savings); return s; }
    private static Map after(Map before, List smells) { int savings = 0; for (Object item : smells) savings += ((Number) ((Map) item).get("estimatedTokenSavings")).intValue(); Map after = new LinkedHashMap(); int tokens = ((Number) before.get("estimatedContextTokens")).intValue(); after.put("estimatedContextTokens", Math.max(0, tokens - savings)); after.put("estimatedTokenSavings", savings); return after; }
    static String html(String title, Map report) { return "<!doctype html><html><head><meta charset=\"utf-8\"><title>" + title + "</title></head><body><h1>" + title + "</h1><pre>" + escape(JsonSupport.toJson(report)) + "</pre></body></html>\n"; }
    private static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
