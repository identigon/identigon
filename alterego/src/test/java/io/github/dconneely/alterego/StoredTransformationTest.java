package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.dconneely.alterego.store.InMemoryMappingStore;
import io.github.dconneely.alterego.store.MappingStore;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Exercises {@code stored()} (spec section 5.2): persists and reuses input-to-output mappings. */
class StoredTransformationTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);

  private static AlterEgo alterego(MappingStore store) {
    return AlterEgo.builder().salt(SALT).mappingStore(store).build();
  }

  @Test
  void reusesTheStoredOutputOnRepeatedCallsEvenIfTheStrategyChanges() {
    // A strategy that returns a fresh, ever-incrementing value each call: if stored() is truly
    // caching rather than re-running the strategy, every call after the first must return the
    // exact same (first) value regardless of the strategy's own non-determinism.
    AtomicInteger counter = new AtomicInteger();
    Strategy<String> everIncrementing = (in, ctx) -> "gen-" + counter.getAndIncrement();
    Transformation<String> t = alterego(new InMemoryMappingStore()).bind("test:stored", everIncrementing).stored();

    String first = t.apply("alice");
    for (int i = 0; i < 5; i++) {
      assertEquals(first, t.apply("alice"));
    }
  }

  @Test
  void differentInputsGetIndependentStoredValues() {
    AtomicInteger counter = new AtomicInteger();
    Strategy<String> everIncrementing = (in, ctx) -> "gen-" + counter.getAndIncrement();
    Transformation<String> t = alterego(new InMemoryMappingStore()).bind("test:stored", everIncrementing).stored();

    String forAlice = t.apply("alice");
    String forBob = t.apply("bob");
    assertNotEquals(forAlice, forBob);
    assertEquals(forAlice, t.apply("alice"));
    assertEquals(forBob, t.apply("bob"));
  }

  @Test
  void persistsAcrossSeparateTransformationInstancesSharingTheSameStore() {
    MappingStore sharedStore = new InMemoryMappingStore();
    AtomicInteger counter = new AtomicInteger();
    Strategy<String> everIncrementing = (in, ctx) -> "gen-" + counter.getAndIncrement();

    Transformation<String> first = alterego(sharedStore).bind("test:stored", everIncrementing).stored();
    String value = first.apply("alice");

    Transformation<String> second = alterego(sharedStore).bind("test:stored", everIncrementing).stored();
    assertEquals(value, second.apply("alice"));
  }

  @Test
  void isIdempotent() {
    AtomicInteger counter = new AtomicInteger();
    Strategy<String> everIncrementing = (in, ctx) -> "gen-" + counter.getAndIncrement();
    Transformation<String> t =
        alterego(new InMemoryMappingStore()).bind("test:stored", everIncrementing).stored().stored();
    String first = t.apply("alice");
    assertEquals(first, t.apply("alice"));
  }

  @Test
  void nullStillPassesThroughWithoutTouchingTheStore() {
    Strategy<String> shouldNotRun = (in, ctx) -> {
      throw new AssertionError("strategy should not run for a null input");
    };
    Transformation<String> t = alterego(new InMemoryMappingStore()).bind("test:stored", shouldNotRun).stored();
    assertNull(t.apply(null));
  }
}
