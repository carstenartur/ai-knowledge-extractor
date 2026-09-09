package org.aiknowledge.core.analysis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Independently versioned experimental weights; not a validated measure of human cognitive load. */
public final class BoundaryScoringModel {
    public static final String VERSION = "1.0";
    public static final Map<String, Double> WEIGHTS;
    static {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("structuralCoupling", 0.20);
        weights.put("orchestration", 0.20);
        weights.put("modelTranslation", 0.15);
        weights.put("semanticCoupling", 0.15);
        weights.put("errorComplexity", 0.10);
        weights.put("dependencySurface", 0.10);
        weights.put("contractClarity", 0.10);
        WEIGHTS = Collections.unmodifiableMap(weights);
    }
    private BoundaryScoringModel() { }

    /** Recomputes the proxy from raw dimensions, optionally with sensitivity-study weights. */
    public static int score(Map<String, ? extends Map<String, ?>> dimensions, Map<String, Double> weights) {
        if (!weights.keySet().equals(WEIGHTS.keySet())) throw new IllegalArgumentException("Expected the seven boundary dimensions");
        double total = 0;
        double weightSum = 0;
        for (String name : WEIGHTS.keySet()) {
            Double weight = weights.get(name);
            if (weight == null || !Double.isFinite(weight) || weight < 0) throw new IllegalArgumentException("Invalid weight: " + name);
            if (!dimensions.containsKey(name) || !(dimensions.get(name).get("score") instanceof Number value)
                    || !Double.isFinite(value.doubleValue()) || value.doubleValue() < 0 || value.doubleValue() > 100) {
                throw new IllegalArgumentException("Invalid dimension score: " + name);
            }
            double score = value.doubleValue();
            total += weight * (name.equals("contractClarity") ? 100 - score : score);
            weightSum += weight;
        }
        if (weightSum == 0) throw new IllegalArgumentException("At least one weight must be positive");
        return Math.max(0, Math.min(100, (int) Math.round(total / weightSum)));
    }
}
