package org.aiknowledge.core.analysis;

/**
 * Shared semantics for language-specific control-flow complexity providers.
 *
 * <p>Parsers may have different precision, but an emitted value with this model
 * identifier follows the same decision-point and nesting interpretation.</p>
 */
public final class ComplexityModel {
    public static final String ID = "aiknowledge-control-flow-v1";

    private ComplexityModel() {
    }
}
