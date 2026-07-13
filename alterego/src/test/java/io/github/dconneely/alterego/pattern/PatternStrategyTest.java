package io.github.dconneely.alterego.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.dconneely.alterego.AlterEgoPatternException;
import io.github.dconneely.alterego.Randomness;
import io.github.dconneely.alterego.TransformationContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatternStrategyTest {

  /**
   * A {@link Randomness} that returns one fixed value from {@code nextInt} and fails on any
   * other method, so a test can pin exactly which draw the 'A' token consumes.
   */
  private static Randomness fixedNextInt(int value) {
    return new ThrowingRandomness() {
      @Override
      public int nextInt(int bound) {
        return value;
      }
    };
  }

  /** A {@link Randomness} that fails on every method, proving a literal pattern never draws. */
  private static Randomness neverCalled() {
    return new ThrowingRandomness();
  }

  private static class ThrowingRandomness implements Randomness {
    @Override
    public int nextInt(int bound) {
      throw new AssertionError("unexpected nextInt call");
    }

    @Override
    public long nextLong(long bound) {
      throw new AssertionError("unexpected nextLong call");
    }

    @Override
    public boolean nextBoolean() {
      throw new AssertionError("unexpected nextBoolean call");
    }

    @Override
    public <T> T pick(List<T> choices) {
      throw new AssertionError("unexpected pick call");
    }

    @Override
    public char digit() {
      throw new AssertionError("unexpected digit call");
    }

    @Override
    public char letterUpper() {
      throw new AssertionError("unexpected letterUpper call");
    }

    @Override
    public char letterLower() {
      throw new AssertionError("unexpected letterLower call");
    }
  }

  private static String render(String pattern, Randomness random) {
    TransformationContext ctx = new FakeContext(random);
    return PatternStrategy.compile(pattern).transform("input-ignored", ctx);
  }

  // --- Appendix A.3 token 'A': k = nextInt(52); k < 26 ? 'A' + k : 'a' + (k - 26) -------------

  @Test
  void tokenAMapsZeroToUpperA() {
    assertEquals("A", render("A", fixedNextInt(0)));
  }

  @Test
  void tokenAMapsTwentyFiveToUpperZ() {
    assertEquals("Z", render("A", fixedNextInt(25)));
  }

  @Test
  void tokenAMapsTwentySixToLowerA() {
    assertEquals("a", render("A", fixedNextInt(26)));
  }

  @Test
  void tokenAMapsFiftyOneToLowerZ() {
    assertEquals("z", render("A", fixedNextInt(51)));
  }

  @Test
  void tokenAMapsMidUpperRange() {
    assertEquals("M", render("A", fixedNextInt(12)));
  }

  @Test
  void tokenAMapsMidLowerRange() {
    assertEquals("n", render("A", fixedNextInt(39)));
  }

  // --- literals never touch randomness ----------------------------------------------------

  @Test
  void plainLiteralPatternNeverCallsRandomness() {
    // None of these characters are D/L/l/A, so this exercises the pure-literal path.
    assertEquals("GU12 4XY", render("GU12 4XY", neverCalled()));
  }

  @Test
  void escapedTokenCharacterIsLiteral() {
    assertEquals("DLlA", render("\\D\\L\\l\\A", neverCalled()));
  }

  @Test
  void escapedBackslashIsLiteral() {
    assertEquals("\\", render("\\\\", neverCalled()));
  }

  @Test
  void emptyPatternCompilesToEmptyOutput() {
    assertEquals("", render("", neverCalled()));
  }

  // --- compile-time errors -------------------------------------------------------------------

  @Test
  void trailingBackslashThrowsWithPosition() {
    AlterEgoPatternException ex =
        assertThrows(AlterEgoPatternException.class, () -> PatternStrategy.compile("DL\\"));
    assertEquals(2, ex.position());
  }

  @Test
  void trailingBackslashAtStartThrowsWithPositionZero() {
    AlterEgoPatternException ex =
        assertThrows(AlterEgoPatternException.class, () -> PatternStrategy.compile("\\"));
    assertEquals(0, ex.position());
  }
}
