package org.identigon.effigies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunCommandTest {

    private record Result(int code, String out, String err) {}

    private static Result invoke(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = RunCommand.execute(args,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void missingArgsPrintsUsage() {
        Result r = invoke();
        assertEquals(EffigiesCli.EXIT_USAGE, r.code());
        assertTrue(r.err().contains("Usage: run"));
    }

    @Test
    void missingSourcePasswordEnvVarFailsWithUsage() {
        Result r = invoke("--source-url", "jdbc:h2:mem:unused", "--source-user", "sa",
            "--target-url", "jdbc:h2:mem:unused", "--target-user", "sa");
        assertEquals(EffigiesCli.EXIT_USAGE, r.code());
        assertTrue(r.err().contains("IDENTIGON_SOURCE_PASSWORD"));
    }

    @Test
    void runsAFullPipelineAgainstRealDatabases(@TempDir Path tempDir) throws Exception {
        String sourceUrl = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        String targetUrl = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

        try (Connection conn = DriverManager.getConnection(sourceUrl, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE person (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            stmt.execute("INSERT INTO person VALUES (1, 'Alice'), (2, 'Bob')");
        }
        try (Connection conn = DriverManager.getConnection(targetUrl, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE person (id BIGINT PRIMARY KEY, name VARCHAR(100))");
        }

        Path policy = tempDir.resolve("policy.yaml");
        Files.writeString(policy, """
            autoInfer: false
            tables:
              PERSON:
                columns:
                  ID:
                    role: PRIMARY_KEY
                    surrogateStrategy: SEQUENTIAL_LONG
                  NAME:
                    role: PAYLOAD
            """);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = RunCommand.run(
            new SimpleDataSource(sourceUrl, "sa", ""),
            new SimpleDataSource(targetUrl, "sa", ""),
            policy, "ephemeral", null, null,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));

        String outStr = out.toString(StandardCharsets.UTF_8);
        assertEquals(0, code, "err: " + err.toString(StandardCharsets.UTF_8) + " / out: " + outStr);
        assertTrue(outStr.contains("Pipeline completed successfully"), outStr);
        assertTrue(outStr.contains("Rows processed: 2"), outStr);
        assertTrue(outStr.contains("DPIA artefact written to"), outStr);

        assertEquals(2, countRows(targetUrl), "both source rows must have been cloned to the target");
        // RunCommand hardcodes the DPIA report path relative to the working directory, not the
        // policy file's location, so this is where it actually lands regardless of tempDir.
        assertTrue(Files.exists(Path.of("./dpia-report.html")),
            "DPIA HTML report should have been written to the working directory");

        // Clean up the DPIA artefacts this test writes into the actual working directory (RunCommand
        // hardcodes "./dpia-report.*", not the temp dir).
        Files.deleteIfExists(Path.of("./dpia-report.html"));
        Files.deleteIfExists(Path.of("./dpia-report.json"));
        Files.deleteIfExists(Path.of("./dpia-report.md"));
    }

    @Test
    void tooShortAPersistentSaltFailsBeforeEitherDatabaseIsTouched(@TempDir Path tempDir) throws Exception {
        Path policy = tempDir.resolve("policy.yaml");
        Files.writeString(policy, """
            autoInfer: false
            tables:
              PERSON:
                columns:
                  ID:
                    role: PRIMARY_KEY
                    surrogateStrategy: SEQUENTIAL_LONG
            """);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        // Neither database exists at this URL - if the salt-length check ran late (inside pipeline
        // construction, after a connection is opened), this would fail with a connection error
        // instead of the salt-length message.
        int code = RunCommand.run(
            new SimpleDataSource("jdbc:no-such-dialect://nowhere", "u", "p"),
            new SimpleDataSource("jdbc:no-such-dialect://nowhere", "u", "p"),
            policy, "persistent", "short".getBytes(StandardCharsets.UTF_8), null,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(1, code);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("IDENTIGON_SALT must be at least"),
            err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void reportsAPipelineFailureRatherThanThrowing(@TempDir Path tempDir) throws Exception {
        Path policy = tempDir.resolve("policy.yaml");
        Files.writeString(policy, """
            autoInfer: false
            tables:
              PERSON:
                columns:
                  ID:
                    role: PRIMARY_KEY
                    surrogateStrategy: SEQUENTIAL_LONG
            """);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        // Neither database exists at this URL -- the pipeline must fail cleanly, not throw out of
        // run(), and the error message must actually say something (not swallow it to "").
        int code = RunCommand.run(
            new SimpleDataSource("jdbc:no-such-dialect://nowhere", "u", "p"),
            new SimpleDataSource("jdbc:no-such-dialect://nowhere", "u", "p"),
            policy, "ephemeral", null, null,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(1, code);
        String errStr = err.toString(StandardCharsets.UTF_8);
        assertTrue(errStr.contains("Error executing pipeline: "), errStr);
        assertTrue(errStr.length() > "Error executing pipeline: ".length(), "must not swallow the exception detail: " + errStr);
    }

    private static long countRows(String url) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM person")) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
