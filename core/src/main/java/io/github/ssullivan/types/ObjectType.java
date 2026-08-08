package io.github.ssullivan.types;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The shape of a JSON object: field names mapped to the shape observed for each, plus which of
 * those fields are optional (present in some but not all merged samples — see
 * {@link io.github.ssullivan.analyze.SchemaMerger}). A freshly-parsed object from a single
 * document has no optional fields; optionality only appears once shapes have been merged.
 * Fields are kept in sorted-by-name order for deterministic output.
 * <p>
 * <strong>Do not mutate an instance after adding it to another type.</strong> {@code equals}
 * and {@code hashCode} are derived from the fields, and {@link ArrayType}/{@link UnionType}
 * store their members in hash-based sets — so calling {@link #addField} or
 * {@link #markOptional} on an already-stored instance leaves it unfindable in the set that
 * contains it. Finish building a shape first, then add it. Everything this library returns is
 * built that way; the hazard only applies to types you assemble yourself.
 */
public final class ObjectType implements JsonType {
    private final Map<String, JsonType> fields = new TreeMap<>();
    private final Set<String> optionalFields = new TreeSet<>();

    /**
     * Creates an object shape with no fields.
     */
    public ObjectType() {
    }

    /**
     * Adds a field, replacing any shape already recorded under {@code name}.
     *
     * @param name the field name
     * @param type the shape observed for that field
     */
    public void addField(String name, JsonType type) {
        this.fields.put(name, type);
    }

    /**
     * Returns the fields of this object.
     *
     * @return an unmodifiable view of the fields, in sorted-by-name order
     */
    public Map<String, JsonType> getFields() {
        return Collections.unmodifiableMap(fields);
    }

    /**
     * Records that a field is absent from at least one merged sample.
     *
     * @param name the field name
     */
    public void markOptional(String name) {
        this.optionalFields.add(name);
    }

    /**
     * Reports whether a field was absent from at least one merged sample.
     *
     * @param name the field name
     * @return {@code true} if the field is optional
     */
    public boolean isOptional(String name) {
        return this.optionalFields.contains(name);
    }

    /**
     * Returns the optional fields of this object.
     *
     * @return an unmodifiable view of the optional field names
     */
    public Set<String> getOptionalFields() {
        return Collections.unmodifiableSet(optionalFields);
    }

    @Override
    public String toString() {
        return "{ " + fields.entrySet()
                .stream()
                .map(entry -> String.format("'%s': %s", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", ")) + " }";
    }

    /**
     * Reports whether this object holds a field of exactly the given shape.
     *
     * @param name the field name
     * @param type the shape to compare against
     * @return {@code true} if the field exists and its shape equals {@code type}
     */
    public boolean contains(String name, JsonType type) {
        JsonType existing = fields.get(name);
        if (existing == null) {
            return false;
        }
        return Objects.equals(existing, type);
    }

    /**
     * Creates a single-field object shape.
     *
     * @param field the field name
     * @param type  the shape observed for that field
     * @return the new object shape
     */
    public static ObjectType of(String field, JsonType type) {
        ObjectType retval = new ObjectType();
        retval.addField(field, type);
        return retval;
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
