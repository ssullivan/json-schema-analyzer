package com.github.ssullivan.types;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ObjectType implements JsonType {
    private final Map<String, JsonType> fields = new TreeMap<>();

    public void addField(String name, JsonType type) {
        this.fields.put(name, type);
    }

    public Map<String, JsonType> getFields() {
        return fields;
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
        return Objects.equals(fields, objectType.fields);
    }


    @Override
    public int compareTo(JsonType o) {
        if (this == o) return 0;
        if (o == null || getClass() != o.getClass()) return -1;
        if (Objects.equals(fields, ((ObjectType) o).fields)) return 0;
        return 1;
    }
}
