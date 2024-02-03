package com.github.ssullivan.types;

public final class NullType implements JsonType {

    @Override
    public String toString() {
        return "null";
    }

    @Override
    public String jsonType() {
        return toString();
    }

    @Override
    public int ordinal() {
        return -1;
    }

    private static final class Singleton {
        private static final NullType INSTANCE = new NullType();
    }

    public static NullType instance() {
        return NullType.Singleton.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int compareTo(JsonType o) {
        return Integer.compare(ordinal(), o.ordinal());
    }
}
