package io.github.ssullivan.jackson;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ssullivan.types.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Translates an inferred {@link JsonType} shape into a
 * <a href="https://json-schema.org/specification-links.html#draft-7">JSON Schema draft-07</a>
 * document. {@code $schema} is only ever set on the document root, never on nested sub-schemas.
 */
public final class JsonSchemaWriter {
    private static final String DRAFT_07 = "http://json-schema.org/draft-07/schema#";

    private JsonSchemaWriter() {
    }

    /**
     * Translates an inferred shape into a JSON Schema draft-07 document.
     *
     * @param type a non-null shape, typically produced by {@link io.github.ssullivan.analyze.JsonSchemaAnalyzer}
     *             or {@link io.github.ssullivan.analyze.SchemaMerger}
     * @return the equivalent JSON Schema draft-07 document
     */
    public static ObjectNode toJsonSchema(JsonType type) {
        Objects.requireNonNull(type, "JsonSchemaWriter.toJsonSchema: parameter `type` must not be null");

        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("$schema", DRAFT_07);
        root.setAll(toSchemaNode(type));
        return root;
    }

    private static ObjectNode toSchemaNode(JsonType type) {
        if (type instanceof ScalarType scalarType) {
            return scalarSchema(scalarType);
        }
        if (type instanceof ObjectType objectType) {
            return objectSchema(objectType);
        }
        if (type instanceof ArrayType arrayType) {
            return arraySchema(arrayType);
        }
        if (type instanceof UnionType unionType) {
            return unionSchema(unionType);
        }
        throw new IllegalArgumentException("Unsupported JsonType: " + type);
    }

    private static ObjectNode scalarSchema(ScalarType scalarType) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", jsonSchemaTypeName(scalarType));
        return node;
    }

    private static ObjectNode objectSchema(ObjectType objectType) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "object");

        ObjectNode properties = node.putObject("properties");
        for (Map.Entry<String, JsonType> entry : objectType.getFields().entrySet()) {
            properties.set(entry.getKey(), toSchemaNode(entry.getValue()));
        }

        // draft-07's own meta-schema requires "required" to have at least one entry, so omit
        // it entirely rather than emitting an invalid empty array.
        List<String> required = objectType.getFields().keySet().stream()
                .filter(name -> !objectType.isOptional(name))
                .sorted()
                .toList();
        if (!required.isEmpty()) {
            ArrayNode requiredNode = node.putArray("required");
            required.forEach(requiredNode::add);
        }

        return node;
    }

    private static ObjectNode arraySchema(ArrayType arrayType) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "array");

        Set<JsonType> elements = arrayType.getFields();
        if (elements.size() == 1) {
            node.set("items", toSchemaNode(elements.iterator().next()));
        } else if (elements.size() > 1) {
            node.set("items", anyOfSchema(elements));
        }
        // no observed elements -> omit "items" entirely (unconstrained), rather than asserting
        // no elements are ever allowed.

        return node;
    }

    private static ObjectNode unionSchema(UnionType unionType) {
        Set<JsonType> members = unionType.getMembers();
        boolean allScalar = members.stream().allMatch(member -> member instanceof ScalarType);

        if (allScalar) {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            ArrayNode typeNode = node.putArray("type");
            members.stream()
                    .map(member -> jsonSchemaTypeName((ScalarType) member))
                    .distinct()
                    .sorted()
                    .forEach(typeNode::add);
            return node;
        }

        // At least one member is an object/array, which needs a full sub-schema rather than a
        // bare type name.
        return anyOfSchema(members);
    }

    private static ObjectNode anyOfSchema(Set<JsonType> members) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        ArrayNode anyOf = node.putArray("anyOf");
        members.stream()
                .map(JsonSchemaWriter::toSchemaNode)
                .sorted(Comparator.comparing(Object::toString))
                .forEach(anyOf::add);
        return node;
    }

    private static String jsonSchemaTypeName(ScalarType scalarType) {
        return switch (scalarType) {
            case NULL -> "null";
            case STRING -> "string";
            case INTEGER -> "integer";
            case FLOAT, NUMBER -> "number";
            case BOOLEAN -> "boolean";
        };
    }
}
