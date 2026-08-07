package com.github.ssullivan;

import com.github.ssullivan.analyze.JsonSchemaAnalyzer;
import com.github.ssullivan.analyze.SchemaMerger;
import com.github.ssullivan.jackson.Json;
import com.github.ssullivan.jackson.JsonSchemaWriter;
import com.github.ssullivan.types.*;
import picocli.CommandLine;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.Callable;

public class Main {
    @CommandLine.Command(name = "json-analyze")
    static class JsonSchemaAnalyzeCommand implements Callable<JsonType> {
        @CommandLine.Option(names = {"-i", "--input-file"}, description = "The JSON file to analyze", required = true)
        private File file;

        @CommandLine.Option(names = {"-m", "--merge-samples"},
                description = "Treat each element of a top-level JSON array as one sample and merge them into a single shape")
        private boolean mergeSamples;

        @CommandLine.Option(names = {"-s", "--json-schema"},
                description = "Print the shape as a JSON Schema (draft-07) document instead of the default compact notation")
        boolean jsonSchema;

        @Override
        public JsonType call() throws Exception {
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                JsonSchemaAnalyzer analyzer = new JsonSchemaAnalyzer();
                JsonType result = analyzer.parse(fileInputStream);

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
        int exitCode = commandLine.execute(args);

        if (exitCode == 0) {
            JsonType result = commandLine.getExecutionResult();
            Object output = command.jsonSchema ? JsonSchemaWriter.toJsonSchema(result) : result;
            System.out.println(Json.MAPPER.writeValueAsString(output));
        }

        System.exit(exitCode);
    }
}
