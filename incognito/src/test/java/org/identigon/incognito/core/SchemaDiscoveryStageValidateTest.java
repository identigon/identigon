package org.identigon.incognito.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import org.identigon.incognito.api.ColumnRole;
import org.identigon.incognito.api.IncognitoException;
import org.identigon.incognito.api.SurrogateStrategy;
import org.identigon.incognito.engine.SchemaInspector;
import org.identigon.incognito.policy.AnonymisationPolicy;
import org.junit.jupiter.api.Test;

/**
 * {@link SchemaDiscoveryStage#validate} is the same fail-closed check {@link
 * SchemaDiscoveryStage#process} runs, but callable directly against an already-discovered schema -
 * no {@code PipelineContext}, no target, no database connection of any kind. This is what makes
 * effigies' {@code validate} command possible; these tests construct {@code TableMetadata} fixtures
 * directly rather than inspecting a real database, to demonstrate exactly that.
 */
class SchemaDiscoveryStageValidateTest {

  @Test
  void aFullyClassifiedTablePassesWithNoDatabaseInvolvedAtAll() {
    SchemaInspector.TableMetadata customers =
        new SchemaInspector.TableMetadata(
            "CUSTOMERS",
            List.of("ID"),
            Map.of(),
            List.of(),
            List.of("ID", "NAME"),
            List.of(),
            List.of(),
            Map.of("ID", Types.BIGINT, "NAME", Types.VARCHAR),
            List.of());

    AnonymisationPolicy policy =
        AnonymisationPolicy.builder()
            .table(
                "CUSTOMERS",
                t ->
                    t.column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                        .column("NAME", ColumnRole.PAYLOAD))
            .build();

    assertDoesNotThrow(() -> new SchemaDiscoveryStage().validate(List.of(customers), policy));
  }

  @Test
  void anUnclassifiedColumnFailsClosedTheSameWayProcessDoes() {
    SchemaInspector.TableMetadata customers =
        new SchemaInspector.TableMetadata(
            "CUSTOMERS",
            List.of("ID"),
            Map.of(),
            List.of(),
            List.of("ID", "NAME"),
            List.of(),
            List.of(),
            Map.of("ID", Types.BIGINT, "NAME", Types.VARCHAR),
            List.of());

    // NAME left unclassified.
    AnonymisationPolicy policy =
        AnonymisationPolicy.builder()
            .table(
                "CUSTOMERS",
                t -> t.column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG))
            .build();

    IncognitoException.ConfigException ex =
        assertThrows(
            IncognitoException.ConfigException.class,
            () -> new SchemaDiscoveryStage().validate(List.of(customers), policy));
    assertTrue(ex.getMessage().contains("NAME"), ex.getMessage());
    assertTrue(ex.getMessage().contains("Fail-closed"), ex.getMessage());
  }

  /**
   * A {@code FOREIGN_KEY} column with no {@code references} used to validate cleanly and then hit a
   * raw {@code NullPointerException} at {@code run} time (v3.1.0 tutorial-feedback finding): {@code
   * TableTransformLoadStage.buildFkTransformer} passes the null {@code referencedTable} straight to
   * the key-translation store's lookup. This is the fail-closed check that catches it here instead
   * - and, since the parent's single-column PK is discoverable, offers the exact suggestion {@code
   * ScaffoldCommand} would.
   */
  @Test
  void aForeignKeyColumnWithNoReferencesFailsClosedWithASuggestion() {
    SchemaInspector.TableMetadata customers =
        new SchemaInspector.TableMetadata(
            "CUSTOMERS",
            List.of("ID"),
            Map.of(),
            List.of(),
            List.of("ID"),
            List.of(),
            List.of(),
            Map.of("ID", Types.BIGINT),
            List.of());
    SchemaInspector.TableMetadata orders =
        new SchemaInspector.TableMetadata(
            "ORDERS",
            List.of("ID"),
            Map.of("CUSTOMER_ID", "CUSTOMERS"),
            List.of(),
            List.of("ID", "CUSTOMER_ID"),
            List.of(),
            List.of(),
            Map.of("ID", Types.BIGINT, "CUSTOMER_ID", Types.BIGINT),
            List.of(
                new SchemaInspector.ForeignKeyConstraint(
                    "CUSTOMERS", List.of("CUSTOMER_ID"), List.of("ID"))));

    // CUSTOMER_ID declared FOREIGN_KEY but never given a references block.
    AnonymisationPolicy policy =
        AnonymisationPolicy.builder()
            .table(
                "ORDERS",
                t ->
                    t.column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                        .column("CUSTOMER_ID", ColumnRole.FOREIGN_KEY))
            .build();

    IncognitoException.ConfigException ex =
        assertThrows(
            IncognitoException.ConfigException.class,
            () -> new SchemaDiscoveryStage().validate(List.of(customers, orders), policy));
    assertTrue(ex.getMessage().contains("'CUSTOMER_ID'"), ex.getMessage());
    assertTrue(ex.getMessage().contains("does not declare a references block"), ex.getMessage());
    assertTrue(
        ex.getMessage().contains("references: { table: CUSTOMERS, column: ID }"), ex.getMessage());
  }

  @Test
  void aForeignKeyColumnWithReferencesDeclaredPasses() {
    SchemaInspector.TableMetadata customers =
        new SchemaInspector.TableMetadata(
            "CUSTOMERS",
            List.of("ID"),
            Map.of(),
            List.of(),
            List.of("ID"),
            List.of(),
            List.of(),
            Map.of("ID", Types.BIGINT),
            List.of());
    SchemaInspector.TableMetadata orders =
        new SchemaInspector.TableMetadata(
            "ORDERS",
            List.of("ID"),
            Map.of("CUSTOMER_ID", "CUSTOMERS"),
            List.of(),
            List.of("ID", "CUSTOMER_ID"),
            List.of(),
            List.of(),
            Map.of("ID", Types.BIGINT, "CUSTOMER_ID", Types.BIGINT),
            List.of(
                new SchemaInspector.ForeignKeyConstraint(
                    "CUSTOMERS", List.of("CUSTOMER_ID"), List.of("ID"))));

    AnonymisationPolicy policy =
        AnonymisationPolicy.builder()
            .table(
                "ORDERS",
                t ->
                    t.column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                        .column(
                            org.identigon.incognito.policy.ColumnPolicy.builder("CUSTOMER_ID")
                                .role(ColumnRole.FOREIGN_KEY)
                                .references("CUSTOMERS", "ID")
                                .build()))
            .build();

    assertDoesNotThrow(
        () -> new SchemaDiscoveryStage().validate(List.of(customers, orders), policy));
  }

  /**
   * A composite FK is resolved purely structurally by {@code buildFkTransformer} (it never reads
   * {@code referencedTable}/{@code referencedColumn}), so - unlike the single-column case above -
   * it must stay valid with no {@code references} block declared on either column.
   */
  @Test
  void aCompositeForeignKeyNeedsNoReferencesBlock() {
    SchemaInspector.TableMetadata authorship =
        new SchemaInspector.TableMetadata(
            "AUTHORSHIP",
            List.of("AUTHOR_ID", "BOOK_ID"),
            Map.of(),
            List.of(),
            List.of("AUTHOR_ID", "BOOK_ID"),
            List.of(),
            List.of(),
            Map.of("AUTHOR_ID", Types.BIGINT, "BOOK_ID", Types.BIGINT),
            List.of());
    SchemaInspector.TableMetadata chapter =
        new SchemaInspector.TableMetadata(
            "CHAPTER",
            List.of("ID"),
            Map.of("AUTHOR_ID", "AUTHORSHIP", "BOOK_ID", "AUTHORSHIP"),
            List.of(),
            List.of("ID", "AUTHOR_ID", "BOOK_ID"),
            List.of(),
            List.of(),
            Map.of("ID", Types.BIGINT, "AUTHOR_ID", Types.BIGINT, "BOOK_ID", Types.BIGINT),
            List.of(
                new SchemaInspector.ForeignKeyConstraint(
                    "AUTHORSHIP",
                    List.of("AUTHOR_ID", "BOOK_ID"),
                    List.of("AUTHOR_ID", "BOOK_ID"))));

    AnonymisationPolicy policy =
        AnonymisationPolicy.builder()
            .table(
                "CHAPTER",
                t ->
                    t.column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                        .column("AUTHOR_ID", ColumnRole.FOREIGN_KEY)
                        .column("BOOK_ID", ColumnRole.FOREIGN_KEY))
            .build();

    assertDoesNotThrow(
        () -> new SchemaDiscoveryStage().validate(List.of(authorship, chapter), policy));
  }
}
