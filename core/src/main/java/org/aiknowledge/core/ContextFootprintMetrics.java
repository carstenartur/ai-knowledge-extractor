package org.aiknowledge.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives context-footprint metrics from extracted facts without treating every
 * class as an equal fixed cost. The estimates intentionally remain tokenizer
 * independent, but use observed line counts and capability-local working sets.
 */
final class ContextFootprintMetrics {
    private static final int CODE_TOKENS_PER_LINE = 8;
    private static final int DOC_TOKENS_PER_LINE = 6;
    private static final int FALLBACK_CLASS_TOKENS = 260;
    private static final int FALLBACK_TEST_TOKENS = 140;
    private static final int FALLBACK_DOC_TOKENS = 180;

    private ContextFootprintMetrics() {
    }

    static Map<String, Object> calculate(RepositorySnapshot snapshot) {
        List<FactSize> production = sizes(snapshot.classes, "class", CODE_TOKENS_PER_LINE, FALLBACK_CLASS_TOKENS);
        List<FactSize> tests = sizes(snapshot.tests, "test", CODE_TOKENS_PER_LINE, FALLBACK_TEST_TOKENS);
        List<FactSize> docs = sizes(snapshot.docs, "doc", DOC_TOKENS_PER_LINE, FALLBACK_DOC_TOKENS);

        int productionTokens = tokens(production);
        int testTokens = tokens(tests);
        int documentationTokens = tokens(docs);
        int repositoryTokens = productionTokens + testTokens + documentationTokens;
        int productionLines = lines(production);
        int totalLines = productionLines + lines(tests) + lines(docs);

        List<Integer> capabilityWorkingSets = capabilityWorkingSets(snapshot, production);
        int medianWorkingSet = percentile(capabilityWorkingSets, 0.50d, repositoryTokens);
        int p90WorkingSet = percentile(capabilityWorkingSets, 0.90d, repositoryTokens);
        double p90ContextShare = ratio(p90WorkingSet, repositoryTokens);
        double evidenceRatio = ratio(testTokens + documentationTokens, productionTokens);
        double tokensPerKloc = totalLines == 0 ? 0.0d : repositoryTokens * 1000.0d / totalLines;

        // Lower is better. This proxy measures how much of the repository a
        // typical large capability requires, with a bounded penalty for weak
        // evidence. Repository growth alone does not increase the score when
        // capability-local working sets remain stable.
        double evidencePenalty = productionTokens <= 0
                ? 0.0d
                : 15.0d * Math.max(0.0d, 0.25d - Math.min(0.25d, evidenceRatio)) / 0.25d;
        double rawDebt = 100.0d * p90ContextShare + evidencePenalty;
        double normalizedDebt = round2(Math.min(100.0d, rawDebt));
        double efficiency = round2(Math.max(0.0d, 100.0d - normalizedDebt));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("repositoryContextTokens", repositoryTokens);
        result.put("productionContextTokens", productionTokens);
        result.put("testEvidenceTokens", testTokens);
        result.put("documentationContextTokens", documentationTokens);
        result.put("productionLines", productionLines);
        result.put("totalContextLines", totalLines);
        result.put("tokensPerKloc", round2(tokensPerKloc));
        result.put("medianCapabilityWorkingSetTokens", medianWorkingSet);
        result.put("p90CapabilityWorkingSetTokens", p90WorkingSet);
        result.put("p90RepositoryContextShare", round4(p90ContextShare));
        result.put("evidenceToProductionRatio", round4(evidenceRatio));
        result.put("normalizedContextDebt", normalizedDebt);
        result.put("contextEfficiencyScore", efficiency);
        result.put("capabilitySampleCount", capabilityWorkingSets.size());
        result.put("method", "line-weighted-capability-working-set-proxy");
        return result;
    }

    private static List<Integer> capabilityWorkingSets(RepositorySnapshot snapshot, List<FactSize> production) {
        Map<String, Integer> byClass = new LinkedHashMap<>();
        for (FactSize fact : production) {
            byClass.put(fact.name(), fact.tokens());
        }
        List<Integer> result = new ArrayList<>();
        for (Object item : snapshot.capabilities) {
            if (!(item instanceof Map capability)) continue;
            Object classes = capability.get("classes");
            if (!(classes instanceof List classNames) || classNames.isEmpty()) continue;
            int tokens = 0;
            for (Object className : classNames) {
                tokens += byClass.getOrDefault(String.valueOf(className), FALLBACK_CLASS_TOKENS);
            }
            if (tokens > 0) result.add(tokens);
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    private static List<FactSize> sizes(List facts, String kind, int tokensPerLine, int fallbackTokens) {
        List<FactSize> result = new ArrayList<>();
        for (Object item : facts) {
            if (!(item instanceof Map fact)) continue;
            int lineCount = number(fact.get("lineCount"));
            int estimatedTokens = lineCount > 0 ? lineCount * tokensPerLine : fallbackTokens;
            String name = switch (kind) {
                case "class" -> String.valueOf(fact.getOrDefault("class", fact.getOrDefault("name", "")));
                case "test" -> String.valueOf(fact.getOrDefault("testClass", fact.getOrDefault("class", "")));
                default -> String.valueOf(fact.getOrDefault("title", fact.getOrDefault("path", "")));
            };
            result.add(new FactSize(name, lineCount, estimatedTokens));
        }
        return result;
    }

    private static int percentile(List<Integer> values, double percentile, int fallback) {
        if (values.isEmpty()) return fallback;
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }

    private static int tokens(List<FactSize> facts) {
        return facts.stream().mapToInt(FactSize::tokens).sum();
    }

    private static int lines(List<FactSize> facts) {
        return facts.stream().mapToInt(FactSize::lines).sum();
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static double ratio(int numerator, int denominator) {
        return denominator <= 0 ? 0.0d : numerator / (double) denominator;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0d) / 10000.0d;
    }

    private record FactSize(String name, int lines, int tokens) {
    }
}
