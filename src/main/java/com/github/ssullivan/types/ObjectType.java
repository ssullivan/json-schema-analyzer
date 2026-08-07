package com.github.ssullivan.types;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class ObjectType implements JsonType {
    private final Map<String, JsonType> fields = new TreeMap<>();
    private final Set<String> optionalFields = new TreeSet<>();

    public void addField(String name, JsonType type) {
        this.fields.put(name, type);
    }

    public Map<String, JsonType> getFields() {
        return fields;
    }

    public void markOptional(String name) {
        this.optionalFields.add(name);
    }

    public boolean isOptional(String name) {
        return this.optionalFields.contains(name);
    }

    public Set<String> getOptionalFields() {
        return optionalFields;
    }

    @Override
    public String toString() {
        return "{ " + fields.entrySet()
                .stream()
                .map(entry -> String.format("'%s': %s", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", ")) + " }";
    }

    public boolean contains(String name, JsonType type) {
        JsonType existing = fields.get(name);
        if (existing == null) {
            return false;
        }
        return Objects.equals(existing, type);
    }

    public static ObjectType of(String field, JsonType type) {
        ObjectType retval = new ObjectType();
        retval.addField(field, type);
        return retval;
    }

    @Override
    public String jsonType() {
        return "object";
    }

    @Override
    public int ordinal() {
        return 5;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObjectType objectType = (ObjectType) o;
        return Objects.equals(fields, objectType.fields)
                && Objects.equals(optionalFields, objectType.optionalFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields, optionalFields);
    }
}
