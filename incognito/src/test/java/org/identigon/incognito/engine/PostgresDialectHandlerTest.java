package org.identigon.incognito.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure unit coverage for {@link PostgresDialectHandler#buildInsertSql} - no database needed, since
 * it's just string assembly. Complements {@link PostgresDialectHandlerFkQuotingE2ETest} (which
 * covers FK constraint quoting) and {@code PostgresDialectHandlerIdentifierQuotingE2ETest} (which
 * covers the DB-touching methods this class can't reach without a connection).
 */
class PostgresDialectHandlerTest {

    private final PostgresDialectHandler handler = new PostgresDialectHandler();

    @Test
    void quotesTableAndColumnIdentifiers() {
        String sql = handler.buildInsertSql("MixedCase", List.of("Id", "Name"), false);
        assertEquals("INSERT INTO \"MixedCase\" (\"Id\", \"Name\") VALUES (?, ?)", sql);
    }

    @Test
    void identityPkAddsOverridingSystemValue() {
        String sql = handler.buildInsertSql("t", List.of("id"), true);
        assertEquals("INSERT INTO \"t\" (\"id\") VALUES (?)", sql.replace("OVERRIDING SYSTEM VALUE ", ""));
        assertEquals(
            "INSERT INTO \"t\" (\"id\") OVERRIDING SYSTEM VALUE VALUES (?)",
            sql);
    }

    @Test
    void embeddedDoubleQuoteInIdentifierIsDoubled() {
        // PostgreSQL's own escaping convention for a literal " inside a delimited identifier.
        String sql = handler.buildInsertSql("weird\"table", List.of("col"), false);
        assertEquals("INSERT INTO \"weird\"\"table\" (\"col\") VALUES (?)", sql);
    }
}
