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
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidateCommandTest {

    private record Result(int code, String out, String err) {}

    private static Result invoke(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = ValidateCommand.execute(args,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void missingArgsPrintsUsage() {
        Result r = invoke();
        assertEquals(EffigiesCli.EXIT_USAGE, r.code());
        assertTrue(r.err().contains("Usage: validate"));
    }

    @Test
    void helpFlagPrintsUsageAndSucceedsWithoutAnyOtherArgRequired() {
        Result r = invoke("--help");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("Usage: validate"));
    }

    @Test
    void missingPasswordEnvVarFailsWithUsage() {
        Result r = invoke("--source-url", "jdbc:h2:mem:unused", "--source-user", "sa");
        assertEquals(EffigiesCli.EXIT_USAGE, r.code());
        assertTrue(r.err().contains("IDENTIGON_SOURCE_PASSWORD"));
    }

    @Test
    void aNonExistentPolicyFileFailsRatherThanThrowing(@TempDir Path tempDir) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = ValidateCommand.run(new SimpleDataSource("jdbc:no-such-dialect://nowhere", "u", "p"),
            tempDir.resolve("no-such-policy.yaml"),
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(1, code);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Error: "),
            err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void aValidPolicyPassesAgainstItsSchema(@TempDir Path tempDir) throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
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
        int code = ValidateCommand.run(new SimpleDataSource(url, "sa", ""), policy,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(0, code, "err: " + err.toString(StandardCharsets.UTF_8));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Policy is valid against 1 discovered table"),
            out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void anInvalidPolicyFailsWithTheEnginesFailClosedDiagnostic(@TempDir Path tempDir) throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            // NAME is left unclassified in the policy below - must fail closed.
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
            """);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = ValidateCommand.run(new SimpleDataSource(url, "sa", ""), policy,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(1, code);
        String errStr = err.toString(StandardCharsets.UTF_8);
        assertTrue(errStr.contains("Fail-closed"), errStr);
        assertTrue(errStr.contains("NAME"), errStr);
    }

    @Test
    void reportsASchemaInspectionFailureRatherThanThrowing(@TempDir Path tempDir) throws Exception {
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
        int code = ValidateCommand.run(new SimpleDataSource("jdbc:no-such-dialect://nowhere", "u", "p"), policy,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(1, code);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Error: "),
            err.toString(StandardCharsets.UTF_8));
    }
}
