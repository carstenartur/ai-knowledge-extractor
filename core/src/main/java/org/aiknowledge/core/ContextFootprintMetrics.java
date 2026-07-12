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

        double evidencePenalty = productionTokens <= 0
                ? 0.0d
                : 15.0d * Math.max(0.0d, 0.25d - Math.min(0.25d, evidenceRatio)) / 0.25d;
        double rawDebt = 100.0d * p90ContextShare + evidencePenalty;
        double normalizedDebt = measured ? round2(Math.min(100.0d, rawDebt)) : 100.0d;
        double efficiency = round2(Math.max(0.0d, 100.0d - normalizedDebt));

        Map<String, Object> referenceSources = new LinkedHashMap<>();
        referenceSources.put("matchedTypes", samples.matchedTypeReferenceCount());
        referenceSources.put("classes", samples.explicitTypeReferenceCount());
        referenceSources.put("matchedModules", samples.matchedModuleReferenceCount());
        referenceSources.put("modules", samples.explicitModuleReferenceCount());
        referenceSources.put("ownerModules", samples.ownerModuleReferenceCount());
        referenceSources.put("matchedPackages", samples.matchedPackageReferenceCount());
        referenceSources.put("packages", samples.explicitPackageReferenceCount());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 3);
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
        result.put("capabilitiesWithoutSelectors", samples.capabilitiesWithoutSelectors());
        result.put("capabilitiesWithoutResolvedTypes", samples.capabilitiesWithoutResolvedTypes());
        result.put("unresolvedCapabilityTypeReferences", samples.unresolvedTypeReferenceCount());
        result.put("unresolvedCapabilityModuleReferences", samples.unresolvedModuleReferenceCount());
        result.put("unresolvedCapabilityPackageReferences", samples.unresolvedPackageReferenceCount());
        result.put("capabilityReferenceSources", referenceSources);
        result.put("method", "line-weighted-capability-selector-working-set-proxy");
        return result;
    }

    private static CapabilitySamples capabilityWorkingSets(
            RepositorySnapshot snapshot,
            List<FactSize> production) {
        Map<String, FactSize> byClass = new LinkedHashMap<>();
        for (FactSize fact : production) {
            byClass.put(fact.name(), fact);
        }
        Map<String, String> modulePaths = modulePaths(snapshot.modules);

        List<Integer> workingSets = new ArrayList<>();
        Counters counters = new Counters();
        for (Object item : snapshot.capabilities) {
            if (!(item instanceof Map capability)) continue;
            counters.capabilityCount++;

            Set<String> typeSelectors = new LinkedHashSet<>();
            counters.matchedTypeReferences += addReferences(typeSelectors, capability.get("matchedTypes"));
            counters.explicitTypeReferences += addReferences(typeSelectors, capability.get("classes"));

            Set<String> moduleSelectors = new LinkedHashSet<>();
            counters.matchedModuleReferences += addReferences(moduleSelectors, capability.get("matchedModules"));
            counters.explicitModuleReferences += addReferences(moduleSelectors, capability.get("modules"));
            counters.ownerModuleReferences += addReferences(moduleSelectors, capability.get("ownerModules"));

            Set<String> packageSelectors = new LinkedHashSet<>();
            counters.matchedPackageReferences += addReferences(packageSelectors, capability.get("matchedPackages"));
            counters.explicitPackageReferences += addReferences(packageSelectors, capability.get("packages"));

            if (typeSelectors.isEmpty() && moduleSelectors.isEmpty() && packageSelectors.isEmpty()) {
                counters.withoutSelectors++;
                continue;
            }

            Map<String, FactSize> selected = new LinkedHashMap<>();
            for (String className : typeSelectors) {
                FactSize fact = byClass.get(className);
                if (fact == null) {
                    counters.unresolvedTypeReferences++;
                } else {
                    selected.put(fact.name(), fact);
                }
            }
            for (String module : moduleSelectors) {
                int before = selected.size();
                for (FactSize fact : production) {
                    if (matchesModule(fact, module, modulePaths)) {
                        selected.put(fact.name(), fact);
                    }
                }
                if (selected.size() == before && production.stream()
                        .noneMatch(fact -> matchesModule(fact, module, modulePaths))) {
                    counters.unresolvedModuleReferences++;
                }
            }
            for (String packageName : packageSelectors) {
                int before = selected.size();
                for (FactSize fact : production) {
                    if (matchesPackage(fact, packageName)) {
                        selected.put(fact.name(), fact);
                    }
                }
                if (selected.size() == before && production.stream()
                        .noneMatch(fact -> matchesPackage(fact, packageName))) {
                    counters.unresolvedPackageReferences++;
                }
            }

            if (selected.isEmpty()) {
                counters.withoutResolvedTypes++;
            } else {
                workingSets.add(selected.values().stream().mapToInt(FactSize::tokens).sum());
            }
        }
        workingSets.sort(Comparator.naturalOrder());
        return counters.toSamples(workingSets);
    }

    private static Map<String, String> modulePaths(List modules) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Object item : modules) {
            if (!(item instanceof Map module)) continue;
            String name = text(module.get("name"));
            String path = normalizePath(text(module.get("path")));
            if (!name.isEmpty()) result.put(name, path);
        }
        return result;
    }

    private static boolean matchesModule(
            FactSize fact,
            String moduleSelector,
            Map<String, String> modulePaths) {
        String selector = moduleSelector.trim();
        if (selector.isEmpty()) return false;
        if (selector.equals(fact.moduleName())) return true;
        String path = normalizePath(modulePaths.getOrDefault(selector, selector));
        if (path.isEmpty()) return false;
        return fact.sourceFile().equals(path) || fact.sourceFile().startsWith(path + "/");
    }

    private static boolean matchesPackage(FactSize fact, String packageSelector) {
        String selector = packageSelector.trim();
        if (selector.isEmpty()) return false;
        return fact.packageName().equals(selector) || fact.packageName().startsWith(selector + ".");
    }

    private static int addReferences(Set<String> target, Object value) {
        int before = target.size();
        if (value instanceof List<?> list) {
            for (Object item : list) addReference(target, item);
        } else if (value instanceof String text && (text.contains(",") || text.contains(";"))) {
            for (String item : text.split("[,;]")) addReference(target, item);
        } else {
            addReference(target, value);
        }
        return target.size() - before;
    }

    private static void addReference(Set<String> target, Object value) {
        String name = text(value);
        if (!name.isEmpty() && !"null".equals(name)) target.add(name);
    }

    private static List<FactSize> sizes(List facts, String kind, int tokensPerLine, int fallbackTokens) {
        List<FactSize> result = new ArrayList<>();
        for (Object item : facts) {
            if (!(item instanceof Map fact)) continue;
            int lineCount = number(fact.get("lineCount"));
            int estimatedTokens = lineCount > 0 ? lineCount * tokensPerLine : fallbackTokens;
            String name = switch (kind) {
                case "class" -> text(fact.getOrDefault("class", fact.getOrDefault("name", "")));
                case "test" -> text(fact.getOrDefault("testClass", fact.getOrDefault("class", "")));
                default -> text(fact.getOrDefault("title", fact.getOrDefault("path", "")));
            };
            result.add(new FactSize(
                    name,
                    text(fact.get("package")),
                    text(fact.get("module")),
                    normalizePath(text(fact.getOrDefault("sourceFile", fact.getOrDefault("path", "")))),
                    lineCount,
                    estimatedTokens));
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

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String normalizePath(String value) {
        String path = value.replace('\\', '/').trim();
        while (path.startsWith("./")) path = path.substring(2);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path;
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

    private record FactSize(
            String name,
            String packageName,
            String moduleName,
            String sourceFile,
            int lines,
            int tokens) {
    }

    private static final class Counters {
        private int capabilityCount;
        private int matchedTypeReferences;
        private int explicitTypeReferences;
        private int matchedModuleReferences;
        private int explicitModuleReferences;
        private int ownerModuleReferences;
        private int matchedPackageReferences;
        private int explicitPackageReferences;
        private int unresolvedTypeReferences;
        private int unresolvedModuleReferences;
        private int unresolvedPackageReferences;
        private int withoutSelectors;
        private int withoutResolvedTypes;

        private CapabilitySamples toSamples(List<Integer> workingSets) {
            return new CapabilitySamples(
                    List.copyOf(workingSets), capabilityCount,
                    matchedTypeReferences, explicitTypeReferences,
                    matchedModuleReferences, explicitModuleReferences, ownerModuleReferences,
                    matchedPackageReferences, explicitPackageReferences,
                    unresolvedTypeReferences, unresolvedModuleReferences, unresolvedPackageReferences,
                    withoutSelectors, withoutResolvedTypes);
        }
    }

    private record CapabilitySamples(
            List<Integer> workingSets,
            int capabilityCount,
            int matchedTypeReferenceCount,
            int explicitTypeReferenceCount,
            int matchedModuleReferenceCount,
            int explicitModuleReferenceCount,
            int ownerModuleReferenceCount,
            int matchedPackageReferenceCount,
            int explicitPackageReferenceCount,
            int unresolvedTypeReferenceCount,
            int unresolvedModuleReferenceCount,
            int unresolvedPackageReferenceCount,
            int capabilitiesWithoutSelectors,
            int capabilitiesWithoutResolvedTypes) {
    }
}
