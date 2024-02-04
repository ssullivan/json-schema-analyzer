package com.github.ssullivan.types;

public sealed interface JsonType
        extends Comparable<JsonType>
        permits ArrayType, ObjectType, BooleanType, NullType, FloatNumberType, IntNumberType, StringType {

    String jsonType();

    int ordinal();

    @Override
    default int compareTo(JsonType o) {
        return Integer.compare(ordinal(), o.ordinal());
    }


}
