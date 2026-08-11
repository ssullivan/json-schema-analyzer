package io.github.ssullivan.types;

/**
 * The JSON scalar value kinds: strings (plus {@link #DATE}, {@link #DATE_TIME}, and
 * {@link #UUID} for a string recognized as one of those formats — only produced when
 * {@link io.github.ssullivan.analyze.JsonSchemaAnalyzer} is constructed with format detection
 * enabled), numbers (integer/float, plus {@link #NUMBER} for a merged integer/float produced by
 * {@link io.github.ssullivan.analyze.SchemaMerger}), booleans, and null.
 */
public enum ScalarType implements JsonType {
    /** JSON {@code null}. */
    NULL("null"),
    /** A JSON string. */
    STRING("string"),
    /** An ISO 8601 calendar date ({@code YYYY-MM-DD}), detected only with format detection on. */
    DATE("date"),
    /** An RFC 3339 timestamp, detected only with format detection on. */
    DATE_TIME("date-time"),
    /** A canonical 8-4-4-4-12 hex UUID, detected only with format detection on. */
    UUID("uuid"),
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
