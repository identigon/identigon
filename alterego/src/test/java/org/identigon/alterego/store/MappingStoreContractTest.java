package org.identigon.alterego.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.identigon.alterego.store.MappingStore.PutUniqueResult;
import org.junit.jupiter.api.Test;

/**
 * Reusable {@link MappingStore} SPI contract (SPECIFICATION.md section 5.1, section 10): any
 * implementation — the in-memory one shipped in M4, or a future external one (JDBC, file-backed)
 * — can subclass this and inherit the same tests. Covers {@code get}/{@code putIfAbsent}
 * semantics, {@code putIfAbsentUnique}'s three outcomes, namespace isolation, and the atomicity
 * of {@code putIfAbsentUnique} under concurrent callers.
 */
public abstract class MappingStoreContractTest {

  protected abstract MappingStore createStore();

  @Test
  void getReturnsEmptyForUnknownKey() {
    assertEquals(Optional.empty(), createStore().get("ns", "unknown"));
  }

  @Test
  void putIfAbsentStoresANewKey() {
    MappingStore store = createStore();
    assertEquals("value", store.putIfAbsent("ns", "key", "value"));
    assertEquals(Optional.of("value"), store.get("ns", "key"));
  }

  @Test
  void putIfAbsentDoesNotOverwriteAnExistingKey() {
    MappingStore store = createStore();
    store.putIfAbsent("ns", "key", "first");
    assertEquals("first", store.putIfAbsent("ns", "key", "second"));
    assertEquals(Optional.of("first"), store.get("ns", "key"));
  }

  @Test
  void putIfAbsentUniqueStoresANewMapping() {
    MappingStore store = createStore();
    PutUniqueResult result = store.putIfAbsentUnique("ns", "key", "value");
    assertInstanceOf(PutUniqueResult.Stored.class, result);
    assertEquals(Optional.of("value"), store.get("ns", "key"));
  }

  @Test
  void putIfAbsentUniqueReturnsExistingMappingForAnAlreadyMappedKey() {
    MappingStore store = createStore();
    store.putIfAbsentUnique("ns", "key", "value");
    PutUniqueResult result = store.putIfAbsentUnique("ns", "key", "different-value");
    assertEquals(new PutUniqueResult.ExistingMapping("value"), result);
    // The second call must not have overwritten the first mapping.
    assertEquals(Optional.of("value"), store.get("ns", "key"));
  }

  @Test
  void putIfAbsentUniqueReturnsValueTakenWhenTheValueIsUsedByAnotherKey() {
    MappingStore store = createStore();
    store.putIfAbsentUnique("ns", "key-a", "value");
    PutUniqueResult result = store.putIfAbsentUnique("ns", "key-b", "value");
    assertInstanceOf(PutUniqueResult.ValueTaken.class, result);
    assertEquals(Optional.empty(), store.get("ns", "key-b"));
  }

  @Test
  void namespacesAreIndependent() {
    MappingStore store = createStore();
    store.putIfAbsentUnique("ns-a", "key", "value");
    // Same key AND same value in a different namespace must not collide with namespace "ns-a".
    PutUniqueResult result = store.putIfAbsentUnique("ns-b", "key", "value");
    assertInstanceOf(PutUniqueResult.Stored.class, result);
    assertEquals(Optional.of("value"), store.get("ns-a", "key"));
    assertEquals(Optional.of("value"), store.get("ns-b", "key"));
  }

  @Test
  void concurrentPutIfAbsentUniqueNeverProducesDuplicateValuesOrLosesMappings() throws InterruptedException {
    ConcurrencyCheckResult result = runConcurrencyCheck(createStore(), 64);
    assertTrue(result.noDuplicateValues(), "duplicate value detected: " + result);
    assertTrue(result.noLostKeys(), "lost key detected: " + result);
  }

  /**
   * Hammers {@code putIfAbsentUnique} with {@code threadCount} threads, all racing on one
   * initially-contested shared value with distinct keys (exactly one should win it), then each
   * loser retrying with its own guaranteed-distinct fallback value (simulating {@code unique()}'s
   * collision-escape retry at the raw-store level). A correct, atomic implementation always ends
   * with every key mapped and every mapped value distinct. Exposed (not private) so a test can
   * run it against a deliberately non-atomic fake store and confirm the check actually detects
   * the violation, rather than passing vacuously.
   */
  static ConcurrencyCheckResult runConcurrencyCheck(MappingStore store, int threadCount) throws InterruptedException {
    String namespace = "concurrency-check";
    String sharedValue = "contested-value";
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    try {
      List<? extends Future<?>> futures =
          IntStream.range(0, threadCount)
              .mapToObj(
                  i ->
                      pool.submit(
                          () -> {
                            awaitUninterruptibly(start);
                            String key = "key-" + i;
                            PutUniqueResult first = store.putIfAbsentUnique(namespace, key, sharedValue);
                            if (!(first instanceof PutUniqueResult.Stored)) {
                              store.putIfAbsentUnique(namespace, key, "fallback-value-" + i);
                            }
                          }))
              .toList();
      start.countDown();
      for (Future<?> future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }
    } catch (Exception e) {
      throw new AssertionError("concurrency check failed to run to completion", e);
    } finally {
      pool.shutdown();
    }

    List<String> mappedValues =
        IntStream.range(0, threadCount)
            .mapToObj(i -> store.get(namespace, "key-" + i))
            .flatMap(Optional::stream)
            .toList();
    boolean noLostKeys = mappedValues.size() == threadCount;
    boolean noDuplicateValues = mappedValues.size() == Set.copyOf(mappedValues).size();
    return new ConcurrencyCheckResult(noDuplicateValues, noLostKeys, mappedValues);
  }

  private static void awaitUninterruptibly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  record ConcurrencyCheckResult(boolean noDuplicateValues, boolean noLostKeys, List<String> mappedValues) {}
}
