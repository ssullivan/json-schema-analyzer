package com.github.ssullivan.types;

public final class IntNumberType implements JsonType {
    private IntNumberType() {
    }

    @Override
    public String toString() {
        return "integer";
    }

    private static final class Singleton {
        private static final IntNumberType INSTANCE = new IntNumberType();
    }

    public static IntNumberType instance() {
        return IntNumberType.Singleton.INSTANCE;
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
