package com.github.ssullivan.types;

public final class StringType implements JsonType {
    @Override
    public String toString() {
        return "string";
    }

    @Override
    public int compareTo(JsonType o) {
        return Integer.compare(ordinal(), o.ordinal());
    }

    @Override
    public String jsonType() {
        return "string";
    }

    @Override
    public int ordinal() {
        return 0;
    }

    private static final class Singleton {
        private static final StringType INSTANCE = new StringType();
    }

    public static StringType instance() {
        return StringType.Singleton.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }


}
