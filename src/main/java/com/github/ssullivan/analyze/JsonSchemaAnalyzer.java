package com.github.ssullivan.analyze;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.github.ssullivan.types.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * The JsonSchemaAnalyzer is used to describe the schema of a single JSON document.
 */
public class JsonSchemaAnalyzer {
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    public JsonSchemaAnalyzer() {

    }

    /**
     * Creates a {@link JsonType} schema from the provided JSON source.
     *
     * @param source a non-null {@link InputStream} that contains JSON
     * @return a non-null instance of {@link JsonType}
     * @throws IOException if the source cannot be read or contains malformed JSON
     */
    public JsonType parse(InputStream source) throws IOException {
        Objects.requireNonNull(source, "The method parameter `source` must not be null");

        try (JsonParser jParser = JSON_FACTORY.createParser(source)) {
            jParser.nextToken();
            JsonToken currentToken = jParser.getCurrentToken();
            if (currentToken == JsonToken.START_OBJECT) {
                ObjectType root = new ObjectType();
                handleObjectStruct(jParser, root);
                return root;
            } else if (currentToken == JsonToken.START_ARRAY) {
                return handleObjectArray(jParser);
            } else if (currentToken != null && currentToken.isScalarValue()) {
                return convertScalarToken(currentToken);
            } else {
                throw new RuntimeException("Unsupported root");
            }
        }
    }

    private static void handleObjectStruct(JsonParser jParser, ObjectType objectType) throws IOException {
        while (jParser.nextToken() != JsonToken.END_OBJECT) {
            JsonToken currentToken = jParser.getCurrentToken();
            String name = jParser.getCurrentName();
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
                value = convertScalarToken(currentToken);
            }
            else {
                continue;
            }

            JsonType existing = objectType.getFields().get(name);
            objectType.addField(name, existing == null ? value : SchemaMerger.merge(existing, value));
        }
    }

    private static ArrayType handleObjectArray(JsonParser jParser) throws IOException {
        ArrayType arrayType = new ArrayType();
        while (jParser.nextToken() != JsonToken.END_ARRAY) {
            JsonToken currentToken = jParser.getCurrentToken();

            if (currentToken.isScalarValue()) {
                arrayType.addField(convertScalarToken(currentToken));
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
     * @param jsonToken a non-null {@link JsonToken}
     * @return the {@link ScalarType} matching the token
     */
    private static JsonType convertScalarToken(JsonToken jsonToken) {
        if (jsonToken == null) {
            throw new NullPointerException("JsonToken must not be null");
        }

        return switch (jsonToken) {
            case VALUE_TRUE, VALUE_FALSE -> ScalarType.BOOLEAN;
            case VALUE_NUMBER_FLOAT -> ScalarType.FLOAT;
            case VALUE_NUMBER_INT -> ScalarType.INTEGER;
            case VALUE_STRING -> ScalarType.STRING;
            case VALUE_NULL -> ScalarType.NULL;
            default -> throw new RuntimeException("Unsupported JSON ScalarType");
        };
    }
}
