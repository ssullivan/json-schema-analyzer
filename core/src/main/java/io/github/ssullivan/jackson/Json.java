package io.github.ssullivan.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.ssullivan.types.*;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders a {@link JsonType} shape as this project's compact notation: scalar types print as
 * their name ({@code "string"}, {@code "integer"}, ...), a {@link UnionType} prints as its
 * members joined with {@code |} (e.g. {@code "integer|string"}) — collapsing an
 * {@link ObjectType}/{@link ArrayType}/multi-value {@link EnumType} member down to the generic
 * label {@code "object"}/{@code "array"}/{@code "string"}, since none of those fit unambiguously
 * inside an outer pipe-joined member list — and an {@link ObjectType}'s optional fields get a
 * {@code ?} suffix on the key (e.g. {@code "email?"}).
 * <p>
 * Rendering goes through {@link #write(Object)}. The underlying {@code ObjectMapper} is kept
 * private deliberately: it is shared across every call, and Jackson's thread-safety guarantee
 * holds only while a mapper is never reconfigured after construction — so nothing outside this
 * class is given the chance to reconfigure it.
 */
public final class Json {
    private static final ObjectMapper MAPPER = configureObjectMapper();

    private Json() {
    }

    /**
     * Renders a value using this project's compact notation.
     *
     * @param value a {@link io.github.ssullivan.types.JsonType} shape, or any other value the
     *              shared mapper can serialize (the CLI also passes the {@code ObjectNode}
     *              produced by {@link JsonSchemaWriter})
     * @return the rendered JSON
     * @throws JsonProcessingException if the value cannot be serialized
     */
    public static String write(Object value) throws JsonProcessingException {
        return MAPPER.writeValueAsString(value);
    }

    private static ObjectMapper configureObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        SimpleModule module = new SimpleModule();
        module.addSerializer(ScalarType.class, new JsonSerializer<>() {
            @Override
            public void serialize(ScalarType jsonType, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeString(jsonType.toString());
            }
        });
        module.addSerializer(EnumType.class, new JsonSerializer<>() {
            @Override
            public void serialize(EnumType enumType, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeString(enumType.toString());
            }
        });
        module.addSerializer(UnionType.class, new JsonSerializer<>() {
            @Override
            public void serialize(UnionType unionType, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                String joined = unionType.getMembers().stream()
                        .map(Json::unionMemberLabel)
                        .distinct()
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

    private static String unionMemberLabel(JsonType type) {
        if (type instanceof ObjectType) {
            return "object";
        }
        if (type instanceof ArrayType) {
            return "array";
        }
        if (type instanceof EnumType) {
            // A multi-value EnumType's own toString() is already a pipe-joined list; treating
            // that as one more atomic label here would nest one pipe-joined list inside another,
            // making the two ambiguous (see LlmWriter.memberLabel, which applies the same rule).
            // Collapse to the generic label instead, exactly like ObjectType/ArrayType above.
            return "string";
        }
        return String.valueOf(type);
    }
}
