package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dconneely.alterego.store.InMemoryMappingStore;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * A stored value that fails to decode into its canonical type (corrupted store, renamed enum
 * constant) throws {@link AlterEgoStoreException} with a useful message (spec section 5.1) —
 * shared by {@code stored()} and {@code unique()}, since both decode through the same path.
 */
class DecodeFailureTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);
  private static final String DOMAIN = "test:decode";

  @Test
  void storedThrowsOnAnUndecodableStoredValue() {
    InMemoryMappingStore store = new InMemoryMappingStore();
    AlterEgo eg = AlterEgo.builder().salt(SALT).mappingStore(store).build();
    Strategy<LocalDate> strategy = (in, ctx) -> in;
    Transformation<LocalDate> t = eg.bind(DOMAIN, LocalDate.class, strategy).stored();

    LocalDate input = LocalDate.of(2026, 1, 1);
    String key = Derivation.mapKey(SALT, DOMAIN, input.toString(), false);
    store.putIfAbsent(DOMAIN, key, "not-a-valid-date");

    AlterEgoStoreException ex = assertThrows(AlterEgoStoreException.class, () -> t.apply(input));
    assertTrue(ex.getMessage().contains("not-a-valid-date"), "message should name the bad value: " + ex.getMessage());
  }

  @Test
  void uniqueThrowsOnAnUndecodableExistingMapping() {
    InMemoryMappingStore store = new InMemoryMappingStore();
    AlterEgo eg = AlterEgo.builder().salt(SALT).mappingStore(store).build();
    Strategy<LocalDate> strategy = (in, ctx) -> in;
    Transformation<LocalDate> t = eg.bind(DOMAIN, LocalDate.class, strategy).unique();

    LocalDate input = LocalDate.of(2026, 1, 1);
    String key = Derivation.mapKey(SALT, DOMAIN, input.toString(), false);
    store.putIfAbsent(DOMAIN, key, "not-a-valid-date");

    assertThrows(AlterEgoStoreException.class, () -> t.apply(input));
  }
}
