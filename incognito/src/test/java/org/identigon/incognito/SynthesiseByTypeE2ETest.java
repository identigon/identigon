package org.identigon.incognito;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.identigon.incognito.api.ColumnRole;
import org.identigon.incognito.api.DirectIdStrategy;
import org.identigon.incognito.api.IncognitoException;
import org.identigon.incognito.api.IncognitoPipeline;
import org.identigon.incognito.api.PipelineResult;
import org.identigon.incognito.api.QuasiIdStrategy;
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
 * SYNTHESISE-by-type routing (SPEC Appendix B). A {@code QUASI_ID SYNTHESISE} column may carry an
 * author-declared {@code directIdStrategy} hint to synthesise a guaranteed-fictional typed value
 * (here {@code ALTEREGO_CITY}); a SYNTHESISE column whose source type has no built-in mapping and no
 * hint (here an {@code INTEGER}) must fail closed at discovery rather than silently shape-fabricate.
 * Requires Docker; skips gracefully otherwise.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SynthesiseByTypeE2ETest {

    private PostgreSQLContainer pg;
    private String jdbcBase;

    @BeforeAll
    void setUp() {
        boolean dockerAvailable;
        try {
            dockerAvailable = org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable, "Docker not available — skipping Testcontainers E2E");
        pg = new PostgreSQLContainer(TestPostgres.IMAGE)
            .withDatabaseName("synth").withUsername("test").withPassword("test");
        pg.start();
        jdbcBase = "jdbc:postgresql://" + pg.getHost() + ":" + pg.getFirstMappedPort() + "/";
    }

    @AfterAll
    void tearDown() {
        if (pg != null) pg.stop();
    }

    @Test
    void typedHintSynthesisesAFictionalCity() throws Exception {
        Assumptions.assumeTrue(pg != null, "Docker/PostgreSQL not available");
        String ddl = """
            CREATE TABLE people (
                id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                city  VARCHAR(100) NOT NULL
            );
            """;
        DataSource[] ds = freshDatabases("city", ddl,
            "INSERT INTO people (city) VALUES ('Q')");   // 1-char source: shape-preserving would stay 1 char

        AnonymisationPolicy policy = AnonymisationPolicy.builder()
            .table("people", t -> t
                .column("id", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                .column(ColumnPolicy.builder("city").role(ColumnRole.QUASI_ID)
                    .quasiIdStrategy(QuasiIdStrategy.SYNTHESISE)
                    .directIdStrategy(DirectIdStrategy.ALTEREGO_CITY).build()))
            .build();

        PipelineResult result = run(ds, policy);
        assertTrue(result.success(), "pipeline should succeed");

        try (Connection conn = ds[1].getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT city FROM people")) {
            rs.next();
            String city = rs.getString(1);
            assertNotEquals("Q", city, "the real value must not survive");
            // A shape-preserving scramble of a 1-char input stays 1 char; the typed city generator
            // produces a real (multi-character) fictional city name — proving the hint was routed.
            assertTrue(city.length() > 1, "typed city hint should yield a real city name, got: " + city);
        }
    }

    @Test
    void unmappedTypeSynthesiseFailsClosed() throws Exception {
        Assumptions.assumeTrue(pg != null, "Docker/PostgreSQL not available");
        String ddl = """
            CREATE TABLE readings (
                id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                score  INTEGER NOT NULL
            );
            """;
        DataSource[] ds = freshDatabases("numeric", ddl, "INSERT INTO readings (score) VALUES (42)");

        // A numeric QUASI_ID SYNTHESISE with no directIdStrategy hint has no built-in mapping — abort.
        AnonymisationPolicy policy = AnonymisationPolicy.builder()
            .table("readings", t -> t
                .column("id", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                .column(ColumnPolicy.builder("score").role(ColumnRole.QUASI_ID)
                    .quasiIdStrategy(QuasiIdStrategy.SYNTHESISE).build()))
            .build();

        IncognitoException.ConfigException ex = assertThrows(IncognitoException.ConfigException.class,
            () -> run(ds, policy), "SYNTHESISE on an unmapped (numeric) type must fail closed");
        assertTrue(ex.getMessage().contains("SYNTHESISE"), "message names the offending strategy");
    }

    // --- helpers ---

    private DataSource[] freshDatabases(String tag, String ddl, String seedSql) throws SQLException {
        String src = "synth_" + tag + "_src";
        String tgt = "synth_" + tag + "_tgt";
        try (Connection admin = DriverManager.getConnection(jdbcBase + "postgres", pg.getUsername(), pg.getPassword())) {
            admin.setAutoCommit(true);
            try (Statement stmt = admin.createStatement()) {
                stmt.execute("DROP DATABASE IF EXISTS " + src);
                stmt.execute("DROP DATABASE IF EXISTS " + tgt);
                stmt.execute("CREATE DATABASE " + src);
                stmt.execute("CREATE DATABASE " + tgt);
            }
        }
        for (String db : new String[]{src, tgt}) {
            try (Connection conn = DriverManager.getConnection(jdbcBase + db, pg.getUsername(), pg.getPassword())) {
                conn.setAutoCommit(true);
                try (Statement stmt = conn.createStatement()) { stmt.execute(ddl); }
            }
        }
        try (Connection conn = DriverManager.getConnection(jdbcBase + src, pg.getUsername(), pg.getPassword())) {
            conn.setAutoCommit(true);
            try (Statement stmt = conn.createStatement()) { stmt.execute(seedSql); }
        }
        return new DataSource[]{
            new SimpleDataSource(jdbcBase + src, pg.getUsername(), pg.getPassword()),
            new SimpleDataSource(jdbcBase + tgt, pg.getUsername(), pg.getPassword())
        };
    }

    private PipelineResult run(DataSource[] ds, AnonymisationPolicy policy) {
        return IncognitoPipeline.builder()
            .source(ds[0]).target(ds[1]).ephemeralSalt().policy(policy)
            .stage(new SchemaDiscoveryStage())
            .stage(new TableTransformLoadStage())
            .stage(new VerificationStage())
            .build().execute();
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
