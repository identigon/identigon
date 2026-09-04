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
import org.identigon.incognito.api.PipelineStage;
import org.identigon.incognito.api.SurrogateStrategy;
import org.identigon.incognito.engine.SchemaInspector;
import org.identigon.incognito.engine.TableDependencyGraph;
import org.identigon.incognito.policy.AnonymisationPolicy;
import org.junit.jupiter.api.Test;

/**
 * {@code TableTransformLoadStage.buildFkTransformer}'s defence-in-depth guard for a single-column
 * {@code FOREIGN_KEY} with no declared {@code references}. Normally {@code
 * SchemaDiscoveryStage.validateTablePolicy} already catches this fail-closed before any row is
 * touched ({@link SchemaDiscoveryStageValidateTest}), and {@code run} always calls it first - so
 * this guard is only reachable by a caller that skips {@code SchemaDiscoveryStage} entirely, which
 * this test simulates directly (a hand-rolled discovery stage that populates the attributes {@code
 * TableTransformLoadStage} needs without ever calling {@code validate}). Before this guard existed,
 * that path NPE'd inside {@code ConcurrentHashMap.get(null)} instead of failing with a named
 * diagnostic. H2 in-memory, no Docker/Testcontainers needed.
 */
class ForeignKeyReferencesRequiredTest {

  private DataSource freshDb(String ddl) throws SQLException {
    String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    try (Connection conn = DriverManager.getConnection(url, "sa", "");
        Statement stmt = conn.createStatement()) {
      stmt.execute(ddl);
    }
    return new SimpleDataSource(url, "sa", "");
  }

  @Test
  void missingReferencesFailsClosedInsteadOfNpeingWhenSchemaDiscoveryStageIsSkipped()
      throws SQLException {
    String ddl =
        """
            CREATE TABLE CUSTOMERS (ID BIGINT PRIMARY KEY);
            CREATE TABLE ORDERS (ID BIGINT PRIMARY KEY, CUSTOMER_ID BIGINT NOT NULL);
            """;
    DataSource src = freshDb(ddl);
    DataSource tgt = freshDb(ddl);
    try (Connection conn = src.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("INSERT INTO CUSTOMERS (ID) VALUES (1)");
      stmt.execute("INSERT INTO ORDERS (ID, CUSTOMER_ID) VALUES (1, 1)");
    }

    // CUSTOMER_ID declared FOREIGN_KEY but never given a references block - the same
    // mis-declaration SchemaDiscoveryStageValidateTest proves validate() catches. Here it
    // reaches TableTransformLoadStage directly instead.
    AnonymisationPolicy policy =
        AnonymisationPolicy.builder()
            .table(
                "CUSTOMERS",
                t -> t.column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG))
            .table(
                "ORDERS",
                t ->
                    t.column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                        .column("CUSTOMER_ID", ColumnRole.FOREIGN_KEY))
            .build();

    // A hand-rolled discovery stage standing in for "a caller that skips SchemaDiscoveryStage
    // entirely": it populates exactly what TableTransformLoadStage reads from the context
    // attributes, via the same SchemaInspector/TableDependencyGraph SchemaDiscoveryStage itself
    // uses, but never calls validate().
    PipelineStage rawDiscoveryWithNoValidation =
        context -> {
          var metadata = new SchemaInspector().inspect(context.source());
          var plan = new TableDependencyGraph().computeTopologicalOrder(metadata);
          context.attributes().put(SchemaDiscoveryStage.ATTR_TABLE_METADATA, metadata);
          context.attributes().put(SchemaDiscoveryStage.ATTR_EXECUTION_PLAN, plan);
          return new PipelineStage.StageResult(
              "RawDiscoveryWithNoValidation", true, metadata.size(), "discovered, unvalidated");
        };

    IncognitoException.ConstraintException ex =
        assertThrows(
            IncognitoException.ConstraintException.class,
            () ->
                IncognitoPipeline.builder()
                    .source(src)
                    .target(tgt)
                    .ephemeralSalt()
                    .policy(policy)
                    .stage(rawDiscoveryWithNoValidation)
                    .stage(new TableTransformLoadStage())
                    .build()
                    .execute(),
            "a missing references block must fail closed here too, not just in validate()");

    assertTrue(ex.getMessage().contains("'CUSTOMER_ID'"), ex.getMessage());
    assertTrue(ex.getMessage().contains("no references declared"), ex.getMessage());
    assertFalse(ex.getMessage().contains("NullPointerException"), ex.getMessage());
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
