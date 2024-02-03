package com.github.ssullivan;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.ssullivan.analyze.JsonSchemaAnalyzer;
import com.github.ssullivan.jackson.Json;
import com.github.ssullivan.types.*;
import picocli.CommandLine;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;

public class Main {
    @CommandLine.Command(name = "json-analyze")
    private static class JsonSchemaAnalyzeCommand implements Callable<JsonType> {
        @CommandLine.Option(names = {"-i", "--input-file"}, description = "The JSON file to analyze", required = true)
        private File file;

        @Override
        public JsonType call() throws Exception {
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                JsonSchemaAnalyzer analyzer = new JsonSchemaAnalyzer();
                return analyzer.parse(fileInputStream);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        CommandLine commandLine = new CommandLine(new JsonSchemaAnalyzeCommand());
        int exitCode = commandLine.execute(args);
        JsonType result = commandLine.getExecutionResult();


        System.out.println(Json.MAPPER.writeValueAsString(result));

        System.exit(exitCode);
    }



}
