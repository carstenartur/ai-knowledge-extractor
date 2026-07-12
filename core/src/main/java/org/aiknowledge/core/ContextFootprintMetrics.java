package org.aiknowledge.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        WorkingSetSamples workingSets = capabilityWorkingSets(snapshot, production);
        boolean available = !workingSets.tokens().isEmpty();
        int medianWorkingSet = percentile(workingSets.tokens(), 0.50d);
        int p90WorkingSet = percentile(workingSets.tokens(), 0.90d);
        double p90ContextShare = available ? ratio(p90WorkingSet, repositoryTokens) : 0.0d;
        double evidenceRatio = ratio(testTokens + documentationTokens, productionTokens);
        double tokensPerKloc = totalLines == 0 ? 0.0d : repositoryTokens * 1000.0d / totalLines;

        // Lower is better. Do not invent a repository-sized capability when no
        // linked/explicit type identities exist; report that state separately.
        double evidencePenalty = available && productionTokens > 0
                ? 15.0d * Math.max(0.0d, 0.25d - Math.min(0.25d, evidenceRatio)) / 0.25d
                : 0.0d;
        double rawDebt = available ? 100.0d * p90ContextShare + evidencePenalty : 0.0d;
        double normalizedDebt = round2(Math.min(100.0d, rawDebt));
        double efficiency = available ? round2(Math.max(0.0d, 100.0d - normalizedDebt)) : 0.0d;

        Map<String, Object> sources = new LinkedHashMap<>();
        sources.put("linkedMatchedTypes", workingSets.linkedCapabilities());
        sources.put("explicitClasses", workingSets.explicitCapabilities());
        sources.put("combinedLinkedAndExplicit", workingSets.combinedCapabilities());
        sources.put("withoutResolvedTypes", workingSets.unavailableCapabilities());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 2);
        result.put("measurementStatus", available ? "available" : "unavailable-no-capability-working-set");
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
        result.put("capabilitySampleCount", workingSets.tokens().size());
        result.put("unresolvedCapabilityTypeReferences", workingSets.unresolvedTypeReferences());
        result.put("capabilityWorkingSetSources", sources);
        result.put("workingSetIdentityFields", List.of("matchedTypes", "classes"));
        result.put("method", "line-weighted-linked-capability-working-set-proxy");
        return result;
    }

    private static WorkingSetSamples capabilityWorkingSets(
            RepositorySnapshot snapshot,
            List<FactSize> production) {
        Map<String, Integer> byClass = new LinkedHashMap<>();
        for (FactSize fact : production) byClass.put(fact.name(), fact.tokens());

        List<Integer> result = new ArrayList<>();
        int linkedCapabilities = 0;
        int explicitCapabilities = 0;
        int combinedCapabilities = 0;
        int unavailableCapabilities = 0;
        int unresolvedReferences = 0;

        for (Object item : snapshot.capabilities) {
            if (!(item instanceof Map capability)) continue;
            List<String> linked = strings(capability.get("matchedTypes"));
            List<String> explicit = strings(capability.get("classes"));
            boolean hasLinked = !linked.isEmpty();
            boolean hasExplicit = !explicit.isEmpty();

            if (hasLinked && hasExplicit) combinedCapabilities++;
            else if (hasLinked) linkedCapabilities++;
            else if (hasExplicit) explicitCapabilities++;

            Set<String> classNames = new LinkedHashSet<>();
            classNames.addAll(linked);
            classNames.addAll(explicit);
            int tokens = 0;
            for (String className : classNames) {
                Integer classTokens = byClass.get(className);
                if (classTokens == null) unresolvedReferences++;
                else tokens += classTokens;
            }
            if (tokens > 0) result.add(tokens);
            else unavailableCapabilities++;
        }
        result.sort(Comparator.naturalOrder());
        return new WorkingSetSamples(
                List.copyOf(result),
                linkedCapabilities,
                explicitCapabilities,
                combinedCapabilities,
                unavailableCapabilities,
                unresolvedReferences);
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            String text = String.valueOf(item);
            if (!text.isBlank() && !"null".equals(text)) result.add(text);
        }
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

    private static int percentile(List<Integer> values, double percentile) {
        if (values.isEmpty()) return 0;
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

    private record WorkingSetSamples(
            List<Integer> tokens,
            int linkedCapabilities,
            int explicitCapabilities,
            int combinedCapabilities,
            int unavailableCapabilities,
            int unresolvedTypeReferences) {
    }
}
