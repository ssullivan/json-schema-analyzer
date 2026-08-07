package io.github.ssullivan;

import io.github.ssullivan.format.LlmWriter;
import io.github.ssullivan.types.*;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LlmWriterTest {

    @Test
    void testRootScalar() {
        assertEquals("integer", LlmWriter.write(ScalarType.INTEGER));
    }

    @Test
    void testRootUnion() {
        assertEquals("integer|string", LlmWriter.write(new UnionType(Set.of(ScalarType.INTEGER, ScalarType.STRING))));
    }

    @Test
    void testSimpleObject() {
        ObjectType objectType = new ObjectType();
        objectType.addField("id", ScalarType.INTEGER);
        objectType.addField("name", ScalarType.STRING);

        assertEquals("id: integer\nname: string", LlmWriter.write(objectType));
    }

    @Test
    void testOptionalFieldGetsQuestionMarkSuffix() {
        ObjectType objectType = new ObjectType();
        objectType.addField("id", ScalarType.INTEGER);
        objectType.addField("email", ScalarType.STRING);
        objectType.markOptional("email");

        assertEquals("email?: string\nid: integer", LlmWriter.write(objectType));
    }

    @Test
    void testEmptyRootObject() {
        assertEquals("object", LlmWriter.write(new ObjectType()));
    }

    @Test
    void testNestedObjectIndentsWithoutBraces() {
        ObjectType product = ObjectType.of("name", ScalarType.STRING);
        ObjectType root = ObjectType.of("product", product);

        assertEquals("product:\n  name: string", LlmWriter.write(root));
    }

    @Test
    void testEmptyNestedObjectCollapsesToInlineLabel() {
        ObjectType root = ObjectType.of("metadata", new ObjectType());

        assertEquals("metadata: object", LlmWriter.write(root));
    }

    @Test
    void testArrayOfScalarsFieldIsInline() {
        ObjectType root = ObjectType.of("tags", ArrayType.of(ScalarType.STRING));

        assertEquals("tags[]: string", LlmWriter.write(root));
    }

    @Test
    void testOptionalArrayFieldKeepsQuestionMarkBeforeBrackets() {
        ObjectType root = new ObjectType();
        root.addField("tags", ArrayType.of(ScalarType.STRING));
        root.markOptional("tags");

        assertEquals("tags?[]: string", LlmWriter.write(root));
    }

    @Test
    void testArrayOfObjectsFieldNestsAsBlock() {
        ObjectType element = ObjectType.of("size", ScalarType.INTEGER);
        ObjectType root = ObjectType.of("sizes", ArrayType.of(element));

        assertEquals("sizes[]:\n  size: integer", LlmWriter.write(root));
    }

    @Test
    void testArrayOfArraysCollapsesBracketsOntoOneLine() {
        ObjectType root = ObjectType.of("matrix", ArrayType.of(ArrayType.of(ScalarType.INTEGER)));

        assertEquals("matrix[][]: integer", LlmWriter.write(root));
    }

    @Test
    void testHeterogeneousArrayFieldJoinsMemberLabels() {
        ObjectType root = ObjectType.of("values", ArrayType.of(ScalarType.INTEGER, ScalarType.STRING));

        assertEquals("values[]: integer|string", LlmWriter.write(root));
    }

    @Test
    void testEmptyArrayFieldRendersGenericLabel() {
        ObjectType root = ObjectType.of("tags", new ArrayType());

        assertEquals("tags[]: array", LlmWriter.write(root));
    }

    @Test
    void testUnionWithObjectMemberCollapsesToGenericLabel() {
        ObjectType root = new ObjectType();
        root.addField("id", new UnionType(Set.of(ScalarType.STRING, ObjectType.of("nested", ScalarType.INTEGER))));

        assertEquals("id: object|string", LlmWriter.write(root));
    }

    @Test
    void testUnionWithTwoDistinctObjectShapesDedupesGenericLabel() {
        // Two structurally different ObjectTypes both collapse to the "object" label; without
        // deduping, this rendered as "object|object|string" instead of "object|string".
        ObjectType root = new ObjectType();
        root.addField("id", new UnionType(Set.of(
                ScalarType.STRING,
                ObjectType.of("a", ScalarType.INTEGER),
                ObjectType.of("b", ScalarType.STRING)
        )));

        assertEquals("id: object|string", LlmWriter.write(root));
    }

    @Test
    void testHeterogeneousArrayWithTwoDistinctArrayShapesDedupesGenericLabel() {
        ObjectType root = ObjectType.of("matrix", ArrayType.of(
                ScalarType.INTEGER,
                ArrayType.of(ScalarType.STRING),
                ArrayType.of(ScalarType.BOOLEAN)
        ));

        assertEquals("matrix[]: array|integer", LlmWriter.write(root));
    }

    @Test
    void testRootArrayOfObjectsNestsWithoutLeadingName() {
        ObjectType element = ObjectType.of("id", ScalarType.INTEGER);

        assertEquals("[]:\n  id: integer", LlmWriter.write(ArrayType.of(element)));
    }

    @Test
    void testRootArrayOfScalarsIsInline() {
        assertEquals("[]: integer", LlmWriter.write(ArrayType.of(ScalarType.INTEGER)));
    }

    @Test
    void testEmptyRootArray() {
        assertEquals("[]: array", LlmWriter.write(new ArrayType()));
    }

    @Test
    void testFullExampleMatchesReadmeShape() {
        ObjectType sizeElement = ObjectType.of("size", ScalarType.INTEGER);
        ObjectType product = new ObjectType();
        product.addField("name", ScalarType.STRING);
        product.addField("sizes", ArrayType.of(sizeElement));
        product.addField("weight", ScalarType.FLOAT);
        product.addField("enabled", ScalarType.BOOLEAN);

        ObjectType root = new ObjectType();
        root.addField("version", ScalarType.INTEGER);
        root.addField("product", product);

        String expected = String.join("\n",
                "product:",
                "  enabled: boolean",
                "  name: string",
                "  sizes[]:",
                "    size: integer",
                "  weight: float",
                "version: integer"
        );
        assertEquals(expected, LlmWriter.write(root));
    }

    @Test
    void testWriteRejectsNull() {
        assertThrows(NullPointerException.class, () -> LlmWriter.write(null));
    }
}
