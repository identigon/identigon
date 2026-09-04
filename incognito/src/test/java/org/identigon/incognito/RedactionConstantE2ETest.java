package org.identigon.incognito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.identigon.incognito.api.ColumnRole;
import org.identigon.incognito.api.IncognitoException;
import org.identigon.incognito.api.IncognitoPipeline;
import org.identigon.incognito.api.PipelineResult;
import org.identigon.incognito.api.RedactionStrategy;
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
 * {@code ColumnPolicy.redactionConstant} - a caller-chosen fixed placeholder for a {@code
 * RedactionStrategy.CONSTANT} column (e.g. {@code "0000 0000 0000 0000"} for a card number),
 * text-type columns only, checked at pipeline-build time rather than per row. Requires Docker;
 * skips gracefully otherwise.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedactionConstantE2ETest {

  private PostgreSQLContainer pg;
  private DataSource sourceDs;
  private DataSource targetDs;

  private static final String DDL =
      """
        CREATE TABLE payment_method (
            id            SERIAL PRIMARY KEY,
            card_number   VARCHAR(19) NOT NULL,
            amount        INTEGER NOT NULL
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
              .withDatabaseName("redconst_source")
              .withUsername("test")
              .withPassword("test");
      pg.start();

      try (Connection conn =
          DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
        conn.setAutoCommit(true);
        try (Statement stmt = conn.createStatement()) {
          stmt.execute(DDL);
          stmt.execute(
              "INSERT INTO payment_method (card_number, amount) VALUES "
                  + "('4111 1111 1111 1111', 5000), ('5500 0000 0000 0004', 12000)");
        }
      }

      String jdbcBase = "jdbc:postgresql://" + pg.getHost() + ":" + pg.getFirstMappedPort() + "/";
      try (Connection admin =
          DriverManager.getConnection(jdbcBase + "postgres", pg.getUsername(), pg.getPassword())) {
        admin.setAutoCommit(true);
        try (Statement stmt = admin.createStatement()) {
          stmt.execute("CREATE DATABASE redconst_target");
        }
      }
      String targetUrl = jdbcBase + "redconst_target";
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
      throw new RuntimeException("Failed to set up redactionConstant E2E databases", e);
    }
  }

  @AfterAll
  void tearDown() {
    if (pg != null) {
      pg.stop();
    }
  }

  @Test
  void redactionConstantOverridesTheTextDefault() throws Exception {
    Assumptions.assumeTrue(sourceDs != null, "Docker/PostgreSQL not available");

    AnonymisationPolicy policy =
        AnonymisationPolicy.builder()
            .table(
                "payment_method",
                t ->
                    t.column("id", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                        .column(
                            ColumnPolicy.builder("card_number")
                                .role(ColumnRole.SENSITIVE)
                                .distinguishing(true)
                                .redactionStrategy(RedactionStrategy.CONSTANT)
                                .redactionConstant("0000 0000 0000 0000")
                                .build())
                        .column("amount", ColumnRole.PAYLOAD))
            .build();

    PipelineResult result =
        IncognitoPipeline.builder()
            .source(sourceDs)
            .target(targetDs)
            .ephemeralSalt()
            .policy(policy)
            .stage(new SchemaDiscoveryStage())
            .stage(new TableTransformLoadStage())
            .stage(new VerificationStage())
            .build()
            .execute();
    assertTrue(result.success(), "pipeline should succeed with a custom text redaction constant");

    try (Connection conn = targetDs.getConnection()) {
      assertEquals(2, scalar(conn, "SELECT COUNT(*) FROM payment_method"));
      assertEquals(
          0,
          scalar(
              conn,
              "SELECT COUNT(*) FROM payment_method WHERE card_number IN ('4111 1111 1111 "
                  + "1111','5500 0000 0000 0004')"),
          "no source card number survives");
      assertEquals(
          2,
          scalar(
              conn,
              "SELECT COUNT(*) FROM payment_method WHERE card_number = '0000 0000 0000 0000'"),
          "every card_number redacted to the caller-chosen constant");
      // amount is PAYLOAD, kept real, unaffected by the neighbouring column's redaction.
      assertEquals(
          2, scalar(conn, "SELECT COUNT(*) FROM payment_method WHERE amount IN (5000, 12000)"));
    }
  }

  @Test
  void redactionConstantOnNonTextColumnFailsClosedAtBuildTime() {
    Assumptions.assumeTrue(sourceDs != null, "Docker/PostgreSQL not available");

    AnonymisationPolicy policy =
        AnonymisationPolicy.builder()
            .table(
                "payment_method",
                t ->
                    t.column("id", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                        .column("card_number", ColumnRole.PAYLOAD)
                        .column(
                            ColumnPolicy.builder("amount") // INTEGER - not a text type
                                .role(ColumnRole.SENSITIVE)
                                .distinguishing(true)
                                .redactionStrategy(RedactionStrategy.CONSTANT)
                                .redactionConstant("0000")
                                .build()))
            .build();

    IncognitoException.ConfigException ex =
        assertThrows(
            IncognitoException.ConfigException.class,
            () ->
                IncognitoPipeline.builder()
                    .source(sourceDs)
                    .target(targetDs)
                    .ephemeralSalt()
                    .policy(policy)
                    .stage(new SchemaDiscoveryStage())
                    .stage(new TableTransformLoadStage())
                    .stage(new VerificationStage())
                    .build()
                    .execute());
    assertTrue(
        ex.getMessage().contains("redactionConstant") && ex.getMessage().contains("text-type"),
        "message names the text-type requirement: " + ex.getMessage());
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
