package com.github.ssullivan;

import com.github.ssullivan.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
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
    void testMissingFileReturnsNonZeroExitAndNoResult(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.json");

        CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute("-i", missing.toString());

        assertNotEquals(0, exitCode);
        assertNull(commandLine.getExecutionResult());
    }

    @Test
    void testMissingRequiredInputOptionFailsFastWithNonZeroExit() {
        CommandLine commandLine = new CommandLine(new Main.JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute();

        assertNotEquals(0, exitCode);
    }

    private static Path writeJson(Path dir, String json) throws IOException {
        Path file = dir.resolve("input.json");
        Files.writeString(file, json);
        return file;
    }
}
