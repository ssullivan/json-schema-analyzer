package com.github.ssullivan.types;

public sealed interface JsonType
        permits ArrayType, ObjectType, BooleanType, NullType, FloatNumberType, IntNumberType, StringType {

    String jsonType();

    int ordinal();

}
