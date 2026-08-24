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
 * {@code DirectIdStrategy.ALTEREGO_NHS_NUMBER}, wired to {@code AlterEgo.nhsNumber()}
 * (SPEC §4.1/§4.3). Every fabricated value must carry the reserved {@code 999} test-range prefix,
 * and {@code VerificationStage} must assert it on the target the same way it does for
 * e-mail/postcode/domain/URL/NINO. Requires Docker; skips gracefully otherwise.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NhsNumberE2ETest {

    private PostgreSQLContainer pg;
    private DataSource sourceDs;
    private DataSource targetDs;

    private static final String DDL = """
        CREATE TABLE patient (
            id          SERIAL PRIMARY KEY,
            nhs_number  VARCHAR(14) NOT NULL
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
            pg = new PostgreSQLContainer(TestPostgres.IMAGE)
                .withDatabaseName("nhs_source").withUsername("test").withPassword("test");
            pg.start();

            try (Connection conn = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
                conn.setAutoCommit(true);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(DDL);
                    stmt.execute("INSERT INTO patient (nhs_number) VALUES "
                        + "('111 222 3333'), ('444 555 6666')");
                }
            }

            String jdbcBase = "jdbc:postgresql://" + pg.getHost() + ":" + pg.getFirstMappedPort() + "/";
            try (Connection admin = DriverManager.getConnection(jdbcBase + "postgres", pg.getUsername(), pg.getPassword())) {
                admin.setAutoCommit(true);
                try (Statement stmt = admin.createStatement()) { stmt.execute("CREATE DATABASE nhs_target"); }
            }
            String targetUrl = jdbcBase + "nhs_target";
            try (Connection conn = DriverManager.getConnection(targetUrl, pg.getUsername(), pg.getPassword())) {
                conn.setAutoCommit(true);
                try (Statement stmt = conn.createStatement()) { stmt.execute(DDL); }
            }

            sourceDs = new SimpleDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
            targetDs = new SimpleDataSource(targetUrl, pg.getUsername(), pg.getPassword());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set up NHS number E2E databases", e);
        }
    }

    @AfterAll
    void tearDown() {
        if (pg != null) pg.stop();
    }

    @Test
    void nhsNumbersAreFabricatedAndVerified() throws Exception {
        Assumptions.assumeTrue(sourceDs != null, "Docker/PostgreSQL not available");

        AnonymisationPolicy policy = AnonymisationPolicy.builder()
            .table("patient", t -> t
                .column("id", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                .column("nhs_number", ColumnRole.DIRECT_ID, DirectIdStrategy.ALTEREGO_NHS_NUMBER))
            .build();

        PipelineResult result = IncognitoPipeline.builder()
            .source(sourceDs).target(targetDs).ephemeralSalt().policy(policy)
            .stage(new SchemaDiscoveryStage())
            .stage(new TableTransformLoadStage())
            .stage(new VerificationStage())
            .build().execute();
        assertTrue(result.success(), "pipeline should succeed and pass NHS number fictionality verification");

        try (Connection conn = targetDs.getConnection()) {
            assertEquals(2, scalar(conn, "SELECT COUNT(*) FROM patient"));
            assertEquals(0, scalar(conn, "SELECT COUNT(*) FROM patient WHERE nhs_number IN ('111 222 3333','444 555 6666')"),
                "no source NHS number survives");
            assertEquals(2, scalar(conn, "SELECT COUNT(*) FROM patient WHERE nhs_number LIKE '999 %'"),
                "every fabricated NHS number carries the reserved 999 test-range prefix");
            assertEquals(2, scalar(conn,
                "SELECT COUNT(*) FROM patient WHERE nhs_number ~ '^999 [0-9]{3} [0-9]{4}$'"),
                "every fabricated NHS number matches the 999 ddd dddc format");
        }
    }

    private long scalar(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
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
