package io.github.ssullivan;

import io.github.ssullivan.analyze.JsonSchemaAnalyzer;
import io.github.ssullivan.analyze.SchemaMerger;
import io.github.ssullivan.format.LlmWriter;
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
    /**
     * Reports the version stamped into the shaded jar's manifest, so {@code --version} can't
     * drift out of sync with the POM. Falls back to {@code dev} when running from
     * {@code target/classes}, where there is no manifest to read.
     */
    static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            String version = Main.class.getPackage().getImplementationVersion();
            return new String[]{"json-analyze " + (version != null ? version : "dev")};
        }
    }

    @CommandLine.Command(name = "json-analyze",
            mixinStandardHelpOptions = true,
            versionProvider = VersionProvider.class,
            description = "Describes the fields and datatypes in a JSON document.")
    static class JsonSchemaAnalyzeCommand implements Callable<JsonType> {
        @CommandLine.Option(names = {"-i", "--input-file"}, description = "The JSON file to analyze; reads from stdin if omitted")
        private File file;

        @CommandLine.Option(names = {"-m", "--merge-samples"},
                description = "Treat each element of a top-level JSON array as one sample and merge them into a single shape")
        private boolean mergeSamples;

        @CommandLine.Option(names = {"-s", "--json-schema"},
                description = "Print the shape as a JSON Schema (draft-07) document instead of the default compact notation")
        boolean jsonSchema;

        @CommandLine.Option(names = {"-l", "--llm"},
                description = "Print the shape in a compact, punctuation-free notation designed to be pasted into an LLM prompt cheaply, instead of the default compact notation")
        boolean llm;

        @Override
        public JsonType call() throws Exception {
            if (jsonSchema && llm) {
                throw new IllegalArgumentException("--json-schema and --llm cannot be used together");
            }

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

        // --help and --version also exit 0, but skip call() entirely and so leave no result
        // behind; without this guard they would print a trailing "null".
        JsonType result = commandLine.getExecutionResult();
        if (exitCode == 0 && result != null) {
            if (command.llm) {
                System.out.println(LlmWriter.write(result));
            } else {
                Object output = command.jsonSchema ? JsonSchemaWriter.toJsonSchema(result) : result;
                System.out.println(Json.MAPPER.writeValueAsString(output));
            }
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
