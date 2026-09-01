package org.identigon.effigies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import org.identigon.incognito.engine.SchemaInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScaffoldCommandTest {

    private record Result(int code, String out, String err) {}

    private static Result invoke(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = ScaffoldCommand.execute(args,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void helpFlagPrintsUsageAndSucceedsWithoutAnyOtherArgRequired() {
        Result r = invoke("--help");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("Usage: scaffold"));
    }

    @Test
    void helpFlagIsRecognisedAnywhereInArgsNotJustFirst() {
        Result r = invoke("--source-url", "jdbc:h2:mem:unused", "-h");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("Usage: scaffold"));
    }

    @Test
    void refusesToOverwriteAnExistingFileWithoutForce(@TempDir File tempDir) throws Exception {
        File file = new File(tempDir, "policy.scaffold.yaml");
        Files.writeString(file.toPath(), "pre-existing content");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = ScaffoldCommand.run(new SimpleDataSource("jdbc:no-such-dialect://nowhere", "u", "p"),
            file, false,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(1, code);
        String errStr = err.toString(StandardCharsets.UTF_8);
        assertTrue(errStr.contains("already exists"), errStr);
        assertTrue(errStr.contains("--force"), errStr);
        assertEquals("pre-existing content", Files.readString(file.toPath()),
            "must not touch the file - or even connect to the database - when refusing to overwrite");
    }

    @Test
    void forceOverwritesAnExistingFile(@TempDir File tempDir) throws Exception {
        File file = new File(tempDir, "policy.scaffold.yaml");
        Files.writeString(file.toPath(), "pre-existing content");
        String url = "jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url, "sa", "");
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE t (id BIGINT PRIMARY KEY)");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = ScaffoldCommand.run(new SimpleDataSource(url, "sa", ""), file, true,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(0, code, "err: " + err.toString(StandardCharsets.UTF_8));
        assertTrue(Files.readString(file.toPath()).contains("tables:"),
            "the file must actually have been rewritten with a scaffold");
    }

    @Test
    void testWriteScaffold(@TempDir File tempDir) throws Exception {
        File file = new File(tempDir, "policy.scaffold.yaml");

        SchemaInspector.TableMetadata t1 = new SchemaInspector.TableMetadata(
            "users",
            List.of("id"),
            Map.of(),
            List.of(),
            List.of("id", "name", "gen_col"),
            List.of("gen_col"),
            List.of(),
            Map.of("id", Types.INTEGER, "name", Types.VARCHAR, "gen_col", Types.VARCHAR),
            List.of()
        );

        SchemaInspector.TableMetadata t2 = new SchemaInspector.TableMetadata(
            "orders",
            List.of("id"),
            Map.of("user_id", "users"),
            List.of(),
            List.of("id", "user_id"),
            List.of(),
            List.of(),
            Map.of("id", Types.INTEGER, "user_id", Types.INTEGER),
            List.of()
        );

        ScaffoldCommand.writeScaffold(file, List.of(t1, t2));

        String content = Files.readString(file.toPath());

        assertTrue(content.contains("tables:"));
        assertTrue(content.contains("JDBC's own name"),
            "must disclose that reported types are JDBC's, not the database's own: " + content);
        assertTrue(content.contains("  users:"));
        assertTrue(content.contains("      id:            # type: INTEGER, pk"));
        assertTrue(content.contains("        role:              #"));
        assertTrue(content.contains("      name:            # type: VARCHAR"));
        assertFalse(content.contains("gen_col"));

        assertTrue(content.contains("  orders:"));
        assertTrue(content.contains("      user_id:            # type: INTEGER, fk -> users"));
    }

    @Test
    void structuralPkAndFkAreSuggestedWithAPreFilledReference(@TempDir File tempDir) throws Exception {
        File file = new File(tempDir, "policy.scaffold.yaml");

        SchemaInspector.TableMetadata customers = new SchemaInspector.TableMetadata(
            "customers", List.of("id"), Map.of(), List.of(), List.of("id"), List.of(), List.of(),
            Map.of("id", Types.INTEGER), List.of());
        SchemaInspector.TableMetadata orders = new SchemaInspector.TableMetadata(
            "orders", List.of("id"), Map.of("customer_id", "customers"), List.of(),
            List.of("id", "customer_id"), List.of(), List.of(),
            Map.of("id", Types.INTEGER, "customer_id", Types.INTEGER), List.of());

        ScaffoldCommand.writeScaffold(file, List.of(customers, orders));
        String content = Files.readString(file.toPath());

        // The PK column: a structural fact, not a heuristic guess.
        assertTrue(content.contains("Suggestion: PRIMARY_KEY, structurally discovered - not a guess"));
        assertTrue(content.contains("surrogateStrategy: # TODO if PRIMARY_KEY (Suggestion: SEQUENTIAL_LONG)"));

        // The FK column: also structural, with the target's own PK column pre-filled - not left
        // for the author to look up and retype.
        assertTrue(content.contains("Suggestion: FOREIGN_KEY -> customers, structurally discovered - not a guess"));
        assertTrue(content.contains("references:        # TODO if FOREIGN_KEY (Suggestion: {table: customers, column: id})"));
    }

    @Test
    void directIdSuggestionIncludesAMatchingStrategyStub(@TempDir File tempDir) throws Exception {
        File file = new File(tempDir, "policy.scaffold.yaml");

        SchemaInspector.TableMetadata customers = new SchemaInspector.TableMetadata(
            "customers", List.of(), Map.of(), List.of(), List.of("email"), List.of(), List.of(),
            Map.of("email", Types.VARCHAR), List.of());

        ScaffoldCommand.writeScaffold(file, List.of(customers));
        String content = Files.readString(file.toPath());

        assertTrue(content.contains("Suggestion: DIRECT_ID based on EMAIL_PATTERN"));
        assertTrue(content.contains("directIdStrategy:  # TODO if DIRECT_ID (Suggestion: ALTEREGO_EMAIL)"));
    }

    @Test
    void quasiIdSuggestionIncludesADirectIdStrategyHintStub(@TempDir File tempDir) throws Exception {
        File file = new File(tempDir, "policy.scaffold.yaml");

        SchemaInspector.TableMetadata customers = new SchemaInspector.TableMetadata(
            "customers", List.of(), Map.of(), List.of(), List.of("postcode"), List.of(), List.of(),
            Map.of("postcode", Types.VARCHAR), List.of());

        ScaffoldCommand.writeScaffold(file, List.of(customers));
        String content = Files.readString(file.toPath());

        assertTrue(content.contains("Suggestion: QUASI_ID based on POSTCODE_PATTERN"));
        assertTrue(content.contains("directIdStrategy:  # TODO if QUASI_ID (Suggestion: ALTEREGO_POSTCODE)"));
    }

    @Test
    void quasiIdSuggestionWithNoTypedGeneratorGetsNoStub(@TempDir File tempDir) throws Exception {
        File file = new File(tempDir, "policy.scaffold.yaml");

        SchemaInspector.TableMetadata employees = new SchemaInspector.TableMetadata(
            "employees", List.of(), Map.of(), List.of(), List.of("date_of_birth"), List.of(), List.of(),
            Map.of("date_of_birth", Types.DATE), List.of());

        ScaffoldCommand.writeScaffold(file, List.of(employees));
        String content = Files.readString(file.toPath());

        // DOB_PATTERN infers QUASI_ID, but a temporal SYNTHESISE needs no hint (SPEC Appendix B) -
        // no directIdStrategy stub should be suggested.
        assertTrue(content.contains("Suggestion: QUASI_ID based on DOB_PATTERN"));
        assertFalse(content.contains("directIdStrategy"));
    }

    @Test
    void sensitiveSuggestionIncludesADistinguishingStub(@TempDir File tempDir) throws Exception {
        File file = new File(tempDir, "policy.scaffold.yaml");

        SchemaInspector.TableMetadata customers = new SchemaInspector.TableMetadata(
            "customers", List.of(), Map.of(), List.of(), List.of("card_number"), List.of(), List.of(),
            Map.of("card_number", Types.VARCHAR), List.of());

        ScaffoldCommand.writeScaffold(file, List.of(customers));
        String content = Files.readString(file.toPath());

        assertTrue(content.contains("Suggestion: SENSITIVE based on CREDIT_CARD_PATTERN"));
        assertTrue(content.contains("distinguishing:    # TODO if SENSITIVE (true|false - does this alone identify someone?)"));
    }
}
