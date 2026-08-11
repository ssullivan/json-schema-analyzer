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

    @Test
    void testParseJsonLinesMergesEachLineAsASample() throws IOException {
        String ndjson = String.join("\n",
                "{\"id\": 1, \"name\": \"Alice\", \"email\": \"a@example.com\"}",
                "{\"id\": 2, \"name\": \"Bob\"}",
                "{\"id\": \"3\", \"name\": \"Carol\"}");

        JsonType schema = inferSchemaFromLines(ndjson);

        assertInstanceOf(ObjectType.class, schema);
        ObjectType objectType = (ObjectType) schema;
        assertFalse(objectType.isOptional("id"));
        assertFalse(objectType.isOptional("name"));
        assertTrue(objectType.isOptional("email"));
        assertInstanceOf(UnionType.class, objectType.getFields().get("id"));
        assertEquals(
                Set.of(ScalarType.INTEGER, ScalarType.STRING),
                ((UnionType) objectType.getFields().get("id")).getMembers()
        );
    }

    @Test
    void testParseJsonLinesSkipsBlankLines() throws IOException {
        String ndjson = "{\"id\": 1}\n\n   \n{\"id\": 2}\n";

        JsonType schema = inferSchemaFromLines(ndjson);

        assertEquals(ObjectType.of("id", ScalarType.INTEGER), schema);
    }

    @Test
    void testParseJsonLinesOnSingleLineReturnsThatLinesShape() throws IOException {
        JsonType schema = inferSchemaFromLines("{\"id\": 1}");

        assertEquals(ObjectType.of("id", ScalarType.INTEGER), schema);
    }

    @Test
    void testParseJsonLinesEmptyInputThrowsHelpfulException() {
        JsonSchemaAnalysisException exception =
                assertThrows(JsonSchemaAnalysisException.class, () -> inferSchemaFromLines("  \n  \n"));

        assertTrue(exception.getMessage().contains("empty"));
    }

    @Test
    void testParseJsonLinesMalformedLineReportsItsOwnLineNumber() {
        String ndjson = "{\"id\": 1}\n{\"id\": }\n";

        JsonSchemaAnalysisException exception =
                assertThrows(JsonSchemaAnalysisException.class, () -> inferSchemaFromLines(ndjson));

        assertTrue(exception.getMessage().contains("line 2"), exception.getMessage());
    }

    @Test
    void testFormatDetectionOffByDefaultLeavesDateLikeStringsPlain() throws IOException {
        assertEquals(ScalarType.STRING, inferSchema("\"2023-01-15\""));
    }

    @Test
    void testFormatDetectionOnDetectsDate() throws IOException {
        assertEquals(ScalarType.DATE, inferSchema("\"2023-01-15\"", true));
    }

    @Test
    void testFormatDetectionOnDetectsDateTime() throws IOException {
        assertEquals(ScalarType.DATE_TIME, inferSchema("\"2023-01-15T10:30:00Z\"", true));
    }

    @Test
    void testFormatDetectionOnDetectsUuid() throws IOException {
        assertEquals(ScalarType.UUID, inferSchema("\"550e8400-e29b-41d4-a716-446655440000\"", true));
    }

    @Test
    void testFormatDetectionOnLeavesPlainStringsAlone() throws IOException {
        assertEquals(ScalarType.STRING, inferSchema("\"hello\"", true));
    }

    @Test
    void testFormatDetectionAppliesInsideNestedObjectsAndArrays() throws IOException {
        String json = "{\"events\": [\"2023-01-15\", \"2023-02-20\"]}";

        JsonType schema = inferSchema(json, true);

        assertEquals(
                ObjectType.of("events", ArrayType.of(ScalarType.DATE)),
                schema
        );
    }

    @Test
    void testFormatDetectionAppliesInJsonLinesMode() throws IOException {
        String ndjson = String.join("\n",
                "{\"created\": \"2023-01-15\"}",
                "{\"created\": \"2023-02-20\"}");

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer(true).parseJsonLines(inputStream);

            assertEquals(ObjectType.of("created", ScalarType.DATE), schema);
        }
    }

    @Test
    void testEnumDetectionOffByDefaultLeavesStringsAsScalarType() throws IOException {
        JsonType schema = inferSchema("{\"status\": \"active\"}", false, false);

        assertEquals(ObjectType.of("status", ScalarType.STRING), schema);
    }

    @Test
    void testSingleObservedValueIsNotReportedAsAnEnum() throws IOException {
        // Only genuine variation is reported, so a field that merely happens to be constant
        // never has its one value shown.
        JsonType schema = inferSchema("{\"status\": \"active\"}", false, true);

        assertEquals(ObjectType.of("status", ScalarType.STRING), schema);
    }

    @Test
    void testEnumDetectionAppliesToBareArraysOfStrings() throws IOException {
        // All elements of an array share one position, so their values pool into one enum.
        JsonType schema = inferSchema("{\"tags\": [\"a\", \"b\", \"a\"]}", false, true);

        assertEquals(ObjectType.of("tags", ArrayType.of(EnumType.of("a", "b"))), schema);
    }

    @Test
    void testBareArrayOfStringsPastTheCapStaysPlainString() throws IOException {
        JsonType schema = inferSchema("{\"tags\": [\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]}", false, true);

        assertEquals(ObjectType.of("tags", ArrayType.of(ScalarType.STRING)), schema);
    }

    @Test
    void testEnumDetectionAppliesInsideNestedObjects() throws IOException {
        String ndjson = "{\"outer\":{\"status\":\"open\"}}\n{\"outer\":{\"status\":\"closed\"}}\n";

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer(false, true).parseJsonLines(inputStream);

            assertEquals(
                    ObjectType.of("outer", ObjectType.of("status", EnumType.of("open", "closed"))),
                    schema
            );
        }
    }

    @Test
    void testEnumDetectionAppliesToFieldsOfObjectsInsideAnArray() throws IOException {
        // The limitation the previous design had to accept: values are collected per position
        // rather than embedded in each element, so the array still dedupes to one element shape
        // *and* the field gets its enum.
        String json = "{\"items\": [{\"status\":\"open\"},{\"status\":\"closed\"},{\"status\":\"open\"}]}";

        JsonType schema = inferSchema(json, false, true);

        assertEquals(
                ObjectType.of("items", ArrayType.of(ObjectType.of("status", EnumType.of("open", "closed")))),
                schema
        );
    }

    @Test
    void testEnumDetectionAppliesToObjectsNestedDeeperInsideAnArray() throws IOException {
        String json = "{\"items\": [{\"inner\":{\"status\":\"open\"}},{\"inner\":{\"status\":\"closed\"}}]}";

        JsonType schema = inferSchema(json, false, true);

        assertEquals(
                ObjectType.of("items", ArrayType.of(
                        ObjectType.of("inner", ObjectType.of("status", EnumType.of("open", "closed"))))),
                schema
        );
    }

    @Test
    void testHeterogeneousArrayGetsNoEnumsSincePooledValuesWouldMisdescribeIt() throws IOException {
        // A discriminated union: the two element shapes differ, so the values pooled at the
        // shared element position don't belong to either shape individually. Stamping
        // "click|scroll" onto both would describe records like {"type":"click","dy":2} that
        // never occurred, so nothing under the array is substituted.
        String json = "{\"events\":[{\"type\":\"click\",\"x\":1},{\"type\":\"scroll\",\"dy\":2}]}";

        JsonType schema = inferSchema(json, false, true);

        assertInstanceOf(ObjectType.class, schema);
        ArrayType events = (ArrayType) ((ObjectType) schema).getFields().get("events");
        assertEquals(2, events.getFields().size());
        for (JsonType element : events.getFields()) {
            assertEquals(ScalarType.STRING, ((ObjectType) element).getFields().get("type"));
        }
    }

    @Test
    void testUniformArrayStillGetsEnumsAfterTheHeterogeneousGuard() throws IOException {
        // The guard keys off the number of distinct element shapes, so an array whose elements
        // agree -- the case this feature is for -- is unaffected.
        String json = "{\"events\":[{\"type\":\"click\",\"x\":1},{\"type\":\"scroll\",\"x\":2}]}";

        JsonType schema = inferSchema(json, false, true);

        ArrayType events = (ArrayType) ((ObjectType) schema).getFields().get("events");
        assertEquals(1, events.getFields().size());
        assertEquals(
                EnumType.of("click", "scroll"),
                ((ObjectType) events.getFields().iterator().next()).getFields().get("type")
        );
    }

    @Test
    void testEmptyStringValueDisqualifiesThePosition() throws IOException {
        // Rendered, an empty value would be indistinguishable from the separator around it
        // ("a[]: |b"), so it is not treated as an enum value.
        JsonType schema = inferSchema("{\"a\": [\"\", \"b\"]}", false, true);

        assertEquals(ObjectType.of("a", ArrayType.of(ScalarType.STRING)), schema);
    }

    @Test
    void testWhitespacePaddedValueDisqualifiesThePosition() throws IOException {
        JsonType schema = inferSchema("{\"a\": [\" x\", \"y\"]}", false, true);

        assertEquals(ObjectType.of("a", ArrayType.of(ScalarType.STRING)), schema);
    }

    @Test
    void testEnumDetectionAppliesAtDepthThroughMixedNesting() throws IOException {
        // a.b[].c.d -- proves position descent is correct through both field and element steps.
        String json = "{\"a\":{\"b\":[{\"c\":{\"d\":\"x\"}},{\"c\":{\"d\":\"y\"}}]}}";

        JsonType schema = inferSchema(json, false, true);

        assertEquals(
                ObjectType.of("a", ObjectType.of("b", ArrayType.of(
                        ObjectType.of("c", ObjectType.of("d", EnumType.of("x", "y")))))),
                schema
        );
    }

    @Test
    void testArrayOfRecordsDedupesToOneElementShapeAndStillDetectsTheEnum() throws IOException {
        // The regression this feature originally shipped with: distinct EnumTypes embedded in
        // each record made them unequal, so a 3-record array kept 3 element shapes and the LLM
        // notation collapsed the whole thing to a bare "object" label. Now the shape is built
        // enum-free and values are substituted in afterwards, so both properties hold at once.
        String json = "[{\"id\":1,\"status\":\"open\"},{\"id\":2,\"status\":\"closed\"},{\"id\":3,\"status\":\"pending\"}]";

        JsonType schema = inferSchema(json, false, true);

        assertInstanceOf(ArrayType.class, schema);
        assertEquals(1, ((ArrayType) schema).getFields().size());

        ObjectType expectedElement = new ObjectType();
        expectedElement.addField("id", ScalarType.INTEGER);
        expectedElement.addField("status", EnumType.of("open", "closed", "pending"));
        assertEquals(ArrayType.of(expectedElement), schema);
    }

    @Test
    void testParseSamplesMergesTopLevelArrayElementsIntoAnEnum() throws IOException {
        String json = "[{\"status\":\"open\"},{\"status\":\"closed\"},{\"status\":\"pending\"}]";

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer(false, true).parseSamples(inputStream);

            assertEquals(ObjectType.of("status", EnumType.of("open", "closed", "pending")), schema);
        }
    }

    @Test
    void testParseSamplesDetectsEnumsInsideNestedArraysWhileKeepingOneElementShape() throws IOException {
        String json = "[{\"id\":1,\"tags\":[{\"k\":\"a\"}]},{\"id\":2,\"tags\":[{\"k\":\"b\"}]}]";

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer(false, true).parseSamples(inputStream);

            assertInstanceOf(ObjectType.class, schema);
            JsonType tagsType = ((ObjectType) schema).getFields().get("tags");
            assertEquals(ArrayType.of(ObjectType.of("k", EnumType.of("a", "b"))), tagsType);
        }
    }

    @Test
    void testParseJsonLinesDetectsEnumsInsideNestedArraysWhileKeepingOneElementShape() throws IOException {
        String ndjson = "{\"tags\":[{\"k\":\"a\"}]}\n{\"tags\":[{\"k\":\"b\"}]}\n";

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer(false, true).parseJsonLines(inputStream);

            assertEquals(ObjectType.of("tags", ArrayType.of(ObjectType.of("k", EnumType.of("a", "b")))), schema);
        }
    }

    @Test
    void testMixedFormatAndPlainStringAtOnePositionIsNotAnEnum() throws IOException {
        // With -f on, one sample's value is a date and another's is not. Reporting an enum built
        // only from the non-date samples would misdescribe the position, so it stays a string.
        String ndjson = "{\"d\":\"2023-01-15\"}\n{\"d\":\"whenever\"}\n{\"d\":\"whenever\"}\n";

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer(true, true).parseJsonLines(inputStream);

            assertEquals(ObjectType.of("d", ScalarType.STRING), schema);
        }
    }

    @Test
    void testPositionThatMergesToAUnionIsNotReportedAsAnEnum() throws IOException {
        // `id` is a low-cardinality string in some samples and an integer in others, so it isn't
        // a categorical value -- the collected values are discarded rather than shown.
        String ndjson = "{\"id\":\"a\"}\n{\"id\":\"b\"}\n{\"id\":1}\n";

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer(false, true).parseJsonLines(inputStream);

            assertInstanceOf(ObjectType.class, schema);
            JsonType idType = ((ObjectType) schema).getFields().get("id");
            assertInstanceOf(UnionType.class, idType);
            assertEquals(Set.of(ScalarType.INTEGER, ScalarType.STRING), ((UnionType) idType).getMembers());
        }
    }

    @Test
    void testEnumsNestedInsideAUnionsObjectMemberStillApply() throws IOException {
        // The union itself gets no enum, but object members of it are still descended into.
        String ndjson = "{\"x\":{\"k\":\"a\"}}\n{\"x\":{\"k\":\"b\"}}\n{\"x\":1}\n";

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer(false, true).parseJsonLines(inputStream);

            assertInstanceOf(ObjectType.class, schema);
            JsonType xType = ((ObjectType) schema).getFields().get("x");
            assertInstanceOf(UnionType.class, xType);
            assertTrue(((UnionType) xType).getMembers()
                    .contains(ObjectType.of("k", EnumType.of("a", "b"))));
        }
    }

    @Test
    void testOptionalityIsPreservedThroughEnumSubstitution() throws IOException {
        String ndjson = "{\"id\":1,\"status\":\"open\"}\n{\"id\":2}\n{\"id\":3,\"status\":\"closed\"}\n";

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer(false, true).parseJsonLines(inputStream);

            assertInstanceOf(ObjectType.class, schema);
            ObjectType objectType = (ObjectType) schema;
            assertFalse(objectType.isOptional("id"));
            assertTrue(objectType.isOptional("status"));
            assertEquals(EnumType.of("open", "closed"), objectType.getFields().get("status"));
        }
    }

    @Test
    void testParseSamplesOnNonArrayRootDescribesTheDocumentItself() throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream("{\"id\": 1}".getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer().parseSamples(inputStream);

            assertEquals(ObjectType.of("id", ScalarType.INTEGER), schema);
        }
    }

    @Test
    void testParseSamplesOnEmptyArrayYieldsEmptyArrayType() throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream("[]".getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer().parseSamples(inputStream);

            assertEquals(new ArrayType(), schema);
        }
    }

    @Test
    void testParseSamplesMergesOptionalityTheSameWayTheOldMergeReduceDid() throws IOException {
        String json = "[{\"id\": 1, \"name\": \"Alice\"}, {\"id\": 2}]";

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer().parseSamples(inputStream);

            assertInstanceOf(ObjectType.class, schema);
            ObjectType objectType = (ObjectType) schema;
            assertFalse(objectType.isOptional("id"));
            assertTrue(objectType.isOptional("name"));
        }
    }

    @Test
    void testParseSamplesSkippingAlreadyFoldedShapesKeepsOptionality() throws IOException {
        // Samples repeat their shape, so identical ones are skipped rather than re-merged. That
        // is only safe because folding an already-absorbed shape cannot change the result -- if
        // the skip were wrong, "name" would come back required here.
        String json = "[{\"id\":1,\"name\":\"a\"},{\"id\":2},{\"id\":3,\"name\":\"b\"},{\"id\":4}]";

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer().parseSamples(inputStream);

            assertInstanceOf(ObjectType.class, schema);
            ObjectType objectType = (ObjectType) schema;
            assertFalse(objectType.isOptional("id"));
            assertTrue(objectType.isOptional("name"));
            assertEquals(ScalarType.STRING, objectType.getFields().get("name"));
        }
    }

    @Test
    void testParseSamplesSkippingAlreadyFoldedShapesKeepsUnions() throws IOException {
        String json = "[{\"id\":1},{\"id\":\"two\"},{\"id\":3},{\"id\":\"four\"}]";

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer().parseSamples(inputStream);

            assertInstanceOf(ObjectType.class, schema);
            JsonType idType = ((ObjectType) schema).getFields().get("id");
            assertInstanceOf(UnionType.class, idType);
            assertEquals(Set.of(ScalarType.INTEGER, ScalarType.STRING), ((UnionType) idType).getMembers());
        }
    }

    @Test
    void testParseSamplesStaysCorrectPastTheFoldedShapeLimit() throws IOException {
        // More structurally distinct samples than the dedupe cache holds, so it stops
        // deduplicating partway through and falls back to plain merging; the result must be
        // unaffected. Enum detection on a unique-per-sample field is the natural way to get
        // there, and the values also blow past MAX_VALUES, so the field ends up plain "string".
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < 1100; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"id\":").append(i).append(",\"tag\":\"v").append(i).append("\"}");
        }
        json.append(']');

        try (ByteArrayInputStream inputStream =
                     new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer(false, true).parseSamples(inputStream);

            ObjectType expected = new ObjectType();
            expected.addField("id", ScalarType.INTEGER);
            expected.addField("tag", ScalarType.STRING);
            assertEquals(expected, schema);
        }
    }

    @Test
    void testParseSamplesMalformedInputReportsLocation() {
        assertThrows(JsonSchemaAnalysisException.class, () -> {
            try (ByteArrayInputStream inputStream =
                         new ByteArrayInputStream("[{\"a\": }]".getBytes(StandardCharsets.UTF_8))) {
                new JsonSchemaAnalyzer().parseSamples(inputStream);
            }
        });
    }

    @Test
    void testEnumDetectionDoesNotApplyToDetectedDateOrUuidFields() throws IOException {
        JsonType schema = inferSchema("{\"day\": \"2023-01-15\"}", true, true);

        assertEquals(ObjectType.of("day", ScalarType.DATE), schema);
    }

    @Test
    void testSameValueOnEveryJsonLineIsNotReportedAsAnEnum() throws IOException {
        String ndjson = String.join("\n",
                "{\"status\": \"active\"}",
                "{\"status\": \"active\"}");

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer(false, true).parseJsonLines(inputStream);

            assertEquals(ObjectType.of("status", ScalarType.STRING), schema);
        }
    }

    @Test
    void testEnumDetectionMergesDistinctValuesAcrossJsonLines() throws IOException {
        String ndjson = String.join("\n",
                "{\"status\": \"open\"}",
                "{\"status\": \"closed\"}",
                "{\"status\": \"pending\"}");

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer(false, true).parseJsonLines(inputStream);

            assertEquals(ObjectType.of("status", EnumType.of("open", "closed", "pending")), schema);
        }
    }

    @Test
    void testEnumDetectionStaysEnumAtExactlyFiveDistinctValues() throws IOException {
        String ndjson = String.join("\n",
                "{\"status\": \"a\"}",
                "{\"status\": \"b\"}",
                "{\"status\": \"c\"}",
                "{\"status\": \"d\"}",
                "{\"status\": \"e\"}");

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer(false, true).parseJsonLines(inputStream);

            assertEquals(ObjectType.of("status", EnumType.of("a", "b", "c", "d", "e")), schema);
        }
    }

    @Test
    void testEnumDetectionFallsBackToPlainStringPastCardinalityCap() throws IOException {
        String ndjson = String.join("\n",
                "{\"status\": \"a\"}",
                "{\"status\": \"b\"}",
                "{\"status\": \"c\"}",
                "{\"status\": \"d\"}",
                "{\"status\": \"e\"}",
                "{\"status\": \"f\"}");

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer(false, true).parseJsonLines(inputStream);

            assertEquals(ObjectType.of("status", ScalarType.STRING), schema);
        }
    }

    @Test
    void testRootLevelScalarsPoolIntoAnEnumAcrossJsonLines() throws IOException {
        // Bare root scalars occupy one position too, so they pool like any other.
        String ndjson = "\"active\"\n\"inactive\"\n";

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8))) {
            JsonType schema = new JsonSchemaAnalyzer(false, true).parseJsonLines(inputStream);

            assertEquals(EnumType.of("active", "inactive"), schema);
        }
    }

    private JsonType inferSchema(String json) throws IOException {
        return inferSchema(json, false);
    }

    private JsonType inferSchema(String json, boolean detectFormats) throws IOException {
        return inferSchema(json, detectFormats, false);
    }

    private JsonType inferSchema(String json, boolean detectFormats, boolean detectEnums) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            JsonSchemaAnalyzer jsonSchemaAnalyzer = new JsonSchemaAnalyzer(detectFormats, detectEnums);
            return jsonSchemaAnalyzer.parse(inputStream);
        }
    }

    private JsonType inferSchemaFromLines(String ndjson) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8))) {
            JsonSchemaAnalyzer jsonSchemaAnalyzer = new JsonSchemaAnalyzer();
            return jsonSchemaAnalyzer.parseJsonLines(inputStream);
        }
    }
}
