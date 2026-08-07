package com.github.ssullivan.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.ssullivan.types.*;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

public final class Json {
    public static final ObjectMapper MAPPER = configureObjectMapper();

    private static ObjectMapper configureObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        SimpleModule module = new SimpleModule();
        module.addSerializer(StringType.class, new JsonSerializer<>() {
            @Override
            public void serialize(StringType jsonType, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeString(jsonType.toString());
            }
        });
        module.addSerializer(FloatNumberType.class, new JsonSerializer<>() {
            @Override
            public void serialize(FloatNumberType jsonType, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeString(jsonType.toString());
            }
        });
        module.addSerializer(IntNumberType.class, new JsonSerializer<>() {
            @Override
            public void serialize(IntNumberType jsonType, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeString(jsonType.toString());
            }
        });
        module.addSerializer(BooleanType.class, new JsonSerializer<>() {
            @Override
            public void serialize(BooleanType jsonType, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeString(jsonType.toString());
            }
        });
        module.addSerializer(NullType.class, new JsonSerializer<>() {
            @Override
            public void serialize(NullType jsonType, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeString(jsonType.toString());
            }
        });
        module.addSerializer(NumberType.class, new JsonSerializer<>() {
            @Override
            public void serialize(NumberType jsonType, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeString(jsonType.toString());
            }
        });
        module.addSerializer(UnionType.class, new JsonSerializer<>() {
            @Override
            public void serialize(UnionType unionType, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                String joined = unionType.getMembers().stream()
                        .map(String::valueOf)
                        .sorted()
                        .collect(Collectors.joining("|"));
                jsonGenerator.writeString(joined);
            }
        });
        module.addSerializer(ArrayType.class, new JsonSerializer<>() {
            @Override
            public void serialize(ArrayType arrayType, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeStartArray();
                for (JsonType jsonType : arrayType.getFields()) {
                    jsonGenerator.writeObject(jsonType);
                }
                jsonGenerator.writeEndArray();
            }
        });
        module.addSerializer(ObjectType.class, new JsonSerializer<>() {
            @Override
            public void serialize(ObjectType objectType, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeStartObject();
                for (Map.Entry<String, JsonType> entry : objectType.getFields().entrySet()) {
                    String key = entry.getKey();
                    if (objectType.isOptional(key)) {
                        key = key + "?";
                    }
                    jsonGenerator.writeObjectField(key, entry.getValue());
                }
                jsonGenerator.writeEndObject();
            }
        });
        objectMapper.registerModule(module);
        return objectMapper;
    }

}
