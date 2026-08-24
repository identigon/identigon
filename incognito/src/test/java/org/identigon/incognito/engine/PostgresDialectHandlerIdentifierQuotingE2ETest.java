package org.identigon.incognito.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.identigon.incognito.TestPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Regression test: {@link PostgresDialectHandler#preLoadTable} (owner-mode fallback),
 * {@link PostgresDialectHandler#postLoadTable}, and {@link PostgresDialectHandler#resyncSequence}
 * must quote identifiers, not just {@link PostgresDialectHandler#buildInsertSql} and the FK-drop/
 * recreate pair ({@link PostgresDialectHandlerFkQuotingE2ETest}) - a mixed-case table/column name
 * exercises exactly the gap those two didn't cover.
 *
 * <p>Requires Docker; skips gracefully otherwise.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresDialectHandlerIdentifierQuotingE2ETest {

    private PostgreSQLContainer pg;
    private Connection ownerConn;
    private Connection nonSuperuserConn;

    @BeforeAll
    void setUp() throws Exception {
        boolean dockerAvailable;
        try {
            dockerAvailable = org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable, "Docker not available - skipping dialect-handler E2E");

        pg = new PostgreSQLContainer(TestPostgres.IMAGE)
            .withDatabaseName("identifier_quoting").withUsername("test").withPassword("test");
        pg.start();

        ownerConn = DriverManager.getConnection(pg.getJdbcUrl(), "test", "test");
        ownerConn.setAutoCommit(true);
        try (Statement stmt = ownerConn.createStatement()) {
            // Mixed-case table and identity PK column: PostgreSQL folds unquoted identifiers to
            // lowercase, so both are only reachable when properly double-quoted.
            stmt.execute("""
                CREATE TABLE "MixedCaseWidget" (
                    "Id"   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    "Name" TEXT
                );
                """);
            stmt.execute("INSERT INTO \"MixedCaseWidget\" (\"Name\") VALUES ('a'), ('b')");
            // Move the sequence out of step with the data, so resyncSequence has something to fix.
            stmt.execute("SELECT setval(pg_get_serial_sequence('\"MixedCaseWidget\"', 'Id'), 1)");

            stmt.execute("DROP ROLE IF EXISTS quoting_owner");
            stmt.execute("CREATE ROLE quoting_owner LOGIN PASSWORD 'x' NOSUPERUSER");
            stmt.execute("GRANT CONNECT ON DATABASE identifier_quoting TO quoting_owner");
            stmt.execute("ALTER TABLE \"MixedCaseWidget\" OWNER TO quoting_owner");
        }

        nonSuperuserConn = DriverManager.getConnection(pg.getJdbcUrl(), "quoting_owner", "x");
        nonSuperuserConn.setAutoCommit(true);
    }

    @AfterAll
    void tearDown() throws SQLException {
        if (nonSuperuserConn != null) nonSuperuserConn.close();
        if (ownerConn != null) ownerConn.close();
        if (pg != null) pg.stop();
    }

    @Test
    void preLoadTableFallbackQuotesTheTableName() {
        Assumptions.assumeTrue(nonSuperuserConn != null, "Docker/PostgreSQL not available");
        PostgresDialectHandler handler = new PostgresDialectHandler();

        // quoting_owner is not a superuser, so SET session_replication_role fails (42501) and this
        // falls back to owner-mode ALTER TABLE ... DISABLE TRIGGER USER, which must quote the
        // mixed-case table name to find it at all.
        assertDoesNotThrow(() -> handler.preLoadTable(nonSuperuserConn, "MixedCaseWidget"));
    }

    @Test
    void postLoadTableQuotesTheTableName() {
        Assumptions.assumeTrue(ownerConn != null, "Docker/PostgreSQL not available");
        PostgresDialectHandler handler = new PostgresDialectHandler();

        assertDoesNotThrow(() -> handler.postLoadTable(ownerConn, "MixedCaseWidget"));
    }

    @Test
    void resyncSequenceQuotesTheTableAndColumn() throws SQLException {
        Assumptions.assumeTrue(ownerConn != null, "Docker/PostgreSQL not available");
        PostgresDialectHandler handler = new PostgresDialectHandler();

        handler.resyncSequence(ownerConn, "MixedCaseWidget", "Id");

        // The sequence was deliberately desynced to 1 in setUp(); a correct resync (which needs
        // MAX("Id") FROM "MixedCaseWidget" to actually find the table/column) advances it past the
        // two existing rows, so the next identity insert doesn't collide.
        try (Statement stmt = ownerConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT nextval(pg_get_serial_sequence('\"MixedCaseWidget\"', 'Id'))")) {
            rs.next();
            assertEquals(3, rs.getLong(1), "sequence must resync past the two existing rows, not stay at 1");
        }
    }
}
