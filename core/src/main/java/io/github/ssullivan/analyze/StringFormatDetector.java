package io.github.ssullivan.analyze;

import io.github.ssullivan.types.ScalarType;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Structurally classifies a JSON string value as one of the formats {@link JsonSchemaAnalyzer}
 * can optionally detect — {@link ScalarType#DATE}, {@link ScalarType#DATE_TIME},
 * {@link ScalarType#UUID} — or {@link ScalarType#STRING} if none match.
 * <p>
 * Validation is purely structural (shape, not full calendar/semantic correctness): e.g.
 * {@code "2023-13-45"} still matches {@link ScalarType#DATE} because it has the right shape,
 * consistent with this project's "describe the shape, don't fully validate the data" philosophy.
 * Every pattern is fully anchored and uses only bounded, non-nested quantifiers, so none of them
 * are vulnerable to catastrophic backtracking regardless of input length.
 */
final class StringFormatDetector {

    /** ISO 8601 calendar date: {@code YYYY-MM-DD}. */
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /**
     * RFC 3339 timestamp: a {@link #DATE} shape, {@code T}/{@code t}, a time, optional
     * fractional seconds, and a {@code Z}/{@code z} or {@code +HH:MM}/{@code -HH:MM} offset. The
     * offset is mandatory per RFC 3339 — a bare local time with no zone info doesn't match.
     */
    private static final Pattern DATE_TIME = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}[Tt]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([Zz]|[+-]\\d{2}:\\d{2})$");

    /** Canonical 8-4-4-4-12 hex UUID (36 characters including hyphens), either case. */
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private StringFormatDetector() {
    }

    /**
     * Classifies a JSON string value's format.
     *
     * @param value a non-null string value, as read from a {@code VALUE_STRING} token
     * @return the matching {@link ScalarType}, or {@link ScalarType#STRING} if none of the
     *         supported formats match
     */
    static ScalarType detect(String value) {
        Objects.requireNonNull(value, "StringFormatDetector.detect: parameter `value` must not be null");

        int len = value.length();
        if (len == 10 && DATE.matcher(value).matches()) {
            return ScalarType.DATE;
        }
        if (len >= 20 && DATE_TIME.matcher(value).matches()) {
            return ScalarType.DATE_TIME;
        }
        if (len == 36 && UUID_PATTERN.matcher(value).matches()) {
            return ScalarType.UUID;
        }
        return ScalarType.STRING;
    }
}
