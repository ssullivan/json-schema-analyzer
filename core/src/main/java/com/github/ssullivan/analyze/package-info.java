/**
 * Produces and combines {@link com.github.ssullivan.types.JsonType} shapes:
 * {@link com.github.ssullivan.analyze.JsonSchemaAnalyzer} infers one from a single JSON
 * document, and {@link com.github.ssullivan.analyze.SchemaMerger} folds two independently
 * inferred shapes — typically two sample records — into one combined shape.
 */
package com.github.ssullivan.analyze;
