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
    void testVersionProviderFallsBackWhenNoManifestIsPresent() {
        // Tests run from target/classes, where there is no jar manifest to read.
        String[] version = new Main.VersionProvider().getVersion();

        assertEquals(1, version.length);
        assertTrue(version[0].startsWith("json-analyze "));
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
