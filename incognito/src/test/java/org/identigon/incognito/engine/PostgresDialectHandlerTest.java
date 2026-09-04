package org.identigon.incognito.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.util.ArrayList;
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
    assertEquals(
        "INSERT INTO \"t\" (\"id\") VALUES (?)", sql.replace("OVERRIDING SYSTEM VALUE ", ""));
    assertEquals("INSERT INTO \"t\" (\"id\") OVERRIDING SYSTEM VALUE VALUES (?)", sql);
  }

  @Test
  void embeddedDoubleQuoteInIdentifierIsDoubled() {
    // PostgreSQL's own escaping convention for a literal " inside a delimited identifier.
    String sql = handler.buildInsertSql("weird\"table", List.of("col"), false);
    assertEquals("INSERT INTO \"weird\"\"table\" (\"col\") VALUES (?)", sql);
  }

  /**
   * A {@code String} value is bound as {@code Types.OTHER} ('unknown') so PostgreSQL casts it to
   * the target column's actual type, the way a string literal would - this is what lets a kept
   * enum/user-type value (e.g. an {@code mpaa_rating}) round-trip, where a plain {@code VARCHAR}
   * bind fails with a type mismatch. {@link BulkDatabaseLoadStageTest} covers that this method is
   * actually reached from {@code insertRow}; this covers the coercion rule itself.
   */
  @Test
  void bindsStringValuesAsTypesOtherSoPostgresCastsThem() throws Exception {
    List<String> calls = new ArrayList<>();
    handler.bindValue(recordingPreparedStatement(calls), 1, "mpaa_rating_value");
    assertEquals(List.of("setObject(1, mpaa_rating_value, OTHER)"), calls);
  }

  @Test
  void bindsNonStringValuesPlainly() throws Exception {
    List<String> calls = new ArrayList<>();
    handler.bindValue(recordingPreparedStatement(calls), 2, 42L);
    assertEquals(List.of("setObject(2, 42)"), calls);
  }

  @Test
  void bindsNullPlainlyNotAsTypesOther() throws Exception {
    // null is not an instanceof String, so it takes the same plain path as any other type -
    // Types.OTHER is specifically the String-coercion rule, not a catch-all for "unknown".
    List<String> calls = new ArrayList<>();
    handler.bindValue(recordingPreparedStatement(calls), 3, null);
    assertEquals(List.of("setObject(3, null)"), calls);
  }

  // == and the test's own classloader are the correct choice for a proxy with no identity of its
  // own, matching BulkDatabaseLoadStageTest's established idiom for this same false-positive pair.
  @SuppressWarnings({"PMD.CompareObjectsWithEquals", "PMD.UseProperClassLoader"})
  private static PreparedStatement recordingPreparedStatement(List<String> calls) {
    InvocationHandler handler =
        (proxy, method, args) ->
            switch (method.getName()) {
              case "setObject" -> {
                calls.add(
                    args.length == 3
                        ? "setObject("
                            + args[0]
                            + ", "
                            + args[1]
                            + ", "
                            + sqlTypeName((int) args[2])
                            + ")"
                        : "setObject(" + args[0] + ", " + args[1] + ")");
                yield null;
              }
              case "equals" -> proxy == args[0];
              case "hashCode" -> System.identityHashCode(proxy);
              case "toString" -> "FakePreparedStatement";
              default -> null;
            };
    return (PreparedStatement)
        Proxy.newProxyInstance(
            PostgresDialectHandlerTest.class.getClassLoader(),
            new Class<?>[] {PreparedStatement.class},
            handler);
  }

  private static String sqlTypeName(int jdbcType) {
    return jdbcType == java.sql.Types.OTHER ? "OTHER" : String.valueOf(jdbcType);
  }
}
