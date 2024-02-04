package com.github.ssullivan.types;

import java.util.Objects;

public final class FloatNumberType implements JsonType {
    @Override
    public String toString() {
        return "float";
    }

    @Override
    public String jsonType() {
        return "number";
    }

    @Override
    public int ordinal() {
        return 2;
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
    public int compareTo(JsonType o) {
        return Integer.compare(ordinal(), o.ordinal());
    }
}
