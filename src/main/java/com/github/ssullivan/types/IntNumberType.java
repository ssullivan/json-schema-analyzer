package com.github.ssullivan.types;

public final class IntNumberType implements JsonType {
    @Override
    public String toString() {
        return "integer";
    }

    @Override
    public String jsonType() {
        return "number";
    }

    @Override
    public int ordinal() {
        return 1;
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
    public int compareTo(JsonType o) {
        return Integer.compare(ordinal(), o.ordinal());
    }
}
