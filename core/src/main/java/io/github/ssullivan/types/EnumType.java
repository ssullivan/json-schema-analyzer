package io.github.ssullivan.types;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a string field observed to take on only a small, fixed set of distinct literal
 * values across merged samples — produced only by {@link io.github.ssullivan.analyze.SchemaMerger}
 * and {@link io.github.ssullivan.analyze.JsonSchemaAnalyzer} when enum detection is enabled. A
 * field whose distinct-value count exceeds {@link #MAX_VALUES} collapses to
 * {@link ScalarType#STRING} instead of becoming (or remaining) an {@code EnumType}.
 */
public final class EnumType implements JsonType {

    /**
     * The maximum number of distinct values a field can hold and still be reported as an enum;
     * beyond this, {@link io.github.ssullivan.analyze.SchemaMerger} falls back to the generic
     * {@link ScalarType#STRING}.
     */
    public static final int MAX_VALUES = 5;

    private final Set<String> values;

    /**
     * Creates an enum of the given values. The set is copied, so later changes to
     * {@code values} do not affect this instance.
     *
     * @param values the distinct literal values observed in this position
     */
    public EnumType(Set<String> values) {
        this.values = new LinkedHashSet<>(values);
    }

    /**
     * Returns the values this enum is made of.
     *
     * @return an unmodifiable view of the enum's distinct values
     */
    public Set<String> getValues() {
        return Collections.unmodifiableSet(values);
    }

    /**
     * Creates an enum from the given values.
     *
     * @param values the distinct values; duplicates are collapsed
     * @return the new enum
     */
    public static EnumType of(String... values) {
        return new EnumType(new LinkedHashSet<>(Arrays.asList(values)));
    }

    /**
     * Sorted, pipe-joined, unquoted values (e.g. {@code "active|inactive|pending"}) — or, when
     * only one value has ever been observed, the generic {@link ScalarType#STRING} label, so a
     * field that merely happens to be constant across a small sample never leaks that value.
     * <p>
     * A value containing a backslash, a pipe, or a newline is backslash-escaped first (e.g. a
     * value literally equal to {@code "a|b"} renders as {@code a\|b}), so it can never be
     * mistaken for a separator between two distinct values, and can never split the
     * single-line-per-field notation this feeds into (see
     * {@link io.github.ssullivan.format.LlmWriter}) across multiple lines.
     */
    @Override
    public String toString() {
        if (values.size() <= 1) {
            return ScalarType.STRING.toString();
        }
        return values.stream().sorted().map(EnumType::escape).collect(Collectors.joining("|"));
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnumType enumType = (EnumType) o;
        return Objects.equals(values, enumType.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }
}
