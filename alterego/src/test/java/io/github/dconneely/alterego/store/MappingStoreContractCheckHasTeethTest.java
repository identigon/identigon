package io.github.dconneely.alterego.store;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Proves {@link MappingStoreContractTest#runConcurrencyCheck} actually detects a broken,
 * non-atomic {@code putIfAbsentUnique} rather than passing vacuously against any store — a real
 * requirement, not just an inherited test that happens to be green (spec section 10: "a
 * deliberately broken fake store... fails at least one contract test").
 */
class MappingStoreContractCheckHasTeethTest {

  @Test
  void detectsANonAtomicPutIfAbsentUnique() throws InterruptedException {
    MappingStoreContractTest.ConcurrencyCheckResult result =
        MappingStoreContractTest.runConcurrencyCheck(new NonAtomicFakeMappingStore(), 64);
    assertFalse(
        result.noDuplicateValues() && result.noLostKeys(),
        "the non-atomic fake store should have failed the concurrency check, but didn't: " + result);
  }

  /**
   * Deliberately non-atomic: {@code putIfAbsentUnique} checks both maps, then sleeps (widening
   * the race window so the violation reproduces reliably rather than depending on unlucky
   * timing), and only then writes — the textbook check-then-act race the real atomicity
   * requirement exists to rule out.
   */
  private static final class NonAtomicFakeMappingStore implements MappingStore {
    private final Map<String, String> forward = new HashMap<>();
    private final Map<String, String> inverse = new HashMap<>();

    @Override
    public synchronized Optional<String> get(String namespace, String key) {
      return Optional.ofNullable(forward.get(key));
    }

    @Override
    public synchronized String putIfAbsent(String namespace, String key, String value) {
      return forward.putIfAbsent(key, value) == null ? value : forward.get(key);
    }

    @Override
    public PutUniqueResult putIfAbsentUnique(String namespace, String key, String value) {
      synchronized (this) {
        if (forward.containsKey(key)) {
          return new PutUniqueResult.ExistingMapping(forward.get(key));
        }
        if (inverse.containsKey(value)) {
          return new PutUniqueResult.ValueTaken();
        }
      }
      try {
        Thread.sleep(5);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      synchronized (this) {
        forward.put(key, value);
        inverse.put(value, key);
      }
      return new PutUniqueResult.Stored();
    }
  }
}
