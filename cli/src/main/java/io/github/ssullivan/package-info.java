/**
 * The command line entry point. {@link io.github.ssullivan.Main} is the only class here — it
 * wires picocli option parsing to {@code json-schema-explorer-core}'s
 * {@link io.github.ssullivan.analyze.JsonSchemaAnalyzer},
 * {@link io.github.ssullivan.analyze.SchemaMerger}, and
 * {@link io.github.ssullivan.jackson.JsonSchemaWriter}. All of the actual shape-inference logic
 * lives in {@code core}; nothing in this module is a dependency of anything else.
 */
package io.github.ssullivan;
