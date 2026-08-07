package io.github.ssullivan.format;

import io.github.ssullivan.types.*;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders a {@link JsonType} shape as a compact, indentation-based notation with no braces,
 * quotes, or commas — cheaper to spend tokens on than pretty-printed JSON when the shape is
 * headed into an LLM prompt rather than a machine parser. Nesting is expressed purely through
 * two-space indentation, array-typed fields get a trailing {@code []} on their name (repeated
 * once per level of array nesting, e.g. {@code matrix[][]: integer}), optional fields keep the
 * {@code ?} suffix used by {@link io.github.ssullivan.jackson.Json}'s compact notation, and
 * heterogeneous types join their member labels with {@code |} the same way that class's
 * {@link UnionType} handling does — including collapsing an {@link ObjectType} or {@link ArrayType}
 * member down to the generic label {@code "object"}/{@code "array"}, since a fully nested
 * sub-shape doesn't fit on one joined line.
 */
public final class LlmWriter {
    private LlmWriter() {
    }

    /**
     * @param type a non-null shape, typically produced by
     *             {@link io.github.ssullivan.analyze.JsonSchemaAnalyzer} or
     *             {@link io.github.ssullivan.analyze.SchemaMerger}
     * @return the shape rendered in this class's notation, with no trailing newline
     */
    public static String write(JsonType type) {
        Objects.requireNonNull(type, "LlmWriter.write: parameter `type` must not be null");

        StringBuilder sb = new StringBuilder();
        writeRoot(sb, type);
        return sb.toString().stripTrailing();
    }

    private static void writeRoot(StringBuilder sb, JsonType type) {
        if (type instanceof ObjectType objectType) {
            if (objectType.getFields().isEmpty()) {
                sb.append("object");
            } else {
                writeFields(sb, objectType, 0);
            }
        } else if (type instanceof ArrayType arrayType) {
            writeArrayEntry(sb, "", arrayType, 0);
        } else {
            sb.append(inlineLabel(type));
        }
    }

    private static void writeFields(StringBuilder sb, ObjectType objectType, int indent) {
        for (var entry : objectType.getFields().entrySet()) {
            String name = entry.getKey();
            if (objectType.isOptional(name)) {
                name = name + "?";
            }
            writeField(sb, name, entry.getValue(), indent);
        }
    }

    private static void writeField(StringBuilder sb, String name, JsonType type, int indent) {
        if (type instanceof ObjectType objectType) {
            if (objectType.getFields().isEmpty()) {
                indent(sb, indent).append(name).append(": object\n");
            } else {
                indent(sb, indent).append(name).append(":\n");
                writeFields(sb, objectType, indent + 1);
            }
        } else if (type instanceof ArrayType arrayType) {
            writeArrayEntry(sb, name, arrayType, indent);
        } else {
            indent(sb, indent).append(name).append(": ").append(inlineLabel(type)).append('\n');
        }
    }

    /**
     * Writes an array-typed field, or — when {@code name} is empty — an array-shaped document
     * root. A single array-of-array element shape collapses onto one line as a repeated
     * {@code []} suffix rather than nesting a near-empty block per level.
     */
    private static void writeArrayEntry(StringBuilder sb, String name, ArrayType arrayType, int indent) {
        String label = name + "[]";
        Set<JsonType> elements = arrayType.getFields();

        if (elements.isEmpty()) {
            indent(sb, indent).append(label).append(": array\n");
            return;
        }

        if (elements.size() == 1) {
            JsonType element = elements.iterator().next();
            if (element instanceof ObjectType objectType) {
                if (objectType.getFields().isEmpty()) {
                    indent(sb, indent).append(label).append(": object\n");
                } else {
                    indent(sb, indent).append(label).append(":\n");
                    writeFields(sb, objectType, indent + 1);
                }
            } else if (element instanceof ArrayType nested) {
                writeArrayEntry(sb, label, nested, indent);
            } else {
                indent(sb, indent).append(label).append(": ").append(inlineLabel(element)).append('\n');
            }
            return;
        }

        String joined = elements.stream()
                .map(LlmWriter::memberLabel)
                .distinct()
                .sorted()
                .collect(Collectors.joining("|"));
        indent(sb, indent).append(label).append(": ").append(joined).append('\n');
    }

    private static String inlineLabel(JsonType type) {
        if (type instanceof UnionType unionType) {
            return unionType.getMembers().stream()
                    .map(LlmWriter::memberLabel)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining("|"));
        }
        return memberLabel(type);
    }

    private static String memberLabel(JsonType type) {
        if (type instanceof ObjectType) {
            return "object";
        }
        if (type instanceof ArrayType) {
            return "array";
        }
        return String.valueOf(type);
    }

    private static StringBuilder indent(StringBuilder sb, int indent) {
        return sb.append("  ".repeat(indent));
    }
}
