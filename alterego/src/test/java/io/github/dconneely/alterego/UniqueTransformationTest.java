package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.dconneely.alterego.store.InMemoryMappingStore;
import io.github.dconneely.alterego.store.MappingStore;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@code unique()} (spec section 5.3): the retry loop, the collision-exhaustion
 * exception, and the order-independence caveat (undecorated transformations don't care about
 * order; {@code unique()} is the documented exception only when two inputs' natural candidates
 * actually collide).
 */
class UniqueTransformationTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);

  private static AlterEgo alterego(MappingStore store) {
    return AlterEgo.builder().salt(SALT).mappingStore(store).build();
  }

  @Test
  void exhaustsATinyOutputSpaceAndThrowsCollisionException() {
    // pattern("D") has exactly 10 possible outputs; the 11th distinct input can never find a
    // free one once all 10 are taken.
    AlterEgo eg = alterego(new InMemoryMappingStore());
    Transformation<String> t = eg.pattern("D").unique();
    for (int i = 0; i < 10; i++) {
      assertNotNull(t.apply("input-" + i));
    }
    assertThrows(AlterEgoCollisionException.class, () -> t.apply("input-10"));
  }

  @Test
  void distinctInputsNeverMapToTheSameOutputEvenWhenNaturalCandidatesCollide() {
    Strategy<String> pickOfTwo = (in, ctx) -> ctx.random().pick(List.of("X", "Y"));
    String[] colliding = findNaturallyCollidingPair(pickOfTwo);

    Transformation<String> t = alterego(new InMemoryMappingStore()).bind("test:collide", pickOfTwo).unique();
    String outputA = t.apply(colliding[0]);
    String outputB = t.apply(colliding[1]);
    assertNotEquals(outputA, outputB);
  }

  @Test
  void wheneverACollisionOccursWhicheverInputIsProcessedFirstKeepsItsNaturalCandidate() {
    Strategy<String> pickOfTwo = (in, ctx) -> ctx.random().pick(List.of("X", "Y"));
    String[] colliding = findNaturallyCollidingPair(pickOfTwo);
    String naturalCandidate =
        alterego(new InMemoryMappingStore()).bind("test:collide", pickOfTwo).apply(colliding[0]);

    // Processed first: colliding[0] keeps the natural candidate; colliding[1] is re-derived.
    Transformation<String> firstOrder =
        alterego(new InMemoryMappingStore()).bind("test:collide", pickOfTwo).unique();
    assertEquals(naturalCandidate, firstOrder.apply(colliding[0]));
    assertNotEquals(naturalCandidate, firstOrder.apply(colliding[1]));

    // Reversed: colliding[1] is processed first and keeps the natural candidate for *its* input
    // (which, since both naturally collide to the same value, is the same literal value);
    // colliding[0] is now the one re-derived away from it.
    Transformation<String> secondOrder =
        alterego(new InMemoryMappingStore()).bind("test:collide", pickOfTwo).unique();
    assertEquals(naturalCandidate, secondOrder.apply(colliding[1]));
    assertNotEquals(naturalCandidate, secondOrder.apply(colliding[0]));
  }

  @Test
  void isDeterministicAndOrderIndependentAbsentAnyCollision() {
    // Distinct inputs whose *natural* candidates already differ: unique() changes nothing, and
    // processing order doesn't affect the result (section 3.1's general order-independence,
    // undisturbed because no collision occurs here).
    Strategy<String> strategy = (in, ctx) -> ctx.random().pick(List.of("Alice", "Bob", "Carol", "Dave", "Eve"));
    String[] nonColliding = findNonCollidingPair(strategy);

    Transformation<String> orderOne = alterego(new InMemoryMappingStore()).bind("test:order", strategy).unique();
    String a1 = orderOne.apply(nonColliding[0]);
    String b1 = orderOne.apply(nonColliding[1]);

    Transformation<String> orderTwo = alterego(new InMemoryMappingStore()).bind("test:order", strategy).unique();
    String b2 = orderTwo.apply(nonColliding[1]);
    String a2 = orderTwo.apply(nonColliding[0]);

    assertEquals(a1, a2);
    assertEquals(b1, b2);
  }

  /**
   * Brute-force search (deterministic: fixed salt/domain/strategy) for two distinct inputs whose
   * undecorated outputs happen to be equal, so a test can exercise {@code unique()}'s actual
   * collision-escape path rather than only its (overwhelmingly common) no-collision path.
   */
  private static String[] findNaturallyCollidingPair(Strategy<String> strategy) {
    Transformation<String> undecorated = alterego(new InMemoryMappingStore()).bind("test:collide", strategy);
    Map<String, String> outputToInput = new HashMap<>();
    for (int i = 0; i < 200; i++) {
      String input = "input-" + i;
      String output = undecorated.apply(input);
      String previousInput = outputToInput.putIfAbsent(output, input);
      if (previousInput != null) {
        return new String[] {previousInput, input};
      }
    }
    throw new AssertionError("no naturally colliding pair found in 200 samples");
  }

  /** The counterpart search: two distinct inputs whose undecorated outputs differ. */
  private static String[] findNonCollidingPair(Strategy<String> strategy) {
    Transformation<String> undecorated = alterego(new InMemoryMappingStore()).bind("test:order", strategy);
    String first = undecorated.apply("input-0");
    for (int i = 1; i < 200; i++) {
      String candidate = "input-" + i;
      if (!undecorated.apply(candidate).equals(first)) {
        return new String[] {"input-0", candidate};
      }
    }
    throw new AssertionError("no non-colliding pair found in 200 samples");
  }
}
