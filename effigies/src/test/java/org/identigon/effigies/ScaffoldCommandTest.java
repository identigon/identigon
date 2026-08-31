package org.identigon.effigies;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import org.identigon.incognito.engine.SchemaInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScaffoldCommandTest {

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

        assertTrue(content.contains("autoInfer: false"));
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
