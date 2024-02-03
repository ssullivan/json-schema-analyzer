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
     * @throws IOException
     */
    public JsonType parse(InputStream source) throws IOException {
        Objects.requireNonNull(source, "The method parameter `source` must not be null");

        JsonParser jParser = JSON_FACTORY.createParser(source);

        JsonPath path = new JsonPath();
        JsonToken currentToken;

        jParser.nextToken();
        currentToken = jParser.getCurrentToken();
        if (currentToken == JsonToken.START_OBJECT) {
            ObjectType root = new ObjectType();
            handleObjectStruct(jParser, path, root);
            return root;
        }
        else if (currentToken == JsonToken.START_ARRAY) {
            return handleObjectArray(jParser, path, null);
        }
        else {
            throw new RuntimeException("Unsupported root");
        }
    }


    private static ArrayType handleObjectArray(JsonParser jParser, JsonPath jsonPath, JsonType schema) throws IOException {
        if (jsonPath.nonEmpty()) {
            jsonPath.push(".");
        }

        ArrayType arrayType = new ArrayType();
        String name = jParser.getCurrentName();
        while (jParser.nextToken() != JsonToken.END_ARRAY) {
            JsonToken currentToken = jParser.getCurrentToken();

            if (currentToken.isScalarValue()) {
                arrayType.addField(convertScalarToken(currentToken));
            }
            else if (currentToken == JsonToken.START_OBJECT) {
                ObjectType objectType = new ObjectType();
                handleObjectStruct(jParser, new JsonPath(), objectType);
                arrayType.addField(objectType);
            } else if (currentToken == JsonToken.START_ARRAY) {
                handleObjectArray(jParser, jsonPath, arrayType);
            }
        }

        if (schema != null) {
            if (schema instanceof ArrayType) {
                ((ArrayType) schema).addField(arrayType);
            } else if (schema instanceof ObjectType) {
                ((ObjectType) schema).addField(name, arrayType);
            }
        }


        if (jsonPath.nonEmpty()) {
            jsonPath.pop();
        }

        return arrayType;
    }

    private static void handleObjectStruct(JsonParser jParser, JsonPath jsonPath, ObjectType objectType) throws IOException {
        if (jsonPath.nonEmpty()) {
            jsonPath.push(".");
        }
        while (jParser.nextToken() != JsonToken.END_OBJECT) {
            JsonToken currentToken = jParser.getCurrentToken();
            if (currentToken == JsonToken.START_OBJECT) {
                jsonPath.push(jParser.getCurrentName());
                handleObjectStruct(jParser, jsonPath, objectType);
                jsonPath.pop();
            }
            else if (currentToken == JsonToken.START_ARRAY) {
                jsonPath.push(jParser.getCurrentName());
                handleObjectArray(jParser, jsonPath, objectType);
                jsonPath.pop();
            }
            else if (currentToken.isScalarValue()) {
                String key = jsonPath + jParser.getCurrentName();
                objectType.addField(key, convertScalarToken(currentToken));
            }
        }

        if (jsonPath.nonEmpty()) {
            jsonPath.pop();
        }
    }

    /**
     * Convert a jackson {@link JsonToken} to one of our internal {@link JsonType} representations.
     *
     * @param jsonToken a non-null {@link JsonToken}
     * @return one of {@link StringType}, {@link IntNumberType}, {@link FloatNumberType}, {@link NullType}, {@link BooleanType}
     */
    private static JsonType convertScalarToken(JsonToken jsonToken) {
        if (jsonToken == null) {
            throw new NullPointerException("JsonToken must not be null");
        }

        return switch (jsonToken) {
            case VALUE_TRUE, VALUE_FALSE -> BooleanType.instance();
            case VALUE_NUMBER_FLOAT -> FloatNumberType.instance();
            case VALUE_NUMBER_INT -> IntNumberType.instance();
            case VALUE_STRING -> StringType.instance();
            case VALUE_NULL -> NullType.instance();
            default -> throw new RuntimeException("Unsupported JSON ScalarType");
        };
    }
}
