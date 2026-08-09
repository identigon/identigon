package org.identigon.alterego.pattern;

import java.util.ArrayList;
import java.util.List;
import org.identigon.alterego.AlterEgoPatternException;
import org.identigon.alterego.Randomness;
import org.identigon.alterego.Strategy;
import org.identigon.alterego.TransformationContext;

/**
 * Compiles and applies the pattern language of SPECIFICATION.md section 4.6: {@code D} (digit),
 * {@code L}/{@code l} (upper/lower letter), {@code A} (letter, either case), {@code \x} (literal
 * {@code x}), and any other character (copied as a literal). Compilation happens once, at
 * {@link #compile(String)} time, not per element.
 */
public final class PatternStrategy implements Strategy<String> {

  private final List<PatternToken> tokens;

  private PatternStrategy(List<PatternToken> tokens) {
    this.tokens = tokens;
  }

  /**
   * Compiles {@code pattern}, throwing {@link AlterEgoPatternException} if it is malformed.
   *
   * @param pattern the pattern text (section 4.6)
   * @return the compiled strategy
   */
  public static PatternStrategy compile(String pattern) {
    List<PatternToken> tokens = new ArrayList<>();
    int i = 0;
    while (i < pattern.length()) {
      char c = pattern.charAt(i);
      if (c == '\\') {
        if (i + 1 >= pattern.length()) {
          throw new AlterEgoPatternException(
              "Trailing '\\' with no character to escape", i);
        }
        tokens.add(new PatternToken.Literal(pattern.charAt(i + 1)));
        i += 2;
      } else {
        tokens.add(tokenFor(c));
        i += 1;
      }
    }
    return new PatternStrategy(List.copyOf(tokens));
  }

  private static PatternToken tokenFor(char c) {
    return switch (c) {
      case 'D' -> new PatternToken.RandomDigit();
      case 'L' -> new PatternToken.RandomUpper();
      case 'l' -> new PatternToken.RandomLower();
      case 'A' -> new PatternToken.RandomLetter();
      default -> new PatternToken.Literal(c);
    };
  }

  @Override
  public String transform(String input, TransformationContext context) {
    Randomness random = context.random();
    StringBuilder sb = new StringBuilder(tokens.size());
    for (PatternToken token : tokens) {
      sb.append(render(token, random));
    }
    return sb.toString();
  }

  private static char render(PatternToken token, Randomness random) {
    return switch (token) {
      case PatternToken.RandomDigit _ -> random.digit();
      case PatternToken.RandomUpper _ -> random.letterUpper();
      case PatternToken.RandomLower _ -> random.letterLower();
      case PatternToken.RandomLetter _ -> randomLetterEitherCase(random);
      case PatternToken.Literal(char value) -> value;
    };
  }

  /** Appendix A.3: {@code k = nextInt(52); k < 26 ? 'A' + k : 'a' + (k - 26)}. */
  private static char randomLetterEitherCase(Randomness random) {
    int k = random.nextInt(52);
    return k < 26 ? (char) ('A' + k) : (char) ('a' + (k - 26));
  }
}
