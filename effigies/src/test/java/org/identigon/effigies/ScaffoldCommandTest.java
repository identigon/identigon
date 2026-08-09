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
}
