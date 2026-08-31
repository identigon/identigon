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
        SchemaInspector.TableMetadata customers = new SchemaInspector.TableMetadata(
            "CUSTOMERS", List.of("ID"), Map.of(), List.of(), List.of("ID", "NAME"), List.of(),
            List.of(), Map.of("ID", Types.BIGINT, "NAME", Types.VARCHAR), List.of());

        AnonymisationPolicy policy = AnonymisationPolicy.builder()
            .table("CUSTOMERS", t -> t
                .column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG)
                .column("NAME", ColumnRole.PAYLOAD))
            .build();

        assertDoesNotThrow(() -> new SchemaDiscoveryStage().validate(List.of(customers), policy));
    }

    @Test
    void anUnclassifiedColumnFailsClosedTheSameWayProcessDoes() {
        SchemaInspector.TableMetadata customers = new SchemaInspector.TableMetadata(
            "CUSTOMERS", List.of("ID"), Map.of(), List.of(), List.of("ID", "NAME"), List.of(),
            List.of(), Map.of("ID", Types.BIGINT, "NAME", Types.VARCHAR), List.of());

        // NAME left unclassified.
        AnonymisationPolicy policy = AnonymisationPolicy.builder()
            .table("CUSTOMERS", t -> t.column("ID", ColumnRole.PRIMARY_KEY, SurrogateStrategy.SEQUENTIAL_LONG))
            .build();

        IncognitoException.ConfigException ex = assertThrows(IncognitoException.ConfigException.class,
            () -> new SchemaDiscoveryStage().validate(List.of(customers), policy));
        assertTrue(ex.getMessage().contains("NAME"), ex.getMessage());
        assertTrue(ex.getMessage().contains("Fail-closed"), ex.getMessage());
    }
}
