package org.identigon.incognito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.identigon.incognito.api.ColumnRole;
import org.identigon.incognito.api.DirectIdStrategy;
import org.identigon.incognito.api.IncognitoPipeline;
import org.identigon.incognito.api.PipelineResult;
import org.identigon.incognito.api.SurrogateStrategy;
import org.identigon.incognito.core.SchemaDiscoveryStage;
import org.identigon.incognito.core.TableTransformLoadStage;
import org.identigon.incognito.core.VerificationStage;
import org.identigon.incognito.policy.AnonymisationPolicy;
import org.identigon.incognito.policy.ColumnPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * De-risks {@code BulkDatabaseLoadStage.resolveDeferredCyclicFKs}'s batching-by-shape refactor: two
 * independent self-referential cyclic tables, each with a differently-named FK column, so their
 * deferred updates fall into two distinct {@code UpdateShape} groups (different {@code tableName}
 * <em>and</em> different {@code fkColumn}) processed via two separate {@code PreparedStatement}s.
 * {@link CyclicFkE2ETest} already covers batching correctness <em>within</em> one group; this
 * covers that grouping by table/column doesn't cross-contaminate values between groups sharing one
 * run.
 *
 * <p>Requires Docker; skips gracefully otherwise.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiGroupDeferredCyclicFkE2ETest {

  private PostgreSQLContainer pg;
  private DataSource sourceDs;
  private DataSource targetDs;

  private static final String DDL =
      """
        CREATE TABLE dept_a (
            id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            name     VARCHAR(100) NOT NULL,
            peer_id  BIGINT REFERENCES dept_a(id)
        );
        CREATE TABLE dept_b (
            id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            name        VARCHAR(100) NOT NULL,
            sibling_id  BIGINT REFERENCES dept_b(id)
        );
        """;

  @BeforeAll
  void setUp() {
    boolean dockerAvailable;
    try {
      dockerAvailable = org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
    } catch (Exception e) {
      dockerAvailable = false;
    }
    Assumptions.assumeTrue(dockerAvailable, "Docker not available - skipping Testcontainers E2E");

    try {
      pg =
          new PostgreSQLContainer(TestPostgres.IMAGE)
              .withDatabaseName("multi_group_cyclic_source")
              .withUsername("test")
              .withPassword("test");
      pg.start();

      try (Connection conn =
          DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
        conn.setAutoCommit(true);
        try (Statement stmt = conn.createStatement()) {
          stmt.execute(DDL);
          // dept_a: two mutual pairs (1<->2, 3<->4) - a genuine 2-cycle, so at least one row
          // per pair must always be deferred regardless of scan order.
          stmt.execute("INSERT INTO dept_a (name) VALUES ('A1'), ('A2'), ('A3'), ('A4')");
          stmt.execute("UPDATE dept_a SET peer_id = 2 WHERE id = 1");
          stmt.execute("UPDATE dept_a SET peer_id = 1 WHERE id = 2");
          stmt.execute("UPDATE dept_a SET peer_id = 4 WHERE id = 3");
          stmt.execute("UPDATE dept_a SET peer_id = 3 WHERE id = 4");
          // dept_b: one mutual pair (1<->2), a different column name and a different row
          // count from dept_a's group, so the two groups' batches genuinely differ in shape.
          stmt.execute("INSERT INTO dept_b (name) VALUES ('B1'), ('B2')");
          stmt.execute("UPDATE dept_b SET sibling_id = 2 WHERE id = 1");
          stmt.execute("UPDATE dept_b SET sibling_id = 1 WHERE id = 2");
        }
      }

      String jdbcBase = "jdbc:postgresql://" + pg.getHost() + ":" + pg.getFirstMappedPort() + "/";
      String targetUrl = jdbcBase + "multi_group_cyclic_target";
      try (Connection admin =
          DriverManager.getConnection(jdbcBase + "postgres", pg.getUsername(), pg.getPassword())) {
        admin.setAutoCommit(true);
        try (Statement stmt = admin.createStatement()) {
          stmt.execute("CREATE DATABASE multi_group_cyclic_target");
        }
      }
      try (Connection conn =
          DriverManager.getConnection(targetUrl, pg.getUsername(), pg.getPassword())) {
        conn.setAutoCommit(true);
        try (Statement stmt = conn.createStatement()) {
          stmt.execute(DDL);
        }
      }

      sourceDs = new SimpleDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
      targetDs = new SimpleDataSource(targetUrl, pg.getUsername(), pg.getPassword());
    } catch (SQLException e) {
      throw new RuntimeException("Failed to set up multi-group cyclic-FK E2E databases", e);
    }
  }

  @AfterAll
  void tearDown() {
    if (pg != null) {
      pg.stop();
    }
  }

  private AnonymisationPolicy policy() {
    return AnonymisationPolicy.builder()
        .table(
            "dept_a",
            t ->
                t.column("id", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                    .column("name", ColumnRole.DIRECT_ID, DirectIdStrategy.ALTEREGO_GENERIC)
                    .column(
                        ColumnPolicy.builder("peer_id")
                            .role(ColumnRole.FOREIGN_KEY)
                            .references("dept_a", "id")
                            .build()))
        .table(
            "dept_b",
            t ->
                t.column("id", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                    .column("name", ColumnRole.DIRECT_ID, DirectIdStrategy.ALTEREGO_GENERIC)
                    .column(
                        ColumnPolicy.builder("sibling_id")
                            .role(ColumnRole.FOREIGN_KEY)
                            .references("dept_b", "id")
                            .build()))
        .build();
  }

  @Test
  void twoIndependentCyclicGroupsBothResolveWithoutCrossContamination() throws Exception {
    Assumptions.assumeTrue(sourceDs != null, "Docker/PostgreSQL not available");

    PipelineResult result =
        IncognitoPipeline.builder()
            .source(sourceDs)
            .target(targetDs)
            .ephemeralSalt()
            .policy(policy())
            .stage(new SchemaDiscoveryStage())
            .stage(new TableTransformLoadStage())
            .stage(new VerificationStage())
            .build()
            .execute();
    assertTrue(result.success(), "pipeline should succeed");

    try (Connection conn = targetDs.getConnection()) {
      assertEquals(4, scalar(conn, "SELECT COUNT(*) FROM dept_a"), "dept_a row count preserved");
      assertEquals(2, scalar(conn, "SELECT COUNT(*) FROM dept_b"), "dept_b row count preserved");

      // No placeholder (-1) survives in either group.
      assertEquals(
          0,
          scalar(conn, "SELECT COUNT(*) FROM dept_a WHERE peer_id = -1"),
          "dept_a's cyclic-FK placeholder must be resolved");
      assertEquals(
          0,
          scalar(conn, "SELECT COUNT(*) FROM dept_b WHERE sibling_id = -1"),
          "dept_b's cyclic-FK placeholder must be resolved");

      // Mutual topology preserved within each group independently.
      assertEquals(
          0,
          scalar(
              conn,
              "SELECT COUNT(*) FROM dept_a a WHERE NOT EXISTS "
                  + "(SELECT 1 FROM dept_a b WHERE b.id = a.peer_id AND b.peer_id = a.id)"),
          "dept_a mutual pairs must survive remapping (a->b implies b->a)");
      assertEquals(
          0,
          scalar(
              conn,
              "SELECT COUNT(*) FROM dept_b a WHERE NOT EXISTS "
                  + "(SELECT 1 FROM dept_b b WHERE b.id = a.sibling_id AND b.sibling_id = a.id)"),
          "dept_b mutual pairs must survive remapping (a->b implies b->a)");

      // Cross-contamination check: dept_a's surrogate ids and dept_b's never collide by
      // construction (both start their own SEQUENTIAL_LONG sequence at 1), so this only
      // proves something meaningful if the group-a batch didn't accidentally write into
      // dept_b's table or vice versa - re-assert row counts plus referential integrity once
      // more, scoped per table, to be explicit about that.
      assertEquals(
          0,
          scalar(
              conn,
              "SELECT COUNT(*) FROM dept_a a WHERE a.peer_id IS NOT NULL "
                  + "AND NOT EXISTS (SELECT 1 FROM dept_a b WHERE b.id = a.peer_id)"),
          "no dangling dept_a.peer_id");
      assertEquals(
          0,
          scalar(
              conn,
              "SELECT COUNT(*) FROM dept_b a WHERE a.sibling_id IS NOT NULL "
                  + "AND NOT EXISTS (SELECT 1 FROM dept_b b WHERE b.id = a.sibling_id)"),
          "no dangling dept_b.sibling_id");
    }
  }

  private long scalar(Connection conn, String sql) throws SQLException {
    try (Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    }
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
