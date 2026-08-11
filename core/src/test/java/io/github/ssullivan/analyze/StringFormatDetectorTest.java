package io.github.ssullivan.analyze;

import io.github.ssullivan.types.ScalarType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringFormatDetectorTest {

    @Test
    void testDetectsValidDate() {
        assertEquals(ScalarType.DATE, StringFormatDetector.detect("2023-01-15"));
    }

    @Test
    void testDetectsValidDateTimeWithUppercaseZ() {
        assertEquals(ScalarType.DATE_TIME, StringFormatDetector.detect("2023-01-15T10:30:00Z"));
    }

    @Test
    void testDetectsValidDateTimeWithLowercaseTAndZ() {
        assertEquals(ScalarType.DATE_TIME, StringFormatDetector.detect("2023-01-15t10:30:00z"));
    }

    @Test
    void testDetectsValidDateTimeWithFractionalSeconds() {
        assertEquals(ScalarType.DATE_TIME, StringFormatDetector.detect("2023-01-15T10:30:00.123456Z"));
    }

    @Test
    void testDetectsValidDateTimeWithPositiveOffset() {
        assertEquals(ScalarType.DATE_TIME, StringFormatDetector.detect("2023-01-15T10:30:00+05:30"));
    }

    @Test
    void testDetectsValidDateTimeWithNegativeOffset() {
        assertEquals(ScalarType.DATE_TIME, StringFormatDetector.detect("2023-01-15T10:30:00-08:00"));
    }

    @Test
    void testDetectsLowercaseUuid() {
        assertEquals(ScalarType.UUID, StringFormatDetector.detect("550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    void testDetectsUppercaseUuid() {
        assertEquals(ScalarType.UUID, StringFormatDetector.detect("550E8400-E29B-41D4-A716-446655440000"));
    }

    @Test
    void testPlainStringIsNotDetected() {
        assertEquals(ScalarType.STRING, StringFormatDetector.detect("hello world"));
    }

    @Test
    void testDateWithSlashSeparatorsIsNotDetected() {
        assertEquals(ScalarType.STRING, StringFormatDetector.detect("2023/01/15"));
    }

    @Test
    void testDateTimeMissingOffsetIsNotDetected() {
        // RFC 3339 requires an offset; a bare local time with no zone info isn't a match
        assertEquals(ScalarType.STRING, StringFormatDetector.detect("2023-01-15T10:30:00"));
    }

    @Test
    void testUuidMissingHyphensIsNotDetected() {
        assertEquals(ScalarType.STRING, StringFormatDetector.detect("550e8400e29b41d4a716446655440000"));
    }

    @Test
    void testUuidWrongGroupLengthIsNotDetected() {
        assertEquals(ScalarType.STRING, StringFormatDetector.detect("550e840-e29b-41d4-a716-446655440000"));
    }

    @Test
    void testEmptyStringIsNotDetected() {
        assertEquals(ScalarType.STRING, StringFormatDetector.detect(""));
    }

    @Test
    void testDateStructurallyValidButCalendarInvalidStillDetected() {
        // No calendar validation is performed by design -- shape, not semantic correctness
        assertEquals(ScalarType.DATE, StringFormatDetector.detect("2023-13-45"));
    }

    @Test
    void testDetectRejectsNull() {
        assertThrows(NullPointerException.class, () -> StringFormatDetector.detect(null));
    }
}
