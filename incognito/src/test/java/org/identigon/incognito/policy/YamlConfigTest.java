package org.identigon.incognito.policy;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.identigon.incognito.api.ColumnRole;
import org.identigon.incognito.api.DirectIdStrategy;
import org.identigon.incognito.api.IncognitoException;
import org.identigon.incognito.api.StructuralUniquenessMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlConfigTest {

    @TempDir Path tempDir;

    @Test
    void testParseValidConfig() {
        // autoInfer: true is a no-op left over from before v2.0.0 (AnonymisationPolicy.Builder no
        // longer has the method) - included deliberately, to demonstrate a pre-v2.0.0 policy.yaml
        // that still declares it keeps parsing rather than failing on an unrecognised key.
        String yamlString = """
            autoInfer: true
            maxCategoricalCardinality: 100
            tables:
              users:
                columns:
                  id:
                    role: PRIMARY_KEY
                    surrogateStrategy: SEQUENTIAL_LONG
                  email:
                    role: DIRECT_ID
                    directIdStrategy: ALTEREGO_EMAIL
                  dob:
                    role: QUASI_ID
                    quasiIdStrategy: SYNTHESISE
                  status:
                    role: PAYLOAD
              orders:
                columns:
                  id:
                    role: PRIMARY_KEY
                    surrogateStrategy: UUID_V4
                  user_id:
                    role: FOREIGN_KEY
                    references:
                      table: users
                      column: id
            """;

        InputStream inputStream = new ByteArrayInputStream(yamlString.getBytes(StandardCharsets.UTF_8));
        YamlPolicyParser parser = new YamlPolicyParser();

        AnonymisationPolicy policy = parser.parse(inputStream);

        assertEquals(100, policy.maxCategoricalCardinality());
        assertNotNull(policy.tables());
        assertEquals(2, policy.tables().size());

        TablePolicy usersTable = policy.table("users").orElseThrow();
        assertEquals("users", usersTable.tableName());
        assertEquals(4, usersTable.columns().size());

        ColumnPolicy emailCol = usersTable.column("email").orElseThrow();
        assertEquals("email", emailCol.columnName());
        assertEquals(ColumnRole.DIRECT_ID, emailCol.role());
        assertEquals(DirectIdStrategy.ALTEREGO_EMAIL, emailCol.directIdStrategy());

        TablePolicy ordersTable = policy.table("orders").orElseThrow();
        assertEquals("orders", ordersTable.tableName());
        assertEquals(2, ordersTable.columns().size());

        ColumnPolicy userIdCol = ordersTable.column("user_id").orElseThrow();
        assertEquals("user_id", userIdCol.columnName());
        assertEquals(ColumnRole.FOREIGN_KEY, userIdCol.role());
        assertEquals("users", userIdCol.referencedTable());
        assertEquals("id", userIdCol.referencedColumn());
    }

    @Test
    void testParseInvalidYaml() {
        String yamlString = """
            autoInfer: true
              invalid_indentation: 100
            """;

        InputStream inputStream = new ByteArrayInputStream(yamlString.getBytes(StandardCharsets.UTF_8));
        YamlPolicyParser parser = new YamlPolicyParser();

        assertThrows(IncognitoException.ConfigException.class, () -> parser.parse(inputStream));
    }

    @Test
    void columnMissingRoleKeyParsesWithNullRoleNotPayloadDefault() {
        // A column entry present under `columns:` but missing the `role:` key (e.g. a policy
        // author who declared a strategy but forgot the role) must NOT silently resolve to
        // ColumnRole.PAYLOAD - SchemaDiscoveryStage relies on role() staying null to fail closed.
        String yamlString = """
            tables:
              customers:
                columns:
                  ssn:
                    directIdStrategy: ALTEREGO_GENERIC
            """;

        InputStream inputStream = new ByteArrayInputStream(yamlString.getBytes(StandardCharsets.UTF_8));
        AnonymisationPolicy policy = new YamlPolicyParser().parse(inputStream);

        ColumnPolicy ssnCol = policy.table("customers").orElseThrow().column("ssn").orElseThrow();
        assertNull(ssnCol.role(), "a column missing the role: key must parse with a null role, "
            + "never default to PAYLOAD");
    }

    @Test
    void columnWithBlankRoleValueParsesWithNullRoleNotAnEnumException() {
        // Distinct from the missing-key case above: `scaffold`'s own output always emits every
        // key with a blank value ("role:              # TODO classify ..." - the key IS present,
        // its YAML value is null), not an absent key. ColumnRole.valueOf(String.valueOf(null))
        // used to evaluate ColumnRole.valueOf("NULL") and throw IllegalArgumentException instead
        // of leaving the field null for fail-closed validation to report clearly.
        String yamlString = """
            tables:
              customers:
                columns:
                  ssn:
                    role:
            """;

        InputStream inputStream = new ByteArrayInputStream(yamlString.getBytes(StandardCharsets.UTF_8));
        AnonymisationPolicy policy = new YamlPolicyParser().parse(inputStream);

        ColumnPolicy ssnCol = policy.table("customers").orElseThrow().column("ssn").orElseThrow();
        assertNull(ssnCol.role(), "a column with a blank role: value must parse with a null role, "
            + "never throw trying to resolve it as an enum constant");
    }

    @Test
    void testParseEmptyConfig() {
        String yamlString = "";

        InputStream inputStream = new ByteArrayInputStream(yamlString.getBytes(StandardCharsets.UTF_8));
        YamlPolicyParser parser = new YamlPolicyParser();

        AnonymisationPolicy policy = parser.parse(inputStream);

        assertEquals(64, policy.maxCategoricalCardinality());
        assertEquals(StructuralUniquenessMode.OFF, policy.structuralUniqueness(), "off by default (SPEC §2.4)");
        assertEquals(5, policy.structuralRarenessK());
        assertTrue(policy.tables().isEmpty());
    }

    @Test
    void mistypedOptionalColumnKeyFailsRatherThanSilentlyDropping() {
        // A typo on an optional key (jitterdays for jitterDays) must not vanish silently: the run
        // would otherwise use JITTER_DAYS's internal default window instead of the declared one,
        // diverging from what the policy says with no signal (the bug the new check closes).
        String yamlString = """
            tables:
              orders:
                columns:
                  ordered_on:
                    role: QUASI_ID
                    quasiIdStrategy: JITTER_DAYS
                    jitterdays: 10
            """;

        InputStream inputStream = new ByteArrayInputStream(yamlString.getBytes(StandardCharsets.UTF_8));
        YamlPolicyParser parser = new YamlPolicyParser();

        IncognitoException.ConfigException ex = assertThrows(
            IncognitoException.ConfigException.class, () -> parser.parse(inputStream));
        assertTrue(ex.getMessage().contains("'jitterdays'"), ex.getMessage());
        assertTrue(ex.getMessage().contains("'ordered_on'"), ex.getMessage());
        assertTrue(ex.getMessage().contains("'orders'"), ex.getMessage());
    }

    @Test
    void everyUnrecognisedKeyIsReportedInOneRunNotOnePerRetry() {
        // Matches SchemaDiscoveryStage's "fix all at once" convention: three typos, at three
        // different nesting levels, all in one exception rather than one per re-run.
        String yamlString = """
            maxCategoricalCardinalty: 64
            tables:
              customers:
                extraTableKey: nonsense
                columns:
                  email:
                    role: DIRECT_ID
                    directIdStrategey: ALTEREGO_EMAIL
            """;

        InputStream inputStream = new ByteArrayInputStream(yamlString.getBytes(StandardCharsets.UTF_8));
        YamlPolicyParser parser = new YamlPolicyParser();

        IncognitoException.ConfigException ex = assertThrows(
            IncognitoException.ConfigException.class, () -> parser.parse(inputStream));
        assertTrue(ex.getMessage().contains("'maxCategoricalCardinalty'"), ex.getMessage());
        assertTrue(ex.getMessage().contains("'extraTableKey'"), ex.getMessage());
        assertTrue(ex.getMessage().contains("'directIdStrategey'"), ex.getMessage());
    }

    @Test
    void retiredAutoInferKeyIsToleratedNotJustAnyUnrecognisedKey() {
        // The one deliberate exception (RETIRED_ROOT_KEYS): a pre-v2.0.0 policy.yaml carrying
        // autoInfer must keep parsing, unlike every other unrecognised key.
        String yamlString = """
            autoInfer: true
            tables:
              customers:
                columns:
                  status:
                    role: PAYLOAD
            """;

        InputStream inputStream = new ByteArrayInputStream(yamlString.getBytes(StandardCharsets.UTF_8));
        AnonymisationPolicy policy = new YamlPolicyParser().parse(inputStream);

        assertEquals(1, policy.tables().size());
    }

    @Test
    void unrecognisedKeyDiagnosticSurvivesParsingFromAPathNotJustAnInputStream() throws Exception {
        // parse(Path) delegates to parse(InputStream) then closes the file - it must rethrow that
        // ConfigException unchanged, not catch-and-rewrap it behind a generic "Failed to read YAML"
        // message that discards exactly the detail (which key, where) the user needs to fix it.
        String yamlString = """
            tables:
              customers:
                columns:
                  email:
                    role: DIRECT_ID
                    directIdStrategey: ALTEREGO_EMAIL
            """;
        Path policyFile = tempDir.resolve("policy.yaml");
        Files.writeString(policyFile, yamlString);

        IncognitoException.ConfigException ex = assertThrows(
            IncognitoException.ConfigException.class, () -> new YamlPolicyParser().parse(policyFile));
        assertTrue(ex.getMessage().contains("'directIdStrategey'"), ex.getMessage());
        assertFalse(ex.getMessage().contains("Failed to read YAML"), ex.getMessage());
    }

    @Test
    void structuralUniquenessKeysParseFromYaml() {
        String yamlString = """
            structuralUniqueness: REPORT
            structuralRarenessK: 10
            """;

        InputStream inputStream = new ByteArrayInputStream(yamlString.getBytes(StandardCharsets.UTF_8));
        AnonymisationPolicy policy = new YamlPolicyParser().parse(inputStream);

        assertEquals(StructuralUniquenessMode.REPORT, policy.structuralUniqueness());
        assertEquals(10, policy.structuralRarenessK());
    }
}
