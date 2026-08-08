package io.github.ssullivan.types;

/**
 * The JSON scalar value kinds: strings, numbers (integer/float, plus {@link #NUMBER} for a
 * merged integer/float produced by {@link io.github.ssullivan.analyze.SchemaMerger}),
 * booleans, and null.
 */
public enum ScalarType implements JsonType {
    /** JSON {@code null}. */
    NULL("null"),
    /** A JSON string. */
    STRING("string"),
    /** A JSON number with no fractional part. */
    INTEGER("integer"),
    /** A JSON number with a fractional part. */
    FLOAT("float"),
    /** JSON {@code true} or {@code false}. */
    BOOLEAN("boolean"),
    /**
     * A number of unspecified precision, produced by merging an {@link #INTEGER} with a
     * {@link #FLOAT}. Never produced by parsing a single document.
     */
    NUMBER("number");

    private final String label;

    ScalarType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
