/**
 * Two independent ways to render a {@link io.github.ssullivan.types.JsonType} shape as text.
 * <p>
 * {@link io.github.ssullivan.jackson.Json} produces this project's own compact notation
 * (e.g. {@code "id": "integer|string"}, {@code "email?": "null|string"}) via a Jackson
 * {@code SimpleModule} of custom serializers registered on a shared {@code ObjectMapper}.
 * {@link io.github.ssullivan.jackson.JsonSchemaWriter} produces a real
 * <a href="https://json-schema.org/specification-links.html#draft-7">JSON Schema draft-07</a>
 * document by building a Jackson {@code ObjectNode} tree directly — a structurally different
 * output (nested {@code properties}/{@code required}/{@code items} keywords), so it doesn't fit
 * the serializer-registration approach {@code Json} uses. Both classes are independent; callers
 * pick whichever rendering they want for a given {@code JsonType}.
 */
package io.github.ssullivan.jackson;
