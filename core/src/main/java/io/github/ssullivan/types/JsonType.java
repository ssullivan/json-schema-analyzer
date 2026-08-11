package io.github.ssullivan.types;

/**
 * The inferred shape of a JSON value — what this library produces instead of the value itself.
 * <p>
 * This is a sealed hierarchy of exactly five cases, so callers can pattern-match over it
 * exhaustively:
 * <ul>
 *   <li>{@link ObjectType} — named fields, each with its own shape, some possibly optional</li>
 *   <li>{@link ArrayType} — the distinct element shapes observed in an array</li>
 *   <li>{@link ScalarType} — a string, number, boolean, or null</li>
 *   <li>{@link EnumType} — a string field observed to take on only a small, fixed set of
 *       distinct literal values</li>
 *   <li>{@link UnionType} — more than one shape observed in the same position</li>
 * </ul>
 * Instances come from {@link io.github.ssullivan.analyze.JsonSchemaAnalyzer#parse} or
 * {@link io.github.ssullivan.analyze.SchemaMerger#merge}, and can be rendered with
 * {@link io.github.ssullivan.jackson.Json#write}, {@link io.github.ssullivan.format.LlmWriter},
 * or {@link io.github.ssullivan.jackson.JsonSchemaWriter}.
 */
public sealed interface JsonType
        permits ArrayType, EnumType, ObjectType, ScalarType, UnionType {
}
