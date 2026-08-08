package io.github.ssullivan;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the shaded jar as a real subprocess, covering the parts of the build that only exist once
 * packaged: the {@code Main-Class} entry that makes {@code java -jar} work at all, and the
 * filtered {@code version.properties} resource surviving into the shaded artifact. Either could
 * break silently while every unit test still passed.
 * <p>
 * Skips rather than fails when the jar hasn't been packaged yet, so {@code mvn test} (which runs
 * before {@code package}) stays green; {@code mvn verify} is what actually exercises this.
 */
class ShadedJarIT {

    private static Path jar;

    @BeforeAll
    static void locateJar() throws IOException {
        Path targetDir = Path.of(System.getProperty("buildDirectory", "target"));
        if (!Files.isDirectory(targetDir)) {
            return;
        }
        try (Stream<Path> entries = Files.list(targetDir)) {
            jar = entries
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("json-schema-analyzer-")
                                && name.endsWith(".jar")
                                && !name.contains("sources")
                                && !name.contains("javadoc")
                                && !name.contains("original");
                    })
                    .max(Comparator.comparing(Path::toString))
                    .orElse(null);
        }
    }

    @Test
    void testVersionReportsTheRealVersionFromThePackagedResource() throws Exception {
        // Shading rewrites the jar's contents, so this is where a lost or unfiltered
        // version.properties would show up — as a "dev" fallback instead of a real version.
        Result result = run("--version");

        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().startsWith("json-analyze "), result.output());
        assertTrue(result.output().contains(expectedVersion()),
                "expected the POM version " + expectedVersion() + " but got: " + result.output());
    }

    @Test
    void testHelpExitsZero() throws Exception {
        Result result = run("--help");

        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("Usage: json-analyze"), result.output());
    }

    @Test
    void testAnalyzesStdinEndToEnd() throws Exception {
        Result result = run("-l");

        assertEquals(0, result.exitCode(), result.output());
        assertEquals("id: integer\nname: string", result.output().strip());
    }

    /** Derived from the jar's filename, which the build stamps with the project version. */
    private static String expectedVersion() {
        String name = jar.getFileName().toString();
        return name.substring("json-schema-analyzer-".length(), name.length() - ".jar".length());
    }

    private static Result run(String... args) throws Exception {
        Assumptions.assumeTrue(jar != null, "shaded jar not packaged yet; run `mvn verify`");

        List<String> command = Stream.concat(
                Stream.of(ProcessHandle.current().info().command().orElse("java"), "-jar", jar.toString()),
                Stream.of(args)).toList();

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        process.getOutputStream().write("{\"id\": 1, \"name\": \"a\"}".getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "the jar did not exit within 60s");
        return new Result(process.exitValue(), output);
    }

    private record Result(int exitCode, String output) {
    }
}
