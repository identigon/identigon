package io.github.dconneely.alterego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@code postcode()}: format shape and the section 4.1 fictionality guarantee (every
 * default-configured UK postcode's inward code ends in a letter never used in a real postcode),
 * plus the {@code PostcodeOptions.realistic()} opt-out.
 */
class PostcodeTest {

  private static final byte[] SALT = "test-salt-at-least-16-bytes!!".getBytes(StandardCharsets.UTF_8);
  private static final Pattern SHAPE = Pattern.compile("[A-Z]{1,2}[0-9]{1,2} [0-9][A-Z]{2}");
  private static final Set<Character> NEVER_USED_LETTERS = Set.of('C', 'I', 'K', 'M', 'O', 'V');

  private static AlterEgo gb() {
    return AlterEgo.builder().salt(SALT).build(); // default locale is Locale.UK
  }

  @Test
  void matchesPlausibleGbShape() {
    Transformation<String> t = gb().postcode();
    for (int i = 0; i < 50; i++) {
      String result = t.apply("input-" + i);
      assertTrue(SHAPE.matcher(result).matches(), "unexpected shape: " + result);
    }
  }

  // The fictionality property test (every default output violates the inward-code rule, large
  // sample) now lives in FictionalityTest, alongside emailAddress()'s and phoneNumber()'s.

  @Test
  void realisticOptionCanProduceLettersOutsideTheNeverUsedSet() {
    // Not a guarantee either way — realistic() only lifts the restriction, drawing from the
    // full alphabet, so across enough samples at least one should fall outside the fictional set.
    Transformation<String> t = gb().postcode(PostcodeOptions.realistic());
    boolean sawOutsideNeverUsedSet = false;
    for (int i = 0; i < 200; i++) {
      String result = t.apply("input-" + i);
      char lastLetter = result.charAt(result.length() - 1);
      if (!NEVER_USED_LETTERS.contains(lastLetter)) {
        sawOutsideNeverUsedSet = true;
        break;
      }
    }
    assertTrue(sawOutsideNeverUsedSet, "realistic() never produced a letter outside C I K M O V");
  }

  @Test
  void isDeterministic() {
    Transformation<String> t = gb().postcode();
    assertEquals(t.apply("original"), gb().postcode().apply("original"));
  }

  @Test
  void missingCountryFailsFastAtFactoryCallTime() {
    AlterEgo noCountry = AlterEgo.builder().salt(SALT).locale(Locale.of("en")).build();
    assertThrows(AlterEgoConfigException.class, noCountry::postcode);
  }

  @Test
  void noFormatDefinedForUnsupportedCountryFailsFast() {
    AlterEgo us = AlterEgo.builder().salt(SALT).locale(Locale.US).build();
    assertThrows(AlterEgoConfigException.class, us::postcode);
  }

  @Test
  void nonAsciiAndEmptyInputsAreHandled() {
    Transformation<String> t = gb().postcode();
    assertTrue(SHAPE.matcher(t.apply("Örebro Müller 東京")).matches());
    assertTrue(SHAPE.matcher(t.apply("")).matches());
  }
}
