package org.identigon.incognito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * Proves {@code TableTransformLoadStage} actually opens an {@code alterego} {@code RecordScope} per
 * source row, not just that the code compiles: {@code ALTEREGO_CITY}/{@code _POSTCODE}/ {@code
 * _PHONE} on the same table must cohere on the same UK region (alterego SPEC §6.3), the way {@code
 * alterego}'s own {@code RecordCoherenceIntegrationTest} proves the underlying mechanism does in
 * isolation. Verified here via postcode/phone (the postcode's own leading area letters must match
 * the phone's area dialling code, per the same area-&gt;dialling-code table that test uses) - city
 * is exactly the same mechanism/code path, not independently re-verified. Requires Docker; skips
 * gracefully otherwise.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecordCoherenceE2ETest {

  private PostgreSQLContainer pg;
  private DataSource sourceDs;
  private DataSource targetDs;

  private static final Pattern LEADING_LETTERS = Pattern.compile("^[A-Z]+");

  private static final String DDL =
      """
        CREATE TABLE branch (
            id        SERIAL PRIMARY KEY,
            city      VARCHAR(60) NOT NULL,
            postcode  VARCHAR(10) NOT NULL,
            phone     VARCHAR(20) NOT NULL
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
              .withDatabaseName("coherence_source")
              .withUsername("test")
              .withPassword("test");
      pg.start();

      try (Connection conn =
          DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
        conn.setAutoCommit(true);
        try (Statement stmt = conn.createStatement()) {
          stmt.execute(DDL);
          // Twenty rows: with only ~15 UK nations/areas with a dialling-range entry, this
          // gives good odds of hitting several different areas, not just the same one by luck.
          List<String> rows = new java.util.ArrayList<>();
          for (int i = 0; i < 20; i++) {
            rows.add("('Old Town " + i + "', 'AA1 1AA', '01632 000000')");
          }
          stmt.execute(
              "INSERT INTO branch (city, postcode, phone) VALUES " + String.join(", ", rows));
        }
      }

      String jdbcBase = "jdbc:postgresql://" + pg.getHost() + ":" + pg.getFirstMappedPort() + "/";
      try (Connection admin =
          DriverManager.getConnection(jdbcBase + "postgres", pg.getUsername(), pg.getPassword())) {
        admin.setAutoCommit(true);
        try (Statement stmt = admin.createStatement()) {
          stmt.execute("CREATE DATABASE coherence_target");
        }
      }
      String targetUrl = jdbcBase + "coherence_target";
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
      throw new RuntimeException("Failed to set up record-coherence E2E databases", e);
    }
  }

  @AfterAll
  void tearDown() {
    if (pg != null) {
      pg.stop();
    }
  }

  @Test
  void postcodeAndPhoneCohereOnTheSameAreaWithinEachRow() throws Exception {
    Assumptions.assumeTrue(sourceDs != null, "Docker/PostgreSQL not available");

    AnonymisationPolicy policy =
        AnonymisationPolicy.builder()
            .table(
                "branch",
                t ->
                    t.column("id", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                        .column("city", ColumnRole.DIRECT_ID, DirectIdStrategy.ALTEREGO_CITY)
                        .column(
                            "postcode", ColumnRole.DIRECT_ID, DirectIdStrategy.ALTEREGO_POSTCODE)
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
    assertTrue(result.success(), "pipeline should succeed");

    int checked = 0;
    try (Connection conn = targetDs.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT postcode, phone FROM branch")) {
      while (rs.next()) {
        String postcode = rs.getString("postcode");
        String phone = rs.getString("phone");

        Matcher m = LEADING_LETTERS.matcher(postcode);
        assertTrue(m.find(), "postcode should start with letters: " + postcode);
        String area = m.group();

        assertTrue(
            phoneMatchesAreaOrNeutralFallback(area, phone),
            "postcode '"
                + postcode
                + "' (area '"
                + area
                + "') and phone '"
                + phone
                + "' are not coherent - RecordScope isn't linking them within the row");
        checked++;
      }
    }
    assertEquals(20, checked, "every row should have been checked");
  }

  /**
   * The same area -&gt; dialling-code table {@code alterego}'s own {@code
   * RecordCoherenceIntegrationTest} uses as ground truth for the coherence guarantee - duplicated
   * here deliberately, so this test verifies incognito's *wiring* against a fixed,
   * independently-known-correct expectation, not against whatever alterego happens to do today.
   */
  private static boolean phoneMatchesAreaOrNeutralFallback(String area, String phoneResult) {
    return switch (area) {
      case "LS" -> phoneResult.startsWith("0113 496 0");
      case "S" -> phoneResult.startsWith("0114 496 0");
      case "NG" -> phoneResult.startsWith("0115 496 0");
      case "LE" -> phoneResult.startsWith("0116 496 0");
      case "BS" -> phoneResult.startsWith("0117 496 0");
      case "B" -> phoneResult.startsWith("0121 496 0");
      case "EH" -> phoneResult.startsWith("0131 496 0");
      case "G" -> phoneResult.startsWith("0141 496 0");
      case "L" -> phoneResult.startsWith("0151 496 0");
      case "M" -> phoneResult.startsWith("0161 496 0");
      case "NE" -> phoneResult.startsWith("0191 498 0");
      case "BT" -> phoneResult.startsWith("028 9649 6");
      case "CF" -> phoneResult.startsWith("029 2018 0");
      case "E", "EC", "N", "NW", "SE", "SW", "W", "WC" -> phoneResult.startsWith("020 7946 0");
      default -> phoneResult.startsWith("01632 960"); // no range for this area: neutral fallback
    };
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
