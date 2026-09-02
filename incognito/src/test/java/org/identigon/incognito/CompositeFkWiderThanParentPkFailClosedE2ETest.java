package org.identigon.incognito;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.identigon.incognito.api.ColumnRole;
import org.identigon.incognito.api.IncognitoException;
import org.identigon.incognito.api.IncognitoPipeline;
import org.identigon.incognito.api.SurrogateStrategy;
import org.identigon.incognito.core.SchemaDiscoveryStage;
import org.identigon.incognito.core.TableTransformLoadStage;
import org.identigon.incognito.policy.AnonymisationPolicy;
import org.identigon.incognito.policy.ColumnPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Regression test: a composite FK that references a {@code UNIQUE} constraint WIDER than the
 * parent's actual (narrower) primary key must fail closed at transform-time, the mirror image of
 * {@link PartialCompositeFkFailClosedE2ETest}'s narrower case.
 *
 * <p>{@code region}'s real PK is {@code (country, code)} (2 columns), with a separate, wider
 * {@code UNIQUE (country, code, variant)} (3 columns); {@code district}'s composite FK references
 * that 3-column constraint, not the PK. Before this fix, {@code buildFkTransformer} iterated only
 * over the (fewer) PK columns to build the lookup key, silently dropping the FK's extra
 * {@code variant} column instead of detecting the mismatch - the old
 * {@code orderedChildCols.contains(null)} check only ever saw entries for the columns it *did*
 * iterate, so a composite FK that fully covered the PK (as this one does, plus one column more)
 * slipped through with no null anywhere.
 *
 * <p>Requires Docker; skips gracefully otherwise.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompositeFkWiderThanParentPkFailClosedE2ETest {

    private PostgreSQLContainer pg;
    private DataSource sourceDs;
    private DataSource targetDs;

    private static final String DDL = """
        CREATE TABLE region (
            country  VARCHAR(2) NOT NULL,
            code     INT NOT NULL,
            variant  INT NOT NULL DEFAULT 0,
            PRIMARY KEY (country, code),
            UNIQUE (country, code, variant)
        );
        CREATE TABLE district (
            country     VARCHAR(2) NOT NULL,
            code        INT NOT NULL,
            variant     INT NOT NULL,
            district_no INT NOT NULL,
            PRIMARY KEY (country, code, district_no),
            FOREIGN KEY (country, code, variant) REFERENCES region(country, code, variant)
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
                .withDatabaseName("wider_fk_source").withUsername("test").withPassword("test");
            pg.start();

            try (Connection conn = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
                conn.setAutoCommit(true);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(DDL);
                    stmt.execute("INSERT INTO region (country, code, variant) VALUES ('GB', 1, 0)");
                    stmt.execute("INSERT INTO district (country, code, variant, district_no) VALUES ('GB', 1, 0, 1)");
                }
            }

            String jdbcBase = "jdbc:postgresql://" + pg.getHost() + ":" + pg.getFirstMappedPort() + "/";
            try (Connection admin = DriverManager.getConnection(jdbcBase + "postgres", pg.getUsername(), pg.getPassword())) {
                admin.setAutoCommit(true);
                try (Statement stmt = admin.createStatement()) {
                    stmt.execute("CREATE DATABASE wider_fk_target");
                }
            }
            String targetUrl = jdbcBase + "wider_fk_target";
            try (Connection conn = DriverManager.getConnection(targetUrl, pg.getUsername(), pg.getPassword())) {
                conn.setAutoCommit(true);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(DDL);
                }
            }

            sourceDs = new SimpleDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
            targetDs = new SimpleDataSource(targetUrl, pg.getUsername(), pg.getPassword());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set up wider-composite-FK E2E databases", e);
        }
    }

    @AfterAll
    void tearDown() {
        if (pg != null) pg.stop();
    }

    private AnonymisationPolicy policy() {
        return AnonymisationPolicy.builder()
            .table("region", t -> t
                .column(ColumnPolicy.builder("country").role(ColumnRole.PRIMARY_KEY).surrogateStrategy(SurrogateStrategy.PASSTHROUGH_SURROGATE).build())
                .column(ColumnPolicy.builder("code").role(ColumnRole.PRIMARY_KEY).surrogateStrategy(SurrogateStrategy.PASSTHROUGH_SURROGATE).build())
                .column("variant", ColumnRole.PAYLOAD))
            .table("district", t -> t
                .column(ColumnPolicy.builder("country").role(ColumnRole.FOREIGN_KEY).references("region", "country").build())
                .column(ColumnPolicy.builder("code").role(ColumnRole.FOREIGN_KEY).references("region", "code").build())
                .column(ColumnPolicy.builder("variant").role(ColumnRole.FOREIGN_KEY).references("region", "variant").build())
                .column("district_no", ColumnRole.PRIMARY_KEY, SurrogateStrategy.PASSTHROUGH_SURROGATE))
            .build();
    }

    @Test
    void widerCompositeFkFailsClosedInsteadOfDroppingTheExtraColumn() {
        Assumptions.assumeTrue(sourceDs != null, "Docker/PostgreSQL not available");

        IncognitoException.ConstraintException ex = assertThrows(IncognitoException.ConstraintException.class, () ->
            IncognitoPipeline.builder()
                .source(sourceDs).target(targetDs).ephemeralSalt().policy(policy())
                .stage(new SchemaDiscoveryStage())
                .stage(new TableTransformLoadStage())
                .build()
                .execute(),
            "a composite FK wider than the parent PK must fail closed, not silently drop the extra column");

        assertTrue(ex.getMessage().contains("is not exactly that table's primary key"),
            "message should explain the wider-than-PK problem: " + ex.getMessage());
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
