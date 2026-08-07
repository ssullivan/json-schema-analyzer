package io.github.ssullivan.types;

public sealed interface JsonType
        permits ArrayType, ObjectType, ScalarType, UnionType {
}
