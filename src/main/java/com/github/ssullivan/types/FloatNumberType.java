package com.github.ssullivan.types;

public final class FloatNumberType implements JsonType {
    private FloatNumberType() {
    }

    @Override
    public String toString() {
        return "float";
    }

    private static final class Singleton {
        private static final FloatNumberType INSTANCE = new FloatNumberType();
    }

    public static FloatNumberType instance() {
        return FloatNumberType.Singleton.INSTANCE;
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
