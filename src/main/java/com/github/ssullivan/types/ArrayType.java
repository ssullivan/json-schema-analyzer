package com.github.ssullivan.types;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class ArrayType implements JsonType {
    private final Set<JsonType> fields = new TreeSet<>();

    public void addField(JsonType type) {
        if (this.fields.add(type)) {
            int j = 0;
        }
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
    public String jsonType() {
        return "array";
    }

    @Override
    public int ordinal() {
        return 4;
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

    @Override
    public int compareTo(JsonType o) {
        if (this == o) return 0;
        if (o == null || getClass() != o.getClass()) return -1;
        if (Objects.equals(fields, ((ArrayType) o).fields)) return 0;
        return 1;
    }
}
