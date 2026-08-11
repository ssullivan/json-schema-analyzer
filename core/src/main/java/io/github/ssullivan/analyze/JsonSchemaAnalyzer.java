package io.github.ssullivan.analyze;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import io.github.ssullivan.types.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The JsonSchemaAnalyzer is used to describe the schema of a single JSON document.
 */
public class JsonSchemaAnalyzer {
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    /**
     * How many distinct sample shapes {@link SampleFolder} will remember before it stops
     * deduplicating. Bounded so that input whose samples are all structurally distinct — enum
     * detection on a high-cardinality field, say — degrades to plain merging rather than
     * retaining a shape per sample.
     */
    private static final int FOLDED_SHAPE_LIMIT = 1024;

    private final boolean detectFormats;
    private final boolean detectEnums;

    /**
     * Creates an analyzer with string format detection and enum detection off. Instances are
     * immutable and thread-safe, so one can be reused across documents and shared between
     * threads.
     */
    public JsonSchemaAnalyzer() {
        this(false, false);
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
        this(detectFormats, false);
    }

    /**
     * Creates an analyzer, optionally opting in to string format detection and/or enum
     * detection. Instances are immutable and thread-safe, so one can be reused across documents
     * and shared between threads.
     *
     * @param detectFormats when {@code true}, string values are checked against
     *                      {@link StringFormatDetector} and may come back as
     *                      {@link ScalarType#DATE}, {@link ScalarType#DATE_TIME}, or
     *                      {@link ScalarType#UUID} instead of the generic {@link ScalarType#STRING}.
     * @param detectEnums   when {@code true}, a string value's literal text is recorded against
     *                      the position it was found at, and any position that ends up holding
     *                      between two and {@link EnumType#MAX_VALUES} distinct values is
     *                      reported as an {@link EnumType} of them rather than the generic
     *                      {@link ScalarType#STRING}. A position holding more values than that,
     *                      or one whose values are sometimes a detected format
     *                      ({@link ScalarType#DATE}/{@link ScalarType#DATE_TIME}/
     *                      {@link ScalarType#UUID}), stays {@link ScalarType#STRING}; so does one
     *                      holding a single value, so a field that merely happens to be constant
     *                      never has that value reported.
     *                      <p>
     *                      Values are collected by position rather than embedded in the shape as
     *                      parsing goes, and substituted in only once the shape is final. Nothing
     *                      about the shape differs while it is being built or merged, so array
     *                      elements still deduplicate by equality and enum detection applies at
     *                      any depth — including fields of objects inside an array, and bare
     *                      arrays of strings, whose elements all share one position and therefore
     *                      pool their values. Off by default so existing callers' output is
     *                      unaffected unless they opt in.
     */
    public JsonSchemaAnalyzer(boolean detectFormats, boolean detectEnums) {
        this.detectFormats = detectFormats;
        this.detectEnums = detectEnums;
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
                PathNode root = newRootNode();
                jParser.nextToken();
                return applyEnums(parseRootValue(jParser, jParser.currentToken(), root), root);
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
                PathNode root = newRootNode();
                SampleFolder folder = new SampleFolder();
                JsonToken token;
                while ((token = jParser.nextToken()) != null) {
                    folder.fold(parseRootValue(jParser, token, root));
                }
                JsonType merged = folder.result();
                if (merged == null) {
                    throw JsonSchemaAnalysisException.unsupportedRoot(null, jParser.currentLocation());
                }
                return applyEnums(merged, root);
            } catch (JsonProcessingException e) {
                throw JsonSchemaAnalysisException.malformedJson(e);
            }
        }
    }

    /**
     * Creates a single merged {@link JsonType} shape from a top-level JSON array, treating each
     * of its elements as one sample and folding them together via {@link SchemaMerger} — a field
     * missing from some samples becomes optional, and one whose type varies across samples
     * becomes a union. This is what the CLI's {@code -m} flag calls.
     * <p>
     * Each element is parsed as an independent sample rather than accumulated as an array
     * element, which matters in two ways: enum detection applies to a sample's own fields (see
     * the {@code detectEnums} constructor parameter), and merging stays linear in the number of
     * samples because no intermediate {@link ArrayType} of per-sample shapes is ever built.
     * <p>
     * Input whose root is not an array has no samples to merge, so it is described as the single
     * document it is, exactly as {@link #parse} would. An empty array yields an empty
     * {@link ArrayType}.
     *
     * @param source a non-null {@link InputStream} that contains JSON
     * @return the merged shape of every element, or the document's own shape if its root is not
     *         an array
     * @throws IOException                if the source itself cannot be read (a genuine I/O
     *                                     failure)
     * @throws JsonSchemaAnalysisException if the source can be read but its content isn't
     *                                     analyzable (malformed or unsupported JSON)
     */
    public JsonType parseSamples(InputStream source) throws IOException {
        Objects.requireNonNull(source, "The method parameter `source` must not be null");

        try (JsonParser jParser = JSON_FACTORY.createParser(source)) {
            try {
                PathNode root = newRootNode();
                jParser.nextToken();
                JsonToken currentToken = jParser.currentToken();
                if (currentToken != JsonToken.START_ARRAY) {
                    return applyEnums(parseRootValue(jParser, currentToken, root), root);
                }

                SampleFolder folder = new SampleFolder();
                while (jParser.nextToken() != JsonToken.END_ARRAY) {
                    folder.fold(parseRootValue(jParser, jParser.currentToken(), root));
                }
                JsonType merged = folder.result();
                return merged == null ? new ArrayType() : applyEnums(merged, root);
            } catch (JsonProcessingException e) {
                throw JsonSchemaAnalysisException.malformedJson(e);
            }
        }
    }

    private JsonType parseRootValue(JsonParser jParser, JsonToken currentToken, PathNode node) throws IOException {
        if (currentToken == JsonToken.START_OBJECT) {
            ObjectType root = new ObjectType();
            handleObjectStruct(jParser, root, node);
            return root;
        } else if (currentToken == JsonToken.START_ARRAY) {
            return handleObjectArray(jParser, node);
        } else if (currentToken != null && currentToken.isScalarValue()) {
            return convertScalarToken(jParser, currentToken, node);
        } else {
            throw JsonSchemaAnalysisException.unsupportedRoot(currentToken, jParser.currentLocation());
        }
    }

    /**
     * @param node this object's position in the value-collection tree, or {@code null} when enum
     *             detection is off — see the {@code detectEnums} constructor parameter
     */
    private void handleObjectStruct(JsonParser jParser, ObjectType objectType, PathNode node) throws IOException {
        while (jParser.nextToken() != JsonToken.END_OBJECT) {
            JsonToken currentToken = jParser.currentToken();
            String name = jParser.currentName();
            PathNode fieldNode = node == null ? null : node.field(name);
            JsonType value;
            if (currentToken == JsonToken.START_OBJECT) {
                ObjectType nested = new ObjectType();
                handleObjectStruct(jParser, nested, fieldNode);
                value = nested;
            }
            else if (currentToken == JsonToken.START_ARRAY) {
                value = handleObjectArray(jParser, fieldNode);
            }
            else if (currentToken.isScalarValue()) {
                value = convertScalarToken(jParser, currentToken, fieldNode);
            }
            else {
                continue;
            }

            JsonType existing = objectType.getFields().get(name);
            objectType.addField(name, existing == null ? value : SchemaMerger.merge(existing, value));
        }
    }

    /**
     * Folds samples into one combined shape, skipping any whose shape has already been folded
     * in. Real sample sets repeat their shape heavily — only optionality and type differences
     * produce a distinct one — and folding a shape that is already absorbed cannot change the
     * result, so remembering what has been seen turns one merge per *sample* into one per
     * distinct shape. That is the same win the previous {@link ArrayType}-based {@code -m} path
     * got implicitly from its element set, kept here now that samples are merged as they stream.
     */
    private static final class SampleFolder {
        private final Set<JsonType> folded = new HashSet<>();
        private JsonType merged;

        void fold(JsonType sample) {
            if (merged == null) {
                merged = sample;
                folded.add(sample);
                return;
            }
            if (folded.contains(sample)) {
                return;
            }
            if (folded.size() < FOLDED_SHAPE_LIMIT) {
                folded.add(sample);
            }
            merged = SchemaMerger.merge(merged, sample);
        }

        JsonType result() {
            return merged;
        }
    }

    private ArrayType handleObjectArray(JsonParser jParser, PathNode node) throws IOException {
        // Every element shares one position, so their values pool into a single enum candidate.
        PathNode elementNode = node == null ? null : node.element();
        ArrayType arrayType = new ArrayType();
        while (jParser.nextToken() != JsonToken.END_ARRAY) {
            JsonToken currentToken = jParser.currentToken();

            if (currentToken.isScalarValue()) {
                arrayType.addField(convertScalarToken(jParser, currentToken, elementNode));
            }
            else if (currentToken == JsonToken.START_OBJECT) {
                ObjectType objectType = new ObjectType();
                handleObjectStruct(jParser, objectType, elementNode);
                arrayType.addField(objectType);
            } else if (currentToken == JsonToken.START_ARRAY) {
                arrayType.addField(handleObjectArray(jParser, elementNode));
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
     * @param node      this value's position in the value-collection tree, or {@code null} when
     *                  enum detection is off
     * @return the {@link ScalarType} matching the token
     */
    private JsonType convertScalarToken(JsonParser jParser, JsonToken jsonToken, PathNode node) throws IOException {
        if (jsonToken == null) {
            throw new NullPointerException("JsonToken must not be null");
        }

        return switch (jsonToken) {
            case VALUE_TRUE, VALUE_FALSE -> ScalarType.BOOLEAN;
            case VALUE_NUMBER_FLOAT -> ScalarType.FLOAT;
            case VALUE_NUMBER_INT -> ScalarType.INTEGER;
            case VALUE_STRING -> convertStringToken(jParser, node);
            case VALUE_NULL -> ScalarType.NULL;
            default -> throw JsonSchemaAnalysisException.unsupportedScalarToken(jsonToken, jParser.currentLocation());
        };
    }

    /**
     * Resolves a string token's {@link ScalarType} and, when enum detection is on, records its
     * literal text against {@code node} so {@link #applyEnums} can decide afterwards whether that
     * position is an enum. The token's text is read at most once, and not at all when neither
     * detection is enabled. A value that {@code -f} recognizes as a specific format poisons the
     * position: those aren't categorical values, and reporting an enum built only from the
     * position's *other* samples would misdescribe it.
     */
    private ScalarType convertStringToken(JsonParser jParser, PathNode node) throws IOException {
        if (!detectFormats && node == null) {
            return ScalarType.STRING;
        }
        String text = jParser.getText();
        ScalarType scalarValue = detectFormats ? StringFormatDetector.detect(text) : ScalarType.STRING;
        if (node != null) {
            if (scalarValue == ScalarType.STRING) {
                node.record(text);
            } else {
                node.poison();
            }
        }
        return scalarValue;
    }

    private PathNode newRootNode() {
        return detectEnums ? new PathNode() : null;
    }

    /**
     * Replaces {@link ScalarType#STRING} with an {@link EnumType} at every position whose
     * collected values qualify, rebuilding the shape around them. Run once, after the shape is
     * final, so that nothing about it differed while it was being built or merged.
     * <p>
     * A {@link UnionType}'s string member is deliberately left alone — a position that is
     * sometimes a non-string isn't a categorical value, matching how {@link SchemaMerger} already
     * discards enum information on that kind of collision — but object and array members are
     * still descended into so enums nested within them apply.
     */
    private static JsonType applyEnums(JsonType shape, PathNode node) {
        if (node == null) {
            return shape;
        }
        if (shape == ScalarType.STRING) {
            return node.isEnum() ? new EnumType(node.values()) : shape;
        }
        if (shape instanceof ObjectType objectType) {
            ObjectType result = new ObjectType();
            for (Map.Entry<String, JsonType> entry : objectType.getFields().entrySet()) {
                result.addField(entry.getKey(), applyEnums(entry.getValue(), node.existingField(entry.getKey())));
            }
            objectType.getOptionalFields().forEach(result::markOptional);
            return result;
        }
        if (shape instanceof ArrayType arrayType) {
            Set<JsonType> elements = arrayType.getFields();
            // Every element pooled its values into one position, which only describes the array
            // truthfully while the elements share a single shape. When they don't -- a
            // discriminated union, say -- stamping the pooled set onto each shape would claim
            // combinations that never occurred, so nothing under a heterogeneous array is
            // substituted.
            PathNode elementNode = elements.size() == 1 ? node.existingElement() : null;
            ArrayType result = new ArrayType();
            for (JsonType element : elements) {
                result.addField(applyEnums(element, elementNode));
            }
            return result;
        }
        if (shape instanceof UnionType unionType) {
            Set<JsonType> members = new LinkedHashSet<>();
            for (JsonType member : unionType.getMembers()) {
                members.add(member == ScalarType.STRING ? member : applyEnums(member, node));
            }
            return new UnionType(members);
        }
        return shape;
    }

    /**
     * One position a string value can appear at, and the distinct values seen there. Array
     * elements all share a single {@link #element()} node, so their values pool together.
     * <p>
     * A position stops collecting once it holds more than {@link EnumType#MAX_VALUES} values, or
     * once a value there turns out to be a detected format, which bounds retained memory by the
     * document's shape rather than by how much data it holds.
     */
    private static final class PathNode {
        private final Map<String, PathNode> fields = new HashMap<>();
        private PathNode element;
        private Set<String> values;
        private boolean poisoned;

        PathNode field(String name) {
            return fields.computeIfAbsent(name, ignored -> new PathNode());
        }

        PathNode element() {
            if (element == null) {
                element = new PathNode();
            }
            return element;
        }

        PathNode existingField(String name) {
            return fields.get(name);
        }

        PathNode existingElement() {
            return element;
        }

        void record(String value) {
            if (poisoned) {
                return;
            }
            // An empty or whitespace-padded value can't be told apart from the separators and
            // indentation around it once rendered ("a[]: |b"), and isn't a useful categorical
            // label anyway, so one is enough to disqualify the position.
            if (value.isEmpty() || !value.equals(value.strip())) {
                poison();
                return;
            }
            if (values == null) {
                values = new LinkedHashSet<>();
            }
            values.add(value);
            if (values.size() > EnumType.MAX_VALUES) {
                poison();
            }
        }

        void poison() {
            poisoned = true;
            values = null;
        }

        boolean isEnum() {
            return !poisoned && values != null && values.size() > 1;
        }

        Set<String> values() {
            return values;
        }
    }
}
