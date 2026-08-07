package io.github.ssullivan;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ssullivan.analyze.JsonSchemaAnalysisException;
import io.github.ssullivan.analyze.JsonSchemaAnalyzer;
import io.github.ssullivan.analyze.SchemaMerger;
import io.github.ssullivan.types.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaAnalyzerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testAddingArrayFields() {
        ArrayType boolArray = ArrayType.of(ScalarType.BOOLEAN);
        ArrayType intArray = ArrayType.of(ScalarType.INTEGER);

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
                ScalarType.INTEGER,
                ScalarType.BOOLEAN,
                ScalarType.STRING,
                ScalarType.FLOAT

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
        assertTrue(((ObjectType) schema).contains("integer", ScalarType.INTEGER));
        assertTrue(((ObjectType) schema).contains("long", ScalarType.INTEGER));
        assertTrue(((ObjectType) schema).contains("boolean", ScalarType.BOOLEAN));
        assertTrue(((ObjectType) schema).contains("string", ScalarType.STRING));
        assertTrue(((ObjectType) schema).contains("float", ScalarType.FLOAT));
        assertTrue(((ObjectType) schema).contains("array", ArrayType.of(ScalarType.INTEGER)));
    }

    @Test
    void testNestedObject() throws IOException {
        Map<String, Object> stringObjectMap =
                Map.of("a1", Map.of("a2", Map.of("a3", Map.of("a4", Map.of("a5", Map.of("a6", 1))))));

        String json = MAPPER.writeValueAsString(stringObjectMap);

        JsonType schema = inferSchema(json);

        assertInstanceOf(ObjectType.class, schema);
        assertEquals(
                ObjectType.of("a1",
                        ObjectType.of("a2",
                                ObjectType.of("a3",
                                        ObjectType.of("a4",
                                                ObjectType.of("a5",
                                                        ObjectType.of("a6", ScalarType.INTEGER)))))),
                schema
        );
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
                                                                ScalarType.INTEGER
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
                                                ArrayType.of(ScalarType.INTEGER)
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
        assertTrue(arrayType.getFields().contains(ObjectType.of("dup", ScalarType.INTEGER)));
    }

    @Test
    void testNestedObjectWithArrayFieldNests() throws IOException {
        // Given an object whose nested object contains an array field
        Map<String, Object> stringObjectMap =
                Map.of("a1", Map.of("a2", List.of(1, 2, 3)));

        String json = MAPPER.writeValueAsString(stringObjectMap);

        JsonType schema = inferSchema(json);

        // The array field should nest under its enclosing object, not flatten to a dotted key
        assertEquals(
                ObjectType.of("a1", ObjectType.of("a2", ArrayType.of(ScalarType.INTEGER))),
                schema
        );
    }

    @Test
    void testMergingSamplesEndToEnd() throws IOException {
        // Given four realistic user-record samples with varying shape:
        // one missing "email", one with a null "email", one with "id" as a string
        Map<String, Object> record3 = new LinkedHashMap<>();
        record3.put("id", 3);
        record3.put("name", "Carol");
        record3.put("email", null);

        List<Object> samples = List.of(
                Map.of("id", 1, "name", "Alice", "email", "a@example.com"),
                Map.of("id", 2, "name", "Bob"),
                record3,
                Map.of("id", "4", "name", "Dave", "email", "d@example.com")
        );

        String json = MAPPER.writeValueAsString(samples);
        JsonType schema = inferSchema(json);

        assertInstanceOf(ArrayType.class, schema);
        JsonType merged = ((ArrayType) schema).getFields().stream()
                .reduce(SchemaMerger::merge)
                .orElseThrow();

        assertInstanceOf(ObjectType.class, merged);
        ObjectType objectType = (ObjectType) merged;

        assertFalse(objectType.isOptional("id"));
        assertFalse(objectType.isOptional("name"));
        assertTrue(objectType.isOptional("email"));

        assertEquals(ScalarType.STRING, objectType.getFields().get("name"));
        assertInstanceOf(UnionType.class, objectType.getFields().get("id"));
        assertEquals(
                Set.of(ScalarType.INTEGER, ScalarType.STRING),
                ((UnionType) objectType.getFields().get("id")).getMembers()
        );
        assertInstanceOf(UnionType.class, objectType.getFields().get("email"));
        assertEquals(
                Set.of(ScalarType.STRING, ScalarType.NULL),
                ((UnionType) objectType.getFields().get("email")).getMembers()
        );
    }

    @Test
    void testScalarRoot() throws IOException {
        // A bare scalar value is a valid, if unusual, JSON document
        JsonType schema = inferSchema("\"hello\"");

        assertEquals(ScalarType.STRING, schema);
    }

    @Test
    void testEmptyInputThrowsJsonSchemaAnalysisExceptionWithHelpfulMessage() {
        // Empty input has no first token to inspect; this must fail cleanly rather than NPE,
        // with a message that says what was expected and where, not just "unsupported root"
        JsonSchemaAnalysisException exception =
                assertThrows(JsonSchemaAnalysisException.class, () -> inferSchema(""));

        assertTrue(exception.getMessage().contains("empty"));
        assertTrue(exception.getMessage().contains("line 1"));
        assertTrue(exception.getMessage().contains("object"));
        assertTrue(exception.getMessage().contains("array"));
    }

    @Test
    void testMalformedJsonThrowsJsonSchemaAnalysisExceptionWithCleanMessage() {
        // Truncated mid-object: Jackson's own parse failure, not one of our hand-written cases
        JsonSchemaAnalysisException exception =
                assertThrows(JsonSchemaAnalysisException.class, () -> inferSchema("{\"a\": "));

        assertTrue(exception.getMessage().contains("line"));
        assertTrue(exception.getMessage().contains("column"));
        // Jackson's raw message leaks internal detail like this; ours must not
        assertFalse(exception.getMessage().contains("REDACTED"));
        assertFalse(exception.getMessage().contains("StreamReadFeature"));
        assertNotNull(exception.getCause());
    }

    @Test
    void testGenuineIoFailurePropagatesUnwrapped() {
        // A real I/O failure (not a content problem) must stay a checked IOException, not get
        // folded into JsonSchemaAnalysisException
        InputStream brokenStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk on fire");
            }
        };

        JsonSchemaAnalyzer analyzer = new JsonSchemaAnalyzer();
        IOException exception = assertThrows(IOException.class, () -> analyzer.parse(brokenStream));

        assertEquals("disk on fire", exception.getMessage());
    }

    private JsonType inferSchema(String json) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            JsonSchemaAnalyzer jsonSchemaAnalyzer = new JsonSchemaAnalyzer();
            return jsonSchemaAnalyzer.parse(inputStream);
        }
    }
}
