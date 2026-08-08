package io.github.ssullivan;

import io.github.ssullivan.jackson.Json;
import io.github.ssullivan.types.*;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the renderer behind the CLI's default output — the one users get with no flags, and the
 * one the README's examples show.
 */
class JsonTest {

    @Test
    void testScalarsRenderAsTheirTypeName() throws Exception {
        assertEquals("\"string\"", Json.write(ScalarType.STRING));
        assertEquals("\"integer\"", Json.write(ScalarType.INTEGER));
        assertEquals("\"float\"", Json.write(ScalarType.FLOAT));
        assertEquals("\"boolean\"", Json.write(ScalarType.BOOLEAN));
        assertEquals("\"null\"", Json.write(ScalarType.NULL));
        assertEquals("\"number\"", Json.write(ScalarType.NUMBER));
    }

    @Test
    void testEmptyObjectAndArray() throws Exception {
        assertEquals("{ }", Json.write(new ObjectType()));
        assertEquals("[ ]", Json.write(new ArrayType()));
    }

    @Test
    void testObjectRendersFieldsSortedByName() throws Exception {
        ObjectType objectType = new ObjectType();
        objectType.addField("zebra", ScalarType.STRING);
        objectType.addField("apple", ScalarType.INTEGER);

        assertEquals("""
                {
                  "apple" : "integer",
                  "zebra" : "string"
                }""", Json.write(objectType));
    }

    @Test
    void testOptionalFieldGetsQuestionMarkSuffixOnTheKey() throws Exception {
        ObjectType objectType = new ObjectType();
        objectType.addField("id", ScalarType.INTEGER);
        objectType.addField("email", ScalarType.STRING);
        objectType.markOptional("email");

        assertEquals("""
                {
                  "email?" : "string",
                  "id" : "integer"
                }""", Json.write(objectType));
    }

    @Test
    void testNestedObject() throws Exception {
        ObjectType root = ObjectType.of("product", ObjectType.of("name", ScalarType.STRING));

        assertEquals("""
                {
                  "product" : {
                    "name" : "string"
                  }
                }""", Json.write(root));
    }

    @Test
    void testArrayOfSingleShape() throws Exception {
        assertEquals("[ \"integer\" ]", Json.write(ArrayType.of(ScalarType.INTEGER)));
    }

    @Test
    void testArrayKeepsDistinctElementShapesInFirstAppearanceOrder() throws Exception {
        ArrayType arrayType = ArrayType.of(ScalarType.INTEGER, ScalarType.STRING, ScalarType.BOOLEAN);

        assertEquals("[ \"integer\", \"string\", \"boolean\" ]", Json.write(arrayType));
    }

    @Test
    void testArrayOfObjects() throws Exception {
        ArrayType arrayType = ArrayType.of(ObjectType.of("size", ScalarType.INTEGER));

        assertEquals("""
                [ {
                  "size" : "integer"
                } ]""", Json.write(arrayType));
    }

    @Test
    void testUnionJoinsMembersWithPipeInSortedOrder() throws Exception {
        UnionType union = new UnionType(Set.of(ScalarType.STRING, ScalarType.INTEGER));

        assertEquals("\"integer|string\"", Json.write(union));
    }

    @Test
    void testUnionWithNullMember() throws Exception {
        UnionType union = new UnionType(Set.of(ScalarType.NULL, ScalarType.STRING));

        assertEquals("\"null|string\"", Json.write(union));
    }

    @Test
    void testUnionCollapsesObjectAndArrayMembersToGenericLabels() throws Exception {
        UnionType union = new UnionType(Set.of(
                ScalarType.STRING,
                ObjectType.of("id", ScalarType.INTEGER),
                ArrayType.of(ScalarType.INTEGER)));

        assertEquals("\"array|object|string\"", Json.write(union));
    }

    @Test
    void testUnionWithTwoDistinctObjectShapesDedupesGenericLabel() throws Exception {
        // Two structurally different ObjectTypes both collapse to the "object" label; without
        // deduping, this rendered as "object|object|string".
        UnionType union = new UnionType(Set.of(
                ScalarType.STRING,
                ObjectType.of("a", ScalarType.INTEGER),
                ObjectType.of("b", ScalarType.STRING)));

        assertEquals("\"object|string\"", Json.write(union));
    }

    @Test
    void testMatchesTheReadmeExampleOutput() throws Exception {
        // The exact document from README "Example1: JSON Object".
        ObjectType product = new ObjectType();
        product.addField("name", ScalarType.STRING);
        product.addField("sizes", ArrayType.of(ObjectType.of("size", ScalarType.INTEGER)));
        product.addField("weight", ScalarType.FLOAT);
        product.addField("enabled", ScalarType.BOOLEAN);

        ObjectType root = new ObjectType();
        root.addField("version", ScalarType.INTEGER);
        root.addField("product", product);

        assertEquals("""
                {
                  "product" : {
                    "enabled" : "boolean",
                    "name" : "string",
                    "sizes" : [ {
                      "size" : "integer"
                    } ],
                    "weight" : "float"
                  },
                  "version" : "integer"
                }""", Json.write(root));
    }
}
