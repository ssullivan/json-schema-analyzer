/**
 * The inferred-shape model: {@link com.github.ssullivan.types.JsonType} is a sealed interface
 * with four implementations — {@link com.github.ssullivan.types.ObjectType},
 * {@link com.github.ssullivan.types.ArrayType}, {@link com.github.ssullivan.types.ScalarType},
 * and {@link com.github.ssullivan.types.UnionType}. These are plain data types with no
 * dependency on how a shape was produced (parsing, merging) or how it gets rendered (compact
 * notation, JSON Schema) — see {@link com.github.ssullivan.analyze} and
 * {@link com.github.ssullivan.jackson} for those.
 */
package com.github.ssullivan.types;
