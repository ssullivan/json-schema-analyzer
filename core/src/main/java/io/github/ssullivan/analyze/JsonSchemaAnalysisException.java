package io.github.ssullivan.analyze;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Thrown when {@link JsonSchemaAnalyzer} encounters JSON input it cannot describe a shape for.
 * Messages point at the exact line/column that triggered the failure and say what was expected
 * there, so callers don't have to go spelunking through a stack trace to find out what was wrong
 * with their input.
 */
public final class JsonSchemaAnalysisException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private JsonSchemaAnalysisException(String message) {
        super(message);
    }

    private JsonSchemaAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }

    static JsonSchemaAnalysisException malformedJson(JsonProcessingException cause) {
        JsonLocation location = cause.getLocation();
        String locationText = location == null
                ? ""
                : String.format(" (line %d, column %d)", location.getLineNr(), location.getColumnNr());
        return new JsonSchemaAnalysisException(
                String.format("Malformed JSON%s: %s", locationText, cause.getOriginalMessage()), cause);
    }

    static JsonSchemaAnalysisException unsupportedRoot(JsonToken actual, JsonLocation location) {
        String found = actual == null
                ? "no content — the input was empty"
                : String.format("a stray %s token", actual);
        return new JsonSchemaAnalysisException(String.format(
                "Unsupported JSON document (line %d, column %d): expected the document to start "
                        + "with an object ('{'), an array ('['), or a scalar value (string, "
                        + "number, boolean, or null), but found %s.",
                location.getLineNr(), location.getColumnNr(), found));
    }

    static JsonSchemaAnalysisException unsupportedScalarToken(JsonToken token, JsonLocation location) {
        return new JsonSchemaAnalysisException(String.format(
                "Unexpected token %s (line %d, column %d): not a string, number, boolean, or "
                        + "null. This shouldn't be reachable when parsing plain JSON text — "
                        + "if you're seeing this, it likely indicates a bug in json-schema-analyzer "
                        + "itself, not a problem with your input.",
                token, location.getLineNr(), location.getColumnNr()));
    }
}
