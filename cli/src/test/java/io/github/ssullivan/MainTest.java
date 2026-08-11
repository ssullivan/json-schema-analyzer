package io.github.ssullivan;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ssullivan.jackson.JsonSchemaWriter;
import io.github.ssullivan.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    @Test
    void testAnalyzesSingleFile(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir, "{\"id\": 1}");

        CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute("-i", file.toString());

        assertEquals(0, exitCode);
        assertEquals(ObjectType.of("id", ScalarType.INTEGER), commandLine.getExecutionResult());
    }

    @Test
    void testMergeSamplesFlagMergesArrayElements(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir, "[{\"id\": 1, \"name\": \"Alice\"}, {\"id\": 2}]");

        CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute("-i", file.toString(), "-m");

        assertEquals(0, exitCode);
        JsonType result = commandLine.getExecutionResult();
        assertInstanceOf(ObjectType.class, result);
        ObjectType objectType = (ObjectType) result;
        assertFalse(objectType.isOptional("id"));
        assertTrue(objectType.isOptional("name"));
    }

    @Test
    void testWithoutMergeFlagArrayElementsStayIndependent(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir, "[{\"id\": 1}, {\"id\": 2, \"name\": \"Alice\"}]");

        CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute("-i", file.toString());

        assertEquals(0, exitCode);
        JsonType result = commandLine.getExecutionResult();
        assertInstanceOf(ArrayType.class, result);
        assertEquals(2, ((ArrayType) result).getFields().size());
    }

    @Test
    void testJsonLinesFlagMergesEachLineAsASample(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir, "{\"id\": 1, \"name\": \"Alice\"}\n{\"id\": 2}\n");

        CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute("-i", file.toString(), "-j");

        assertEquals(0, exitCode);
        JsonType result = commandLine.getExecutionResult();
        assertInstanceOf(ObjectType.class, result);
        ObjectType objectType = (ObjectType) result;
        assertFalse(objectType.isOptional("id"));
        assertTrue(objectType.isOptional("name"));
    }

    @Test
    void testJsonLinesFlagReadsFromStdin() {
        InputStream originalStdin = System.in;
        System.setIn(new ByteArrayInputStream("{\"id\": 1}\n{\"id\": 2}\n".getBytes(StandardCharsets.UTF_8)));
        try {
            CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
            int exitCode = commandLine.execute("-j");

            assertEquals(0, exitCode);
            assertEquals(ObjectType.of("id", ScalarType.INTEGER), commandLine.getExecutionResult());
        } finally {
            System.setIn(originalStdin);
        }
    }

    @Test
    void testDetectFormatsFlagDetectsDate(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir, "{\"createdAt\": \"2023-01-15\"}");

        CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute("-i", file.toString(), "-f");

        assertEquals(0, exitCode);
        JsonType result = commandLine.getExecutionResult();
        assertInstanceOf(ObjectType.class, result);
        assertEquals(ScalarType.DATE, ((ObjectType) result).getFields().get("createdAt"));
    }

    @Test
    void testWithoutDetectFormatsFlagStringsStayPlain(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir, "{\"createdAt\": \"2023-01-15\"}");

        CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute("-i", file.toString());

        assertEquals(0, exitCode);
        JsonType result = commandLine.getExecutionResult();
        assertInstanceOf(ObjectType.class, result);
        assertEquals(ScalarType.STRING, ((ObjectType) result).getFields().get("createdAt"));
    }

    @Test
    void testDetectFormatsFlagComposesWithMergeFlag(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir,
                "[{\"createdAt\": \"2023-01-15\"}, {\"createdAt\": \"not a date\"}]");

        CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute("-i", file.toString(), "-f", "-m");

        assertEquals(0, exitCode);
        JsonType result = commandLine.getExecutionResult();
        assertInstanceOf(ObjectType.class, result);
        // Merged samples disagree on the field's format, so it falls back to plain "string"
        // rather than reporting a date|string union
        assertEquals(ScalarType.STRING, ((ObjectType) result).getFields().get("createdAt"));
    }

    @Test
    void testDetectFormatsFlagComposesWithJsonSchemaFlag(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir, "{\"createdAt\": \"2023-01-15\"}");

        Main.JsonSchemaAnalyzeCommand command = new Main.JsonSchemaAnalyzeCommand();
        CommandLine commandLine = new CommandLine(command);
        int exitCode = commandLine.execute("-i", file.toString(), "-f", "-s");

        assertEquals(0, exitCode);
        JsonType result = commandLine.getExecutionResult();

        ObjectNode schema = JsonSchemaWriter.toJsonSchema(result);
        assertEquals("date", schema.get("properties").get("createdAt").get("format").asText());
    }

    @Test
    void testDetectFormatsFlagParsesWithoutAffectingCallResultWhenCombinedWithLlm(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir, "{\"createdAt\": \"2023-01-15\"}");

        Main.JsonSchemaAnalyzeCommand command = new Main.JsonSchemaAnalyzeCommand();
        CommandLine commandLine = new CommandLine(command);
        int exitCode = commandLine.execute("-i", file.toString(), "-f", "-l");

        assertEquals(0, exitCode);
        assertTrue(command.llm);
        assertEquals(ObjectType.of("createdAt", ScalarType.DATE), commandLine.getExecutionResult());
    }

    @Test
    void testDetectEnumsFlagComposesWithMergeFlag(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir,
                "[{\"status\": \"open\"}, {\"status\": \"closed\"}, {\"status\": \"pending\"}]");

        CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute("-i", file.toString(), "-m", "-e");

        assertEquals(0, exitCode);
        JsonType result = commandLine.getExecutionResult();
        assertInstanceOf(ObjectType.class, result);
        JsonType statusType = ((ObjectType) result).getFields().get("status");
        assertInstanceOf(EnumType.class, statusType);
        assertEquals(Set.of("open", "closed", "pending"), ((EnumType) statusType).getValues());
    }

    @Test
    void testWithoutDetectEnumsFlagStringsStayPlain(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir,
                "[{\"status\": \"open\"}, {\"status\": \"closed\"}]");

        CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute("-i", file.toString(), "-m");

        assertEquals(0, exitCode);
        JsonType result = commandLine.getExecutionResult();
        assertInstanceOf(ObjectType.class, result);
        assertEquals(ScalarType.STRING, ((ObjectType) result).getFields().get("status"));
    }

    @Test
    void testDetectEnumsFlagFallsBackToPlainStringPastCap(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir,
                "[{\"status\":\"a\"},{\"status\":\"b\"},{\"status\":\"c\"},"
                        + "{\"status\":\"d\"},{\"status\":\"e\"},{\"status\":\"f\"}]");

        CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute("-i", file.toString(), "-m", "-e");

        assertEquals(0, exitCode);
        JsonType result = commandLine.getExecutionResult();
        assertInstanceOf(ObjectType.class, result);
        assertEquals(ScalarType.STRING, ((ObjectType) result).getFields().get("status"));
    }

    @Test
    void testDetectEnumsFlagComposesWithJsonSchemaFlag(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir,
                "[{\"status\": \"open\"}, {\"status\": \"closed\"}]");

        Main.JsonSchemaAnalyzeCommand command = new Main.JsonSchemaAnalyzeCommand();
        CommandLine commandLine = new CommandLine(command);
        int exitCode = commandLine.execute("-i", file.toString(), "-m", "-e", "-s");

        assertEquals(0, exitCode);
        JsonType result = commandLine.getExecutionResult();

        ObjectNode schema = JsonSchemaWriter.toJsonSchema(result);
        ObjectNode statusSchema = (ObjectNode) schema.get("properties").get("status");
        assertEquals("string", statusSchema.get("type").asText());
        assertTrue(statusSchema.get("enum").isArray());
        assertEquals(2, statusSchema.get("enum").size());
    }

    @Test
    void testDetectEnumsFlagComposesWithLlmFlag(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir,
                "[{\"status\": \"open\"}, {\"status\": \"closed\"}]");

        Main.JsonSchemaAnalyzeCommand command = new Main.JsonSchemaAnalyzeCommand();
        CommandLine commandLine = new CommandLine(command);
        int exitCode = commandLine.execute("-i", file.toString(), "-m", "-e", "-l");

        assertEquals(0, exitCode);
        assertTrue(command.llm);
        JsonType result = commandLine.getExecutionResult();
        assertInstanceOf(ObjectType.class, result);
        assertEquals(EnumType.of("open", "closed"), ((ObjectType) result).getFields().get("status"));
    }

    @Test
    void testDetectEnumsFlagComposesWithJsonLinesFlag(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir, "{\"status\": \"open\"}\n{\"status\": \"closed\"}\n");

        CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute("-i", file.toString(), "-j", "-e");

        assertEquals(0, exitCode);
        JsonType result = commandLine.getExecutionResult();
        assertInstanceOf(ObjectType.class, result);
        assertEquals(EnumType.of("open", "closed"), ((ObjectType) result).getFields().get("status"));
    }

    @Test
    void testDetectEnumsFlagAndDetectFormatsFlagComposeIndependently(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir,
                "[{\"status\": \"open\", \"id\": \"550e8400-e29b-41d4-a716-446655440000\"}, "
                        + "{\"status\": \"closed\", \"id\": \"6ba7b810-9dad-11d1-80b4-00c04fd430c8\"}]");

        CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute("-i", file.toString(), "-m", "-e", "-f");

        assertEquals(0, exitCode);
        JsonType result = commandLine.getExecutionResult();
        assertInstanceOf(ObjectType.class, result);
        assertEquals(EnumType.of("open", "closed"), ((ObjectType) result).getFields().get("status"));
        assertEquals(ScalarType.UUID, ((ObjectType) result).getFields().get("id"));
    }

    @Test
    void testMergeFlagOnNonArrayRootLeavesResultUnchanged(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir, "{\"id\": 1}");

        CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute("-i", file.toString(), "-m");

        assertEquals(0, exitCode);
        assertEquals(ObjectType.of("id", ScalarType.INTEGER), commandLine.getExecutionResult());
    }

    @Test
    void testJsonSchemaFlagParsesWithoutAffectingCallResult(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir, "{\"id\": 1}");

        Main.JsonSchemaAnalyzeCommand command = new Main.JsonSchemaAnalyzeCommand();
        CommandLine commandLine = new CommandLine(command);
        int exitCode = commandLine.execute("-i", file.toString(), "-s");

        assertEquals(0, exitCode);
        assertTrue(command.jsonSchema);
        // -s only changes how main() renders the result, not what call() computes
        assertEquals(ObjectType.of("id", ScalarType.INTEGER), commandLine.getExecutionResult());
    }

    @Test
    void testJsonSchemaFlagComposesWithMergeFlag(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir, "[{\"id\": 1, \"name\": \"Alice\"}, {\"id\": 2}]");

        Main.JsonSchemaAnalyzeCommand command = new Main.JsonSchemaAnalyzeCommand();
        CommandLine commandLine = new CommandLine(command);
        int exitCode = commandLine.execute("-i", file.toString(), "-s", "-m");

        assertEquals(0, exitCode);
        assertTrue(command.jsonSchema);
        JsonType result = commandLine.getExecutionResult();
        assertInstanceOf(ObjectType.class, result);
        assertTrue(((ObjectType) result).isOptional("name"));

        // Reproduces exactly what Main.main() does with the call() result when -s is set
        ObjectNode schema = JsonSchemaWriter.toJsonSchema(result);
        assertEquals("object", schema.get("type").asText());
        assertEquals(1, schema.get("required").size());
        assertEquals("id", schema.get("required").get(0).asText());
    }

    @Test
    void testLlmFlagParsesWithoutAffectingCallResult(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir, "{\"id\": 1}");

        Main.JsonSchemaAnalyzeCommand command = new Main.JsonSchemaAnalyzeCommand();
        CommandLine commandLine = new CommandLine(command);
        int exitCode = commandLine.execute("-i", file.toString(), "-l");

        assertEquals(0, exitCode);
        assertTrue(command.llm);
        // -l only changes how main() renders the result, not what call() computes
        assertEquals(ObjectType.of("id", ScalarType.INTEGER), commandLine.getExecutionResult());
    }

    @Test
    void testJsonSchemaAndLlmFlagsTogetherFailFast(@TempDir Path tempDir) throws IOException {
        Path file = writeJson(tempDir, "{\"id\": 1}");

        CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute("-i", file.toString(), "-s", "-l");

        assertNotEquals(0, exitCode);
        assertNull(commandLine.getExecutionResult());
    }

    @Test
    void testMissingFileReturnsNonZeroExitAndNoResult(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.json");

        CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute("-i", missing.toString());

        assertNotEquals(0, exitCode);
        assertNull(commandLine.getExecutionResult());
    }

    @Test
    void testReadsFromStdinWhenInputFileOmitted() {
        InputStream originalStdin = System.in;
        System.setIn(new ByteArrayInputStream("{\"id\": 1}".getBytes(StandardCharsets.UTF_8)));
        try {
            CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
            int exitCode = commandLine.execute();

            assertEquals(0, exitCode);
            assertEquals(ObjectType.of("id", ScalarType.INTEGER), commandLine.getExecutionResult());
        } finally {
            System.setIn(originalStdin);
        }
    }

    @Test
    void testHelpExitsZeroWithNoResultToPrint() {
        // main() only prints when the result is non-null; --help exits 0 without running
        // call(), so a missing guard there would print a trailing "null" after the usage text.
        for (String flag : new String[]{"-h", "--help"}) {
            CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
            assertEquals(0, commandLine.execute(flag));
            assertNull(commandLine.getExecutionResult());
        }
    }

    @Test
    void testVersionExitsZeroWithNoResultToPrint() {
        for (String flag : new String[]{"-V", "--version"}) {
            CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
            assertEquals(0, commandLine.execute(flag));
            assertNull(commandLine.getExecutionResult());
        }
    }

    @Test
    void testVersionProviderReadsTheFilteredResource() {
        // Reads a build-filtered classpath resource rather than the jar manifest, so this works
        // from target/classes too — and, more to the point, inside a native binary, which has no
        // manifest at all.
        String[] version = new Main.VersionProvider().getVersion();

        assertEquals(1, version.length);
        assertTrue(version[0].startsWith("json-analyze "), version[0]);
        assertFalse(version[0].endsWith("dev"), "expected a real version, not the fallback: " + version[0]);
    }

    @Test
    void testErrorLineUsesExceptionMessage() {
        assertEquals("Error: something went wrong", Main.errorLine(new RuntimeException("something went wrong")));
    }

    @Test
    void testErrorLineFallsBackToClassNameWhenMessageIsNull() {
        assertEquals("Error: RuntimeException", Main.errorLine(new RuntimeException()));
    }

    private static Path writeJson(Path dir, String json) throws IOException {
        Path file = dir.resolve("input.json");
        Files.writeString(file, json);
        return file;
    }
}
