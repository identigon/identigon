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
import org.identigon.incognito.api.DirectIdStrategy;
import org.identigon.incognito.api.IncognitoException;
import org.identigon.incognito.api.IncognitoPipeline;
import org.identigon.incognito.api.SurrogateStrategy;
import org.identigon.incognito.policy.AnonymisationPolicy;
import org.identigon.incognito.policy.ColumnPolicy;
import org.junit.jupiter.api.Test;

/**
 * A {@code DIRECT_ID} column with no declared {@code directIdStrategy} fails closed at
 * schema-discovery time (ADR 29), the same way an undeclared SENSITIVE {@code distinguishing} flag
 * already does. H2 in-memory, no Docker/Testcontainers needed - this is config validation in {@link
 * SchemaDiscoveryStage}, before any row is read.
 */
class DirectIdStrategyRequiredTest {

  private DataSource freshDb(String ddl) throws SQLException {
    String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    try (Connection conn = DriverManager.getConnection(url, "sa", "");
        Statement stmt = conn.createStatement()) {
      stmt.execute(ddl);
    }
    return new SimpleDataSource(url, "sa", "");
  }

  private static void run(DataSource src, DataSource tgt, AnonymisationPolicy policy) {
    IncognitoPipeline.builder()
        .source(src)
        .target(tgt)
        .ephemeralSalt()
        .policy(policy)
        .stage(new SchemaDiscoveryStage())
        .stage(new TableTransformLoadStage())
        .stage(new VerificationStage())
        .build()
        .execute();
  }

  @Test
  void directIdWithNoStrategyFailsClosed() throws SQLException {
    // H2 folds unquoted identifiers to UPPER CASE by default (unlike Postgres) - table/column
    // names here must match that, or SchemaInspector reports names the policy never matches
    // and validateTablePolicy silently never runs (RunCommandTest already established this
    // convention for H2-backed tests).
    String ddl =
        """
            CREATE TABLE CUSTOMERS (
                ID    BIGINT PRIMARY KEY,
                EMAIL VARCHAR(255) NOT NULL
            );
            """;
    DataSource src = freshDb(ddl);
    DataSource tgt = freshDb(ddl);

    AnonymisationPolicy policy =
        AnonymisationPolicy.builder()
            .table(
                "CUSTOMERS",
                t ->
                    t.column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                        .column(ColumnPolicy.builder("EMAIL").role(ColumnRole.DIRECT_ID).build()))
            .build();

    IncognitoException.ConfigException ex =
        assertThrows(IncognitoException.ConfigException.class, () -> run(src, tgt, policy));
    assertTrue(
        ex.getMessage().contains("DIRECT_ID") && ex.getMessage().contains("directIdStrategy"),
        "message names the missing-strategy cause: " + ex.getMessage());
  }

  @Test
  void directIdWithExplicitAlteregoGenericPasses() throws SQLException {
    String ddl =
        """
            CREATE TABLE CUSTOMERS (
                ID           BIGINT PRIMARY KEY,
                BANK_ACCOUNT VARCHAR(20) NOT NULL
            );
            """;
    DataSource src = freshDb(ddl);
    DataSource tgt = freshDb(ddl);
    try (Connection conn = src.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("INSERT INTO CUSTOMERS (ID, BANK_ACCOUNT) VALUES (1, '12345678')");
    }

    AnonymisationPolicy policy =
        AnonymisationPolicy.builder()
            .table(
                "CUSTOMERS",
                t ->
                    t.column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                        .column(
                            "BANK_ACCOUNT",
                            ColumnRole.DIRECT_ID,
                            DirectIdStrategy.ALTEREGO_GENERIC))
            .build();

    // ALTEREGO_GENERIC stays a fully valid choice - only an *unstated* one fails closed.
    assertDoesNotThrow(() -> run(src, tgt, policy));
  }

  private record SimpleDataSource(String url, String user, String password) implements DataSource {
    @Override
    public Connection getConnection() throws SQLException {
      return DriverManager.getConnection(url, user, password);
    }

    @Override
    public Connection getConnection(String u, String p) throws SQLException {
      return DriverManager.getConnection(url, u, p);
    }

    @Override
    public java.io.PrintWriter getLogWriter() {
      return null;
    }

    @Override
    public void setLogWriter(java.io.PrintWriter out) {}

    @Override
    public int getLoginTimeout() {
      return 0;
    }

    @Override
    public void setLoginTimeout(int seconds) {}

    @Override
    public java.util.logging.Logger getParentLogger() {
      return java.util.logging.Logger.getGlobal();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
      throw new SQLException("Not a wrapper");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
      return false;
    }
  }
}
