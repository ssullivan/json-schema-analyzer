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
import java.util.Properties;
import java.util.concurrent.Callable;

/**
 * Command line entry point. Parses the arguments with picocli, runs the analysis, and prints the
 * resulting shape in whichever format the flags selected.
 */
public final class Main {

    private Main() {
    }

    /**
     * Reports the version from a build-filtered classpath resource, so {@code --version} can't
     * drift out of sync with the POM. A resource rather than the jar manifest because a native
     * binary has no manifest — reading {@code Implementation-Version} would report {@code dev}
     * there. Falls back to {@code dev} only if the resource is genuinely missing.
     */
    static class VersionProvider implements CommandLine.IVersionProvider {
        private static final String RESOURCE = "/io/github/ssullivan/version.properties";

        @Override
        public String[] getVersion() {
            return new String[]{"json-analyze " + readVersion()};
        }

        private static String readVersion() {
            try (InputStream in = Main.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    return "dev";
                }
                Properties properties = new Properties();
                properties.load(in);
                String version = properties.getProperty("version");
                return version == null || version.isBlank() ? "dev" : version;
            } catch (IOException e) {
                return "dev";
            }
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

        @CommandLine.Option(names = {"-j", "--jsonl"},
                description = "Read the input as newline-delimited JSON (NDJSON), treating each value as one sample and merging them into a single shape, like -m does for a JSON array")
        private boolean jsonLines;

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

                if (jsonLines) {
                    return analyzer.parseJsonLines(inputStream);
                }

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

    /**
     * Runs the CLI and exits the JVM with the resulting status code.
     *
     * @param args the command line arguments
     * @throws IOException if the rendered output cannot be written
     */
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
                System.out.println(Json.write(output));
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
