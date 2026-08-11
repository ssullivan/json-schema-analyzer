package io.github.ssullivan.analyze;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import io.github.ssullivan.types.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * The JsonSchemaAnalyzer is used to describe the schema of a single JSON document.
 */
public class JsonSchemaAnalyzer {
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private final boolean detectFormats;

    /**
     * Creates an analyzer with string format detection off. Instances are immutable and
     * thread-safe, so one can be reused across documents and shared between threads.
     */
    public JsonSchemaAnalyzer() {
        this(false);
    }

    /**
     * Creates an analyzer, optionally opting in to string format detection. Instances are
     * immutable and thread-safe, so one can be reused across documents and shared between
     * threads.
     *
     * @param detectFormats when {@code true}, string values are checked against
     *                      {@link StringFormatDetector} and may come back as
     *                      {@link ScalarType#DATE}, {@link ScalarType#DATE_TIME}, or
     *                      {@link ScalarType#UUID} instead of the generic {@link ScalarType#STRING}.
     *                      Off by default (the no-arg constructor) so existing callers' output
     *                      is unaffected unless they opt in.
     */
    public JsonSchemaAnalyzer(boolean detectFormats) {
        this.detectFormats = detectFormats;
    }

    /**
     * Creates a {@link JsonType} schema from the provided JSON source.
     *
     * @param source a non-null {@link InputStream} that contains JSON
     * @return a non-null instance of {@link JsonType}
     * @throws IOException                if the source itself cannot be read (a genuine I/O
     *                                     failure)
     * @throws JsonSchemaAnalysisException if the source can be read but its content isn't
     *                                     analyzable (malformed or unsupported JSON)
     */
    public JsonType parse(InputStream source) throws IOException {
        Objects.requireNonNull(source, "The method parameter `source` must not be null");

        try (JsonParser jParser = JSON_FACTORY.createParser(source)) {
            try {
                jParser.nextToken();
                return parseRootValue(jParser, jParser.currentToken());
            } catch (JsonProcessingException e) {
                throw JsonSchemaAnalysisException.malformedJson(e);
            }
        }
    }

    /**
     * Creates a single merged {@link JsonType} shape from newline-delimited JSON (NDJSON): a
     * stream of whitespace-separated JSON values, one conventionally per line. Each value is
     * treated as a sample and folded into the others via {@link SchemaMerger}, exactly as the
     * CLI's {@code -m} flag does for elements of a JSON array — a field missing from some
     * samples becomes optional, and one whose type varies across samples becomes a union.
     * <p>
     * Blank lines are ignored. Because Jackson treats all whitespace, including newlines, as
     * insignificant between tokens, this also tolerates a value's JSON spanning multiple lines.
     *
     * @param source a non-null {@link InputStream} that contains NDJSON
     * @return the merged shape of every value in the stream
     * @throws IOException                if the source itself cannot be read (a genuine I/O
     *                                     failure)
     * @throws JsonSchemaAnalysisException if the source can be read but its content isn't
     *                                     analyzable (malformed JSON, or no values at all)
     */
    public JsonType parseJsonLines(InputStream source) throws IOException {
        Objects.requireNonNull(source, "The method parameter `source` must not be null");

        try (JsonParser jParser = JSON_FACTORY.createParser(source)) {
            try {
                JsonType merged = null;
                JsonToken token;
                while ((token = jParser.nextToken()) != null) {
                    JsonType value = parseRootValue(jParser, token);
                    merged = merged == null ? value : SchemaMerger.merge(merged, value);
                }
                if (merged == null) {
                    throw JsonSchemaAnalysisException.unsupportedRoot(null, jParser.currentLocation());
                }
                return merged;
            } catch (JsonProcessingException e) {
                throw JsonSchemaAnalysisException.malformedJson(e);
            }
        }
    }

    private JsonType parseRootValue(JsonParser jParser, JsonToken currentToken) throws IOException {
        if (currentToken == JsonToken.START_OBJECT) {
            ObjectType root = new ObjectType();
            handleObjectStruct(jParser, root);
            return root;
        } else if (currentToken == JsonToken.START_ARRAY) {
            return handleObjectArray(jParser);
        } else if (currentToken != null && currentToken.isScalarValue()) {
            return convertScalarToken(jParser, currentToken);
        } else {
            throw JsonSchemaAnalysisException.unsupportedRoot(currentToken, jParser.currentLocation());
        }
    }

    private void handleObjectStruct(JsonParser jParser, ObjectType objectType) throws IOException {
        while (jParser.nextToken() != JsonToken.END_OBJECT) {
            JsonToken currentToken = jParser.currentToken();
            String name = jParser.currentName();
            JsonType value;
            if (currentToken == JsonToken.START_OBJECT) {
                ObjectType nested = new ObjectType();
                handleObjectStruct(jParser, nested);
                value = nested;
            }
            else if (currentToken == JsonToken.START_ARRAY) {
                value = handleObjectArray(jParser);
            }
            else if (currentToken.isScalarValue()) {
                value = convertScalarToken(jParser, currentToken);
            }
            else {
                continue;
            }

            JsonType existing = objectType.getFields().get(name);
            objectType.addField(name, existing == null ? value : SchemaMerger.merge(existing, value));
        }
    }

    private ArrayType handleObjectArray(JsonParser jParser) throws IOException {
        ArrayType arrayType = new ArrayType();
        while (jParser.nextToken() != JsonToken.END_ARRAY) {
            JsonToken currentToken = jParser.currentToken();

            if (currentToken.isScalarValue()) {
                arrayType.addField(convertScalarToken(jParser, currentToken));
            }
            else if (currentToken == JsonToken.START_OBJECT) {
                ObjectType objectType = new ObjectType();
                handleObjectStruct(jParser, objectType);
                arrayType.addField(objectType);
            } else if (currentToken == JsonToken.START_ARRAY) {
                arrayType.addField(handleObjectArray(jParser));
            }
        }

        return arrayType;
    }

    /**
     * Convert a jackson {@link JsonToken} to one of our internal {@link JsonType} representations.
     *
     * @param jParser   the parser {@code jsonToken} was read from, used only to report a precise
     *                  location if {@code jsonToken} turns out to be unsupported
     * @param jsonToken a non-null {@link JsonToken}
     * @return the {@link ScalarType} matching the token
     */
    private JsonType convertScalarToken(JsonParser jParser, JsonToken jsonToken) throws IOException {
        if (jsonToken == null) {
            throw new NullPointerException("JsonToken must not be null");
        }

        return switch (jsonToken) {
            case VALUE_TRUE, VALUE_FALSE -> ScalarType.BOOLEAN;
            case VALUE_NUMBER_FLOAT -> ScalarType.FLOAT;
            case VALUE_NUMBER_INT -> ScalarType.INTEGER;
            case VALUE_STRING -> detectFormats ? StringFormatDetector.detect(jParser.getText()) : ScalarType.STRING;
            case VALUE_NULL -> ScalarType.NULL;
            default -> throw JsonSchemaAnalysisException.unsupportedScalarToken(jsonToken, jParser.currentLocation());
        };
    }
}
