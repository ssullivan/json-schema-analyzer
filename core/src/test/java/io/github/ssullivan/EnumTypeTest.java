package io.github.ssullivan;

import io.github.ssullivan.types.EnumType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EnumTypeTest {

    @Test
    void testOfFactoryCreatesEnumWithGivenValues() {
        EnumType enumType = EnumType.of("a", "b");

        assertEquals(Set.of("a", "b"), enumType.getValues());
    }

    @Test
    void testEqualsIsOrderIndependent() {
        Set<String> forward = new LinkedHashSet<>(Set.of());
        forward.add("a");
        forward.add("b");

        Set<String> backward = new LinkedHashSet<>(Set.of());
        backward.add("b");
        backward.add("a");

        assertEquals(new EnumType(forward), new EnumType(backward));
    }

    @Test
    void testDistinctValueSetsAreNotEqual() {
        assertNotEquals(EnumType.of("a", "b"), EnumType.of("a", "c"));
    }

    @Test
    void testToStringSingleValueRendersAsPlainStringLabel() {
        assertEquals("string", EnumType.of("active").toString());
    }

    @Test
    void testToStringMultipleValuesJoinsSortedWithPipe() {
        assertEquals("a|b", EnumType.of("b", "a").toString());
    }

    @Test
    void testToStringThreeValuesSortsAllOfThem() {
        assertEquals("closed|open|pending", EnumType.of("pending", "open", "closed").toString());
    }

    @Test
    void testToStringEscapesPipeInValue() {
        // Without escaping, a value literally equal to "a|b" merged with "c" would render
        // byte-identical to the genuine 3-value enum {"a", "b", "c"}
        assertEquals("a\\|b|c", EnumType.of("a|b", "c").toString());
    }

    @Test
    void testToStringDistinguishesValueContainingPipeFromSeparateValues() {
        assertNotEquals(
                EnumType.of("a", "b", "c").toString(),
                EnumType.of("a|b", "c").toString()
        );
    }

    @Test
    void testToStringEscapesBackslashInValue() {
        // Backslash must be escaped first, before pipe/newline escaping is applied, so a
        // literal backslash in the value can't be mistaken for part of an escape sequence
        assertEquals("a\\\\b|c", EnumType.of("a\\b", "c").toString());
    }

    @Test
    void testToStringEscapesNewlineToKeepSingleLineOutput() {
        String result = EnumType.of("line1\nline2", "c").toString();

        assertEquals("c|line1\\nline2", result);
        assertFalse(result.contains("\n"), "escaped output must not contain a raw newline");
    }
}
