package io.github.ssullivan;

import io.github.ssullivan.jackson.Json;
import io.github.ssullivan.types.*;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonTest {

    @Test
    void testUnionWithTwoDistinctObjectShapesDedupesGenericLabel() throws Exception {
        // Two structurally different ObjectTypes both collapse to the "object" label; without
        // deduping, this rendered as "\"object|object|string\"" instead of "\"object|string\"".
        UnionType union = new UnionType(Set.of(
                ScalarType.STRING,
                ObjectType.of("a", ScalarType.INTEGER),
                ObjectType.of("b", ScalarType.STRING)
        ));

        assertEquals("\"object|string\"", Json.write(union));
    }
}
