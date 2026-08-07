package com.github.ssullivan;

import com.github.ssullivan.analyze.JsonSchemaAnalyzer;
import com.github.ssullivan.analyze.SchemaMerger;
import com.github.ssullivan.jackson.Json;
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
        CommandLine commandLine = new CommandLine(new JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute(args);

        if (exitCode == 0) {
            JsonType result = commandLine.getExecutionResult();
            System.out.println(Json.MAPPER.writeValueAsString(result));
        }

        System.exit(exitCode);
    }
}
