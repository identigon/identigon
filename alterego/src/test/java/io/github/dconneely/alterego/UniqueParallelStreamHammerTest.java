package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.dconneely.alterego.store.InMemoryMappingStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * The M4 milestone gate (docs/tasks/M4.md step 6, spec section 10): a real {@code unique()}
 * transformation, backed by {@link InMemoryMappingStore}, driven through a parallel stream —
 * shows no duplicate outputs and no lost mappings under genuine concurrent load, not just via
 * the store's own contract test.
 */
class UniqueParallelStreamHammerTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);

  @Test
  void parallelStreamThroughUniqueProducesNoDuplicatesAndNoLostMappings() {
    AlterEgo eg = AlterEgo.builder().salt(SALT).mappingStore(new InMemoryMappingStore()).build();
    // pattern("DDD") has 1000 possible outputs — comfortably more than the 300 distinct inputs
    // below, so this exercises real (but not exhaustion-forcing) collision retries under
    // concurrency without risking a spurious AlterEgoCollisionException.
    Transformation<String> t = eg.pattern("DDD").unique();

    List<String> inputs = IntStream.range(0, 300).mapToObj(i -> "input-" + i).toList();
    List<String> outputs = inputs.parallelStream().map(t).collect(Collectors.toList());

    assertEquals(inputs.size(), outputs.size(), "lost a mapping: fewer outputs than inputs");
    assertFalse(outputs.contains(null), "a null output was produced");
    assertEquals(
        inputs.size(), Set.copyOf(outputs).size(), "duplicate output detected across parallel unique() calls");
  }
}
