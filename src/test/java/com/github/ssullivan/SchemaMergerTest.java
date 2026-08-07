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
        ObjectType a = ObjectType.of("id", IntNumberType.instance());
        ObjectType b = new ObjectType();
        b.addField("id", IntNumberType.instance());
        b.addField("email", StringType.instance());

        JsonType merged = SchemaMerger.merge(a, b);

        assertInstanceOf(ObjectType.class, merged);
        ObjectType objectType = (ObjectType) merged;
        assertFalse(objectType.isOptional("id"));
        assertTrue(objectType.isOptional("email"));
        assertEquals(StringType.instance(), objectType.getFields().get("email"));
    }

    @Test
    void testConflictingScalarTypesBecomeUnion() {
        ObjectType a = ObjectType.of("id", IntNumberType.instance());
        ObjectType b = ObjectType.of("id", StringType.instance());

        JsonType merged = SchemaMerger.merge(a, b);

        assertInstanceOf(ObjectType.class, merged);
        JsonType idType = ((ObjectType) merged).getFields().get("id");
        assertInstanceOf(UnionType.class, idType);
        assertEquals(Set.of(IntNumberType.instance(), StringType.instance()), ((UnionType) idType).getMembers());
    }

    @Test
    void testIntAndFloatCollapseToNumber() {
        ObjectType a = ObjectType.of("weight", IntNumberType.instance());
        ObjectType b = ObjectType.of("weight", FloatNumberType.instance());

        JsonType merged = SchemaMerger.merge(a, b);

        assertEquals(ObjectType.of("weight", NumberType.instance()), merged);
    }

    @Test
    void testMergingIdenticalObjectsIntroducesNoUnionOrOptionality() {
        ObjectType a = ObjectType.of("id", IntNumberType.instance());
        ObjectType b = ObjectType.of("id", IntNumberType.instance());

        JsonType merged = SchemaMerger.merge(a, b);

        assertEquals(a, merged);
        assertTrue(((ObjectType) merged).getOptionalFields().isEmpty());
    }

    @Test
    void testMergingArraysPreservesHeterogeneousElementShapes() {
        ArrayType circles = ArrayType.of(ObjectType.of("radius", IntNumberType.instance()));
        ArrayType squares = ArrayType.of(ObjectType.of("side", IntNumberType.instance()));

        JsonType merged = SchemaMerger.merge(circles, squares);

        assertInstanceOf(ArrayType.class, merged);
        ArrayType arrayType = (ArrayType) merged;
        assertEquals(2, arrayType.getFields().size());
        assertTrue(arrayType.getFields().contains(ObjectType.of("radius", IntNumberType.instance())));
        assertTrue(arrayType.getFields().contains(ObjectType.of("side", IntNumberType.instance())));
    }

    @Test
    void testOptionalityCarriesForwardAcrossMultipleMerges() {
        ObjectType record1 = new ObjectType();
        record1.addField("id", IntNumberType.instance());
        record1.addField("email", StringType.instance());

        ObjectType record2 = ObjectType.of("id", IntNumberType.instance());

        ObjectType record3 = new ObjectType();
        record3.addField("id", IntNumberType.instance());
        record3.addField("email", StringType.instance());

        JsonType merged = List.<JsonType>of(record1, record2, record3).stream()
                .reduce(SchemaMerger::merge)
                .orElseThrow();

        assertInstanceOf(ObjectType.class, merged);
        assertTrue(((ObjectType) merged).isOptional("email"));
    }
}
