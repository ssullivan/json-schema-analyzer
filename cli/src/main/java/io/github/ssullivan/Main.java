package io.github.ssullivan;

import io.github.ssullivan.analyze.JsonSchemaAnalyzer;
import io.github.ssullivan.analyze.SchemaMerger;
import io.github.ssullivan.jackson.Json;
import io.github.ssullivan.jackson.JsonSchemaWriter;
import io.github.ssullivan.types.*;
import picocli.CommandLine;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

public class Main {
    @CommandLine.Command(name = "json-analyze")
    static class JsonSchemaAnalyzeCommand implements Callable<JsonType> {
        @CommandLine.Option(names = {"-i", "--input-file"}, description = "The JSON file to analyze; reads from stdin if omitted")
        private File file;

        @CommandLine.Option(names = {"-m", "--merge-samples"},
                description = "Treat each element of a top-level JSON array as one sample and merge them into a single shape")
        private boolean mergeSamples;

        @CommandLine.Option(names = {"-s", "--json-schema"},
                description = "Print the shape as a JSON Schema (draft-07) document instead of the default compact notation")
        boolean jsonSchema;

        @Override
        public JsonType call() throws Exception {
            try (InputStream inputStream = file != null ? new FileInputStream(file) : System.in) {
                JsonSchemaAnalyzer analyzer = new JsonSchemaAnalyzer();
                JsonType result = analyzer.parse(inputStream);

                if (mergeSamples && result instanceof ArrayType arrayType) {
                    return arrayType.getFields().stream()
                            .reduce(SchemaMerger::merge)
                            .orElse(arrayType);
                }

                return result;
            }
        }
    }

    public static void main(String[] args) throws IOException {
        JsonSchemaAnalyzeCommand command = new JsonSchemaAnalyzeCommand();
        CommandLine commandLine = new CommandLine(command);
        commandLine.setExecutionExceptionHandler((exception, cl, parseResult) -> {
            System.err.println(errorLine(exception));
            return CommandLine.ExitCode.SOFTWARE;
        });
        int exitCode = commandLine.execute(args);

        if (exitCode == 0) {
            JsonType result = commandLine.getExecutionResult();
            Object output = command.jsonSchema ? JsonSchemaWriter.toJsonSchema(result) : result;
            System.out.println(Json.MAPPER.writeValueAsString(output));
        }

        System.exit(exitCode);
    }

    /**
     * Formats an execution-time failure as a single clean line — no stack trace. Every exception
     * that reaches here already carries a clear message ({@link JsonSchemaAnalyzer}'s own
     * malformed/unsupported-input errors, {@link java.io.FileNotFoundException}'s built-in path
     * detail), so one uniform format covers every case without needing to classify exception
     * types.
     */
    static String errorLine(Throwable exception) {
        String message = exception.getMessage();
        return "Error: " + (message != null ? message : exception.getClass().getSimpleName());
    }
}
