package io.github.ssullivan.types;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The shape of a JSON array: the set of distinct element shapes observed within it. Elements
 * are deliberately not merged against each other — an array is allowed to be a legitimate
 * mix of shapes (e.g. a discriminated union), so {@code [1, "a", {"id": 1}]} keeps all three as
 * separate entries rather than collapsing them. Iteration order reflects first appearance.
 */
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
