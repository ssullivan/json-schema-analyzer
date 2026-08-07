package com.github.ssullivan.types;

public final class BooleanType implements JsonType {
    private BooleanType() {
    }

    @Override
    public String toString() {
        return "boolean";
    }

    @Override
    public String jsonType() {
        return toString();
    }

    @Override
    public int ordinal() {
        return 3;
    }

    private static final class Singleton {
        private static final BooleanType INSTANCE = new BooleanType();
    }

    public static BooleanType instance() {
        return Singleton.INSTANCE;
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
