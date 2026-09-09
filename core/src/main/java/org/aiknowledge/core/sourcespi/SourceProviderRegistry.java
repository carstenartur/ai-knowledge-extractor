package org.aiknowledge.core.sourcespi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.aiknowledge.core.javahttp.JavaHttpBoundaryKnowledgeProvider;
import org.aiknowledge.core.javascript.JavaScriptTypeScriptKnowledgeProvider;

/** Deterministic provider discovery using an explicitly owned classloader. */
public final class SourceProviderRegistry {
    private SourceProviderRegistry() { }

    public static List<SourceKnowledgeProvider> load(ClassLoader loader) {
        List<SourceKnowledgeProvider> all = new ArrayList<>(List.of(
                new JavaScriptTypeScriptKnowledgeProvider(), new JavaHttpBoundaryKnowledgeProvider()));
        ServiceLoader.load(SourceKnowledgeProvider.class, loader).stream()
                .sorted(Comparator.comparing(provider -> provider.type().getName()))
                .forEach(provider -> all.add(provider.get()));
        return validate(all);
    }

    public static List<SourceKnowledgeProvider> validate(List<SourceKnowledgeProvider> providers) {
        Map<String, SourceKnowledgeProvider> ids = new LinkedHashMap<>();
        for (SourceKnowledgeProvider provider : providers.stream()
                .sorted(Comparator.comparing(value -> value.getClass().getName())).toList()) {
            String id = provider.id();
            if (id == null || id.isBlank() || !id.equals(id.trim())) {
                throw new SourceFactContract.ContractException("Source provider "
                        + provider.getClass().getName() + " must declare a nonblank, unpadded id");
            }
            SourceFactContract.requireCompatible(id, provider.contractVersion());
            SourceKnowledgeProvider previous = ids.putIfAbsent(id, provider);
            if (previous != null) {
                throw new SourceFactContract.ContractException("Duplicate source provider id '" + id
                        + "': " + previous.getClass().getName() + " and " + provider.getClass().getName()
                        + ". Remove the duplicate dependency or give the provider a unique id.");
            }
        }
        return ids.values().stream().sorted(
                Comparator.comparingInt(SourceKnowledgeProvider::priority).reversed()
                        .thenComparing(SourceKnowledgeProvider::id)).toList();
    }
}
