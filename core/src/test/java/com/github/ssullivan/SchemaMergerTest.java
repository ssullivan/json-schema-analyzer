package com.github.ssullivan;

import com.github.ssullivan.analyze.SchemaMerger;
import com.github.ssullivan.types.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SchemaMergerTest {

    @Test
    void testFieldMissingFromOneObjectBecomesOptional() {
        ObjectType a = ObjectType.of("id", ScalarType.INTEGER);
        ObjectType b = new ObjectType();
        b.addField("id", ScalarType.INTEGER);
        b.addField("email", ScalarType.STRING);

        JsonType merged = SchemaMerger.merge(a, b);

        assertInstanceOf(ObjectType.class, merged);
        ObjectType objectType = (ObjectType) merged;
        assertFalse(objectType.isOptional("id"));
        assertTrue(objectType.isOptional("email"));
        assertEquals(ScalarType.STRING, objectType.getFields().get("email"));
    }

    @Test
    void testConflictingScalarTypesBecomeUnion() {
        ObjectType a = ObjectType.of("id", ScalarType.INTEGER);
        ObjectType b = ObjectType.of("id", ScalarType.STRING);

        JsonType merged = SchemaMerger.merge(a, b);

        assertInstanceOf(ObjectType.class, merged);
        JsonType idType = ((ObjectType) merged).getFields().get("id");
        assertInstanceOf(UnionType.class, idType);
        assertEquals(Set.of(ScalarType.INTEGER, ScalarType.STRING), ((UnionType) idType).getMembers());
    }

    @Test
    void testIntAndFloatCollapseToNumber() {
        ObjectType a = ObjectType.of("weight", ScalarType.INTEGER);
        ObjectType b = ObjectType.of("weight", ScalarType.FLOAT);

        JsonType merged = SchemaMerger.merge(a, b);

        assertEquals(ObjectType.of("weight", ScalarType.NUMBER), merged);
    }

    @Test
    void testMergingIdenticalObjectsIntroducesNoUnionOrOptionality() {
        ObjectType a = ObjectType.of("id", ScalarType.INTEGER);
        ObjectType b = ObjectType.of("id", ScalarType.INTEGER);

        JsonType merged = SchemaMerger.merge(a, b);

        assertEquals(a, merged);
        assertTrue(((ObjectType) merged).getOptionalFields().isEmpty());
    }

    @Test
    void testMergingArraysPreservesHeterogeneousElementShapes() {
        ArrayType circles = ArrayType.of(ObjectType.of("radius", ScalarType.INTEGER));
        ArrayType squares = ArrayType.of(ObjectType.of("side", ScalarType.INTEGER));

        JsonType merged = SchemaMerger.merge(circles, squares);

        assertInstanceOf(ArrayType.class, merged);
        ArrayType arrayType = (ArrayType) merged;
        assertEquals(2, arrayType.getFields().size());
        assertTrue(arrayType.getFields().contains(ObjectType.of("radius", ScalarType.INTEGER)));
        assertTrue(arrayType.getFields().contains(ObjectType.of("side", ScalarType.INTEGER)));
    }

    @Test
    void testNestedObjectFieldsMergeRecursively() {
        ObjectType a = ObjectType.of("product", ObjectType.of("name", ScalarType.STRING));

        ObjectType productB = new ObjectType();
        productB.addField("name", ScalarType.STRING);
        productB.addField("weight", ScalarType.FLOAT);
        ObjectType b = ObjectType.of("product", productB);

        JsonType merged = SchemaMerger.merge(a, b);

        assertInstanceOf(ObjectType.class, merged);
        JsonType productType = ((ObjectType) merged).getFields().get("product");
        assertInstanceOf(ObjectType.class, productType);

        ObjectType mergedProduct = (ObjectType) productType;
        assertFalse(mergedProduct.isOptional("name"));
        assertTrue(mergedProduct.isOptional("weight"));
        assertEquals(ScalarType.FLOAT, mergedProduct.getFields().get("weight"));
    }

    @Test
    void testOptionalityCarriesForwardAcrossMultipleMerges() {
        ObjectType record1 = new ObjectType();
        record1.addField("id", ScalarType.INTEGER);
        record1.addField("email", ScalarType.STRING);

        ObjectType record2 = ObjectType.of("id", ScalarType.INTEGER);

        ObjectType record3 = new ObjectType();
        record3.addField("id", ScalarType.INTEGER);
        record3.addField("email", ScalarType.STRING);

        JsonType merged = List.<JsonType>of(record1, record2, record3).stream()
                .reduce(SchemaMerger::merge)
                .orElseThrow();

        assertInstanceOf(ObjectType.class, merged);
        assertTrue(((ObjectType) merged).isOptional("email"));
    }

    @Test
    void testMergeRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> SchemaMerger.merge(null, ScalarType.STRING));
        assertThrows(NullPointerException.class, () -> SchemaMerger.merge(ScalarType.STRING, null));
    }
}
