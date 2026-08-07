package com.github.ssullivan.types;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ArrayType implements JsonType {
    private final Set<JsonType> fields = new LinkedHashSet<>();

    public void addField(JsonType type) {
        this.fields.add(type);
    }

    public Set<JsonType> getFields() {
        return fields;
    }

    public static ArrayType of(JsonType... jsonTypes) {
        ArrayType retval = new ArrayType();
        Arrays.stream(jsonTypes)
                .forEach(retval::addField);
        return retval;
    }

    @Override
    public String toString() {
        return "[" + fields.stream().map(String::valueOf).collect(Collectors.joining(", ")) + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayType arrayType = (ArrayType) o;
        return Objects.equals(fields, arrayType.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields);
    }
}
