package io.github.ssullivan;

import io.github.ssullivan.types.*;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * These getters used to hand out the live internal collection, so any caller could silently
 * corrupt an inferred schema (e.g. {@code objectType.getFields().clear()} emptied it).
 */
class JsonTypeEncapsulationTest {

    @Test
    void testObjectTypeFieldsAreUnmodifiable() {
        ObjectType objectType = ObjectType.of("id", ScalarType.INTEGER);

        assertThrows(UnsupportedOperationException.class, () -> objectType.getFields().clear());
        assertEquals(ScalarType.INTEGER, objectType.getFields().get("id"));
    }

    @Test
    void testObjectTypeOptionalFieldsAreUnmodifiable() {
        ObjectType objectType = ObjectType.of("id", ScalarType.INTEGER);

        assertThrows(UnsupportedOperationException.class, () -> objectType.getOptionalFields().add("id"));
        assertEquals(Set.of(), objectType.getOptionalFields());
    }

    @Test
    void testArrayTypeFieldsAreUnmodifiable() {
        ArrayType arrayType = ArrayType.of(ScalarType.INTEGER);

        assertThrows(UnsupportedOperationException.class, () -> arrayType.getFields().clear());
        assertEquals(1, arrayType.getFields().size());
    }

    @Test
    void testUnionTypeMembersAreUnmodifiable() {
        UnionType unionType = new UnionType(Set.of(ScalarType.INTEGER, ScalarType.STRING));

        assertThrows(UnsupportedOperationException.class, () -> unionType.getMembers().clear());
        assertEquals(2, unionType.getMembers().size());
    }

    @Test
    void testEnumTypeValuesAreUnmodifiable() {
        EnumType enumType = EnumType.of("a", "b");

        assertThrows(UnsupportedOperationException.class, () -> enumType.getValues().clear());
        assertEquals(2, enumType.getValues().size());
    }
}
