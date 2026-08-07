package com.github.ssullivan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ssullivan.analyze.JsonSchemaAnalyzer;
import com.github.ssullivan.types.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaAnalyzerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testAddingArrayFields() {
        ArrayType boolArray = ArrayType.of(BooleanType.instance());
        ArrayType intArray = ArrayType.of(IntNumberType.instance());

        ArrayType arrayType = ArrayType.of(boolArray, intArray);
        assertEquals(2, arrayType.getFields().size());
    }

    @Test
    void testSimpleArray() throws IOException {
        // Given a JSON array of values
        List<Object> array = List.of(Integer.MAX_VALUE,
                Long.MAX_VALUE,
                true,
                "string",
                1.5f);

        String json = MAPPER.writeValueAsString(array);

        // Then analyze the JSON
        JsonType schema = inferSchema(json);

        // We should have the following types

        ArrayType expected = ArrayType.of(
                IntNumberType.instance(),
                BooleanType.instance(),
                StringType.instance(),
                FloatNumberType.instance()

        );

        assertInstanceOf(ArrayType.class, schema);
        assertEquals(expected, schema);
    }

    @Test
    void testSimpleObject() throws IOException {
        // Given a json with a single level of fields

        Map<String, Object> stringObjectMap = new HashMap<>();
        stringObjectMap.put("integer", Integer.MAX_VALUE);
        stringObjectMap.put("long", Long.MAX_VALUE);
        stringObjectMap.put("boolean", true);
        stringObjectMap.put("string", "string");
        stringObjectMap.put("float", 1.5f);
        stringObjectMap.put("array", Collections.singleton(1));

        String json = MAPPER.writeValueAsString(stringObjectMap);

        // Then analyze the JSON
        JsonType schema = inferSchema(json);

        // We should have the following types
        assertInstanceOf(ObjectType.class, schema);
        assertTrue(((ObjectType) schema).contains("integer", IntNumberType.instance()));
        assertTrue(((ObjectType) schema).contains("long", IntNumberType.instance()));
        assertTrue(((ObjectType) schema).contains("boolean", BooleanType.instance()));
        assertTrue(((ObjectType) schema).contains("string", StringType.instance()));
        assertTrue(((ObjectType) schema).contains("float", FloatNumberType.instance()));
        assertTrue(((ObjectType) schema).contains("array", ArrayType.of(IntNumberType.instance())));
    }

    @Test
    void testNestedObject() throws IOException {
        Map<String, Object> stringObjectMap =
                Map.of("a1", Map.of("a2", Map.of("a3", Map.of("a4", Map.of("a5", Map.of("a6", 1))))));

        String json = MAPPER.writeValueAsString(stringObjectMap);

        JsonType schema = inferSchema(json);

        assertInstanceOf(ObjectType.class, schema);
        assertEquals(ObjectType.of("a1.a2.a3.a4.a5.a6", IntNumberType.instance()), schema);
    }

    @Test
    void testNestedArrays() throws IOException {
        List<Object> listOfObjects = List.of(List.of(List.of(List.of(List.of(List.of(List.of(1)))))));

        String json = MAPPER.writeValueAsString(listOfObjects);

        JsonType schema = inferSchema(json);

        assertInstanceOf(ArrayType.class, schema);
        assertEquals(
            ArrayType.of(
                ArrayType.of(
                        ArrayType.of(
                                ArrayType.of(
                                        ArrayType.of(
                                                ArrayType.of(
                                                        ArrayType.of(
                                                                IntNumberType.instance()
                                                        )
                                                )
                                        )
                                )
                        )
                )
        ), schema);
    }

    @Test
    void testAlternatingNestedObjectsAndArrays() throws IOException {
        List<Object> listOfObjects = List.of(Map.of("a1", List.of(Map.of("b", List.of(1)))));

        String json = MAPPER.writeValueAsString(listOfObjects);

        JsonType schema = inferSchema(json);
        assertEquals(
                ArrayType.of(
                        ObjectType.of(
                                "a1", ArrayType.of(
                                        ObjectType.of(
                                                "b",
                                                ArrayType.of(IntNumberType.instance())
                                        )
                                )
                        )
                ),
                schema
        );

    }

    @Test
    void testArrayDeduplicatesIdenticalObjectShapes() throws IOException {
        // Given an array containing two structurally-identical objects separated by
        // several distinct object shapes
        List<Object> array = new ArrayList<>();
        array.add(Map.of("dup", 1));
        for (int i = 0; i < 10; i++) {
            array.add(Map.of("f" + i, i));
        }
        array.add(Map.of("dup", 1));

        String json = MAPPER.writeValueAsString(array);

        JsonType schema = inferSchema(json);

        assertInstanceOf(ArrayType.class, schema);
        ArrayType arrayType = (ArrayType) schema;
        // 10 distinct "fN" shapes + 1 deduplicated "dup" shape
        assertEquals(11, arrayType.getFields().size());
        assertTrue(arrayType.getFields().contains(ObjectType.of("dup", IntNumberType.instance())));
    }

    @Test
    void testNestedObjectWithArrayFieldUsesDottedPath() throws IOException {
        // Given an object whose nested object contains an array field
        Map<String, Object> stringObjectMap =
                Map.of("a1", Map.of("a2", List.of(1, 2, 3)));

        String json = MAPPER.writeValueAsString(stringObjectMap);

        JsonType schema = inferSchema(json);

        // The array field should get the same dotted-path prefix as sibling scalar fields
        assertEquals(
                ObjectType.of("a1.a2", ArrayType.of(IntNumberType.instance())),
                schema
        );
    }

    private JsonType inferSchema(String json) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            JsonSchemaAnalyzer jsonSchemaAnalyzer = new JsonSchemaAnalyzer();
            return jsonSchemaAnalyzer.parse(inputStream);
        }
    }
}
