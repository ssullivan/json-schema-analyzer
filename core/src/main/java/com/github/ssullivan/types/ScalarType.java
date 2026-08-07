package com.github.ssullivan.types;

/**
 * The JSON scalar value kinds: strings, numbers (integer/float, plus {@link #NUMBER} for a
 * merged integer/float produced by {@link com.github.ssullivan.analyze.SchemaMerger}),
 * booleans, and null.
 */
public enum ScalarType implements JsonType {
    NULL("null"),
    STRING("string"),
    INTEGER("integer"),
    FLOAT("float"),
    BOOLEAN("boolean"),
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
