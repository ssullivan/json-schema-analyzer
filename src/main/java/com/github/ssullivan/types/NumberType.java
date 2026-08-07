package com.github.ssullivan.types;

/**
 * Represents a merged {@link IntNumberType}/{@link FloatNumberType} — produced only by
 * {@link com.github.ssullivan.analyze.SchemaMerger} when the same field is observed as both
 * an integer and a float across samples.
 */
public final class NumberType implements JsonType {
    private NumberType() {
    }

    @Override
    public String toString() {
        return "number";
    }

    @Override
    public String jsonType() {
        return "number";
    }

    @Override
    public int ordinal() {
        return 6;
    }

    private static final class Singleton {
        private static final NumberType INSTANCE = new NumberType();
    }

    public static NumberType instance() {
        return NumberType.Singleton.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
