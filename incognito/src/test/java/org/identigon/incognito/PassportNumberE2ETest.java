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
 * {@code DirectIdStrategy.ALTEREGO_PASSPORT_NUMBER}, wired to {@code AlterEgo.passportNumber()}
 * (SPEC §4.1/§4.3). Every fabricated value must carry the structurally-impossible {@code ZZ}
 * prefix, and {@code VerificationStage} must assert it on the target the same way it does for
 * e-mail/postcode/domain/URL/NINO/NHS number. Requires Docker; skips gracefully otherwise.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PassportNumberE2ETest {

    private PostgreSQLContainer pg;
    private DataSource sourceDs;
    private DataSource targetDs;

    private static final String DDL = """
        CREATE TABLE traveller (
            id                SERIAL PRIMARY KEY,
            passport_number   VARCHAR(9) NOT NULL
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
        Assumptions.assumeTrue(dockerAvailable, "Docker not available — skipping Testcontainers E2E");

        try {
            pg = new PostgreSQLContainer(TestPostgres.IMAGE)
                .withDatabaseName("passport_source").withUsername("test").withPassword("test");
            pg.start();

            try (Connection conn = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
                conn.setAutoCommit(true);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(DDL);
                    stmt.execute("INSERT INTO traveller (passport_number) VALUES "
                        + "('123456789'), ('987654321')");
                }
            }

            String jdbcBase = "jdbc:postgresql://" + pg.getHost() + ":" + pg.getFirstMappedPort() + "/";
            try (Connection admin = DriverManager.getConnection(jdbcBase + "postgres", pg.getUsername(), pg.getPassword())) {
                admin.setAutoCommit(true);
                try (Statement stmt = admin.createStatement()) { stmt.execute("CREATE DATABASE passport_target"); }
            }
            String targetUrl = jdbcBase + "passport_target";
            try (Connection conn = DriverManager.getConnection(targetUrl, pg.getUsername(), pg.getPassword())) {
                conn.setAutoCommit(true);
                try (Statement stmt = conn.createStatement()) { stmt.execute(DDL); }
            }

            sourceDs = new SimpleDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
            targetDs = new SimpleDataSource(targetUrl, pg.getUsername(), pg.getPassword());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set up passport number E2E databases", e);
        }
    }

    @AfterAll
    void tearDown() {
        if (pg != null) pg.stop();
    }

    @Test
    void passportNumbersAreFabricatedAndVerified() throws Exception {
        Assumptions.assumeTrue(sourceDs != null, "Docker/PostgreSQL not available");

        AnonymisationPolicy policy = AnonymisationPolicy.builder()
            .table("traveller", t -> t
                .column("id", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                .column("passport_number", ColumnRole.DIRECT_ID, DirectIdStrategy.ALTEREGO_PASSPORT_NUMBER))
            .build();

        PipelineResult result = IncognitoPipeline.builder()
            .source(sourceDs).target(targetDs).ephemeralSalt().policy(policy)
            .stage(new SchemaDiscoveryStage())
            .stage(new TableTransformLoadStage())
            .stage(new VerificationStage())
            .build().execute();
        assertTrue(result.success(), "pipeline should succeed and pass passport number fictionality verification");

        try (Connection conn = targetDs.getConnection()) {
            assertEquals(2, scalar(conn, "SELECT COUNT(*) FROM traveller"));
            assertEquals(0, scalar(conn, "SELECT COUNT(*) FROM traveller WHERE passport_number IN ('123456789','987654321')"),
                "no source passport number survives");
            assertEquals(2, scalar(conn, "SELECT COUNT(*) FROM traveller WHERE passport_number LIKE 'ZZ%'"),
                "every fabricated passport number carries the structurally-impossible ZZ prefix");
            assertEquals(2, scalar(conn,
                "SELECT COUNT(*) FROM traveller WHERE passport_number ~ '^ZZ[0-9]{7}$'"),
                "every fabricated passport number matches the ZZddddddd format");
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
