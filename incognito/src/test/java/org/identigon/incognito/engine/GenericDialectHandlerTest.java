package org.identigon.incognito.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure unit coverage for {@link GenericDialectHandler} - no database needed. */
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

  /**
   * {@code GenericDialectHandler} doesn't override {@code bindValue}, so it gets {@code
   * DialectHandler}'s plain-{@code setObject} default - unlike {@code PostgresDialectHandler}, a
   * {@code String} value gets no {@code Types.OTHER} coercion here, proving that rule really is
   * Postgres-specific and not baked into the shared default.
   */
  @Test
  void bindValueUsesThePlainDefaultEvenForAStringValue() throws Exception {
    List<String> calls = new ArrayList<>();
    handler.bindValue(recordingPreparedStatement(calls), 1, "mpaa_rating_value");
    assertEquals(List.of("setObject(1, mpaa_rating_value)"), calls);
  }

  @SuppressWarnings({"PMD.CompareObjectsWithEquals", "PMD.UseProperClassLoader"})
  private static PreparedStatement recordingPreparedStatement(List<String> calls) {
    InvocationHandler h =
        (proxy, method, args) ->
            switch (method.getName()) {
              case "setObject" -> {
                calls.add("setObject(" + args[0] + ", " + args[1] + ")");
                yield null;
              }
              case "equals" -> proxy == args[0];
              case "hashCode" -> System.identityHashCode(proxy);
              case "toString" -> "FakePreparedStatement";
              default -> null;
            };
    return (PreparedStatement)
        Proxy.newProxyInstance(
            GenericDialectHandlerTest.class.getClassLoader(),
            new Class<?>[] {PreparedStatement.class},
            h);
  }
}
