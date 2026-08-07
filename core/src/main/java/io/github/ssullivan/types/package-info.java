/**
 * The inferred-shape model: {@link io.github.ssullivan.types.JsonType} is a sealed interface
 * with four implementations — {@link io.github.ssullivan.types.ObjectType},
 * {@link io.github.ssullivan.types.ArrayType}, {@link io.github.ssullivan.types.ScalarType},
 * and {@link io.github.ssullivan.types.UnionType}. These are plain data types with no
 * dependency on how a shape was produced (parsing, merging) or how it gets rendered (compact
 * notation, JSON Schema) — see {@link io.github.ssullivan.analyze} and
 * {@link io.github.ssullivan.jackson} for those.
 */
package io.github.ssullivan.types;
