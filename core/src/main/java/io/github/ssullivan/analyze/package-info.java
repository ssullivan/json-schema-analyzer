/**
 * Produces and combines {@link io.github.ssullivan.types.JsonType} shapes:
 * {@link io.github.ssullivan.analyze.JsonSchemaAnalyzer} infers one from a single JSON
 * document, and {@link io.github.ssullivan.analyze.SchemaMerger} folds two independently
 * inferred shapes — typically two sample records — into one combined shape.
 */
package io.github.ssullivan.analyze;
