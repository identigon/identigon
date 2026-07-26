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
 * {@code W1A}, {@code EC1A}). Inside an active record scope, the outward code's letters come
 * from the fixed (or, if this is the first field to touch the record's place, freshly
 * established from a real town — {@link PlaceCoherence}) {@code UK_POSTCODE_AREA} (section 6.3);
 * outside any scope, this is exactly the unconstrained shape/letter generation of M2, unchanged.
 */
public final class PostcodeStrategy implements Strategy<String> {

  private static final Set<String> SUPPORTED_COUNTRIES = Set.of("GB");
  private static final List<Character> NEVER_USED_LETTERS = List.of('C', 'I', 'K', 'M', 'O', 'V');

  private final String country;
  private final boolean realistic;

  private PostcodeStrategy(String country, boolean realistic) {
    this.country = country;
    this.realistic = realistic;
  }

  /**
   * Creates a strategy for {@code country}.
   *
   * @param country the ISO 3166-1 alpha-2 country to generate postcodes for
   * @param realistic whether to opt out of the fictionality guarantee
   * @return a strategy for that country
   */
  public static PostcodeStrategy forCountry(String country, boolean realistic) {
    if (!SUPPORTED_COUNTRIES.contains(country)) {
      throw new AlterEgoConfigException("No postcode format defined for country '" + country + "'");
    }
    return new PostcodeStrategy(country, realistic);
  }

  @Override
  public String transform(String input, TransformationContext context) {
    Randomness random = context.random();
    String outward =
        context.record().isActive()
            ? outwardCodeForArea(PlaceCoherence.establish(context, country), random)
            : outwardCode(random);
    char sector = random.digit();
    char unitFirst = random.letterUpper();
    char unitLast = realistic ? random.letterUpper() : random.pick(NEVER_USED_LETTERS);
    return outward + " " + sector + unitFirst + unitLast;
  }

  /**
   * Builds the outward code from an already-fixed record area (section 6.3): the area's own
   * letters, verbatim, plus a freshly-drawn 1 or 2 trailing digits (uniform choice between the
   * two valid shapes) — an additional path alongside {@link #outwardCode}, which stays
   * byte-for-byte unchanged for the no-fixed-area case.
   */
  private static String outwardCodeForArea(String area, Randomness random) {
    boolean twoDigits = random.nextBoolean();
    StringBuilder outward = new StringBuilder(area);
    outward.append(random.digit());
    if (twoDigits) {
      outward.append(random.digit());
    }
    return outward.toString();
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
