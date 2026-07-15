package io.github.dconneely.alterego.strategy;

import io.github.dconneely.alterego.AlterEgoConfigException;
import io.github.dconneely.alterego.Randomness;
import io.github.dconneely.alterego.Strategy;
import io.github.dconneely.alterego.TransformationContext;
import java.util.List;
import java.util.Set;

/**
 * Generates a UK-format postcode (SPECIFICATION.md section 4.1, section 4.3): a plausible
 * outward code (one of the common shapes {@code A9}, {@code A99}, {@code AA9}, {@code AA99}) and
 * an inward code (one digit, two letters). By default the inward code's last letter is drawn
 * only from {@code C I K M O V} — letters Royal Mail never uses there — so the output is
 * guaranteed to never be a real, deliverable postcode (ADR 0005). {@code realistic} opts out,
 * drawing that letter from the full alphabet instead.
 *
 * <p>v1 does not generate the rarer outward shapes with a trailing district letter (e.g.
 * {@code W1A}, {@code EC1A}) or couple the outward code to any real postcode area; the outward
 * code and the town/postcode-area tie-in are M5's record-coherence job (spec section 6.3),
 * documented as a v1 scope limitation, not attempted here.
 */
public final class PostcodeStrategy implements Strategy<String> {

  private static final Set<String> SUPPORTED_COUNTRIES = Set.of("GB");
  private static final List<Character> NEVER_USED_LETTERS = List.of('C', 'I', 'K', 'M', 'O', 'V');

  private final boolean realistic;

  private PostcodeStrategy(boolean realistic) {
    this.realistic = realistic;
  }

  public static PostcodeStrategy forCountry(String country, boolean realistic) {
    if (!SUPPORTED_COUNTRIES.contains(country)) {
      throw new AlterEgoConfigException("No postcode format defined for country '" + country + "'");
    }
    return new PostcodeStrategy(realistic);
  }

  @Override
  public String transform(String input, TransformationContext context) {
    Randomness random = context.random();
    String outward = outwardCode(random);
    char sector = random.digit();
    char unitFirst = random.letterUpper();
    char unitLast = realistic ? random.letterUpper() : random.pick(NEVER_USED_LETTERS);
    return outward + " " + sector + unitFirst + unitLast;
  }

  private static String outwardCode(Randomness random) {
    int shape = random.nextInt(4);
    boolean twoLetters = shape == 2 || shape == 3;
    boolean twoDigits = shape == 1 || shape == 3;

    StringBuilder outward = new StringBuilder();
    outward.append(random.letterUpper());
    if (twoLetters) {
      outward.append(random.letterUpper());
    }
    outward.append(random.digit());
    if (twoDigits) {
      outward.append(random.digit());
    }
    return outward.toString();
  }
}
