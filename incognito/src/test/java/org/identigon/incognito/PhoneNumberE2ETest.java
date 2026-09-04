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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * {@code DirectIdStrategy.ALTEREGO_PHONE}, wired to {@code AlterEgo.phoneNumber()} (SPEC
 * §4.1/§4.3). Every fabricated value must land in one of GB's reserved Ofcom drama-number ranges
 * (an 8-digit area-coded prefix followed by 3 free digits), and {@code VerificationStage} must
 * assert it on the target the same way it does for e-mail/postcode/domain/URL/NINO/NHS
 * number/passport number/driving licence number. Requires Docker; skips gracefully otherwise.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PhoneNumberE2ETest {

  private PostgreSQLContainer pg;
  private DataSource sourceDs;
  private DataSource targetDs;

  private static final String DDL =
      """
        CREATE TABLE customer (
            id      SERIAL PRIMARY KEY,
            phone   VARCHAR(20) NOT NULL
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
              .withDatabaseName("phone_source")
              .withUsername("test")
              .withPassword("test");
      pg.start();

      try (Connection conn =
          DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
        conn.setAutoCommit(true);
        try (Statement stmt = conn.createStatement()) {
          stmt.execute(DDL);
          stmt.execute(
              "INSERT INTO customer (phone) VALUES " + "('020 7946 0958'), ('07123 456789')");
        }
      }

      String jdbcBase = "jdbc:postgresql://" + pg.getHost() + ":" + pg.getFirstMappedPort() + "/";
      try (Connection admin =
          DriverManager.getConnection(jdbcBase + "postgres", pg.getUsername(), pg.getPassword())) {
        admin.setAutoCommit(true);
        try (Statement stmt = admin.createStatement()) {
          stmt.execute("CREATE DATABASE phone_target");
        }
      }
      String targetUrl = jdbcBase + "phone_target";
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
      throw new RuntimeException("Failed to set up phone number E2E databases", e);
    }
  }

  @AfterAll
  void tearDown() {
    if (pg != null) {
      pg.stop();
    }
  }

  @Test
  void phoneNumbersAreFabricatedAndVerified() throws Exception {
    Assumptions.assumeTrue(sourceDs != null, "Docker/PostgreSQL not available");

    AnonymisationPolicy policy =
        AnonymisationPolicy.builder()
            .table(
                "customer",
                t ->
                    t.column("id", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                        .column("phone", ColumnRole.DIRECT_ID, DirectIdStrategy.ALTEREGO_PHONE))
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
    assertTrue(
        result.success(),
        "pipeline should succeed and pass phone number fictionality verification");

    try (Connection conn = targetDs.getConnection()) {
      assertEquals(2, scalar(conn, "SELECT COUNT(*) FROM customer"));
      assertEquals(
          0,
          scalar(
              conn,
              "SELECT COUNT(*) FROM customer WHERE phone IN ('020 7946 0958','07123 456789')"),
          "no source phone number survives");
      assertEquals(
          2,
          scalar(
              conn,
              "SELECT COUNT(*) FROM customer WHERE REGEXP_REPLACE(phone, '[^0-9]', '', 'g') ~ "
                  + "'^(01134960|01144960|01154960|01164960|01174960|01184960|01214960|01314960|"
                  + "01414960|01514960|01614960|01632960|01914980|02079460|02896496|02920180|"
                  + "03069990|07700900|08081570|09098790)[0-9]{3}$'"),
          "every fabricated phone number lands in a reserved Ofcom drama-number range");
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
