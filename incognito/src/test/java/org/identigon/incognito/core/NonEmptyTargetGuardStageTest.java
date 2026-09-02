package org.identigon.incognito.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.identigon.incognito.api.ColumnRole;
import org.identigon.incognito.api.IncognitoException;
import org.identigon.incognito.api.IncognitoPipeline;
import org.identigon.incognito.api.SurrogateStrategy;
import org.identigon.incognito.policy.AnonymisationPolicy;
import org.junit.jupiter.api.Test;

/**
 * {@code run} refuses a target where any policy-covered table already has rows, since a failed
 * run's compensation ({@link IncognitoCleanUpHandler}) deletes existing rows during clean-up, not
 * only the ones this run itself inserted (v3.1.0-era tutorial-feedback finding: the only warning
 * on this used to live in a documentation aside, not the tool). {@code --force}
 * (@code Builder.allowNonEmptyTarget()}) opts out. H2 in-memory, no Docker/Testcontainers needed -
 * this check never reads a fabricated value, only row counts.
 */
class NonEmptyTargetGuardStageTest {

    private static final String DDL = "CREATE TABLE CUSTOMERS (ID BIGINT PRIMARY KEY);";

    private DataSource freshDb(boolean seedARow) throws SQLException {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
                Statement stmt = conn.createStatement()) {
            stmt.execute(DDL);
            if (seedARow) {
                stmt.execute("INSERT INTO CUSTOMERS (ID) VALUES (999)");
            }
        }
        return new SimpleDataSource(url, "sa", "");
    }

    private AnonymisationPolicy policy() {
        return AnonymisationPolicy.builder()
            .table("CUSTOMERS", t -> t.column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG))
            .build();
    }

    @Test
    void nonEmptyTargetFailsClosedNamingTheTableAndRowCount() throws SQLException {
        DataSource src = freshDb(false);
        DataSource tgt = freshDb(true); // one pre-existing row, not put there by this run

        IncognitoException.ConfigException ex = assertThrows(IncognitoException.ConfigException.class, () ->
            IncognitoPipeline.builder().source(src).target(tgt).ephemeralSalt().policy(policy())
                .build().execute(),
            "a non-empty target must fail closed before any row is written");

        assertTrue(ex.getMessage().contains("'CUSTOMERS'"), ex.getMessage());
        assertTrue(ex.getMessage().contains("1 row(s)"), ex.getMessage());
        assertTrue(ex.getMessage().contains("--force"), ex.getMessage());
    }

    @Test
    void emptyTargetPassesTheGuardAndRunSucceeds() throws SQLException {
        DataSource src = freshDb(false);
        DataSource tgt = freshDb(false);

        assertDoesNotThrow(() ->
            IncognitoPipeline.builder().source(src).target(tgt).ephemeralSalt().policy(policy())
                .build().execute());
    }

    @Test
    void allowNonEmptyTargetBypassesTheGuardEvenWithExistingData() throws SQLException {
        DataSource src = freshDb(false);
        DataSource tgt = freshDb(true); // pre-existing row - would normally fail closed

        assertDoesNotThrow(() ->
            IncognitoPipeline.builder().source(src).target(tgt).ephemeralSalt().policy(policy())
                .allowNonEmptyTarget()
                .build().execute(),
            "--force (allowNonEmptyTarget()) must let the run proceed despite existing data");
    }

    private record SimpleDataSource(String url, String user, String password) implements DataSource {
        @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url, user, password); }
        @Override public Connection getConnection(String u, String p) throws SQLException { return DriverManager.getConnection(url, u, p); }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public void setLoginTimeout(int seconds) {}
        @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("Not a wrapper"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }
}
