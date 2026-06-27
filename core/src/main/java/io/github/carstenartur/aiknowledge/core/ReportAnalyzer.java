package io.github.carstenartur.aiknowledge.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ReportAnalyzer {
    Map complexity(ExtractionOptions options, RepositorySnapshot snapshot) {
        int classes = snapshot.classes.size();
        int tests = snapshot.tests.size();
        int docs = snapshot.docs.size();
        int dependencies = snapshot.dependencies.size();
        int capabilities = snapshot.capabilities.size();
        int estimatedTokens = classes * 260 + tests * 140 + docs * 180 + dependencies * 35 + capabilities * 80;
        double density = capabilities == 0 ? 0.0d : (classes + tests + docs) / (double) capabilities;
        List warnings = new ArrayList();
        if (estimatedTokens > 32000) warnings.add("Estimated context size exceeds a practical single-pass review budget.");
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
        report.put("modelProfiles", modelProfiles());
        return report;
    }

    Map optimization(ExtractionOptions options, RepositorySnapshot snapshot, Map complexity) {
        List smells = new ArrayList();
        for (Object item : snapshot.capabilities) {
            Map cap = (Map) item;
            int evidence = ((List) cap.get("classes")).size() + ((List) cap.get("tests")).size() + ((List) cap.get("docs")).size();
            if (evidence == 0) smells.add(smell("Hidden Concept", cap.get("id"), 300));
            if (((List) cap.get("tests")).isEmpty() && !"unknown".equals(cap.get("status"))) smells.add(smell("Weak Evidence", cap.get("id"), 500));
            if (((List) cap.get("classes")).size() > 8) smells.add(smell("Scattered Capability", cap.get("id"), 900));
        }
        if (snapshot.classes.size() > 80) smells.add(smell("Oversized Context Cluster", "repository", snapshot.classes.size() * 20));
        if (snapshot.docs.size() > 30) smells.add(smell("Duplicate Documentation", "docs", snapshot.docs.size() * 25));
        List recommendations = new ArrayList();
        for (Object object : smells) {
            Map s = (Map) object;
            Map r = new LinkedHashMap();
            r.put("title", "Reduce " + s.get("type") + " around " + s.get("subject"));
            r.put("action", action(String.valueOf(s.get("type"))));
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

    Map benchmark(ExtractionOptions options, RepositorySnapshot snapshot, Map complexity) {
        String[] profiles = {"raw", "compact", "review", "architecture", "deep-research"};
        double[] factors = {1.0d, 0.35d, 0.55d, 0.45d, 0.8d};
        int base = ((Number) complexity.get("estimatedContextTokens")).intValue();
        List results = new ArrayList();
        for (int i = 0; i < profiles.length; i++) {
            Map item = new LinkedHashMap();
            int tokens = (int) Math.round(base * factors[i]);
            item.put("profile", profiles[i]);
            item.put("estimatedTokens", tokens);
            item.put("fitsDefaultBudget", tokens <= 128000);
            item.put("risk", tokens > 128000 ? "high" : tokens > 32000 ? "medium" : "low");
            results.add(item);
        }
        Map report = new LinkedHashMap();
        report.put("schemaVersion", 1);
        report.put("method", "deterministic-preflight");
        report.put("results", results);
        report.put("recommendedProfile", "review");
        report.put("note", "No external LLM API is called; empirical latency, cost and task success can be layered on top.");
        return report;
    }

    private static Map smell(String type, Object subject, int savings) { Map s = new LinkedHashMap(); s.put("type", type); s.put("subject", subject); s.put("estimatedTokenSavings", savings); return s; }
    private static String action(String type) { if (type.contains("Weak")) return "Add or link tests and executable evidence."; if (type.contains("Scattered")) return "Create a capability facade or documentation hub."; if (type.contains("Hidden")) return "Document the concept and add seed claims."; return "Split or summarize the context cluster."; }
    private static Map after(Map before, List smells) { int savings = 0; for (Object item : smells) savings += ((Number) ((Map) item).get("estimatedTokenSavings")).intValue(); Map after = new LinkedHashMap(); int tokens = ((Number) before.get("estimatedContextTokens")).intValue(); after.put("estimatedContextTokens", Math.max(0, tokens - savings)); after.put("estimatedTokenSavings", savings); return after; }
    private static List modelProfiles() { List p = new ArrayList(); p.add(profile("gpt-5.5", 128000, 400000)); p.add(profile("claude-opus", 128000, 200000)); p.add(profile("gemini", 128000, 1000000)); p.add(profile("local-small", 16000, 32000)); return p; }
    private static Map profile(String id, int practical, int hard) { Map p = new LinkedHashMap(); p.put("id", id); p.put("practicalContextBudget", practical); p.put("hardContextLimit", hard); return p; }
    static String html(String title, Map report) { return "<!doctype html><html><head><meta charset=\"utf-8\"><title>" + title + "</title><style>body{font-family:system-ui,sans-serif;margin:2rem;max-width:1100px}pre{background:#f6f8fa;padding:1rem;overflow:auto}</style></head><body><h1>" + title + "</h1><pre>" + escape(JsonSupport.toJson(report)) + "</pre></body></html>\n"; }
    private static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
