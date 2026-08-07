package io.github.ssullivan;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ssullivan.jackson.JsonSchemaWriter;
import io.github.ssullivan.types.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaWriterTest {

    @Test
    void testRootHasSchemaDeclaration() {
        ObjectNode schema = JsonSchemaWriter.toJsonSchema(ScalarType.STRING);

        assertEquals("http://json-schema.org/draft-07/schema#", schema.get("$schema").asText());
    }

    @Test
    void testScalarTypesMapToJsonSchemaTypeNames() {
        assertEquals("string", JsonSchemaWriter.toJsonSchema(ScalarType.STRING).get("type").asText());
        assertEquals("integer", JsonSchemaWriter.toJsonSchema(ScalarType.INTEGER).get("type").asText());
        assertEquals("boolean", JsonSchemaWriter.toJsonSchema(ScalarType.BOOLEAN).get("type").asText());
        assertEquals("null", JsonSchemaWriter.toJsonSchema(ScalarType.NULL).get("type").asText());
    }

    @Test
    void testFloatAndNumberBothMapToJsonSchemaNumber() {
        assertEquals("number", JsonSchemaWriter.toJsonSchema(ScalarType.FLOAT).get("type").asText());
        assertEquals("number", JsonSchemaWriter.toJsonSchema(ScalarType.NUMBER).get("type").asText());
    }

    @Test
    void testObjectWithRequiredAndOptionalFields() {
        ObjectType objectType = new ObjectType();
        objectType.addField("id", ScalarType.INTEGER);
        objectType.addField("email", ScalarType.STRING);
        objectType.markOptional("email");

        ObjectNode schema = JsonSchemaWriter.toJsonSchema(objectType);

        assertEquals("object", schema.get("type").asText());
        assertEquals("integer", schema.get("properties").get("id").get("type").asText());
        assertEquals("string", schema.get("properties").get("email").get("type").asText());

        assertTrue(schema.get("required").isArray());
        assertEquals(1, schema.get("required").size());
        assertEquals("id", schema.get("required").get(0).asText());
    }

    @Test
    void testObjectWithNoRequiredFieldsOmitsRequiredArray() {
        ObjectType objectType = new ObjectType();
        objectType.addField("email", ScalarType.STRING);
        objectType.markOptional("email");

        ObjectNode schema = JsonSchemaWriter.toJsonSchema(objectType);

        assertFalse(schema.has("required"));
    }

    @Test
    void testEmptyObjectOmitsRequiredArray() {
        ObjectNode schema = JsonSchemaWriter.toJsonSchema(new ObjectType());

        assertEquals("object", schema.get("type").asText());
        assertTrue(schema.get("properties").isEmpty());
        assertFalse(schema.has("required"));
    }

    @Test
    void testNestedObjects() {
        ObjectType product = ObjectType.of("name", ScalarType.STRING);
        ObjectType root = ObjectType.of("product", product);

        ObjectNode schema = JsonSchemaWriter.toJsonSchema(root);

        ObjectNode productSchema = (ObjectNode) schema.get("properties").get("product");
        assertEquals("object", productSchema.get("type").asText());
        assertEquals("string", productSchema.get("properties").get("name").get("type").asText());
        // $schema must only ever appear at the document root, never on a nested sub-schema
        assertFalse(productSchema.has("$schema"));
    }

    @Test
    void testArrayWithSingleElementShape() {
        ArrayType arrayType = ArrayType.of(ScalarType.INTEGER);

        ObjectNode schema = JsonSchemaWriter.toJsonSchema(arrayType);

        assertEquals("array", schema.get("type").asText());
        assertEquals("integer", schema.get("items").get("type").asText());
    }

    @Test
    void testArrayWithHeterogeneousElementShapesUsesAnyOf() {
        ArrayType arrayType = ArrayType.of(
                ObjectType.of("radius", ScalarType.INTEGER),
                ObjectType.of("side", ScalarType.INTEGER)
        );

        ObjectNode schema = JsonSchemaWriter.toJsonSchema(arrayType);

        assertTrue(schema.get("items").get("anyOf").isArray());
        assertEquals(2, schema.get("items").get("anyOf").size());
    }

    @Test
    void testEmptyArrayOmitsItems() {
        ObjectNode schema = JsonSchemaWriter.toJsonSchema(new ArrayType());

        assertEquals("array", schema.get("type").asText());
        assertFalse(schema.has("items"));
    }

    @Test
    void testScalarOnlyUnionBecomesTypeArray() {
        UnionType unionType = new UnionType(Set.of(ScalarType.INTEGER, ScalarType.STRING));

        ObjectNode schema = JsonSchemaWriter.toJsonSchema(unionType);

        assertTrue(schema.get("type").isArray());
        List<String> types = new ArrayList<>();
        schema.get("type").forEach(node -> types.add(node.asText()));
        assertEquals(List.of("integer", "string"), types);
    }

    @Test
    void testUnionWithObjectMemberUsesAnyOf() {
        UnionType unionType = new UnionType(Set.of(ScalarType.STRING, ObjectType.of("id", ScalarType.INTEGER)));

        ObjectNode schema = JsonSchemaWriter.toJsonSchema(unionType);

        assertTrue(schema.get("anyOf").isArray());
        assertEquals(2, schema.get("anyOf").size());
    }

    @Test
    void testToJsonSchemaRejectsNull() {
        assertThrows(NullPointerException.class, () -> JsonSchemaWriter.toJsonSchema(null));
    }
}
