package org.identigon.incognito.core;

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
import org.identigon.incognito.policy.ColumnPolicy;
import org.junit.jupiter.api.Test;

/**
 * A schema with fail-closed issues in more than one table reports all of them in a single run,
 * not one table (or one issue) per run - {@link SchemaDiscoveryStage#validateTablePolicy}
 * accumulates across every table and every check instead of throwing on the first hit. H2
 * in-memory, no Docker needed - this is config validation, before any row is read.
 */
class FailClosedAccumulatesAcrossTablesTest {

    private DataSource freshDb(String ddl) throws SQLException {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
                Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        }
        return new SimpleDataSource(url, "sa", "");
    }

    @Test
    void unclassifiedColumnsInTwoTablesAreBothReportedInOneRun() throws SQLException {
        String ddl = """
            CREATE TABLE CUSTOMERS (ID BIGINT PRIMARY KEY, STATUS VARCHAR(20) NOT NULL);
            CREATE TABLE ORDERS (ID BIGINT PRIMARY KEY, TOTAL NUMERIC NOT NULL);
            """;
        DataSource src = freshDb(ddl);
        DataSource tgt = freshDb(ddl);

        // Neither STATUS nor TOTAL is classified - one issue per table, in different tables.
        AnonymisationPolicy policy = AnonymisationPolicy.builder()
            .table("CUSTOMERS", t -> t.column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG))
            .table("ORDERS", t -> t.column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG))
            .build();

        IncognitoException.ConfigException ex = assertThrows(IncognitoException.ConfigException.class, () ->
            IncognitoPipeline.builder().source(src).target(tgt).ephemeralSalt().policy(policy)
                .stage(new SchemaDiscoveryStage())
                .stage(new TableTransformLoadStage())
                .stage(new VerificationStage())
                .build().execute());

        assertTrue(ex.getMessage().contains("CUSTOMERS") && ex.getMessage().contains("STATUS"),
            "names the CUSTOMERS.STATUS issue: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("ORDERS") && ex.getMessage().contains("TOTAL"),
            "names the ORDERS.TOTAL issue in the SAME exception: " + ex.getMessage());
    }

    @Test
    void differentCheckKindsInDifferentTablesAreBothReportedInOneRun() throws SQLException {
        String ddl = """
            CREATE TABLE CUSTOMERS (ID BIGINT PRIMARY KEY, EMAIL VARCHAR(255) NOT NULL);
            CREATE TABLE ORDERS (ID BIGINT PRIMARY KEY, NOTES VARCHAR(200) NOT NULL);
            """;
        DataSource src = freshDb(ddl);
        DataSource tgt = freshDb(ddl);

        // CUSTOMERS.EMAIL: DIRECT_ID with no directIdStrategy. ORDERS.NOTES: SENSITIVE with no
        // distinguishing flag. Two different check kinds, two different tables, one run.
        AnonymisationPolicy policy = AnonymisationPolicy.builder()
            .table("CUSTOMERS", t -> t
                .column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                .column(ColumnPolicy.builder("EMAIL").role(ColumnRole.DIRECT_ID).build()))
            .table("ORDERS", t -> t
                .column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                .column(ColumnPolicy.builder("NOTES").role(ColumnRole.SENSITIVE).build()))
            .build();

        IncognitoException.ConfigException ex = assertThrows(IncognitoException.ConfigException.class, () ->
            IncognitoPipeline.builder().source(src).target(tgt).ephemeralSalt().policy(policy)
                .stage(new SchemaDiscoveryStage())
                .stage(new TableTransformLoadStage())
                .stage(new VerificationStage())
                .build().execute());

        assertTrue(ex.getMessage().contains("DIRECT_ID") && ex.getMessage().contains("EMAIL"),
            "names the DIRECT_ID/EMAIL issue: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("SENSITIVE") && ex.getMessage().contains("NOTES"),
            "names the SENSITIVE/NOTES issue in the SAME exception: " + ex.getMessage());
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
