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

        CapabilitySamples samples = capabilityWorkingSets(snapshot, production);
        boolean measured = !samples.workingSets().isEmpty();
        int medianWorkingSet = measured ? percentile(samples.workingSets(), 0.50d) : 0;
        int p90WorkingSet = measured ? percentile(samples.workingSets(), 0.90d) : 0;
        double p90ContextShare = measured ? ratio(p90WorkingSet, repositoryTokens) : 0.0d;
        double evidenceRatio = ratio(testTokens + documentationTokens, productionTokens);
        double tokensPerKloc = totalLines == 0 ? 0.0d : repositoryTokens * 1000.0d / totalLines;

        // Lower is better. A missing capability sample is fail-closed at 100,
        // but is reported explicitly rather than masquerading as a repository-
        // sized capability working set.
        double evidencePenalty = productionTokens <= 0
                ? 0.0d
                : 15.0d * Math.max(0.0d, 0.25d - Math.min(0.25d, evidenceRatio)) / 0.25d;
        double rawDebt = 100.0d * p90ContextShare + evidencePenalty;
        double normalizedDebt = measured ? round2(Math.min(100.0d, rawDebt)) : 100.0d;
        double efficiency = round2(Math.max(0.0d, 100.0d - normalizedDebt));

        Map<String, Object> referenceSources = new LinkedHashMap<>();
        referenceSources.put("matchedTypes", samples.linkedReferenceCount());
        referenceSources.put("classes", samples.explicitReferenceCount());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 2);
        result.put("measurementStatus", measured ? "MEASURED" : "NO_CAPABILITY_SAMPLES");
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
        result.put("capabilityCount", samples.capabilityCount());
        result.put("capabilitySampleCount", samples.workingSets().size());
        result.put("capabilitiesWithoutTypeReferences", samples.capabilitiesWithoutTypeReferences());
        result.put("capabilitiesWithoutResolvedTypes", samples.capabilitiesWithoutResolvedTypes());
        result.put("unresolvedCapabilityTypeReferences", samples.unresolvedReferenceCount());
        result.put("capabilityReferenceSources", referenceSources);
        result.put("method", "line-weighted-linked-capability-working-set-proxy");
        return result;
    }

    private static CapabilitySamples capabilityWorkingSets(
            RepositorySnapshot snapshot,
            List<FactSize> production) {
        Map<String, Integer> byClass = new LinkedHashMap<>();
        for (FactSize fact : production) {
            byClass.put(fact.name(), fact.tokens());
        }

        List<Integer> workingSets = new ArrayList<>();
        int capabilityCount = 0;
        int linkedReferences = 0;
        int explicitReferences = 0;
        int unresolvedReferences = 0;
        int withoutReferences = 0;
        int withoutResolvedTypes = 0;

        for (Object item : snapshot.capabilities) {
            if (!(item instanceof Map capability)) continue;
            capabilityCount++;
            Set<String> classNames = new LinkedHashSet<>();
            linkedReferences += addReferences(classNames, capability.get("matchedTypes"));
            explicitReferences += addReferences(classNames, capability.get("classes"));
            if (classNames.isEmpty()) {
                withoutReferences++;
                continue;
            }

            int tokens = 0;
            int resolved = 0;
            for (String className : classNames) {
                Integer size = byClass.get(className);
                if (size == null) {
                    unresolvedReferences++;
                    continue;
                }
                tokens += size;
                resolved++;
            }
            if (resolved == 0) {
                withoutResolvedTypes++;
            } else {
                workingSets.add(tokens);
            }
        }
        workingSets.sort(Comparator.naturalOrder());
        return new CapabilitySamples(
                List.copyOf(workingSets),
                capabilityCount,
                linkedReferences,
                explicitReferences,
                unresolvedReferences,
                withoutReferences,
                withoutResolvedTypes);
    }

    private static int addReferences(Set<String> target, Object value) {
        int before = target.size();
        if (value instanceof List<?> list) {
            for (Object item : list) addReference(target, item);
        } else {
            addReference(target, value);
        }
        return target.size() - before;
    }

    private static void addReference(Set<String> target, Object value) {
        if (value == null) return;
        String name = String.valueOf(value).trim();
        if (!name.isEmpty() && !"null".equals(name)) target.add(name);
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

    private record CapabilitySamples(
            List<Integer> workingSets,
            int capabilityCount,
            int linkedReferenceCount,
            int explicitReferenceCount,
            int unresolvedReferenceCount,
            int capabilitiesWithoutTypeReferences,
            int capabilitiesWithoutResolvedTypes) {
    }
}
