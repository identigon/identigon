package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class HmacRandomnessTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);

  private static Randomness fresh(String canonical) {
    return Derivation.randomness(SALT, "test:domain", canonical, 0);
  }

  @Test
  void sameKeyProducesSameSequence() {
    Randomness a = fresh("x");
    Randomness b = fresh("x");
    for (int i = 0; i < 50; i++) {
      assertEquals(a.nextInt(1000), b.nextInt(1000));
    }
  }

  @Test
  void differentInputsProduceDifferentSequences() {
    Randomness a = fresh("x");
    Randomness b = fresh("y");
    boolean anyDifferent = false;
    for (int i = 0; i < 20; i++) {
      if (a.nextInt(Integer.MAX_VALUE) != b.nextInt(Integer.MAX_VALUE)) {
        anyDifferent = true;
      }
    }
    assertTrue(anyDifferent);
  }

  @Test
  void nextIntRejectsNonPositiveBound() {
    Randomness r = fresh("x");
    assertThrows(IllegalArgumentException.class, () -> r.nextInt(0));
    assertThrows(IllegalArgumentException.class, () -> r.nextInt(-1));
  }

  @Test
  void nextLongRejectsNonPositiveBound() {
    Randomness r = fresh("x");
    assertThrows(IllegalArgumentException.class, () -> r.nextLong(0));
    assertThrows(IllegalArgumentException.class, () -> r.nextLong(-1));
  }

  @Test
  void pickRejectsEmptyList() {
    Randomness r = fresh("x");
    assertThrows(IllegalArgumentException.class, () -> r.pick(List.of()));
  }

  @Test
  void digitIsAsciiDigit() {
    Randomness r = fresh("x");
    for (int i = 0; i < 200; i++) {
      char c = r.digit();
      assertTrue(c >= '0' && c <= '9');
    }
  }

  @Test
  void letterUpperIsAsciiUpper() {
    Randomness r = fresh("x");
    for (int i = 0; i < 200; i++) {
      char c = r.letterUpper();
      assertTrue(c >= 'A' && c <= 'Z');
    }
  }

  @Test
  void letterLowerIsAsciiLower() {
    Randomness r = fresh("x");
    for (int i = 0; i < 200; i++) {
      char c = r.letterLower();
      assertTrue(c >= 'a' && c <= 'z');
    }
  }

  @Test
  void pickReturnsOnlyListElements() {
    Randomness r = fresh("x");
    List<String> choices = List.of("a", "b", "c");
    for (int i = 0; i < 200; i++) {
      assertTrue(choices.contains(r.pick(choices)));
    }
  }

  @Test
  void streamAdvancesAcrossMultipleHmacBlocks() {
    // A block is 32 bytes = 4 long draws; force well over 10 blocks to exercise lazy block
    // regeneration in HmacRandomness.nextBlock().
    Randomness r = fresh("x");
    for (int i = 0; i < 500; i++) {
      r.nextLong(Long.MAX_VALUE);
    }
  }

  @Property
  void nextIntStaysWithinBound(@ForAll @IntRange(min = 1, max = 1_000_000) int bound) {
    Randomness r = fresh("property-" + bound);
    for (int i = 0; i < 20; i++) {
      int v = r.nextInt(bound);
      assertTrue(v >= 0 && v < bound);
    }
  }

  @Property
  void nextBooleanMatchesNextLongOfTwoEqualsOne(@ForAll("seeds") String seed) {
    Randomness a = fresh(seed);
    Randomness b = fresh(seed);
    assertEquals(b.nextLong(2) == 1, a.nextBoolean());
  }

  @net.jqwik.api.Provide
  net.jqwik.api.Arbitrary<String> seeds() {
    return net.jqwik.api.Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10);
  }
}
