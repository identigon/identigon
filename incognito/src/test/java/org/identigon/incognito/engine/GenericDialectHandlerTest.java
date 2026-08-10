package org.identigon.incognito.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure unit coverage for {@link GenericDialectHandler#buildInsertSql} — no database needed. */
class GenericDialectHandlerTest {

    private final GenericDialectHandler handler = new GenericDialectHandler();

    @Test
    void quotesTableAndColumnIdentifiers() {
        String sql = handler.buildInsertSql("MixedCase", List.of("Id", "Name"), false);
        assertEquals("INSERT INTO \"MixedCase\" (\"Id\", \"Name\") VALUES (?, ?)", sql);
    }

    @Test
    void hasIdentityPkIsIgnored() {
        // Generic ANSI SQL has no OVERRIDING SYSTEM VALUE equivalent; the flag is a Postgres-only
        // concern and must not change the generated SQL here.
        String withIdentity = handler.buildInsertSql("t", List.of("id"), true);
        String withoutIdentity = handler.buildInsertSql("t", List.of("id"), false);
        assertEquals(withoutIdentity, withIdentity);
    }
}
