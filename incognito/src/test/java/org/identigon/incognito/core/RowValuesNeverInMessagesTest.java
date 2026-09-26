package org.identigon.incognito.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * A failure caused by a particular row names the table and column, never the row's value: keys can
 * be PII (a natural key such as an e-mail address), and callers print or log exception messages
 * (SPEC §7.3). H2 in-memory, no Docker needed.
 */
class RowValuesNeverInMessagesTest {

  /** Distinctive enough that it cannot appear in a message by coincidence. */
  private static final long ORPHAN_KEY = 918_273_645L;

  @Test
  void orphanedForeignKeyValueIsWithheldFromTheFailure() throws SQLException {
    // No FK constraint in the source, so it can hold an order whose customer doesn't exist - the
    // same state a live source produces when a child row lands after its parent table was read.
    String ddl =
        """
            CREATE TABLE CUSTOMERS (ID BIGINT PRIMARY KEY);
            CREATE TABLE ORDERS (ID BIGINT PRIMARY KEY, CUSTOMER_ID BIGINT);
            """;
    DataSource src = freshDb(ddl);
    DataSource tgt = freshDb(ddl);
    try (Connection conn = src.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("INSERT INTO CUSTOMERS VALUES (1)");
      stmt.execute("INSERT INTO ORDERS VALUES (1, " + ORPHAN_KEY + ")");
    }

    AnonymisationPolicy policy =
        AnonymisationPolicy.builder()
            .table(
                "CUSTOMERS",
                t -> t.column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG))
            .table(
                "ORDERS",
                t ->
                    t.column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                        .column(
                            ColumnPolicy.builder("CUSTOMER_ID")
                                .role(ColumnRole.FOREIGN_KEY)
                                .references("CUSTOMERS", "ID")
                                .build()))
            .build();

    IncognitoException ex =
        assertThrows(
            IncognitoException.class,
            () ->
                IncognitoPipeline.builder()
                    .source(src)
                    .target(tgt)
                    .ephemeralSalt()
                    .policy(policy)
                    .stage(new SchemaDiscoveryStage())
                    .stage(new TableTransformLoadStage())
                    .stage(new VerificationStage())
                    .build()
                    .execute());

    StringBuilder chain = new StringBuilder();
    for (Throwable t = ex; t != null; t = t.getCause()) {
      chain.append(t).append('\n');
    }
    assertFalse(
        chain.toString().contains(Long.toString(ORPHAN_KEY)),
        "must not carry the row's key value: " + chain);
    assertTrue(
        chain.toString().contains("CUSTOMER_ID") && chain.toString().contains("ORDERS"),
        "still names where the failure is: " + chain);
  }

  private static DataSource freshDb(String ddl) throws SQLException {
    String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    try (Connection conn = DriverManager.getConnection(url, "sa", "");
        Statement stmt = conn.createStatement()) {
      stmt.execute(ddl);
    }
    return new SimpleDataSource(url, "sa", "");
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
