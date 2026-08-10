package org.identigon.effigies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscoverCommandTest {

    private record Result(int code, String out, String err) {}

    private static Result invoke(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = DiscoverCommand.execute(args,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void missingArgsPrintsUsage() {
        Result r = invoke();
        assertEquals(EffigiesCli.EXIT_USAGE, r.code());
        assertTrue(r.err().contains("Usage: discover"));
    }

    @Test
    void missingPasswordEnvVarFailsWithUsage() {
        // IDENTIGON_SOURCE_PASSWORD is not expected to be set in a test environment.
        Result r = invoke("--source-url", "jdbc:h2:mem:unused", "--source-user", "sa");
        assertEquals(EffigiesCli.EXIT_USAGE, r.code());
        assertTrue(r.err().contains("IDENTIGON_SOURCE_PASSWORD"));
    }

    @Test
    void printsDiscoveredTablesAndColumnMetadata() throws SQLException {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE customer (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            stmt.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, "
                + "customer_id BIGINT REFERENCES customer(id))");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = DiscoverCommand.run(new SimpleDataSource(url, "sa", ""),
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(0, code, "err: " + err.toString(StandardCharsets.UTF_8));
        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Table: CUSTOMER") || output.contains("Table: customer"), output);
        assertTrue(output.contains("pk"), "the primary key must be annotated: " + output);
        assertTrue(output.contains("fk -> "), "the foreign key must be annotated: " + output);
    }

    @Test
    void reportsAConnectionFailureRatherThanThrowing() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = DiscoverCommand.run(new SimpleDataSource("jdbc:no-such-dialect://nowhere", "u", "p"),
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(1, code);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Error: "));
    }
}
